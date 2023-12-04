package ru.nsu.fit.dskvl.torrent;

import ru.nsu.fit.dskvl.torrent.network.PeerConnection;

import java.util.*;

public class Downloader {
    private final Map<Torrent, FileDownloader> torrentFileDownloaderMap = new HashMap<>();
    private final Set<FileDownloader> notFinishedDownloaders = new HashSet<>();
    private final Thread thread = new Thread(this::work);

    public Downloader() {
    }

    public void start() {
        thread.start();
    }

    public void work() {
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            notFinishedDownloaders.removeIf(FileDownloader::finished);

            List<FileDownloader> active;
            synchronized (this) {
                active = notFinishedDownloaders.stream()
                        .filter(x -> !x.isWaiting() || x.getPendingRequests() != 0).toList();
                if (active.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
            }

            var requestsTotal = active.stream().map(FileDownloader::getPendingRequests).reduce(0, Integer::sum);
            if (requestsTotal < TOTAL_REQUESTS_LIMIT) {
                active.forEach(FileDownloader::requestPieces);
            }
            active.forEach(FileDownloader::processReceivedPieces);
        }

        torrentFileDownloaderMap.values().forEach(FileDownloader::cancelAll);
    }

    public void addTorrent(Torrent torrent, FileDownloader fileDownloader) {
        if (!torrentFileDownloaderMap.containsKey(torrent)) {
            torrentFileDownloaderMap.put(torrent, fileDownloader);
            notFinishedDownloaders.add(fileDownloader);
        }
    }

    public void addPeerConnection(Torrent torrent, PeerConnection peer) {
        torrentFileDownloaderMap.get(torrent).addConnection(peer);
        synchronized (this) {
            notify();
        }
    }

    public void removePeerConnection(Torrent torrent, PeerConnection peerConnection) {
        torrentFileDownloaderMap.get(torrent).removeConnection(peerConnection);
    }

    public void pieceReceived(PeerConnection peer, byte[] data, Request request) {
        var downloader = torrentFileDownloaderMap.get(request.torrent());
        downloader.pieceReceived(peer, data, request);

        synchronized (this) {
            notify();
        }
    }

    public void peerUpdated(PeerConnection peer) {
        var torrent = peer.getTorrent();
        var downloader = torrentFileDownloaderMap.get(torrent);
        downloader.connectionUpdated(peer);

        synchronized (this) {
            notify();
        }
    }

    public Set<FileDownloader> getDownloaders() {
        return new HashSet<>(notFinishedDownloaders);
    }

    private final static int TOTAL_REQUESTS_LIMIT = 24;
}
