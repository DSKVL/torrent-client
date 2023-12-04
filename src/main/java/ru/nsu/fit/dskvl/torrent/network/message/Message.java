package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;
import ru.nsu.fit.dskvl.torrent.network.PeerConnection;

import java.nio.ByteBuffer;

public abstract class Message {
    protected ByteBuffer buffer;
    protected PeerConnection source = null;

    public static Message buildMessage(byte[] buffer, PeerConnection source) {
        int messageLength = buffer.length;
        if (messageLength == 0) {
            return new KeepaliveMessage();
        }

        var message =  switch (buffer[0]) {
            case 0 -> new ChokeMessage();
            case 1 -> new UnchokeMessage();
            case 2 -> new InterestedMessage();
            case 3 -> new NotInterestedMessage();
            case 4 -> new HaveMessage(buffer);
            case 5 -> new BitfieldMessage(buffer, source);
            case 6 -> new RequestMessage(buffer);
            case 7 -> new PieceMessage(buffer);
            case 8 -> new CancelMessage(buffer);
            default -> throw new IllegalStateException();
        };


        message.source = source;
        return message;
    }

    public ByteBuffer getByteBuffer() { return buffer;}

    public PeerConnection getSource() { return source; }

    abstract public void accept(MessageProcessor processor);

    abstract public String toString();
}