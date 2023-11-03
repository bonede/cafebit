package org.bonede.cafebit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class BTorrent {
    public static class BTorrentError extends RuntimeException{
        public BTorrentError(String msg){
            super(msg);
        }
    }
    private Bencode.BValue bValue;


    public BTorrent(InputStream inputStream) {
        Bencode.BValue bValue = null;
        try {
            bValue = Bencode.parse(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if(bValue.getTag() != Bencode.BValueTag.Dict){
            throw new BTorrentError("Invalid torrent file");
        }
        this.bValue = bValue;
    }

    public Bencode.BValue getInfo(){
        return bValue.get("info");
    }

    public int getLength(){
        return getInfo().get("length").getIntValue();
    }

    public String getName(){
        return new String(getInfo().get("name").getBytesValue());
    }

    public int getPieceLength(){
        return getInfo().get("piece length").getIntValue();
    }

    public int getPieceNum(){
        byte[] bytes = getInfo().get("pieces").getBytesValue();
        return bytes.length / 20;
    }


    public byte[] getPieceAt(int num){
        byte[] bytes = getInfo().get("pieces").getBytesValue();
        return  Arrays.copyOfRange(bytes, num * 20, (num + 1) * 20);
    }


    public String getAnnounce(){
        return new String(this.bValue.get("announce").getBytesValue());
    }




    public byte[] getInfoHash(){
        Bencode.BValue info = getInfo();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            info.encode(outputStream);
            outputStream.close();
            return Crypto.sha1(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
