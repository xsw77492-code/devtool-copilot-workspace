package com.devtoolcopilot.user.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public class TokenUtils {
    private static final SecureRandom RND = new SecureRandom();

    public static String newOpaqueToken() {
        byte[] b = new byte[32];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256Hex(String v) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(v.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String newNumericCode(int len) {
        if (len <= 0) throw new IllegalArgumentException("len must be > 0");
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('0' + RND.nextInt(10)));
        }
        return sb.toString();
    }
}
