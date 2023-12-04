package ru.nsu.fit.dskvl.torrent.network;

import ru.nsu.fit.dskvl.torrent.Torrent;
import ru.nsu.fit.dskvl.torrent.network.message.Message;
import ru.nsu.fit.dskvl.torrent.network.message.RequestMessage;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TorrentNetwork {
    private final Map<PeerConnection, SocketChannel> peerSocketMap = new ConcurrentHashMap<>(4);
    private TorrentAcceptor torrentAcceptor;
    private MessageProcessor messageProcessor;
    private NetworkIO networkIO;

    private final byte[] peerId;

    private TorrentNetwork() {
        StringBuilder peerIdBuilder = new StringBuilder("-TC0001-");
        Random random = new Random();
        for (int i = 0; i < 12; i++) {
            peerIdBuilder.append(random.nextInt(10));
        }
        peerId = peerIdBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public TorrentNetwork(TorrentAcceptor torrentAcceptor) throws IOException {
        this();
        this.torrentAcceptor = torrentAcceptor;
        var selector = Selector.open();
        networkIO = new NetworkIO(torrentAcceptor, selector);
    }

    public TorrentNetwork(TorrentAcceptor torrentAcceptor, int port) throws IOException {
        this();
        this.torrentAcceptor = torrentAcceptor;
        var selector = Selector.open();
        networkIO = new NetworkIO(torrentAcceptor, selector, port);
    }

    public void setMessageProcessor(MessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }

    public void connectToPeer(SocketAddress address, Torrent torrent) throws IOException {
        SocketChannel socketChannel;

        socketChannel = SocketChannel.open();
        socketChannel.connect(address);
        socketChannel.write(ByteBuffer.wrap(getHandshakeMessage(torrent)));

        var buffer = ByteBuffer.allocate(68);
        while (buffer.hasRemaining()) {
            socketChannel.read(buffer);
        }
        buffer.flip();

        PeerConnection peerConnection;
        if (torrentAcceptor.isHandshakeValid(buffer)) {
            peerConnection = torrentAcceptor.addConnection(socketChannel, torrent, networkIO);
            peerSocketMap.put(peerConnection, socketChannel);
        }
    }

    void addPeer(PeerConnection peerConnection, SocketChannel socketChannel) {
        peerSocketMap.put(peerConnection, socketChannel);
    }

    void messageReceived(PeerConnection peerConnection, Message message) {
        System.out.println("Received " + message.toString() + " from " + peerConnection.getAddress());
        message.accept(messageProcessor);
    }

    public void sendMessage(PeerConnection peerConnection, Message message) throws IOException {
        if (!peerSocketMap.containsKey(peerConnection)) {
            throw new IOException("Unable to send message to " + peerConnection.getAddress());
        }
        networkIO.sendData(peerSocketMap.get(peerConnection), message.getByteBuffer());
        System.out.println("Send " + message + " to " + peerConnection.getAddress().toString());
    }

    public void addTorrent(Torrent torrent) {
        torrentAcceptor.addTorrent(torrent);
    }

    public Optional<PeerConnection> getPeerBySocket(SocketChannel socketChannel) {
        return peerSocketMap.entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), socketChannel))
                .map(Map.Entry::getKey)
                .findAny();

    }

    public void removeConnection(SocketChannel socketChannel) {
        networkIO.removeSocketChannel(socketChannel);
        peerSocketMap.values().remove(socketChannel);
    }

    public byte[] getHandshakeMessage(Torrent torrent) {
        byte[] handshake = new byte[68];
        byte[] zeros = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        System.arraycopy(HandshakeStartLine, 0, handshake, 0, 20);
        System.arraycopy(zeros, 0, handshake, 20, 8);
        System.arraycopy(torrent.getInfoHash(), 0, handshake, 28, 20);
        System.arraycopy(peerId, 0, handshake, 48, 20);
        return handshake;
    }

    public void start() {
        networkIO.start();
    }

    public static final byte[] HandshakeStartLine = (((char) 19) + "BitTorrent protocol").getBytes(StandardCharsets.UTF_8);
}

