package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;

import java.nio.ByteBuffer;

public class InterestedMessage extends Message {
    public InterestedMessage() { buffer = ByteBuffer.wrap(new byte[]{0, 0, 0, 1, 2}); }

    @Override
    public String toString() {
        return "INTERESTED";
    }

    @Override
    public void accept(MessageProcessor processor) {
        processor.process(this);
    }
}
