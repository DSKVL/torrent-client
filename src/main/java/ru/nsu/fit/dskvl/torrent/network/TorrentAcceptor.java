package ru.nsu.fit.dskvl.torrent.network;

import ru.nsu.fit.dskvl.torrent.Downloader;
import ru.nsu.fit.dskvl.torrent.Torrent;
import ru.nsu.fit.dskvl.torrent.Uploader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TorrentAcceptor implements ConnectionProcessor {
    private final Map<String, Torrent> torrentMap = new HashMap<>();

    private final Downloader downloader;
    private Uploader uploader;
    private TorrentNetwork torrentNetwork;

    public TorrentAcceptor(Downloader downloader) {
        this.downloader = downloader;
    }

    public void setTorrentNetwork(TorrentNetwork torrentNetwork) {
        this.torrentNetwork = torrentNetwork;
    }
    public void setUploader(Uploader uploader) { this.uploader = uploader; }

    public void addTorrent(Torrent torrent) {
        torrentMap.put(Base64.getEncoder().encodeToString(torrent.getInfoHash()), torrent);
    }

    @Override
    public void accept(NetworkIO networkIO, ServerSocketChannel channel) {
        SocketChannel peer;
        try {
            peer = channel.accept();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }
        var buffer = ByteBuffer.allocate(68);

        try {
            while (buffer.hasRemaining()) {
                peer.read(buffer);
            }
            buffer.flip();

            if (isHandshakeValid(buffer)) {
                var torrent = torrentMap.get(Base64.getEncoder().encodeToString(
                        Arrays.copyOfRange(buffer.array(), 28, 48)));

                peer.write(ByteBuffer.wrap(torrentNetwork.getHandshakeMessage(torrent)));
                addConnection(peer, torrent, networkIO);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void anErrorOccurred(SocketChannel socketChannel) {
        var peer = torrentNetwork.getPeerBySocket(socketChannel);
        torrentNetwork.removeConnection(socketChannel);
        peer.ifPresent(peerConnection -> {
            downloader.removePeerConnection(peerConnection.getTorrent(), peerConnection);
            uploader.removePeerConnection(peerConnection);
        });
    }

    PeerConnection addConnection(SocketChannel peer, Torrent torrent, NetworkIO networkIO) throws IOException {
        peer.configureBlocking(false);

        var address = peer.getRemoteAddress();
        PeerConnection connectionChannel = new PeerConnection(address, torrent);

        networkIO.addSocketChannel(peer, new TorrentDataReader(peer, connectionChannel, torrentNetwork));
        System.out.println(address + " is connected.");
        torrentNetwork.addPeer(connectionChannel, peer);
        downloader.addPeerConnection(torrent, connectionChannel);
        uploader.peerConnected(connectionChannel);
        return connectionChannel;
    }

    public boolean isHandshakeValid(ByteBuffer clientHandshake) {
        return Arrays.equals(TorrentNetwork.HandshakeStartLine,
                Arrays.copyOfRange(clientHandshake.array(), 0, 20)) &&
                torrentMap.containsKey(Base64.getEncoder().encodeToString(
                        Arrays.copyOfRange(clientHandshake.array(), 28, 48)));
    }

}
