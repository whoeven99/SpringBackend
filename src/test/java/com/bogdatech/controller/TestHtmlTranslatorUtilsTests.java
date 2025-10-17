package com.bogdatech.controller;


import com.bogdatech.Service.IVocabularyService;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.LiquidHtmlTranslatorUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 单元测试：针对 LiquidHtmlTranslatorUtils.newJsonTranslateHtml 的稳定性/健壮性测试
 * 核心策略：
 * - spy 一个真实的 LiquidHtmlTranslatorUtils 实例
 * - mock 掉 translateAllList（模拟批量翻译行为）
 * - 在需要时 mock TranslateRequest 的 getter（例如 getTarget）
 */
@SpringBootTest
public class TestHtmlTranslatorUtilsTests {
    @MockBean
    private RedisProcessService redisProcessService;

    @MockBean
    private IVocabularyService vocabularyService;

    @MockBean
    private JsoupUtils jsoupUtils;
    @SpyBean
    private LiquidHtmlTranslatorUtils utilsSpy;

    @Autowired
    private LiquidHtmlTranslatorUtils utils; // Spring 注入的 Bean


    /**
     * 非 HTML 输入应直接返回 null（方法里调用 isHtml 判断）
     */
    @Test
    public void testNonHtmlInputReturnsNull() {
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("en");

        String input = "just a plain text without tags";
        // 不 stub translateAllList，期望方法直接返回 null
        String out = utilsSpy.newJsonTranslateHtml(input, req, null, null, null, false);
        assertNull(out, "非HTML输入应该返回 null");
    }

    /**
     * HTML 片段翻译：保持前后空格，核心文本被替换为翻译结果
     */
    @Test
    public void testFragmentTranslationPreservesSpaces() {
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("fr");
        when(req.getShopName()).thenReturn("shop");
        when(req.getSource()).thenReturn("en");

        String html = "<div>  hello  </div>";
        Map<String, String> translated = Map.of("hello", "bonjour");

        doReturn(translated)
                .when(utilsSpy)
                .translateAllList(anyList(), any(), any(), anyString(), any(), false);
        when(jsoupUtils.translateByCiwiUserModel(any(), any(), any(), any(), any(), any(), any(), false))
                .thenReturn("{\"hello\":\"bonjour\"}");

        String out = utilsSpy.newJsonTranslateHtml(html, req, null, null, null, false);

        assertNotNull(out);
        assertTrue(out.contains(">  bonjour  <"),
                "应保留前后两个空格且文本被翻译");
    }

    /**
     * 完整 HTML 文档：lang 属性应被设置为请求目标语言，文本被翻译
     */
    @Test
    public void testFullHtmlLanguageAttrAndTranslation() {
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("fr");
        when(req.getShopName()).thenReturn("shop");

        String html = "<html><head><title>My</title></head><body><p>Title</p></body></html>";

        // 翻译 map
        Map<String, String> translated = new HashMap<>();
        translated.put("My", "Mon");
        translated.put("Title", "Titre");

        doReturn(translated).when(utilsSpy).translateAllList(anyList(), any(), any(), anyString(), any(), false);
        when(jsoupUtils.translateByCiwiUserModel(any(), any(), any(), any(), any(), any(), any(), false))
                .thenReturn("{\"My\":\"Mon\", \"Title\":\"Titre\"}");
        String out = utilsSpy.newJsonTranslateHtml(html, req, null, null, null, false);
        assertNotNull(out);

        // lang 属性
        assertTrue(out.contains("<html") && out.contains("lang=\"fr\""), "html 标签应包含 lang=\"fr\"");

        // 翻译后的文本片段
        assertTrue(out.contains("Mon") && out.contains("Titre"), "应包含翻译后的 My -> Mon 和 Title -> Titre");
    }

    /**
     * 重复文本只应在 originalTexts 中出现一次（即去重后发送到 translateAllList）
     */
    @Test
    public void testDuplicateTextsTranslatedOnce() {
        // mock request
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("en");
        when(req.getShopName()).thenReturn("shop");

        String html = "<div><p>Duplicate</p><span>Duplicate</span><div> Unique </div></div>";

        // 构造翻译结果
        Map<String, String> translated = new HashMap<>();
        translated.put("Duplicate", "DUP");
        translated.put("Unique", "UNQ");

        // stub translateAllList（这里不要 capture，用 any() 就好）
        doReturn(translated).when(utilsSpy)
                .translateAllList(any(), any(), any(), any(), any(), false);

        // stub jsoupUtils
        when(jsoupUtils.translateByCiwiUserModel(any(), any(), any(), any(), any(), any(), any(), false))
                .thenReturn("{\"Duplicate\":\"DUP\", \"Unique\":\"UNQ\"}");

        // 调用被测方法
        String out = utilsSpy.newJsonTranslateHtml(html, req, null, null, null, false);
        assertNotNull(out);

        // verify + 捕获参数
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(utilsSpy).translateAllList(captor.capture(), any(), any(), any(), any(), false);

        // 验证捕获到的参数
        List<String> passedList = captor.getValue();
        assertNotNull(passedList);
        assertEquals(2, passedList.size(), "originalTexts 应去重后只含 2 个不同文本");
        assertTrue(passedList.contains("Duplicate"));
        assertTrue(passedList.contains("Unique"));

        // 验证输出已替换为翻译结果
        assertTrue(out.contains("DUP"));
        assertTrue(out.contains("UNQ"));
    }


    /**
     * 如果页面只包含空白文本节点（无可翻译核心文本），translateAllList 不应被调用
     */
    @Test
    public void testWhitespaceOnlyNodesDoNotTriggerTranslation() {
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("en");
        when(req.getShopName()).thenReturn("shop");

        String html = "<div>   <p>    </p> \u00A0 </div>"; // 普通空格 + 不断行空格

        // 不 stub translateAllList，直接 verify never called
        String out = utilsSpy.newJsonTranslateHtml(html, req, null, null, null, false);
        assertNotNull(out);

        // translateAllList 应该从未被调用（因为 originalTexts 为空）
        verify(utilsSpy, never()).translateAllList(anyList(), any(), any(), anyString(), any(), false);
    }

    /**
     * 当 translateAllList 返回空 map 时，页面文本保持不变（即填回原文本）
     */
    @Test
    void testNoTranslatedMapLeavesOriginalText() {
        // mock TranslateRequest
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("en");
        when(req.getShopName()).thenReturn("shop");
        when(req.getSource()).thenReturn("source");

        // mock Redis 和 DB 返回 null
        when(redisProcessService.getCacheData(anyString(), anyString())).thenReturn(null);
        when(vocabularyService.getTranslateTextDataInVocabulary(anyString(), anyString(), anyString())).thenReturn(null);

        // mock JsoupUtils 的翻译调用，避免走真实逻辑
        when(jsoupUtils.translateByCiwiUserModel(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString(), false))
                .thenReturn("{}"); // 返回空 JSON，模拟没有翻译

        String html = "<div> keep </div>";
        String out = utils.newJsonTranslateHtml(html, req, null, null, null, false);

        assertNotNull(out);
        assertTrue(out.contains("keep"), "没有翻译结果时应保留原始文本");
    }

    /**
     * 复杂嵌套 + 多节点混合
     * HTML 中包含深度嵌套的 div > span > p，并混合有多段文本。
     * 验证所有文本节点都能被替换，并且 HTML 结构保持不变。*/
    @Test
    public void testNestedElementsTranslation() throws JsonProcessingException {
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("es");
        when(req.getShopName()).thenReturn("shop");

        String html = "<div><span><p>Hello</p>World</span><footer>Bye</footer></div>";
        Map<String, String> translated = Map.of("Hello", "Hola", "World", "Mundo", "Bye", "Adiós");

        doReturn(translated).when(utilsSpy).translateAllList(anyList(), any(), any(), anyString(), any(), false);
        String s = OBJECT_MAPPER.writeValueAsString(translated);
        when(jsoupUtils.translateByCiwiUserModel(any(), any(), any(), any(), any(), any(), any(), false))
                .thenReturn(s);
        String out = utilsSpy.newJsonTranslateHtml(html, req, null, null, null, false);

        assertTrue(out.contains("Hola"));
        assertTrue(out.contains("Mundo"));
        assertTrue(out.contains("Adiós"));
    }

    /**
     *包含 <script>, <style> 等不应翻译的标签
     * */
    @Test
    public void testScriptAndStyleContentNotTranslated() throws JsonProcessingException {
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("de");

        String html = "<html><head><style>.cls{color:red}</style></head>"
                + "<body><script>var a=1;</script><p>Text</p></body></html>";

        Map<String, String> translated = Map.of("Text", "Text_DE");
        doReturn(translated).when(utilsSpy).translateAllList(anyList(), any(), any(), anyString(), any(), false);
        String s = OBJECT_MAPPER.writeValueAsString(translated);
        when(jsoupUtils.translateByCiwiUserModel(any(), any(), any(), any(), any(), any(), any(), false))
                .thenReturn(s);
        String out = utilsSpy.newJsonTranslateHtml(html, req, null, null, null, false);

        // 确认 script/style 保持不变
        assertTrue(out.contains("var a=1;"));
        assertTrue(out.contains(".cls{color:red}"));
        // 确认正常文本被翻译
        assertTrue(out.contains("Text_DE"));
    }

    /**
     *包含 HTML 实体字符（如 &amp;, &nbsp;）
     * */
    @Test
    public void testHtmlEntitiesDecodedAndTranslated() throws JsonProcessingException {
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("fr");

        String html = "<p>A &amp; B&nbsp;C</p>";
        Map<String, String> translated = Map.of("A & B C", "A et B C");
        doReturn(translated).when(utilsSpy).translateAllList(anyList(), any(), any(), anyString(), any(), false);
        String s = OBJECT_MAPPER.writeValueAsString(translated);
        when(jsoupUtils.translateByCiwiUserModel(any(), any(), any(), any(), any(), any(), any(), false))
                .thenReturn(s);
        String out = utilsSpy.newJsonTranslateHtml(html, req, null, null, null, false);

        assertTrue(out.contains("A et B"));
    }

    /**
     * 大段文本 + 多批次翻译
     * 构造超过 50 段文本的 HTML，确认分批翻译逻辑被调用。
     * 可以用 ArgumentCaptor 捕获批次数量。
     * */
    @Test
    public void testLargeHtmlTriggersBatchTranslation() throws JsonProcessingException {
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("en");
        when(req.getShopName()).thenReturn("shop");

        // 构造 > 60 段文本
        StringBuilder sb = new StringBuilder("<div>");
        for (int i = 0; i < 60; i++) {
            sb.append("<p>Text: 12312312312312312312312312312312312312312312312312312312312312312312312 : ").append(i).append("</p>");
        }
        sb.append("</div>");
        String html = sb.toString();

        Map<String, String> translated = new HashMap<>();
        for (int i = 0; i < 60; i++) {
            translated.put("Text: 12312312312312312312312312312312312312312312312312312312312312312312312 : " + i, "T" + i);
        }

        doReturn(translated).when(utilsSpy).translateAllList(anyList(), any(), any(), anyString(), any(), false);
        String s = OBJECT_MAPPER.writeValueAsString(translated);
        when(jsoupUtils.translateByCiwiUserModel(any(), any(), any(), any(), any(), any(), any(), false))
                .thenReturn(s);
        String out = utilsSpy.newJsonTranslateHtml(html, req, null, null, null, false);

        assertTrue(out.contains("T0") && out.contains("T59"));

        // 验证 translateAllList 被调用时 list size > 50
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(utilsSpy).translateAllList(captor.capture(), any(), any(), any(), any(), false);
        assertTrue(captor.getValue().size() >= 50);
    }

    /**
     * 空标签（无文本节点）
     * 确认 <br>, <img> 等不会报错。
     * */
    @Test
    public void testEmptyAndSelfClosingTagsSafe() throws JsonProcessingException {
        TranslateRequest req = mock(TranslateRequest.class);
        when(req.getTarget()).thenReturn("en");

        String html = "<div><img src='a.jpg'/><br/><p>Hi</p></div>";
        Map<String, String> translated = Map.of("Hi", "Hello");
        doReturn(translated).when(utilsSpy).translateAllList(anyList(), any(), any(), anyString(), any(), false);
        String s = OBJECT_MAPPER.writeValueAsString(translated);
        when(jsoupUtils.translateByCiwiUserModel(any(), any(), any(), any(), any(), any(), any(), false))
                .thenReturn(s);
        String out = utilsSpy.newJsonTranslateHtml(html, req, null, null, null, false);
        assertTrue(out.contains("Hello"));
        assertTrue(out.contains("<img src=\"a.jpg\""));
    }

}
