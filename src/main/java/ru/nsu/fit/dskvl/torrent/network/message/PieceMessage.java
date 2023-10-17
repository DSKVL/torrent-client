package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;

import java.nio.ByteBuffer;

public class PieceMessage extends Message {
    int index;
    int begin;
    byte[] data;

    public PieceMessage(byte[] buf) {
        var byteBuffer = ByteBuffer.wrap(buf);
        byteBuffer.position(1);
        this.index = byteBuffer.getInt();
        this.begin = byteBuffer.getInt();
        this.data = new byte[buf.length - 9];
        byteBuffer.get(data);
        buffer = byteBuffer;
        buffer.rewind();
    }

    public PieceMessage(int index, int begin, byte[] data) {
        this.index = index;
        this.begin = begin;
        this.data = data;
        buffer = ByteBuffer.allocate(13 + data.length);
        buffer.putInt(9 + data.length);
        buffer.put((byte) 7);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.put(data);
        buffer.compact();
    }

    public int getIndex() {
        return index;
    }

    public int getBegin() {
        return begin;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        var string = new StringBuilder("PIECE ");
        string.append("index: ").append(index).append(" begin: ").append(begin);
        return string.toString();
    }

    @Override
    public void accept(MessageProcessor processor) {
        processor.process(this);
    }

}
