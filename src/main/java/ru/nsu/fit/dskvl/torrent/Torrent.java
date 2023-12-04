package ru.nsu.fit.dskvl.torrent;

import java.net.SocketAddress;
import java.util.*;

public class Torrent {
    private final byte[] infoHash;
    private final byte[] piecesHashes;
    private final long pieceLength;
    private final long length;
    private final int numberOfPieces;
    private final String fileAddress;
    private final int lastPieceIndex;

    private final ArrayList<SocketAddress> peers;
    private final BitSet partDownloaded;
    private boolean downloaded = false;

    public Torrent(byte[] infoHash, long pieceLength, long length,
                   String fileAddress, byte[] piecesHashes, ArrayList<SocketAddress> peers) throws TorrentClientException {
        this.infoHash = infoHash;
        this.pieceLength = pieceLength;
        this.length = length;
        this.lastPieceIndex = (int) Math.ceilDiv(length, pieceLength) - 1;
        this.piecesHashes = piecesHashes;
        this.peers = peers;
        this.numberOfPieces = (int) Math.ceilDiv(length, pieceLength);
        this.fileAddress = fileAddress;
        this.partDownloaded = new BitSet(numberOfPieces);
    }

    public Torrent(be.christophedetroyer.torrent.Torrent info, ArrayList<SocketAddress> peers) throws TorrentClientException {
         this(HexFormat.of().parseHex(info.getInfo_hash()),
                info.getPieceLength(),
                info.getTotalSize(),
                info.getName(),
                info.getPiecesBlob(),
                peers);
    }

    public byte[] getInfoHash() {
        return infoHash;
    }
    public byte[] getPiecesHashes() { return piecesHashes; }
    public long getPieceLength() { return pieceLength; }
    public long getLength() { return length; }
    public int getLastPieceLength() { return (int) (length - pieceLength * (numberOfPieces - 1)); }
    public int getNumberOfPieces() { return numberOfPieces; }
    public String getFileAddress() { return fileAddress; }
    public int getLastPieceIndex() { return lastPieceIndex; }

    public ArrayList<SocketAddress> getPeers() { return peers; }
    public BitSet getPartDownloaded() {
        var bitset = new BitSet(numberOfPieces);
        bitset.or(partDownloaded);
        return bitset;
    }
    synchronized public boolean isDownloaded() { return downloaded; }
    public int getNumberOfPiecesDownloaded() {
        synchronized (partDownloaded) {
            return partDownloaded.cardinality();
        }
    }
    public boolean hasPiece(int index) {
        synchronized (partDownloaded) {
            return partDownloaded.get(index);
        }
    }

    public void pieceDownloaded(int index) {
        synchronized (partDownloaded) {
            partDownloaded.set(index);
        }
        if (partDownloaded.cardinality() == numberOfPieces) {
            synchronized (this) {
                downloaded = true;
            }
        }
    }
}
