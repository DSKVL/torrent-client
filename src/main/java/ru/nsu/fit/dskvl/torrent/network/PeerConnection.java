package ru.nsu.fit.dskvl.torrent.network;
import ru.nsu.fit.dskvl.torrent.Torrent;

import java.net.SocketAddress;

import java.util.BitSet;

public class PeerConnection {
    private final SocketAddress address;
    private final Torrent torrent;

    private boolean amChoking = true;
    private boolean amInterested = false;
    private boolean peerChocking = true;
    private boolean peerInterested = false;
    private final BitSet bitfield;

    PeerConnection(SocketAddress address, Torrent torrent) {
        this.address = address;
        this.torrent = torrent;
        this.bitfield = new BitSet(torrent.getNumberOfPieces());
    }

    public SocketAddress getAddress() { return address; }
    public Torrent getTorrent() {
        return torrent;
    }

    public boolean isAmChoking() { return amChoking; }
    public boolean isAmInterested() { return amInterested; }
    public boolean isPeerChocking() { return peerChocking; }
    public boolean isPeerInterested() { return peerInterested; }
    public void setAmChoking(boolean amChoking) { this.amChoking = amChoking; }
    public void setAmInterested(boolean asInterested) { this.amInterested = asInterested; }
    public void setPeerChocking(boolean peerChocking) { this.peerChocking = peerChocking; }
    public void setPeerInterested(boolean peerInterested) { this.peerInterested = peerInterested; }

    public void updateBitfield(BitSet bitfield) {
        synchronized (this.bitfield) {
            this.bitfield.clear();
            this.bitfield.or(bitfield);
        }
    }

    public void updatePieceDownloaded(int index) {
        synchronized (bitfield) {
            bitfield.set(index);
        }
    }
    public boolean hasPiece(int index) {
        synchronized (bitfield) {
            return bitfield.get(index);
        }
    }
    public BitSet getBitfield() {
        return bitfield;
    }
}