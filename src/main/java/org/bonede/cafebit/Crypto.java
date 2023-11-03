package org.bonede.cafebit;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Crypto {
    public static byte[] sha1(byte[] input){
        try {
            MessageDigest sha1 = MessageDigest.getInstance("sha1");
            return sha1.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    public static String sha1Hex(byte[] input){
        return hex(sha1(input));
    }

    public static String urlEncodeBytes(byte[] bytes){
        StringBuilder urlString = new StringBuilder(3 * bytes.length);
        for (byte b : bytes) {
            urlString.append(String.format("%%%02x", b));
        }
        return urlString.toString();
    }
}
