package com.bogda.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AESUtilsTest {

    @Test
    public void testEncryptMD5() {
        String md5 = AESUtils.encryptMD5("hello");
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5);
    }

    @Test
    public void testEncryptDeterministic() throws Exception {
        String plain = "The quick brown fox jumps over the lazy dog";
        String a = AESUtils.encrypt(plain);
        String b = AESUtils.encrypt(plain);

        assertNotNull(a);
        assertNotNull(b);
        assertNotEquals(plain, a);
        assertEquals(a, b, "Encryption with fixed key and algorithm should be deterministic");

        String other = AESUtils.encrypt(plain + "!");
        assertNotEquals(a, other, "Different plaintext should produce different ciphertext");
    }
}

