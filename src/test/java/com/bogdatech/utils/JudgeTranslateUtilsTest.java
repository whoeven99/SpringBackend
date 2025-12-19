package com.bogdatech.utils;

import com.bogdatech.enums.RejectRuleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.bogdatech.utils.JudgeTranslateUtils.translationRuleJudgment;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JudgeTranslateUtilsTest {
    @Test
    @DisplayName("value 为 null 或空白时不翻译")
    void testNullOrBlank() {
        assertFalse(translationRuleJudgment("k", null));
        assertFalse(translationRuleJudgment("k", ""));
        assertFalse(translationRuleJudgment("k", "   "));
    }

    @Test
    @DisplayName("HTML 内容直接放行")
    void testHtmlPass() {
        assertTrue(translationRuleJudgment("k", "<div>Hello</div>"));
        assertTrue(translationRuleJudgment("k", "<span>中文</span>"));
    }

    @Test
    @DisplayName("包含 px 的值应被拦截")
    void testContainsPx() {
        assertFalse(translationRuleJudgment("k", "12px"));
        assertFalse(translationRuleJudgment("k", "width:100px"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "TRUE", "FALSE"})
    @DisplayName("true / false 不翻译")
    void testBooleanLiteral(String value) {
        assertFalse(translationRuleJudgment("k", value));
    }

    @Test
    @DisplayName("# 开头且长度不超过 90 应被拦截")
    void testHashPrefix() {
        assertFalse(translationRuleJudgment("k", "#abc"));
        assertFalse(translationRuleJudgment("k", "#1234567890"));
    }

    @Test
    @DisplayName("=+ 开头应被拦截")
    void testPlusEqualPrefix() {
        assertFalse(translationRuleJudgment("k", "=+1+2"));
        assertFalse(translationRuleJudgment("k", "=+SUM(A1:A2)"));
    }

    @Test
    @DisplayName("纯数字或标点应被拦截")
    void testPureDigitPunc() {
        assertFalse(translationRuleJudgment("k", "123"));
        assertFalse(translationRuleJudgment("k", "4.8,755"));
        assertFalse(translationRuleJudgment("k", "!!!"));
    }

    @Test
    @DisplayName("15 位字母数字 ID 应被拦截")
    void testId15() {
        assertFalse(translationRuleJudgment("k", "f72ySxJ79BVY6Jx"));
        assertFalse(translationRuleJudgment("k", "kOnfPGijtn3d0Lw"));
    }

    @Test
    @DisplayName("UUID 应被拦截")
    void testUUID() {
        assertFalse(translationRuleJudgment(
                "k",
                "550e8400-e29b-41d4-a716-446655440000"
        ));
    }

    @Test
    @DisplayName("GA 标识应被拦截")
    void testGA() {
        assertFalse(translationRuleJudgment("k", "GA1.1.1942061952.1762770638"));
    }

    @Test
    @DisplayName("GA key 应被拦截")
    void testGAKey() {
        assertFalse(translationRuleJudgment("k", "_gid"));
        assertFalse(translationRuleJudgment("k", "_gat"));
    }

    @Test
    @DisplayName("Base64 字符串应被拦截")
    void testBase64() {
        assertFalse(translationRuleJudgment(
                "k",
                "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="
        ));
    }

    @Test
    @DisplayName("Hash 值应被拦截")
    void testHash() {
        assertFalse(translationRuleJudgment(
                "k",
                "e10adc3949ba59abbe56e057f20f883e"
        ));
        assertFalse(translationRuleJudgment(
                "k",
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
        ));
    }

    @Test
    @DisplayName("JWT Token 应被拦截")
    void testJwt() {
        assertFalse(translationRuleJudgment(
                "k",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4ifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        ));
    }

    @Test
    @DisplayName("疑似随机字符串应被拦截")
    void testSuspiciousAlnum() {
        assertFalse(translationRuleJudgment("k", "UXxSP8cSm"));
        assertFalse(translationRuleJudgment("k", "UgvyqJcxm123"));
    }

    @Test
    @DisplayName("电话号码应被拦截")
    void testPhone() {
        assertFalse(translationRuleJudgment("k", "+8613812345678"));
    }

    @Test
    @DisplayName("邮箱应被拦截")
    void testEmail() {
        assertFalse(translationRuleJudgment("k", "test@example.com"));
    }

    @Test
    @DisplayName("正常文本应放行")
    void testNormalTextPass() {
        assertTrue(translationRuleJudgment("k", "Hello world"));
        assertTrue(translationRuleJudgment("k", "这是一个商品标题"));
        assertTrue(translationRuleJudgment("k", "Buy now and save more"));
    }

    @Test
    @DisplayName("质量文档数据")
    void testQualityDoc() {
        assertFalse(translationRuleJudgment("k", "f72ySxJ79BVY6Jx"));
        assertFalse(translationRuleJudgment("k", "kOnfPGijtn3d0Lw"));
        assertFalse(translationRuleJudgment("k", "4.8,755"));
        assertFalse(translationRuleJudgment("k", "GA1.1.1942061952.1762770638"));
        assertFalse(translationRuleJudgment("k", "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="));
        assertFalse(translationRuleJudgment("k", "=+ Use new payment method"));
        assertFalse(translationRuleJudgment("k", "YYYY-MM-DD"));
        assertFalse(translationRuleJudgment("k", "G-V8YT1T7LZK"));
        assertTrue(translationRuleJudgment("k", "I like A-B-C"));
        assertFalse(translationRuleJudgment("k", "V-HUB"));
        assertFalse(translationRuleJudgment("k", "essential-performance-t-shirt-navy-blue"));
    }

    @Test
    @DisplayName("RejectRuleEnum 不应为空")
    void testEnumNotEmpty() {
        assertTrue(RejectRuleEnum.values().length > 0);
    }
}
