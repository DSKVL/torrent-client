package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;

import java.nio.ByteBuffer;

public class UnchokeMessage extends Message {
    public UnchokeMessage() { buffer = ByteBuffer.wrap(new byte[]{0, 0, 0, 1, 1}); }

    @Override
    public String toString() {
        return "UNCHOKE";
    }

    @Override
    public void accept(MessageProcessor processor) {
        processor.process(this);
    }
}
