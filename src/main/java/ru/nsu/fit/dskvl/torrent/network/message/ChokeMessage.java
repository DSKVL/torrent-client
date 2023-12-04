package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;

import java.nio.ByteBuffer;

public class ChokeMessage extends Message {
    public ChokeMessage() { buffer = ByteBuffer.wrap(new byte[]{0, 0, 0, 1, 0}); }

    @Override
    public String toString() {
        return "CHOKE";
    }

    @Override
    public void accept(MessageProcessor processor) {
        processor.process(this);
    }
}
