package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CharacterCountUtilsTest {

    private CharacterCountUtils characterCountUtils;

    @BeforeEach
    void setUp() {
        characterCountUtils = new CharacterCountUtils();
    }

    @Test
    void testInitialTotalChars() {
        assertEquals(0, characterCountUtils.getTotalChars());
    }

    @Test
    void testAddChars() {
        characterCountUtils.addChars(10);
        assertEquals(10, characterCountUtils.getTotalChars());
    }

    @Test
    void testAddCharsMultipleTimes() {
        characterCountUtils.addChars(5);
        characterCountUtils.addChars(15);
        characterCountUtils.addChars(20);
        assertEquals(40, characterCountUtils.getTotalChars());
    }

    @Test
    void testAddCharsWithZero() {
        characterCountUtils.addChars(0);
        assertEquals(0, characterCountUtils.getTotalChars());
    }

    @Test
    void testAddCharsWithNegative() {
        characterCountUtils.addChars(10);
        characterCountUtils.addChars(-5);
        assertEquals(5, characterCountUtils.getTotalChars());
    }

    @Test
    void testReset() {
        characterCountUtils.addChars(100);
        characterCountUtils.reset();
        assertEquals(0, characterCountUtils.getTotalChars());
    }

    @Test
    void testResetAfterMultipleAdds() {
        characterCountUtils.addChars(50);
        characterCountUtils.addChars(30);
        characterCountUtils.reset();
        characterCountUtils.addChars(20);
        assertEquals(20, characterCountUtils.getTotalChars());
    }
}

