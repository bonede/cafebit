package org.bonede.cafebit;

public class BTask {
    private BTorrent torrent;
    private int uploaded;
    private int downloaded;
    private int left;
    private int compact;

    public String getDstDir() {
        return dstDir;
    }

    private String dstDir;

    public int getUploaded() {
        return uploaded;
    }

    public int getDownloaded() {
        return downloaded;
    }

    public int getLeft() {
        return left;
    }

    public int getCompact() {
        return compact;
    }

    public BTorrent getTorrent() {
        return torrent;
    }

    public BTask(BTorrent torrent, String dstDir) {
        this.torrent = torrent;
        this.uploaded = 0;
        this.downloaded = 0;
        this.left = torrent.getLength();
        this.compact = 1;
        this.dstDir = dstDir;
    }
}
