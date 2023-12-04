package ru.nsu.fit.dskvl.torrent.network.message;

import ru.nsu.fit.dskvl.torrent.network.MessageProcessor;

public class KeepaliveMessage extends Message {
    @Override
    public String toString() {
        return "KEEPALIVE";
    }

    @Override
    public void accept(MessageProcessor processor) {
        processor.process(this);
    }
}
