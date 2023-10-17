package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;

import java.nio.ByteBuffer;

public class NotInterestedMessage extends Message {
    public NotInterestedMessage() { buffer = ByteBuffer.wrap(new byte[]{0, 0, 0, 1, 3}); }

    @Override
    public String toString() {
        return "INTERESTED";
    }

    @Override
    public void accept(MessageProcessor processor) {
        processor.process(this);
    }
}
