package ru.nsu.fit.dskvl.torrent;

public class TorrentClientException extends Exception {
    private final String message;
    public TorrentClientException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
