package ru.nsu.fit.dskvl.torrent;

import ru.nsu.fit.dskvl.torrent.fileio.TorrentFiles;
import ru.nsu.fit.dskvl.torrent.network.PeerConnection;
import ru.nsu.fit.dskvl.torrent.network.TorrentNetwork;
import ru.nsu.fit.dskvl.torrent.network.message.BitfieldMessage;
import ru.nsu.fit.dskvl.torrent.network.message.ChokeMessage;
import ru.nsu.fit.dskvl.torrent.network.message.PieceMessage;
import ru.nsu.fit.dskvl.torrent.network.message.UnchokeMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Uploader {
    private final TorrentFiles torrentFiles;
    private final TorrentNetwork torrentNetwork;
    private final ConcurrentLinkedDeque<Request> requests = new ConcurrentLinkedDeque<>();
    private final Set<Request> pendingRequests = ConcurrentHashMap.newKeySet(FileDownloader.MAX_PENDING_REQUESTS * 2);
    private final Set<PeerConnection> chockedPeers = ConcurrentHashMap.newKeySet(8);

    private final Thread thread = new Thread(this::work);

    public Uploader(TorrentFiles torrentFiles, TorrentNetwork torrentNetwork) {
        this.torrentFiles = torrentFiles;
        this.torrentNetwork = torrentNetwork;
    }

    public void start() {
        thread.start();
    }

    public void work() {
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            if (!requests.isEmpty()) {
                var request = requests.pop();
                var index = request.index();
                var begin = request.begin();
                var torrent = request.torrent();

                if (!torrent.hasPiece(index)) {
                    return;
                }

                var dst = ByteBuffer.allocate(request.length());
                pendingRequests.add(request);

                torrentFiles.readPiece(dst, torrent, request.index(), request.begin(), () -> {
                    try {
                        if (!pendingRequests.contains(request)) {
                            return;
                        }
                        torrentNetwork.sendMessage(request.source(), new PieceMessage(index, begin, dst.array()));
                        pendingRequests.remove(request);
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                });
            } else {
                Iterator<PeerConnection> iterator = chockedPeers.iterator();
                while (iterator.hasNext()) {
                    PeerConnection peerConnection = iterator.next();
                    try {
                        torrentNetwork.sendMessage(peerConnection, new UnchokeMessage());
                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                    }
                    iterator.remove();
                }
                synchronized (requests) {
                    try {
                        requests.wait();
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
            }
        }

    }

    public void peerInterested(PeerConnection peerConnection) {
        try {
            torrentNetwork.sendMessage(peerConnection, new UnchokeMessage());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void peerConnected(PeerConnection peerConnection) {
        try {
            torrentNetwork.sendMessage(peerConnection, new UnchokeMessage());
            torrentNetwork.sendMessage(peerConnection, new BitfieldMessage(peerConnection.getTorrent().getPartDownloaded()));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void addRequest(Request request) {
        requests.add(request);
        if (requests.size() > TOTAL_REQUESTS_LIMIT) {
            try {
                torrentNetwork.sendMessage(request.source(), new ChokeMessage());
                request.source().setAmChoking(true);
                chockedPeers.add(request.source());
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
        synchronized (requests) {
            requests.notify();
        }
    }

    public void removePeerConnection(PeerConnection peerConnection) {
        pendingRequests.removeIf(x -> x.source() == peerConnection);
    }

    public void cancelRequest(Request request) {
        requests.remove(request);
        pendingRequests.remove(request);
    }

    private final static int TOTAL_REQUESTS_LIMIT = 64;
}



