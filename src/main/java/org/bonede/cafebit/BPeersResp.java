package org.bonede.cafebit;

import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class BPeersResp {
    private int interval;
    private int complete;
    private int incomplete;
    private int minInterval;
    private InetSocketAddress[] peers;

    public int getInterval() {
        return interval;
    }

    public int getComplete() {
        return complete;
    }

    public int getIncomplete() {
        return incomplete;
    }

    public int getMinInterval() {
        return minInterval;
    }

    public InetSocketAddress[] getPeers() {
        return peers;
    }

    public BPeersResp(byte[] bytes) {


        Bencode.BValue bValue = Bencode.parse(bytes);
        Bencode.BValue failure = bValue.get("failure reason");
        if(failure != null){
            throw new Bencode.BError(new String(failure.getBytesValue()));
        }
        this.interval = bValue.get("interval").getIntValue();
        this.complete = bValue.get("complete").getIntValue();
        this.incomplete = bValue.get("incomplete").getIntValue();
        this.minInterval = bValue.get("min interval").getIntValue();
        byte[] peersBytes = bValue.get("peers").getBytesValue();
        int peesNum = peersBytes.length / 6;
        this.peers = new InetSocketAddress[peesNum];
        ByteBuffer buffer = ByteBuffer.wrap(peersBytes);
        byte[] ip = new byte[4];
        for(int i = 0; i < peesNum; i++){
            buffer.get(ip);

            int port = buffer.getShort() & 0xFFFF;


            InetAddress ipAddress = null;
            try {
                ipAddress = InetAddress.getByAddress(ip);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            peers[i] = new InetSocketAddress(ipAddress.getHostAddress(), port);
        }
    }
}
