package com.siparo.common.util;

import java.security.SecureRandom;

public final class CodeGenerator {
    /** Karıştırılabilecek karakterler (0/O, 1/I) çıkarıldı. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodeGenerator() {}

    public static String code(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return builder.toString();
    }

    public static String digits(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) builder.append(RANDOM.nextInt(10));
        return builder.toString();
    }
}
