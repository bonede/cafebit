package org.bonede.cafebit;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.StringJoiner;

public class BClient {
    private int listenPort;
    private String peerId;
    HttpClient httpClient = HttpClient.newHttpClient();


    private int BLOCK_SIZE = 16 * 1024;

    Logger logger = LoggerFactory.getLogger(BClient.class);


    public BClient() {
        this.listenPort = 6881;
        this.peerId = "00112233445566778899";
    }



    public BPeersResp getPeers(BTask bTask) throws IOException, InterruptedException {
        StringJoiner stringJoiner = new StringJoiner("&");

        stringJoiner.add("info_hash" + "=" + Crypto.urlEncodeBytes(bTask.getTorrent().getInfoHash()));
        stringJoiner.add("peer_id" + "=" + this.peerId);
        stringJoiner.add("port" + "=" + this.listenPort);
        stringJoiner.add("uploaded" + "=" + bTask.getUploaded());
        stringJoiner.add("downloaded" + "=" + bTask.getDownloaded());
        stringJoiner.add("left" + "=" + bTask.getLeft());
        stringJoiner.add("compact" + "=" + bTask.getCompact());
        String url = bTask.getTorrent().getAnnounce() + "?" + stringJoiner;
        URI uri = URI.create(url);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .build();

        HttpResponse<byte[]> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if(resp.statusCode() != 200){
            throw new Bencode.BError("Tracker error: " + resp.statusCode());
        }
       return new BPeersResp(resp.body());
    }

    public void download(BTask task) throws IOException, InterruptedException {
        BTorrent bTorrent = task.getTorrent();
        BPeersResp resp = getPeers(task);
        PeerConn conn = PeerConn.connect(peerId, resp.getPeers()[0], bTorrent.getInfoHash());
        byte[] bitField = conn.readBitfield();
        conn.sendInterested();
        conn.readUnchoke();
        Path dstPath = Paths.get(task.getDstDir());
        Files.createDirectories(dstPath);
        Path filePath = dstPath.resolve(bTorrent.getName());
        OutputStream outputStream = new FileOutputStream(filePath.toFile());
        int fileSize = bTorrent.getLength();
        int pieceNum = bTorrent.getPieceNum();
        int pieceLength = bTorrent.getPieceLength();
        int lastPieceLength = fileSize % pieceLength;
        ByteBuffer byteBuffer = ByteBuffer.allocate(pieceLength);
        logger.info("download {} to {}, pieces {}, length {}", bTorrent.getName(), filePath, pieceNum, fileSize);
        for(int i = 0; i < pieceNum; i++){

            byteBuffer.clear();
            int pLength = i == pieceNum - 1 ? lastPieceLength : pieceLength;
            byte[] pieceSha1 = bTorrent.getPieceAt(i);
            int blocks = pLength / BLOCK_SIZE + 1;
            int lastBlockSize = pLength % BLOCK_SIZE;
            logger.info("start download piece {} size {}", i, pLength);
            for(int b = 0; b < blocks; b++){

                int blockSize = b == blocks - 1 ? lastBlockSize : BLOCK_SIZE;
                int offset = BLOCK_SIZE * b;
                logger.info("download block: {}, piece {}, offset {}, block size {}", b, i, offset, blockSize);
                byte[] block = conn.downloadBlock(i, offset, blockSize);
                byteBuffer.put(block);
            }
            byte[] piece = new byte[pLength];
            byteBuffer.flip();
            byteBuffer.get(piece);
            byte[] sha1 = Crypto.sha1(piece);
            if(!Arrays.equals(pieceSha1, sha1)){
                throw new Bencode.BError("sha1 mismatch");
            }
            logger.info("write piece: {}", i);
            outputStream.write(piece);
        }
        outputStream.close();
        logger.info("download complete {}", filePath);
    }
}
