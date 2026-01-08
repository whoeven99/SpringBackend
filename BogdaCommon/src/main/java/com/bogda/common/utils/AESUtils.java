package com.bogda.common.utils;

import com.microsoft.applicationinsights.core.dependencies.google.common.hash.Hashing;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class AESUtils {
    // 密钥（长度必须为 16、24 或 32 字节）
    private static final String SECRET_KEY = "975318642mnbvcxz"; // 16字节 = 128位
    private static final String ALGORITHM = "AES";

    // 加密
    public static String encrypt(String plainText) throws Exception {
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * 对sourceText做MD5加密
     * */
    public static String encryptMD5(String sourceText) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sourceText.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            return null;
        }
    }

    // MurmurHash 加密
    public static String hashEncrypt(String text) {
        long hash = Hashing.murmur3_128()
                .hashString(text, StandardCharsets.UTF_8)
                .asLong();              // 64 bit
        return Long.toUnsignedString(hash, 36); // base36，变短
    }
}
