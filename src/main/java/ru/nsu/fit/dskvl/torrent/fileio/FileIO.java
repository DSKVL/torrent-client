package ru.nsu.fit.dskvl.torrent.fileio;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class FileIO {
    private final BlockingQueue<Runnable> tasks = new ArrayBlockingQueue<>(64);
    private final Thread thread = new Thread(this::processTasks);

    FileIO() {
        thread.start();
    }

    private void processTasks() {
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                tasks.take().run();
            } catch (InterruptedException e) {
                continue;
            }
        }
    }

    public void read(ByteBuffer dst, RandomAccessFile src, long position, CompletionHandler<Integer, ByteBuffer> completionHandler) {
        try {
            tasks.put(() -> {
                try {
                    src.seek(position);
                    completionHandler.completed(src.read(dst.array()), dst);
                } catch (IOException e) {
                    completionHandler.failed(e, dst);
                }
            });
        } catch (InterruptedException e) {
            completionHandler.failed(e, dst);
        }
    }

    public void write(ByteBuffer src, RandomAccessFile dst, long position, CompletionHandler<Integer, ByteBuffer> completionHandler) {
        try {
            tasks.put(() -> {
                try {
                    if (position + src.limit() > dst.length()) {
                        completionHandler.failed(new IndexOutOfBoundsException(), src);
                    }
                    dst.seek(position);
                    dst.write(src.array());
                    completionHandler.completed(src.array().length, src);
                } catch (IOException e) {
                    completionHandler.failed(e, src);
                }
            });
        } catch (InterruptedException e) {
            completionHandler.failed(e, src);
        }
    }
}

