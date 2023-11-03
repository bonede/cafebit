package org.bonede.cafebit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BencodeTest {

    @Test
    public void parseInt(){
        assertEquals(Bencode.parse("i:42e").intValue, 42);
        assertEquals(Bencode.parse("i:-42e").intValue, -42);
    }


    @Test
    public void parseBytes(){
        assertArrayEquals(Bencode.parse("1:1").bytes.array(), "1".getBytes());
    }

}