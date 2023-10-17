package ru.nsu.fit.dskvl.torrent.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class NetworkIO {
    private final Selector selector;
    private final ConnectionProcessor accepter;

    private final Map<SocketChannel, SelectionKey> keyMap = new HashMap<>();
    private final Map<SocketChannel, ArrayDeque<ByteBuffer>> queueMap = new HashMap<>();
    private final Thread thread = new Thread(this::work);

    public NetworkIO(ConnectionProcessor accepter, Selector selector) throws IOException {
        this.accepter = accepter;
        this.selector = selector;
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().bind(null);
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println(server.socket().getLocalSocketAddress().toString() + " is up.");
    }

    public NetworkIO(ConnectionProcessor accepter, Selector selector, int port) throws IOException {
        this.accepter = accepter;
        this.selector = selector;
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().bind(new InetSocketAddress("localhost", port));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println(server.socket().getLocalSocketAddress().toString() + " is up.");
    }

    public void start() {
        thread.start();
    }

    private void work() {
        while (true) {
            try {
                selector.select();
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }

            var iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                if (key.isAcceptable()) {
                    accepter.accept(this, (ServerSocketChannel) key.channel());
                }

                try {
                    if (key.isWritable()) {
                        send(key);
                    }

                    if (key.isReadable()) {
                        DataReader reader = (DataReader) key.attachment();
                        reader.read();
                    }

                } catch (IOException e) {
                    System.err.println(e.getMessage() + " " + key.channel());
                    key.cancel();
                    accepter.anErrorOccurred((SocketChannel) key.channel());
                }
                iterator.remove();
            }
        }
    }

    public void sendData(SocketChannel socketChannel, ByteBuffer buffer) {
        var queue = queueMap.get(socketChannel);
        synchronized (queueMap.get(socketChannel)) {
            queue.push(buffer);
            keyMap.get(socketChannel).interestOpsOr(SelectionKey.OP_WRITE);
        }

        selector.wakeup();
    }

    private void send(SelectionKey key) throws IOException {
        var socketChannel = (SocketChannel) key.channel();
        var queue = queueMap.get(socketChannel);
        try {
            synchronized (queueMap.get(socketChannel)) {
                var buffer = queue.pop();
                socketChannel.write(buffer);
                if (queue.isEmpty()) {
                    key.interestOpsAnd(SelectionKey.OP_READ);
                }
            }
        } catch (NoSuchElementException e) {
            key.interestOpsAnd(SelectionKey.OP_READ);
        }
    }


    public void addSocketChannel(SocketChannel socketChannel, DataReader dataReader) throws ClosedChannelException {
        var key = socketChannel.register(selector, SelectionKey.OP_READ);
        key.attach(dataReader);
        keyMap.put(socketChannel, key);
        queueMap.put(socketChannel, new ArrayDeque<>(32));
    }

    public void removeSocketChannel(SocketChannel socketChannel) {
        keyMap.get(socketChannel).cancel();
        keyMap.remove(socketChannel);
        queueMap.remove(socketChannel);
    }
}
