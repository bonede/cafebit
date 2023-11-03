package org.bonede.cafebit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class BTorrentTest {
    @Test
    public void infoHash() throws IOException {
        String file = "sample.torrent";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(file);
        BTorrent bTorrent = new  BTorrent(inputStream);
        inputStream.close();

        assertEquals("d69f91e6b2ae4c542468d1073a71d4ea13879a7f", Crypto.hex(bTorrent.getInfoHash()));
        assertEquals("http://bittorrent-test-tracker.codecrafters.io/announce", bTorrent.getAnnounce());
        assertEquals(92063, bTorrent.getLength());
        assertEquals(3, bTorrent.getPieceNum());
        assertEquals("e876f67a2a8886e8f36b136726c30fa29703022d", Crypto.hex(bTorrent.getPieceAt(0)));
        assertEquals("6e2275e604a0766656736e81ff10b55204ad8d35", Crypto.hex(bTorrent.getPieceAt(1)));
        assertEquals("f00d937a0213df1982bc8d097227ad9e909acc17", Crypto.hex(bTorrent.getPieceAt(2)));
    }
}