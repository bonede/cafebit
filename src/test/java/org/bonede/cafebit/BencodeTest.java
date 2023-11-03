package org.bonede.cafebit;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BencodeTest {

    @Test
    public void parseInt(){
        Bencode.Parser parser = new Bencode.Parser("i42e");
        parser.parse();
        assertTrue(parser.accepted());
        assertEquals(parser.context(), Bencode.Parser.Context.ContextRoot);
        assertEquals(42, parser.getValue().getIntValue());
    }
//
//
    @Test
    public void parseBytes(){
        Bencode.Parser parser = new Bencode.Parser("1:1");
        parser.parse();
        assertTrue(parser.accepted());
        assertArrayEquals("1".getBytes(), parser.getValue().getBytesValue());
    }
//
    @Test
    public void parseList(){

        Bencode.Parser parser = new Bencode.Parser("l4:spami42e1:1e");
        parser.parse();
        assertTrue(parser.accepted());
        ArrayList<Bencode.BValue> values = parser.getValue().getListValue();
        assertArrayEquals("spam".getBytes(), values.get(0).getBytesValue());
        assertEquals(42, values.get(1).getIntValue());
        assertArrayEquals("1".getBytes(), values.get(2).getBytesValue());

        parser = new Bencode.Parser("llee");
        parser.parse();
        assertTrue(parser.accepted());
        values = parser.getValue().getListValue();
        assertEquals(0, values.get(0).getListValue().size());

        parser = new Bencode.Parser("lll4:spami42e1:12:1ei32eeee");
        parser.parse();
        assertTrue(parser.accepted());
//
//        parser = new Bencode.Parser("l4:spami42e1:12:1ei32elee");
//        parser.parse();
//        assertTrue(parser.accepted());
    }

    @Test
    public void parseDict(){
        Bencode.Parser parser;
        parser = new Bencode.Parser("de");
        parser.parse();
        assertTrue(parser.accepted());
        parser = new Bencode.Parser("d3:bar4:spame");
        parser.parse();
        assertTrue(parser.accepted());
        ArrayList<Bencode.BPair> dict = parser.getValue().getDictValue();
        assertArrayEquals("bar".getBytes(), dict.get(0).getKey().getBytesValue());
        assertArrayEquals("spam".getBytes(), dict.get(0).getValue().getBytesValue());

        parser = new Bencode.Parser("d3:bari42e3:foo4:spame");
        parser.parse();

        assertTrue(parser.accepted());
        dict = parser.getValue().getDictValue();

        assertArrayEquals("bar".getBytes(), dict.get(0).getKey().getBytesValue());
        assertEquals(42, dict.get(0).getValue().getIntValue());

        assertArrayEquals("foo".getBytes(), dict.get(1).getKey().getBytesValue());

        assertArrayEquals("spam".getBytes(), dict.get(1).getValue().getBytesValue());


    }


    @Test
    public void parseFile() throws IOException {
        String file = "sample.torrent";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(file);
        if(inputStream == null){
            throw new RuntimeException("File not found: " + file);
        }
        byte[] bytes = inputStream.readAllBytes();

        Bencode.Parser parser = new Bencode.Parser(bytes);
        parser.parse();
        assertTrue(parser.accepted());
        Bencode.BValue info = parser.getValue().get("info");
        assertNotNull(info);

    }

    @Test
    public void parsePeers() throws IOException {
        String file = "/data/peers.bencode";
        InputStream inputStream = new FileInputStream(file);
        if(inputStream == null){
            throw new RuntimeException("File not found: " + file);
        }
        byte[] bytes = inputStream.readAllBytes();

        Bencode.Parser parser = new Bencode.Parser(bytes);
        parser.parse();
        assertTrue(parser.accepted());



    }

    @Test
    public void encode() throws IOException {
        String file = "sample.torrent";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(file);
        if(inputStream == null){
            throw new RuntimeException("File not found: " + file);
        }
        byte[] bytes = inputStream.readAllBytes();

        Bencode.Parser parser = new Bencode.Parser(bytes);
        parser.parse();
        assertTrue(parser.accepted());

        OutputStream outputStream = new FileOutputStream("/data/sample-out.torrent");

        parser.getValue().encode(outputStream);
        outputStream.close();
    }



}