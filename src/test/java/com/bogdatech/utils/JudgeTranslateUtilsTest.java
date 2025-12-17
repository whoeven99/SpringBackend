package com.bogdatech.utils;

import com.bogdatech.enums.RejectRuleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.bogdatech.utils.JudgeTranslateUtils.generalTranslateV2;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JudgeTranslateUtilsTest {
    @Test
    @DisplayName("value 为 null 或空白时不翻译")
    void testNullOrBlank() {
        assertFalse(generalTranslateV2("k", null));
        assertFalse(generalTranslateV2("k", ""));
        assertFalse(generalTranslateV2("k", "   "));
    }

    @Test
    @DisplayName("HTML 内容直接放行")
    void testHtmlPass() {
        assertTrue(generalTranslateV2("k", "<div>Hello</div>"));
        assertTrue(generalTranslateV2("k", "<span>中文</span>"));
    }

    @Test
    @DisplayName("包含 px 的值应被拦截")
    void testContainsPx() {
        assertFalse(generalTranslateV2("k", "12px"));
        assertFalse(generalTranslateV2("k", "width:100px"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "TRUE", "FALSE"})
    @DisplayName("true / false 不翻译")
    void testBooleanLiteral(String value) {
        assertFalse(generalTranslateV2("k", value));
    }

    @Test
    @DisplayName("# 开头且长度不超过 90 应被拦截")
    void testHashPrefix() {
        assertFalse(generalTranslateV2("k", "#abc"));
        assertFalse(generalTranslateV2("k", "#1234567890"));
    }

    @Test
    @DisplayName("+= 开头应被拦截")
    void testPlusEqualPrefix() {
        assertFalse(generalTranslateV2("k", "+=1+2"));
        assertFalse(generalTranslateV2("k", "+=SUM(A1:A2)"));
    }

    @Test
    @DisplayName("纯数字或标点应被拦截")
    void testPureDigitPunc() {
        assertFalse(generalTranslateV2("k", "123"));
        assertFalse(generalTranslateV2("k", "4.8,755"));
        assertFalse(generalTranslateV2("k", "!!!"));
    }

    @Test
    @DisplayName("15 位字母数字 ID 应被拦截")
    void testId15() {
        assertFalse(generalTranslateV2("k", "f72ySxJ79BVY6Jx"));
        assertFalse(generalTranslateV2("k", "kOnfPGijtn3d0Lw"));
    }

    @Test
    @DisplayName("UUID 应被拦截")
    void testUUID() {
        assertFalse(generalTranslateV2(
                "k",
                "550e8400-e29b-41d4-a716-446655440000"
        ));
    }

    @Test
    @DisplayName("GA 标识应被拦截")
    void testGA() {
        assertFalse(generalTranslateV2("k", "GA1.1.1942061952.1762770638"));
    }

    @Test
    @DisplayName("GA key 应被拦截")
    void testGAKey() {
        assertFalse(generalTranslateV2("k", "_gid"));
        assertFalse(generalTranslateV2("k", "_gat"));
    }

    @Test
    @DisplayName("Base64 字符串应被拦截")
    void testBase64() {
        assertFalse(generalTranslateV2(
                "k",
                "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="
        ));
    }

    @Test
    @DisplayName("Hash 值应被拦截")
    void testHash() {
        assertFalse(generalTranslateV2(
                "k",
                "e10adc3949ba59abbe56e057f20f883e"
        ));
        assertFalse(generalTranslateV2(
                "k",
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
        ));
    }

    @Test
    @DisplayName("JWT Token 应被拦截")
    void testJwt() {
        assertFalse(generalTranslateV2(
                "k",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4ifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        ));
    }

    @Test
    @DisplayName("疑似随机字符串应被拦截")
    void testSuspiciousAlnum() {
        assertFalse(generalTranslateV2("k", "UXxSP8cSm"));
        assertFalse(generalTranslateV2("k", "UgvyqJcxm123"));
    }

    @Test
    @DisplayName("电话号码应被拦截")
    void testPhone() {
        assertFalse(generalTranslateV2("k", "+8613812345678"));
    }

    @Test
    @DisplayName("邮箱应被拦截")
    void testEmail() {
        assertFalse(generalTranslateV2("k", "test@example.com"));
    }

    @Test
    @DisplayName("正常文本应放行")
    void testNormalTextPass() {
        assertTrue(generalTranslateV2("k", "Hello world"));
        assertTrue(generalTranslateV2("k", "这是一个商品标题"));
        assertTrue(generalTranslateV2("k", "Buy now and save more"));
    }

    @Test
    @DisplayName("RejectRuleEnum 不应为空")
    void testEnumNotEmpty() {
        assertTrue(RejectRuleEnum.values().length > 0);
    }
}
