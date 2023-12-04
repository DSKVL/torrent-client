package ru.nsu.fit.dskvl.torrent;

import ru.nsu.fit.dskvl.torrent.fileio.TorrentFiles;
import ru.nsu.fit.dskvl.torrent.network.PeerConnection;
import ru.nsu.fit.dskvl.torrent.network.TorrentNetwork;
import ru.nsu.fit.dskvl.torrent.network.message.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileDownloader {
    private final Torrent torrent;
    private final TorrentFiles torrentFiles;
    private final TorrentNetwork torrentNetwork;
    private final Threadpool threadpool;
    private final Set<PeerConnection> connections = ConcurrentHashMap.newKeySet();
    private final Map<PeerConnection, Set<Request>> requestsMap = new ConcurrentHashMap<>();
    private final ArrayDeque<ReceivedPiece> receivedPieces = new ArrayDeque<>();

    private final int numberOfPartsInPiece;
    private final BitSet availablePieces;
    private final BitSet requestedPieces;

    private final boolean[][] partDownloaded;

    private boolean waiting = false;

    public FileDownloader(Torrent torrent, TorrentFiles torrentFiles, TorrentNetwork torrentNetwork, Threadpool threadpool) {
        this.torrent = torrent;
        this.torrentFiles = torrentFiles;
        this.threadpool = threadpool;
        numberOfPartsInPiece = (int) Math.ceilDiv(torrent.getPieceLength(), PART_SIZE);
        this.torrentNetwork = torrentNetwork;
        this.availablePieces = new BitSet(numberOfPartsInPiece);
        this.requestedPieces = torrent.getPartDownloaded();
        partDownloaded = new boolean[torrent.getNumberOfPieces()][numberOfPartsInPiece];

        for (int i = 0; i < torrent.getLastPieceIndex(); i++) {
            if (torrent.hasPiece(i)) {
                for (int j = 0; j < numberOfPartsInPiece; j++) {
                    partDownloaded[i][j] = true;
                }
            }
        }

        var numberOfPartsInLastPiece = Math.ceilDiv(torrent.getLastPieceLength(), PART_SIZE);
        partDownloaded[torrent.getLastPieceIndex()] = new boolean[numberOfPartsInLastPiece];
        if (torrent.hasPiece(torrent.getLastPieceIndex())) {
            for (int j = 0; j < numberOfPartsInLastPiece; j++) {
                partDownloaded[torrent.getLastPieceIndex()][j] = true;
            }
        }
    }

    public void requestPieces() {
        if (torrent.isDownloaded()) {
            waiting = true;
            return;
        }

        var requestablePieces = new BitSet(requestedPieces.length());
        requestablePieces.or(availablePieces);
        requestablePieces.andNot(requestedPieces);

        if (requestablePieces.cardinality() == 0) {
            requestedPieces.and(torrent.getPartDownloaded());
            setWaiting(true);
            return;
        }

        int currentIndex = nextPieceIndex(requestablePieces, torrent.getNumberOfPieces());
        if (currentIndex == -1) {
            setWaiting(true);
            return;
        }

        sendUnchokes(currentIndex);
        sendInterests(currentIndex);
        requestPieces(currentIndex);
    }

    private int nextPieceIndex(BitSet requestablePieces, int numberOfPieces) {

        Random random = new Random(System.currentTimeMillis());
        int candidateIndex = random.nextInt(numberOfPieces);
        if (requestablePieces.get(candidateIndex)) {
            return candidateIndex;
        }

        int nextRight = requestablePieces.nextSetBit(candidateIndex);
        if (nextRight != -1) {
            return nextRight;
        }

        return requestablePieces.previousSetBit(candidateIndex);
    }

    private void sendUnchokes(int index) {
        connections.stream()
                .filter(x -> x.hasPiece(index) && x.isAmChoking())
                .forEach(x -> {
                    try {
                        torrentNetwork.sendMessage(x, new UnchokeMessage());
                    } catch (IOException e) {
                        removeConnection(x);
                        return;
                    }

                    x.setAmChoking(false);
                });

    }

    private void sendInterests(int index) {
        connections.stream()
                .filter(x -> x.hasPiece(index) && x.isPeerChocking() && !x.isAmInterested())
                .forEach(x -> {
                    try {
                        torrentNetwork.sendMessage(x, new InterestedMessage());
                    } catch (IOException e) {
                        removeConnection(x);
                        return;
                    }

                    x.setAmInterested(true);
                });
    }


    private void requestPieces(int index) {
        var availablePeers = connections.stream()
                .filter(x -> x.hasPiece(index) && !x.isPeerChocking())
                .toList();

        if (availablePeers.isEmpty()) {
            System.out.println("No available seeds.");
            setWaiting(true);
            return;
        }

        var requestablePeers = availablePeers.stream()
                .filter(x -> requestsMap.get(x).size() <= MAX_PENDING_REQUESTS).toList();

        if (requestablePeers.isEmpty()) {
            setWaiting(true);
            return;
        }

        int parts = numberOfPartsInPiece;
        if (torrent.getLastPieceLength() != 0 && index == torrent.getNumberOfPieces() - 1) {
            parts = Math.floorDiv(torrent.getLastPieceLength(), PART_SIZE);
        }

        int i;
        for (i = 0; i < parts; i++) {
            if (!partDownloaded[index][i]) {
                var request = new RequestMessage(index, i * PART_SIZE, PART_SIZE);
                var peer = requestablePeers.get(i % requestablePeers.size());

                try {
                    torrentNetwork.sendMessage(peer, request);
                    requestsMap.get(peer).add(new Request(null, torrent, index, i * PART_SIZE, PART_SIZE));
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                    removeConnection(peer);
                }
            }
        }

        if (torrent.getLastPieceLength() != 0 && index == torrent.getNumberOfPieces() - 1) {
            var request = new RequestMessage(index, i * PART_SIZE, torrent.getLastPieceLength() % PART_SIZE);
            var peer = requestablePeers.get(i % requestablePeers.size());

            try {
                torrentNetwork.sendMessage(peer, request);
                requestsMap.get(peer).add(new Request(null, torrent, index, i * PART_SIZE, torrent.getLastPieceLength() % PART_SIZE));
            } catch (IOException e) {
                System.err.println(e.getMessage());
                removeConnection(peer);
            }
        }
        requestedPieces.set(index);
    }

    record ReceivedPiece(PeerConnection peer, byte[] data, Request request) {
    }

    public void pieceReceived(PeerConnection peer, byte[] data, Request request) {
        synchronized (receivedPieces) {
            receivedPieces.add(new ReceivedPiece(peer, data, request));
        }
        setWaiting(false);
    }

    public void processReceivedPieces() {
        synchronized (receivedPieces) {
            if (receivedPieces.isEmpty()) {
                return;
            }
        }

        while (true) {
            ReceivedPiece rec;
            synchronized (receivedPieces) {
                if (receivedPieces.isEmpty()) {
                    break;
                }
                rec = receivedPieces.pop();
            }
            var peer = rec.peer;
            var data = rec.data;
            var request = rec.request;

            requestsMap.get(peer).remove(request);
            connections.stream()
                    .filter(x -> requestsMap.get(x).contains(request))
                    .forEach(x -> {
                        try {
                            torrentNetwork.sendMessage(x,
                                    new CancelMessage(request.index(), request.begin(), request.length()));
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                            removeConnection(x);
                        }
                    });

            if (torrent.hasPiece(request.index())) {
                return;
            }

            var pieceInfo = partDownloaded[request.index()];
            pieceInfo[request.begin() / PART_SIZE] = true;

            if (gatheredAllParts(request.index())) {
                var validator = new Validator(torrentFiles);
                synchronized (validator) {
                    torrentFiles.writePiece(data, torrent, request.index(), request.begin(), () -> {
                        synchronized (validator) {
                            validator.notify();
                        }
                    });
                    while (true) {
                        try {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }
                            validator.wait();
                            break;
                        } catch (InterruptedException e) {
                            continue;
                        }
                    }
                }
                threadpool.addTask(() -> validator.check(torrent, request.index(), this));
            } else {
                torrentFiles.writePiece(data, torrent, request.index(), request.begin(), () -> {
                });
            }
        }
        setWaiting(false);
    }

    private boolean gatheredAllParts(int index) {
        var pieceInfo = partDownloaded[index];
        for (var isPartDownloaded : pieceInfo) {
            if (!isPartDownloaded) {
                return false;
            }
        }
        return true;
    }

    public void pieceValidated(int index) {
        torrent.pieceDownloaded(index);
        StringBuilder builder = new StringBuilder("Piece ");
        builder.append(index).append(" successfully downloaded. ").append(torrent.getNumberOfPiecesDownloaded());
        builder.append(" of ").append(torrent.getNumberOfPieces()).append(" done.");
        System.out.println(builder);
        sendHaveMessages(index);
    }

    public void pieceDiscarded(int index) {
        synchronized (partDownloaded[index]) {
            Arrays.fill(partDownloaded[index], false);
        }
        synchronized (requestedPieces) {
            requestedPieces.clear(index);
        }
        StringBuilder builder = new StringBuilder("Piece ");
        builder.append(index).append(" is not correct. ");
        System.out.println(builder);
    }

    public void cancelAll() {
        connections.forEach(connection ->
                requestsMap.get(connection).forEach(request -> {
                    try {
                        torrentNetwork.sendMessage(connection, new CancelMessage(request.index(), request.begin(), request.length()));
                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                    }
                }));
    }

    public boolean finished() {
        return torrent.isDownloaded();
    }

    public Torrent getTorrent() {
        return torrent;
    }

    public void addConnection(PeerConnection connection) {
        connections.add(connection);
        requestsMap.put(connection, ConcurrentHashMap.newKeySet());
        synchronized (connection.getBitfield()) {
            synchronized (availablePieces) {
                availablePieces.or(connection.getBitfield());
            }
        }
    }

    public void removeConnection(PeerConnection connection) {
        connections.remove(connection);
        requestsMap.remove(connection);
        synchronized (availablePieces) {
            availablePieces.clear();
            connections.forEach(x -> availablePieces.or(x.getBitfield()));
        }
    }

    public synchronized void connectionUpdated(PeerConnection peerConnection) {
        synchronized (peerConnection.getBitfield()) {
            synchronized (availablePieces) {
                availablePieces.or(peerConnection.getBitfield());
            }
        }
        setWaiting(false);
    }

    public synchronized boolean isWaiting() {
        return waiting;
    }

    public synchronized void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }

    private void sendHaveMessages(int index) {
        connections.forEach(x -> {
            try {
                torrentNetwork.sendMessage(x, new HaveMessage(index));
            } catch (IOException e) {
                System.err.println(e.getMessage());
                removeConnection(x);
            }
        });

    }

    public int getPendingRequests() {
        synchronized (receivedPieces) {
            return receivedPieces.size();
        }
    }

    final static int MAX_PENDING_REQUESTS = 16;
    final static int PART_SIZE = 1 << 14;
}