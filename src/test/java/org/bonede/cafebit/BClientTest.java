package org.bonede.cafebit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class BClientTest {

    @Test
    void getPeers() throws IOException, InterruptedException {
        String file = "sample.torrent";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(file);
        BTorrent bTorrent = new BTorrent(inputStream);
        inputStream.close();
        BClient client = new BClient();
        BTask task = new BTask(bTorrent, "");
        BPeersResp resp = client.getPeers(task);
        assertEquals("178.62.85.20:51489", resp.getPeers()[0]);
        assertEquals("178.62.82.89:51470", resp.getPeers()[1]);
        assertEquals("165.232.33.77:51467", resp.getPeers()[2]);
    }

    @Test
    void download() throws IOException, InterruptedException {
        String file = "sample.torrent";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(file);
        BTorrent bTorrent = new BTorrent(inputStream);
        BClient client = new BClient();
        client.download(new BTask(bTorrent, "/data/download"));
    }
}