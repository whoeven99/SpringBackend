package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.JsoupUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListTranslateStrategyServiceTest {

    @Mock
    private BatchTranslateStrategyService batchTranslateStrategyService;

    @Mock
    private HtmlTranslateStrategyService htmlTranslateStrategyService;

    @InjectMocks
    private ListTranslateStrategyService listTranslateStrategyService;

    private String testTarget;
    private String testAiModel;
    private String testModule;
    private String testShopName;

    @BeforeEach
    void setUp() {
        testTarget = "zh";
        testAiModel = "gemini-3-flash";
        testModule = "METAFIELD";
        testShopName = "unit-test-shop";
    }

    @Test
    void testGetType_ShouldReturnList() {
        assertEquals("LIST", listTranslateStrategyService.getType());
    }

    // ====== 场景 1：纯文本 list，全部走 batch ======
    @Test
    void testTranslate_PureTextList_AllGoBatch() {
        String input = "[\"Hello\",\"World\",\"Goodbye\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        try (MockedStatic<JsoupUtils> mockedJsoup = mockStatic(JsoupUtils.class)) {
            mockedJsoup.when(() -> JsoupUtils.isHtml(anyString())).thenReturn(false);

            doAnswer(invocation -> {
                TranslateContext batchCtx = invocation.getArgument(0);
                Map<Integer, String> original = batchCtx.getOriginalTextMap();
                Map<Integer, String> translated = batchCtx.getTranslatedTextMap();
                for (Map.Entry<Integer, String> entry : original.entrySet()) {
                    translated.put(entry.getKey(), entry.getValue() + "_翻译");
                }
                batchCtx.incrementUsedTokenCount(100);
                batchCtx.setCachedCount(1);
                return null;
            }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            listTranslateStrategyService.translate(ctx);

            verify(batchTranslateStrategyService).translate(any(TranslateContext.class));
            verify(htmlTranslateStrategyService, never()).translate(any(TranslateContext.class));

            List<String> result = JsonUtils.jsonToObject(ctx.getTranslatedContent(), new TypeReference<>() {});
            assertNotNull(result);
            assertEquals(3, result.size());
            assertEquals("Hello_翻译", result.get(0));
            assertEquals("World_翻译", result.get(1));
            assertEquals("Goodbye_翻译", result.get(2));

            assertEquals(100, ctx.getUsedToken());
            assertEquals(1, ctx.getCachedCount());
        }
    }

    // ====== 场景 2：纯 HTML list，全部走 html ======
    @Test
    void testTranslate_PureHtmlList_AllGoHtml() {
        String input = "[\"<p>Hello</p>\",\"<div>World</div>\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        try (MockedStatic<JsoupUtils> mockedJsoup = mockStatic(JsoupUtils.class)) {
            mockedJsoup.when(() -> JsoupUtils.isHtml(anyString())).thenReturn(true);

            doAnswer(invocation -> {
                TranslateContext htmlCtx = invocation.getArgument(0);
                htmlCtx.setTranslatedContent(htmlCtx.getContent().replace("Hello", "你好").replace("World", "世界"));
                htmlCtx.incrementUsedTokenCount(50);
                return null;
            }).when(htmlTranslateStrategyService).translate(any(TranslateContext.class));

            listTranslateStrategyService.translate(ctx);

            verify(htmlTranslateStrategyService, times(2)).translate(any(TranslateContext.class));
            verify(batchTranslateStrategyService, never()).translate(any(TranslateContext.class));

            List<String> result = JsonUtils.jsonToObject(ctx.getTranslatedContent(), new TypeReference<>() {});
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("<p>你好</p>", result.get(0));
            assertEquals("<div>世界</div>", result.get(1));

            assertEquals(100, ctx.getUsedToken());
        }
    }

    // ====== 场景 3：混合 list，文本与 HTML 分别走对应策略 ======
    @Test
    void testTranslate_MixedList_SplitStrategies() {
        String input = "[\"Hello\",\"<p>World</p>\",\"Goodbye\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        try (MockedStatic<JsoupUtils> mockedJsoup = mockStatic(JsoupUtils.class)) {
            mockedJsoup.when(() -> JsoupUtils.isHtml("Hello")).thenReturn(false);
            mockedJsoup.when(() -> JsoupUtils.isHtml("<p>World</p>")).thenReturn(true);
            mockedJsoup.when(() -> JsoupUtils.isHtml("Goodbye")).thenReturn(false);

            doAnswer(invocation -> {
                TranslateContext batchCtx = invocation.getArgument(0);
                Map<Integer, String> original = batchCtx.getOriginalTextMap();
                Map<Integer, String> translated = batchCtx.getTranslatedTextMap();
                for (Map.Entry<Integer, String> entry : original.entrySet()) {
                    translated.put(entry.getKey(), entry.getValue() + "_批量");
                }
                batchCtx.incrementUsedTokenCount(60);
                batchCtx.setGlossaryCount(2);
                return null;
            }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            doAnswer(invocation -> {
                TranslateContext htmlCtx = invocation.getArgument(0);
                htmlCtx.setTranslatedContent("<p>世界</p>");
                htmlCtx.incrementUsedTokenCount(40);
                htmlCtx.setCachedCount(1);
                return null;
            }).when(htmlTranslateStrategyService).translate(any(TranslateContext.class));

            listTranslateStrategyService.translate(ctx);

            verify(batchTranslateStrategyService).translate(any(TranslateContext.class));
            verify(htmlTranslateStrategyService).translate(any(TranslateContext.class));

            List<String> result = JsonUtils.jsonToObject(ctx.getTranslatedContent(), new TypeReference<>() {});
            assertNotNull(result);
            assertEquals(3, result.size());
            assertEquals("Hello_批量", result.get(0));
            assertEquals("<p>世界</p>", result.get(1));
            assertEquals("Goodbye_批量", result.get(2));

            assertEquals(100, ctx.getUsedToken());
            assertEquals(1, ctx.getCachedCount());
            assertEquals(2, ctx.getGlossaryCount());
        }
    }

    // ====== 场景 4：空字符串 / null 元素保持原样 ======
    @Test
    void testTranslate_NullAndEmptyElements_PreservedAsIs() {
        String input = "[\"Hello\",\"\",null,\"World\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        try (MockedStatic<JsoupUtils> mockedJsoup = mockStatic(JsoupUtils.class)) {
            mockedJsoup.when(() -> JsoupUtils.isHtml(anyString())).thenReturn(false);

            doAnswer(invocation -> {
                TranslateContext batchCtx = invocation.getArgument(0);
                Map<Integer, String> original = batchCtx.getOriginalTextMap();
                Map<Integer, String> translated = batchCtx.getTranslatedTextMap();
                for (Map.Entry<Integer, String> entry : original.entrySet()) {
                    translated.put(entry.getKey(), entry.getValue() + "_翻译");
                }
                return null;
            }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            listTranslateStrategyService.translate(ctx);

            List<String> result = JsonUtils.jsonToObject(ctx.getTranslatedContent(), new TypeReference<>() {});
            assertNotNull(result);
            assertEquals(4, result.size());
            assertEquals("Hello_翻译", result.get(0));
            assertEquals("", result.get(1));
            assertNull(result.get(2));
            assertEquals("World_翻译", result.get(3));
        }
    }

    // ====== 场景 5：单个 HTML 元素翻译异常，该元素回退原文，其它正常 ======
    @Test
    void testTranslate_SingleHtmlException_FallbackToOriginal() {
        String input = "[\"<p>Good</p>\",\"<p>Bad</p>\",\"<p>Fine</p>\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        try (MockedStatic<JsoupUtils> mockedJsoup = mockStatic(JsoupUtils.class)) {
            mockedJsoup.when(() -> JsoupUtils.isHtml(anyString())).thenReturn(true);

            doAnswer(invocation -> {
                TranslateContext htmlCtx = invocation.getArgument(0);
                String content = htmlCtx.getContent();
                if (content.contains("Bad")) {
                    throw new RuntimeException("Translation failed for Bad element");
                }
                htmlCtx.setTranslatedContent(content.replace("Good", "好的").replace("Fine", "不错"));
                htmlCtx.incrementUsedTokenCount(30);
                return null;
            }).when(htmlTranslateStrategyService).translate(any(TranslateContext.class));

            listTranslateStrategyService.translate(ctx);

            List<String> result = JsonUtils.jsonToObject(ctx.getTranslatedContent(), new TypeReference<>() {});
            assertNotNull(result);
            assertEquals(3, result.size());
            assertEquals("<p>好的</p>", result.get(0));
            assertEquals("<p>Bad</p>", result.get(1));
            assertEquals("<p>不错</p>", result.get(2));

            assertEquals(60, ctx.getUsedToken());
        }
    }

    // ====== 场景 5b：batch 翻译异常，全部回退原文 ======
    @Test
    void testTranslate_BatchException_FallbackToOriginal() {
        String input = "[\"Hello\",\"World\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        try (MockedStatic<JsoupUtils> mockedJsoup = mockStatic(JsoupUtils.class)) {
            mockedJsoup.when(() -> JsoupUtils.isHtml(anyString())).thenReturn(false);

            doThrow(new RuntimeException("Batch translation failed"))
                    .when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            listTranslateStrategyService.translate(ctx);

            List<String> result = JsonUtils.jsonToObject(ctx.getTranslatedContent(), new TypeReference<>() {});
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("Hello", result.get(0));
            assertEquals("World", result.get(1));
        }
    }

    // ====== 场景 6：输出顺序与输入一致，长度一致 ======
    @Test
    void testTranslate_OutputOrderAndLengthMatchInput() {
        String input = "[\"A\",\"<b>B</b>\",\"C\",\"\",\"<i>D</i>\",\"E\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        try (MockedStatic<JsoupUtils> mockedJsoup = mockStatic(JsoupUtils.class)) {
            mockedJsoup.when(() -> JsoupUtils.isHtml("A")).thenReturn(false);
            mockedJsoup.when(() -> JsoupUtils.isHtml("<b>B</b>")).thenReturn(true);
            mockedJsoup.when(() -> JsoupUtils.isHtml("C")).thenReturn(false);
            mockedJsoup.when(() -> JsoupUtils.isHtml("<i>D</i>")).thenReturn(true);
            mockedJsoup.when(() -> JsoupUtils.isHtml("E")).thenReturn(false);

            doAnswer(invocation -> {
                TranslateContext batchCtx = invocation.getArgument(0);
                Map<Integer, String> original = batchCtx.getOriginalTextMap();
                Map<Integer, String> translated = batchCtx.getTranslatedTextMap();
                for (Map.Entry<Integer, String> entry : original.entrySet()) {
                    translated.put(entry.getKey(), entry.getValue().toLowerCase());
                }
                return null;
            }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            doAnswer(invocation -> {
                TranslateContext htmlCtx = invocation.getArgument(0);
                htmlCtx.setTranslatedContent(htmlCtx.getContent() + "_html_done");
                return null;
            }).when(htmlTranslateStrategyService).translate(any(TranslateContext.class));

            listTranslateStrategyService.translate(ctx);

            List<String> result = JsonUtils.jsonToObject(ctx.getTranslatedContent(), new TypeReference<>() {});
            assertNotNull(result);
            assertEquals(6, result.size());
            assertEquals("a", result.get(0));
            assertEquals("<b>B</b>_html_done", result.get(1));
            assertEquals("c", result.get(2));
            assertEquals("", result.get(3));
            assertEquals("<i>D</i>_html_done", result.get(4));
            assertEquals("e", result.get(5));
        }
    }

    // ====== 场景 7：统计字段累加 ======
    @Test
    void testTranslate_StatsAccumulation() {
        String input = "[\"Text1\",\"<p>Html1</p>\",\"Text2\",\"<p>Html2</p>\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        try (MockedStatic<JsoupUtils> mockedJsoup = mockStatic(JsoupUtils.class)) {
            mockedJsoup.when(() -> JsoupUtils.isHtml("Text1")).thenReturn(false);
            mockedJsoup.when(() -> JsoupUtils.isHtml("<p>Html1</p>")).thenReturn(true);
            mockedJsoup.when(() -> JsoupUtils.isHtml("Text2")).thenReturn(false);
            mockedJsoup.when(() -> JsoupUtils.isHtml("<p>Html2</p>")).thenReturn(true);

            doAnswer(invocation -> {
                TranslateContext batchCtx = invocation.getArgument(0);
                Map<Integer, String> original = batchCtx.getOriginalTextMap();
                for (Map.Entry<Integer, String> entry : original.entrySet()) {
                    batchCtx.getTranslatedTextMap().put(entry.getKey(), entry.getValue() + "_ok");
                }
                batchCtx.incrementUsedTokenCount(200);
                batchCtx.setCachedCount(3);
                batchCtx.setGlossaryCount(5);
                return null;
            }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            doAnswer(invocation -> {
                TranslateContext htmlCtx = invocation.getArgument(0);
                htmlCtx.setTranslatedContent(htmlCtx.getContent() + "_translated");
                htmlCtx.incrementUsedTokenCount(80);
                htmlCtx.setCachedCount(1);
                htmlCtx.setGlossaryCount(2);
                return null;
            }).when(htmlTranslateStrategyService).translate(any(TranslateContext.class));

            listTranslateStrategyService.translate(ctx);

            assertEquals(200 + 80 + 80, ctx.getUsedToken());
            assertEquals(3 + 1 + 1, ctx.getCachedCount());
            assertEquals(5 + 2 + 2, ctx.getGlossaryCount());
        }
    }

    // ====== 空 list 直接返回原文 ======
    @Test
    void testTranslate_EmptyList_ReturnOriginal() {
        String input = "[]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        listTranslateStrategyService.translate(ctx);

        assertEquals(input, ctx.getTranslatedContent());
        verify(batchTranslateStrategyService, never()).translate(any());
        verify(htmlTranslateStrategyService, never()).translate(any());
    }

    // ====== 非法 JSON 返回原文 ======
    @Test
    void testTranslate_InvalidJson_ReturnOriginal() {
        String input = "not a json list";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        listTranslateStrategyService.translate(ctx);

        assertEquals(input, ctx.getTranslatedContent());
    }

    // ====== finishAndGetJsonRecord 写入变量 ======
    @Test
    void testFinishAndGetJsonRecord() {
        String input = "[\"test\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setStrategy("LIST 逐元素翻译");
        ctx.incrementUsedTokenCount(50);
        ctx.setCachedCount(2);
        ctx.setGlossaryCount(1);

        listTranslateStrategyService.finishAndGetJsonRecord(ctx);

        Map<String, String> vars = ctx.getTranslateVariables();
        assertEquals("LIST 逐元素翻译", vars.get("strategy"));
        assertEquals("50", vars.get("usedToken"));
        assertEquals("2", vars.get("cachedCount"));
        assertEquals("1", vars.get("glossaryCount"));
        assertNotNull(vars.get("translatedTime"));
        assertNotNull(vars.get("translatedChars"));
    }

    // ====== 单元素 list ======
    @Test
    void testTranslate_SingleElementList() {
        String input = "[\"OnlyOne\"]";
        TranslateContext ctx = new TranslateContext(input, testTarget, new HashMap<>(), testAiModel);
        ctx.setModule(testModule);
        ctx.setShopName(testShopName);

        try (MockedStatic<JsoupUtils> mockedJsoup = mockStatic(JsoupUtils.class)) {
            mockedJsoup.when(() -> JsoupUtils.isHtml("OnlyOne")).thenReturn(false);

            doAnswer(invocation -> {
                TranslateContext batchCtx = invocation.getArgument(0);
                batchCtx.getTranslatedTextMap().put(0, "唯一");
                batchCtx.incrementUsedTokenCount(10);
                return null;
            }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            listTranslateStrategyService.translate(ctx);

            List<String> result = JsonUtils.jsonToObject(ctx.getTranslatedContent(), new TypeReference<>() {});
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("唯一", result.get(0));
        }
    }
}
