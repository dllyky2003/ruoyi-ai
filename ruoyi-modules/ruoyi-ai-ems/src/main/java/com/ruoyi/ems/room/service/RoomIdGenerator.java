package com.ruoyi.ems.room.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * sessionId 格式：yyyyMMdd-XXXXXXXX
 * 8 位 base32 随机码，兼顾可读性和唯一性。
 */
@Component
public class RoomIdGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int SUFFIX_LEN = 8;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        String date = LocalDate.now().format(DATE_FMT);
        StringBuilder sb = new StringBuilder(SUFFIX_LEN);
        for (int i = 0; i < SUFFIX_LEN; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return date + "-" + sb;
    }
}
