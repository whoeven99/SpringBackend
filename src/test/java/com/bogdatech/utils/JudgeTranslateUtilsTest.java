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
    void testNullOrBlankValue() {
        assertFalse(generalTranslateV2("k1", null));
        assertFalse(generalTranslateV2("k1", ""));
        assertFalse(generalTranslateV2("k1", "   "));
    }

    @Test
    @DisplayName("HTML 内容直接放行")
    void testHtmlShouldPass() {
        assertTrue(generalTranslateV2("k_html", "<div>Hello</div>"));
        assertTrue(generalTranslateV2("k_html", "<span>你好</span>"));
    }

    @Test
    @DisplayName("+= 开头的字符串应被拦截")
    void testPlusEqualPrefix() {
        assertFalse(generalTranslateV2("k1", "+=SUM(A1:A2)"));
        assertFalse(generalTranslateV2("k1", "+=1+2"));
    }

    @Test
    @DisplayName("纯数字应被拦截")
    void testPureNumber() {
        assertFalse(generalTranslateV2("k_num", "123"));
        assertFalse(generalTranslateV2("k_num", "000001"));
    }

    @Test
    @DisplayName("纯数字 + 标点应被拦截")
    void testDigitAndPunctuation() {
        assertFalse(generalTranslateV2("k_punc", "4.8,755"));
        assertFalse(generalTranslateV2("k_punc", "!!!"));
        assertFalse(generalTranslateV2("k_punc", "123-456"));
    }

    @Test
    @DisplayName("UUID 应被拦截")
    void testUUID() {
        assertFalse(generalTranslateV2(
                "k_uuid",
                "550e8400-e29b-41d4-a716-446655440000"
        ));
    }

    @Test
    @DisplayName("15位字母数字 ID 应被拦截")
    void testId15() {
        assertFalse(generalTranslateV2("k_id", "f72ySxJ79BVY6Jx"));
        assertFalse(generalTranslateV2("k_id", "kOnfPGijtn3d0Lw"));
    }

    @Test
    @DisplayName("GA 标识应被拦截")
    void testGA() {
        assertFalse(generalTranslateV2("k_ga", "GA1.1.1942061952.1762770638"));
    }

    @Test
    @DisplayName("Base64 字符串应被拦截")
    void testBase64() {
        assertFalse(generalTranslateV2(
                "k_base64",
                "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="
        ));
    }

    @Test
    @DisplayName("Hash 字符串应被拦截")
    void testHash() {
        assertFalse(generalTranslateV2(
                "k_hash32",
                "e10adc3949ba59abbe56e057f20f883e"
        ));

        assertFalse(generalTranslateV2(
                "k_hash64",
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
        ));
    }

    @Test
    @DisplayName("JWT Token 应被拦截")
    void testJWT() {
        assertFalse(generalTranslateV2(
                "k_jwt",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4ifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        ));
    }

    @Test
    @DisplayName("正常可翻译文本应放行")
    void testNormalTextShouldPass() {
        assertTrue(generalTranslateV2("k_text", "Hello world"));
        assertTrue(generalTranslateV2("k_text", "这是一个商品标题"));
        assertTrue(generalTranslateV2("k_text", "Buy now and save more"));
    }

    @Test
    @DisplayName("RejectRuleEnum 不应为空")
    void testRejectRuleEnumNotEmpty() {
        assertTrue(RejectRuleEnum.values().length > 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123",
            "4.8,755",
            "GA1.1.1.1",
            "f72ySxJ79BVY6Jx"
    })
    void testBatchRejectedValues(String value) {
        assertFalse(generalTranslateV2("k_batch", value));
    }
}
