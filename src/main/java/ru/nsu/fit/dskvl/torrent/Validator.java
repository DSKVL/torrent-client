package ru.nsu.fit.dskvl.torrent;


import ru.nsu.fit.dskvl.torrent.fileio.TorrentFiles;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Validator {
    private final TorrentFiles torrentFiles;

    public Validator(TorrentFiles torrentFiles) {
        this.torrentFiles = torrentFiles;
    }

    public void check(Torrent torrent, int index, FileDownloader downloader) {
        try {
            MessageDigest encrypter = MessageDigest.getInstance("SHA-1");
            ByteBuffer dst = (index != torrent.getNumberOfPieces() - 1) ?
                    ByteBuffer.allocate((int) torrent.getPieceLength()) :
                    ByteBuffer.allocate(torrent.getLastPieceLength());

            synchronized (this) {
                var validator  = this;
                torrentFiles.readPiece(dst, torrent, index, 0,
                    () -> {
                        synchronized (validator) {
                            validator.notify();
                        }
                    });

                encrypter.reset();
                var hashes = torrent.getPiecesHashes();


                while (true) {
                    try {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        wait();
                        break;
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
                byte[] hash = encrypter.digest(dst.array());
                if (Arrays.equals(hash, Arrays.copyOfRange(hashes, index * 20, (index + 1) * 20))) {
                    downloader.pieceValidated(index);
                } else {
                    downloader.pieceDiscarded(index);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            System.err.println(e.getMessage());
        }
    }

    public void check(Torrent torrent) {
        int total = torrent.getNumberOfPieces();

        try {
            for (int i = 0; i < total - 1; i++) {
                int finalI = i;
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");

                ByteBuffer dst = ByteBuffer.allocate((int) torrent.getPieceLength());
                torrentFiles.readPiece(dst, torrent, finalI, 0, () -> {
                    messageDigest.reset();
                    var hashes = torrent.getPiecesHashes();
                    byte[] hash = messageDigest.digest(dst.array());

                    if (Arrays.equals(hash, Arrays.copyOfRange(hashes, finalI * 20, (finalI + 1) * 20))) {
                        torrent.pieceDownloaded(finalI);
                    }
                });
            }

            ByteBuffer dst = ByteBuffer.allocate(torrent.getLastPieceLength());
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            var validator = this;
            torrentFiles.readPiece(dst, torrent, total-1, 0, () -> {
                messageDigest.reset();
                var hashes = torrent.getPiecesHashes();
                byte[] hash = messageDigest.digest(dst.array());

                int lastPieceIndex = torrent.getNumberOfPieces() - 1;
                if (Arrays.equals(hash, Arrays.copyOfRange(hashes, lastPieceIndex * 20, (lastPieceIndex + 1) * 20))) {
                    torrent.pieceDownloaded(lastPieceIndex);
                }
                synchronized (validator) {
                    validator.notify();
                }
            });
        } catch (NoSuchAlgorithmException e) {
            System.err.println(e.getMessage());
        }

        synchronized (this) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    wait();
                    break;
                } catch (InterruptedException e) {
                    continue;
                }
            }
        }
    }
}
