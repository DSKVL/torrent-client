package ru.nsu.fit.dskvl.torrent.fileio;

import ru.nsu.fit.dskvl.torrent.Threadpool;
import ru.nsu.fit.dskvl.torrent.Torrent;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TorrentFiles {
    private final Map<Torrent, RandomAccessFile> map = new HashMap<>();
    private final Threadpool threadpool;
    private final FileIO fileIO = new FileIO();

    public TorrentFiles(Threadpool threadpool) {
        this.threadpool = threadpool;
    }

    public void addTorrent(Torrent torrent) {
        var fileAddress = torrent.getFileAddress();
        var path = Path.of(fileAddress);

        try {
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
            var file = new RandomAccessFile(fileAddress, "rw");
            file.setLength(torrent.getLength());
            map.put(torrent, file);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    public void readPiece(ByteBuffer dst, Torrent torrent, int index, int begin,
                          Runnable onComplete) {
        var file = map.get(torrent);
        fileIO.read(dst, file, torrent.getPieceLength() * index + begin,
                new CompletionHandler<>() {
                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        onComplete.run();
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        System.err.println(exc.getMessage());
                    }
                });
    }

    public void writePiece(byte[] data, Torrent torrent, int index, int begin, Runnable onComplete) {
        var file = map.get(torrent);
        fileIO.write(ByteBuffer.wrap(data), file, (long) index * torrent.getPieceLength() + begin,
                new CompletionHandler<>() {
                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        threadpool.addTask(onComplete);
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        System.err.println(exc.getMessage());
                    }
                });
    }
}
