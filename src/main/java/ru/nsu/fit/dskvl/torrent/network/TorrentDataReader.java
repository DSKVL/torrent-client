package ru.nsu.fit.dskvl.torrent.network;

import ru.nsu.fit.dskvl.torrent.network.message.KeepaliveMessage;
import ru.nsu.fit.dskvl.torrent.network.message.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

class TorrentDataReader implements DataReader {
    private final SocketChannel socketChannel;
    private final PeerConnection peerConnection;
    private final TorrentNetwork torrentNetwork;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(2<<16);
    private int currentMessageLength = -1;

    TorrentDataReader(SocketChannel socketChannel, PeerConnection peerConnection, TorrentNetwork torrentNetwork) {
        this.socketChannel = socketChannel;
        this.peerConnection = peerConnection;
        this.torrentNetwork = torrentNetwork;
    }

    @Override
    public void read() throws IOException {
        int read = socketChannel.read(readBuffer);
        if (read == -1) {
            return;
        }
        readBuffer.flip();

        while (readBuffer.hasRemaining() && readBuffer.remaining() > currentMessageLength) {
            if (currentMessageLength == -1) {
                if (readBuffer.remaining() < 4) {
                    break;
                }
                currentMessageLength = readBuffer.getInt();
            }

            if(currentMessageLength == 0) {
                torrentNetwork.messageReceived(peerConnection, new KeepaliveMessage());
                currentMessageLength = -1;
                continue;
            }

            byte[] currentMessage = new byte[currentMessageLength];

            if (readBuffer.remaining() >= currentMessageLength) {
                readBuffer.get(currentMessage);
                currentMessageLength = -1;
                readBuffer.compact();
                readBuffer.flip();
                torrentNetwork.messageReceived(peerConnection, Message.buildMessage(currentMessage, peerConnection));
            }
        }

        readBuffer.compact();
    }

}
