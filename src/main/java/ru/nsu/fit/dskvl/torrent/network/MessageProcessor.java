package ru.nsu.fit.dskvl.torrent.network;

import ru.nsu.fit.dskvl.torrent.Downloader;
import ru.nsu.fit.dskvl.torrent.Uploader;
import ru.nsu.fit.dskvl.torrent.Request;
import ru.nsu.fit.dskvl.torrent.network.message.*;

public class MessageProcessor {
    private final Uploader uploader;
    private final Downloader downloader;

    public MessageProcessor(Uploader uploader, Downloader downloader) {
        this.uploader = uploader;
        this.downloader = downloader;
    }

    public void process(RequestMessage requestMessage) {
        var source = requestMessage.getSource();
        var torrent = source.getTorrent();
        int index = requestMessage.getIndex();
        int begin = requestMessage.getBegin();
        long length = requestMessage.getLength();

        uploader.addRequest(new Request(source, torrent, index, begin, (int) length));
    }

    public void process(PieceMessage pieceMessage) {
        var source = pieceMessage.getSource();
        var torrent = source.getTorrent();
        int index = pieceMessage.getIndex();
        int begin = pieceMessage.getBegin();
        byte[] data = pieceMessage.getData();

        downloader.pieceReceived(source, data, new Request(null, torrent, index, begin, data.length));
    }

    public void process(HaveMessage haveMessage) {
        var source = haveMessage.getSource();
        var index = haveMessage.getIndex();
        haveMessage.getSource().updatePieceDownloaded(index);
        downloader.peerUpdated(source);
    }

    public void process(ChokeMessage chokeMessage) {
        var source = chokeMessage.getSource();
        source.setPeerChocking(true);
        downloader.peerUpdated(source);
    }

    public void process(UnchokeMessage unchokeMessage) {
        var source = unchokeMessage.getSource();
        unchokeMessage.getSource().setPeerChocking(false);
        downloader.peerUpdated(source);
    }

    public void process(InterestedMessage interestedMessage) {
        var source = interestedMessage.getSource();
        source.setPeerInterested(true);
        uploader.peerInterested(source);
        synchronized (uploader) {
            uploader.notify();
        }
    }

    public void process(NotInterestedMessage notInterestedMessage) {
        notInterestedMessage.getSource().setPeerInterested(false);
    }

    public void process(CancelMessage cancelMessage) {
        var source = cancelMessage.getSource();
        var torrent = source.getTorrent();
        int index = cancelMessage.getIndex();
        int begin = cancelMessage.getBegin();
        int length = cancelMessage.getLength();

        uploader.cancelRequest(new Request(source, torrent, index, begin, length));
    }

    public void process(BitfieldMessage bitfieldMessage) {
        var source = bitfieldMessage.getSource();
        source.updateBitfield(bitfieldMessage.getBitfield());
        downloader.peerUpdated(source);
    }

    public void process(KeepaliveMessage keepaliveMessage) {

    }
}
