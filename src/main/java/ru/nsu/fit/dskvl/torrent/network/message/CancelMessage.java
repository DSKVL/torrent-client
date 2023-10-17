package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;

import java.nio.ByteBuffer;

public class CancelMessage extends Message {
    private final int index;
    private final int begin;
    private final int length;

    public CancelMessage(byte[] buf) {
        var byteBuffer = ByteBuffer.wrap(buf);
        this.index = byteBuffer.getInt(1);
        this.begin = byteBuffer.getInt(5);
        this.length = byteBuffer.getInt(9);
        this.buffer = ByteBuffer.allocate(17);
        buffer.putInt(13);
        buffer.put((byte) 6);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);
        buffer.rewind();
    }

    public CancelMessage(int index, int begin, int length) {
        this.index = index;
        this.begin = begin;
        this.length = length;
        this.buffer = ByteBuffer.allocate(17);
        buffer.putInt(13);
        buffer.put((byte) 6);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);
        buffer.rewind();
    }

    public int getIndex() {
        return index;
    }

    public int getBegin() {
        return begin;
    }

    public int getLength() {
        return length;
    }

    @Override
    public String toString() {
        var string = new StringBuilder("CANCEL ");
        string.append("index: ").append(index).append(" begin: ").append(begin).append(" length: ").append(length);
        return string.toString();
    }

    @Override
    public void accept(MessageProcessor processor) {
        processor.process(this);
    }
}
