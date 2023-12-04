package ru.nsu.fit.dskvl.torrent;

import ru.nsu.fit.dskvl.torrent.network.PeerConnection;

public record Request(PeerConnection source,
                      Torrent torrent,
                      int index,
                      int begin,
                      int length) {
}
