package com.urlshortener.util;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();
    
    /**
     * Encodes a numeric ID to a Base62 string
     * Example: 1000 -> "g8"
     */
    public String encode(long id) {
        if (id == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.insert(0, ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        return sb.toString();
    }
    
    /**
     * Decodes a Base62 string back to a numeric ID
     * Example: "g8" -> 1000
     */
    public long decode(String str) {
        long result = 0;
        for (int i = 0; i < str.length(); i++) {
            result = result * BASE + ALPHABET.indexOf(str.charAt(i));
        }
        return result;
    }
    
    /**
     * Generates a random short code of specified length
     */
    public String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int randomIndex = (int) (Math.random() * BASE);
            sb.append(ALPHABET.charAt(randomIndex));
        }
        return sb.toString();
    }
}