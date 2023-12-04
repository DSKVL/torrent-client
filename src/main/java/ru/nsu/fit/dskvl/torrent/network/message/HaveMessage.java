package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;

import java.nio.ByteBuffer;

public class HaveMessage extends Message {
    int index;

    public HaveMessage(int index) {
        this.index = index;
        buffer = ByteBuffer.allocate(9);
        buffer.putInt(5);
        buffer.put((byte) 4);
        buffer.putInt(index);
        buffer.rewind();
    }

    public HaveMessage(byte[] buf) {
        this(ByteBuffer.wrap(buf).getInt(1));
    }

    public int getIndex() {
        return index;
    }

    @Override
    public String toString() {
        var string = new StringBuilder("HAVE ");
        string.append("index: ").append(index);
        return string.toString();
    }

    @Override
    public void accept(MessageProcessor processor) {
        processor.process(this);
    }
}
