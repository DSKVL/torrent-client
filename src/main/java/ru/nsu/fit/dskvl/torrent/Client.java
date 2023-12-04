package ru.nsu.fit.dskvl.torrent;

import be.christophedetroyer.torrent.TorrentParser;
import ru.nsu.fit.dskvl.torrent.fileio.TorrentFiles;
import ru.nsu.fit.dskvl.torrent.network.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;

public class Client {
    private final Downloader downloader;
    private final Uploader uploader;
    private final TorrentNetwork torrentNetwork;
    private final TorrentFiles torrentFiles;
    private final Threadpool threadpool;

    public static void main(String[] args) {
        File metainfoFile = null;
        int port = -1;
        ArrayList<SocketAddress> peers = new ArrayList<>();

        for (var arg : args) {
            if (arg.startsWith("--metainfo=")) {
                metainfoFile = new File(arg.substring(11));
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring(7), 10);
            }

            else {
                var address = arg.split(":");
                peers.add(new InetSocketAddress(address[0],Integer.parseInt(address[1])));
            }
        }

        if (metainfoFile == null) {
            System.out.println("Try to specify metainfo file via --metainfo==<filename>.");
            return;
        }

        if (!metainfoFile.exists()) {
            System.out.println("The file you specified does not exist.");
            return;
        }

        Threadpool threadpool = new Threadpool(3);
        TorrentFiles torrentFiles = new TorrentFiles(threadpool);
        Torrent torrent;
        try (InputStream metainfoStream = new FileInputStream(metainfoFile)) {
            be.christophedetroyer.torrent.Torrent parsedInfo = TorrentParser.parseTorrent(metainfoStream);
            torrent = new Torrent(parsedInfo, peers);
        } catch (IOException | TorrentClientException e) {
            System.err.println(e.getMessage());
            System.out.println("Unable to read metainfo file.");
            return;
        }
        torrentFiles.addTorrent(torrent);
        new Validator(torrentFiles).check(torrent);

        Downloader downloader = new Downloader();
        TorrentAcceptor torrentAcceptor = new TorrentAcceptor(downloader);
        TorrentNetwork torrentNetwork;
        try {
            torrentNetwork = port != -1 ?
                    new TorrentNetwork(torrentAcceptor, port) : new TorrentNetwork(torrentAcceptor);
        } catch (IOException e) {
            System.out.println("Unable to initialize network: " + e.getMessage());
            return;
        }
        torrentAcceptor.setTorrentNetwork(torrentNetwork);
        Uploader uploader = new Uploader(torrentFiles, torrentNetwork);
        torrentAcceptor.setUploader(uploader);
        MessageProcessor messageProcessor = new MessageProcessor(uploader, downloader);
        torrentNetwork.setMessageProcessor(messageProcessor);

        try {
            Client cl =  new Client(downloader, uploader, torrentFiles, torrentNetwork, threadpool);
            cl.addTorrent(torrent);
            cl.start();
        } catch (TorrentClientException e) {
            System.out.println("Something went wrong during start.");
        }
    }

    public Client(Downloader downloader, Uploader uploader, TorrentFiles torrentFiles, TorrentNetwork torrentNetwork,Threadpool threadpool) throws TorrentClientException {
        this.torrentFiles = torrentFiles;
        this.torrentNetwork = torrentNetwork;
        this.threadpool = threadpool;
        this.downloader = downloader;
        this.uploader = uploader;
    }

    public void addTorrent(Torrent torrent) {
        downloader.addTorrent(torrent, new FileDownloader(torrent, torrentFiles, torrentNetwork, threadpool));
        torrentNetwork.addTorrent(torrent);
    }

    public void start() throws TorrentClientException {
        for (var dl : downloader.getDownloaders()) {
            var torrent = dl.getTorrent();
            for (var address : torrent.getPeers()) {
                try {
                    torrentNetwork.connectToPeer(address, torrent);
                } catch (IOException e) {
                    System.out.println("Unable to connect to" + address.toString());
                }
            }
        }
        torrentNetwork.start();
        downloader.start();
        uploader.start();
    }
}
