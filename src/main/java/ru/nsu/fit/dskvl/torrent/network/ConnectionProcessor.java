package ru.nsu.fit.dskvl.torrent.network;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public interface ConnectionProcessor {
    void accept(NetworkIO networkIO, ServerSocketChannel channel);
    void anErrorOccurred(SocketChannel socketChannel);
}
