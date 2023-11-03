package org.bonede.cafebit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class PeerConn {
    private Socket socket;


    private static final int BUFFER_SIZE = 1024 * 1024;
    private ReadableByteChannel inputChannel;
    private WritableByteChannel outputChannel;
    private ByteBuffer inputBuffer;
    private ByteBuffer outputBuffer;

    private static Logger logger = LoggerFactory.getLogger(PeerConn.class);

    private static byte[] magic = "BitTorrent protocol".getBytes();

    public static PeerConn connect(String peerId,  InetSocketAddress addr, byte[] infoHash) throws IOException {
        PeerConn conn = new PeerConn(addr);
        conn.writeByte(19);
        conn.writeBytes(magic);
        conn.writeBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
        conn.writeBytes(infoHash);
        conn.writeBytes(peerId.getBytes());

        int magicLength = conn.readByte();
        byte[] magicBytes = conn.readBytes(magicLength);
        if(!Arrays.equals(magicBytes, magic)){
            throw new Bencode.BError("Invalid resp");
        }
        conn.readBytes(8);
        byte[] infoHashResp = conn.readBytes(20);
        if(!Arrays.equals(infoHash, infoHashResp)){
            throw new Bencode.BError("Invalid resp");
        }
        conn.readBytes(20);
        logger.info("Peer connected {}", addr);
        return conn;
    }


    public void write(String string) throws IOException {
        outputBuffer.clear();
        outputBuffer.put(string.getBytes());
        outputBuffer.flip();
        outputChannel.write(ByteBuffer.wrap(string.getBytes()));
    }

    public void writeByte(int b) throws IOException {
        outputBuffer.clear();
        outputBuffer.put((byte) (b & 0xff));
        outputBuffer.flip();
        outputChannel.write(outputBuffer);

    }

    public void writeInt(int b) throws IOException {
        outputBuffer.clear();
        outputBuffer.putInt(b);
        outputBuffer.flip();
        outputChannel.write(outputBuffer);
    }

    public void writeBytes(byte[] bytes) throws IOException {
        outputBuffer.clear();
        outputBuffer.put(bytes);
        outputBuffer.flip();
        outputChannel.write(outputBuffer);
    }

    public int readByte() throws IOException {
        inputBuffer.clear();
        inputBuffer.limit(1);
        inputChannel.read(inputBuffer);
        inputBuffer.flip();
        return inputBuffer.get();
    }

    public byte[] readBytes(int size) throws IOException {
        inputBuffer.clear();
        inputBuffer.limit(size);
        byte[] bytes = new byte[size];
        while ( inputBuffer.remaining() != 0){
            inputChannel.read(inputBuffer);
        }
        inputBuffer.flip();
        inputBuffer.get(bytes);
        return bytes;
    }
    private String peerId;

    private PeerConn(InetSocketAddress addr) throws IOException {
        socket = new Socket(addr.getAddress(), addr.getPort());
        this.peerId = peerId;
        inputBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        outputBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        inputChannel = Channels.newChannel(socket.getInputStream());
        outputChannel = Channels.newChannel(socket.getOutputStream());
    }

    public int readMsgLength() throws IOException {
        return readInt();
    }

    public int readInt() throws IOException {
        byte[] bytes = readBytes(4);
        return ByteBuffer.wrap(bytes).getInt();
    }

    private static int M_BITFIELD = 5;
    private static int M_UNCHOKE = 1;
    private static int M_INTERESTED  = 2;
    private static int M_REQUEST = 6;
    private static int M_PIECE = 7;

    public byte[] readBitfield() throws IOException {
        int messageLen = readMsgLength();
        int mid = readByte();
        if(mid != M_BITFIELD){
            throw new IOException("Message is not bit field: " + mid);
        }
        return readBytes(messageLen - 1);
    }

    public void readUnchoke() throws IOException {
        readMsgLength();
        int mid = readByte();
        if(mid != M_UNCHOKE){
            throw new IOException("Message is not unchoke: " + mid);
        }
    }

    public byte[] readBlock() throws IOException {
        int len = readMsgLength();
        int mid = readByte();
        if(mid != M_PIECE){
            throw new IOException("Message is not piece: " + mid);
        }
        int pieceIndex = readInt();
        int begin = readInt();
        int blockLen = len -  1 - 4 - 4;
        byte[] bytes = readBytes(blockLen);
        return bytes;
    }

    public void sendInterested() throws IOException {
        writeInt(1);
        writeByte(M_INTERESTED);
    }

    public void close() throws IOException {
        this.socket.close();
    }

    public byte[] downloadBlock(int pieceIndex, int begin, int blockSize) throws IOException {
        writeInt(13);
        writeByte(M_REQUEST);
        writeInt(pieceIndex);
        writeInt(begin);
        writeInt(blockSize);
        logger.info("download request sent piece {}, offset {}", pieceIndex, begin);
        return readBlock();
    }
}
