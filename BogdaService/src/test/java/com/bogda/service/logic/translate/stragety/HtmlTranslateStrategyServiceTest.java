package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.LiquidHtmlTranslatorUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HtmlTranslateStrategyServiceTest {

    @Mock
    private BatchTranslateStrategyService batchTranslateStrategyService;

    @InjectMocks
    private HtmlTranslateStrategyService htmlTranslateStrategyService;

    private TranslateContext context;
    private String testContent;
    private String testTarget;
    private String testAiModel;

    @BeforeEach
    void setUp() {
        testContent = "<p>Hello World</p>";
        testTarget = "zh";
        testAiModel = "gemini-3-flash";

        context = new TranslateContext(testContent, testTarget, new HashMap<>(), testAiModel);
    }

    @Test
    void testGetType_ShouldReturnHtml() {
        // When
        String type = htmlTranslateStrategyService.getType();

        // Then
        assertEquals("HTML", type);
    }

    @Test
    void testTranslate_WithSimpleHtml_ShouldTranslateTextNodes() {
        // Given
        String htmlContent = "<p>Hello World</p>";
        context = new TranslateContext(htmlContent, testTarget, new HashMap<>(), testAiModel);

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(htmlContent)).thenReturn(htmlContent);
            
            // 先创建 mock Document，完成所有 stubbing（hasHtmlTag 为 false，使用 body.childNodes()）
            Document mockDoc = createMockDocumentForBody("<p>你好世界</p>");
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.parseHtml(eq(htmlContent), eq(testTarget), eq(false))).thenReturn(mockDoc);

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                Map<Integer, String> translatedMap = new HashMap<>();
                translatedMap.put(0, "你好世界");
                ctx.getTranslatedTextMap().putAll(translatedMap);
                return null;
            }).when(batchTranslateStrategyService).translate(context);

            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertEquals("HTML的json翻译", context.getStrategy());
            assertNotNull(context.getTranslatedContent());
            verify(batchTranslateStrategyService).translate(context);
        }
    }

    @Test
    void testTranslate_WithHtmlTag_ShouldUseFullHtml() {
        // Given
        String htmlContent = "<html><body><p>Hello</p></body></html>";
        context = new TranslateContext(htmlContent, testTarget, new HashMap<>(), testAiModel);

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(htmlContent)).thenReturn(htmlContent);
            // hasHtmlTag 为 true，使用 doc.outerHtml()，不需要 body.childNodes()
            Document mockDoc = createMockDocumentForFullHtml(htmlContent);
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.parseHtml(eq(htmlContent), eq(testTarget), eq(true))).thenReturn(mockDoc);
            when(mockDoc.outerHtml()).thenReturn("<html><body><p>你好</p></body></html>");

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                Map<Integer, String> translatedMap = new HashMap<>();
                translatedMap.put(0, "你好");
                ctx.getTranslatedTextMap().putAll(translatedMap);
                return null;
            }).when(batchTranslateStrategyService).translate(context);

            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertTrue(context.isHasHtmlTag());
            assertEquals("HTML的json翻译", context.getStrategy());
            verify(batchTranslateStrategyService).translate(context);
        }
    }

    @Test
    void testTranslate_WithEmptyTextNodes_ShouldHandleGracefully() {
        // Given
        String htmlContent = "<p></p>";
        context = new TranslateContext(htmlContent, testTarget, new HashMap<>(), testAiModel);

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(htmlContent)).thenReturn(htmlContent);
            // hasHtmlTag 为 false，使用 body.childNodes()
            Document mockDoc = createMockDocumentForBody(htmlContent);
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.parseHtml(eq(htmlContent), eq(testTarget), eq(false))).thenReturn(mockDoc);

            doAnswer(invocation -> {
                // No translations needed
                return null;
            }).when(batchTranslateStrategyService).translate(context);

            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertEquals("HTML的json翻译", context.getStrategy());
            assertNull(context.getDoc());
            assertTrue(context.getNodeMap().isEmpty());
        }
    }

    @Test
    void testTranslate_WhenTranslateBodyTagOnly_ShouldReturnSameBodyTag() {
        // Given
        String htmlContent = "<body></body>";
        context = new TranslateContext(htmlContent, testTarget, new HashMap<>(), testAiModel);

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(htmlContent)).thenReturn(htmlContent);

            // hasHtmlTag=false; body 为空且没有任何 textNodes，触发回退到 rawValue
            Document mockDoc = createMockDocumentForEmptyBody();
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.parseHtml(eq(htmlContent), eq(testTarget), eq(false))).thenReturn(mockDoc);

            doNothing().when(batchTranslateStrategyService).translate(any(TranslateContext.class));
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertEquals("<body></body>", context.getTranslatedContent());
            assertEquals("HTML的json翻译", context.getStrategy());
            assertNull(context.getDoc());
            assertTrue(context.getNodeMap().isEmpty());
        }
    }

    @Test
    void testFinishAndGetJsonRecord_ShouldSetVariables() {
        // Given
        context.setStrategy("HTML的json翻译");
        context.setUsedToken(100);
        context.setCachedCount(1);
        context.setGlossaryCount(0);
        context.setTranslatedChars(50);
        context.setStartTime(System.currentTimeMillis() - 1000);

        // When
        htmlTranslateStrategyService.finishAndGetJsonRecord(context);

        // Then
        assertNotNull(context.getTranslateVariables());
        assertEquals("HTML的json翻译", context.getTranslateVariables().get("strategy"));
        assertEquals("100", context.getTranslateVariables().get("usedToken"));
        assertEquals("1", context.getTranslateVariables().get("cachedCount"));
        assertEquals("0", context.getTranslateVariables().get("glossaryCount"));
        assertEquals("50", context.getTranslateVariables().get("translatedChars"));
        assertNotNull(context.getTranslatedTime());
    }

    @Test
    void testTranslate_EmailTemplate_ShouldReplaceHtmlLangAttribute() {
        // Given
        String htmlContent = "<html lang=\"ko\"><body><p>Hello World</p></body></html>";
        String targetLanguage = "zh";
        context = new TranslateContext(
                htmlContent,
                targetLanguage,
                "type",
                "key",
                new HashMap<>(),
                testAiModel,
                TranslateConstants.EMAIL_TEMPLATE
        );

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                // 让所有抽取到的文本都“翻译”为同一个值，确保能验证 replacedCode 的注入效果
                for (Map.Entry<Integer, String> entry : ctx.getOriginalTextMap().entrySet()) {
                    ctx.getTranslatedTextMap().put(entry.getKey(), "你好世界");
                }
                return null;
            }).when(batchTranslateStrategyService).translate(context);

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertNotNull(context.getTranslatedContent());
            assertEquals("EMAIL_TEMPLATE的Liquid HTML翻译", context.getStrategy());
            assertNull(context.getDoc());
            assertTrue(context.getNodeMap().isEmpty());
            assertTrue(context.getTranslatedContent().contains("lang=\"zh\""));
            assertFalse(context.getTranslatedContent().contains("lang=\"ko\""));
            assertTrue(context.getTranslatedContent().contains("你好世界"));
            assertFalse(context.getTranslatedContent().contains("Hello"));
            assertFalse(context.getTranslatedContent().contains("World"));
        }
    }

    @Test
    void testTranslate_EmailTemplate_ShouldReplaceHtmlLangAttribute_SingleQuotes() {
        // Given
        String htmlContent = "<html lang='ko'><body><p>Hello World</p></body></html>";
        String targetLanguage = "zh";
        context = new TranslateContext(
                htmlContent,
                targetLanguage,
                "type",
                "key",
                new HashMap<>(),
                testAiModel,
                TranslateConstants.EMAIL_TEMPLATE
        );

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                for (Map.Entry<Integer, String> entry : ctx.getOriginalTextMap().entrySet()) {
                    ctx.getTranslatedTextMap().put(entry.getKey(), "你好世界");
                }
                return null;
            }).when(batchTranslateStrategyService).translate(context);

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertNotNull(context.getTranslatedContent());
            assertEquals("EMAIL_TEMPLATE的Liquid HTML翻译", context.getStrategy());
            assertNull(context.getDoc());
            assertTrue(context.getNodeMap().isEmpty());
            assertTrue(context.getTranslatedContent().contains("lang='zh'"));
            assertFalse(context.getTranslatedContent().contains("lang='ko'"));
            assertTrue(context.getTranslatedContent().contains("你好世界"));
            assertFalse(context.getTranslatedContent().contains("Hello"));
            assertFalse(context.getTranslatedContent().contains("World"));
        }
    }

    @Test
    void testTranslate_EmailTemplate_ShouldReplaceHtmlLangAttribute_NoQuotes() {
        // Given
        String htmlContent = "<html lang=ko><body><p>Hello World</p></body></html>";
        String targetLanguage = "zh";
        context = new TranslateContext(
                htmlContent,
                targetLanguage,
                "type",
                "key",
                new HashMap<>(),
                testAiModel,
                TranslateConstants.EMAIL_TEMPLATE
        );

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                for (Map.Entry<Integer, String> entry : ctx.getOriginalTextMap().entrySet()) {
                    ctx.getTranslatedTextMap().put(entry.getKey(), "你好世界");
                }
                return null;
            }).when(batchTranslateStrategyService).translate(context);

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertNotNull(context.getTranslatedContent());
            assertEquals("EMAIL_TEMPLATE的Liquid HTML翻译", context.getStrategy());
            assertNull(context.getDoc());
            assertTrue(context.getNodeMap().isEmpty());
            assertTrue(context.getTranslatedContent().contains("lang=zh"));
            assertFalse(context.getTranslatedContent().contains("lang=ko"));
            assertTrue(context.getTranslatedContent().contains("你好世界"));
            assertFalse(context.getTranslatedContent().contains("Hello"));
            assertFalse(context.getTranslatedContent().contains("World"));
        }
    }

    @Test
    void testTranslate_EmailTemplate_ShouldDeduplicateAndSkipProtectedRegions() {
        // Given — "Hello" 同时出现在 class 属性和两个 <p> 的可见文本中
        String htmlContent = "<html lang=\"en\"><body>"
                + "<p class=\"Hello\">Hello</p>"
                + "<p>hello</p>"
                + "<p>test</p>q"
                + "<p>Hello</p>"
                + "</body></html>";
        context = new TranslateContext(
                htmlContent,
                "zh",
                "type",
                "key",
                new HashMap<>(),
                testAiModel,
                TranslateConstants.EMAIL_TEMPLATE
        );

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                // 去重后 originalTextMap 只有一条 {0: "Hello"}
                assertEquals(3, ctx.getOriginalTextMap().size());
                assertEquals("Hello", ctx.getOriginalTextMap().get(0));
                ctx.getTranslatedTextMap().put(0, "你好");
                return null;
            }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertNotNull(context.getTranslatedContent());
            // class 属性中的 "Hello" 不应被替换（受保护区域）
            assertTrue(context.getTranslatedContent().contains("class=\"Hello\""));
            // 两个可见的 Hello 都应被替换为 "你好"
            assertFalse(context.getTranslatedContent().contains(">Hello<"));
            assertTrue(context.getTranslatedContent().contains(">你好<"));
        }
    }

    @Test
    void testTranslate_EmailTemplate_ShouldKeepTextWhenTranslationMissingOrEqual() {
        // Given
        String htmlContent = "<html lang=\"en\"><body><p>Hello World</p></body></html>";
        context = new TranslateContext(
                htmlContent,
                "zh",
                "type",
                "key",
                new HashMap<>(),
                testAiModel,
                TranslateConstants.EMAIL_TEMPLATE
        );

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                // 模拟翻译服务返回与原文一致，触发“保留原文”分支
                ctx.getOriginalTextMap().forEach((k, v) -> ctx.getTranslatedTextMap().put(k, v));
                return null;
            }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertNotNull(context.getTranslatedContent());
            assertTrue(context.getTranslatedContent().contains("Hello World"));
            assertFalse(context.getTranslatedContent().contains("你好"));
        }
    }

    @Test
    void testTranslate_EmailTemplate_ShouldRestoreEscapedNewlinesInFinalOutput() {
        // Given
        String htmlContent = ""
                + "<html lang=\"en\"><body>"
                + "<p>Line one</p>\\n"
                + "<p>Line two</p>"
                + "</body></html>";
        context = new TranslateContext(
                htmlContent,
                "zh",
                "type",
                "key",
                new HashMap<>(),
                testAiModel,
                TranslateConstants.EMAIL_TEMPLATE
        );

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                ctx.getOriginalTextMap().forEach((k, v) -> ctx.getTranslatedTextMap().put(k, v));
                return null;
            }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertNotNull(context.getTranslatedContent());
            assertTrue(context.getTranslatedContent().contains("\n"));
            assertFalse(context.getTranslatedContent().contains("\\n"));
            assertTrue(context.getTranslatedContent().contains("<p>Line one</p>"));
            assertTrue(context.getTranslatedContent().contains("<p>Line two</p>"));
        }
    }

    @Test
    void testTranslate_EmailTemplate_ShouldRestoreEscapedNewlines() {
        // Given
        // 这里刻意在 Liquid 标签之间放入字面量 "\n"（两个字符：\ + n），期望最终输出恢复为真实换行符。
        String htmlContent = "{% assign delivery_method_types = delivery_agreements | map: 'delivery_method_type' | uniq %} \\n {% if delivery_method_types.size > 1 %}{% endif %}";
        String targetLanguage = "zh";
        context = new TranslateContext(
                htmlContent,
                targetLanguage,
                "type",
                "key",
                new HashMap<>(),
                testAiModel,
                TranslateConstants.EMAIL_TEMPLATE
        );

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doAnswer(invocation -> {
                // 为了只测试最终的换行还原逻辑，这里让翻译结果等于原文（避免触发 replacedCode.replace）。
                TranslateContext ctx = invocation.getArgument(0);
                ctx.getOriginalTextMap().forEach((k, v) -> ctx.getTranslatedTextMap().put(k, v));
                return null;
            }).when(batchTranslateStrategyService).translate(context);

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertNotNull(context.getTranslatedContent());
            assertTrue(context.getTranslatedContent().contains("\n"));
            assertFalse(context.getTranslatedContent().contains("\\n"));
            assertTrue(context.getTranslatedContent().contains("{% assign"));
        }
    }

    @Test
    void testTranslate_EmailTemplate_ShouldTranslateEmailTitleCapture() {
        // Given
        String htmlContent = ""
                + "{% capture email_title %}\n"
                + "  Thank you for your purchase!\n"
                + "{% endcapture %}\n"
                + "{% assign delivery_method_types = delivery_agreements | map: 'delivery_method_type' | uniq %}\n"
                + "{% if delivery_method_types.size > 1 %}\n"
                + "  {% assign has_split_cart = true %}\n"
                + "{% else %}\n"
                + "  {% assign has_split_cart = false %}\n"
                + "{% endif %}";
        String targetLanguage = "zh";
        String translateValue = "感谢您的购买！";

        context = new TranslateContext(
                htmlContent,
                targetLanguage,
                "type",
                "key",
                new HashMap<>(),
                testAiModel,
                TranslateConstants.EMAIL_TEMPLATE
        );

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                // 只对我们关心的那条英文做翻译，其它原样保持，避免测试被其它抽取片段干扰
                for (Map.Entry<Integer, String> entry : ctx.getOriginalTextMap().entrySet()) {
                    String originalText = entry.getValue();
                    if ("Thank you for your purchase!".equals(originalText)) {
                        ctx.getTranslatedTextMap().put(entry.getKey(), translateValue);
                    } else {
                        ctx.getTranslatedTextMap().put(entry.getKey(), originalText);
                    }
                }
                return null;
            }).when(batchTranslateStrategyService).translate(context);

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertNotNull(context.getTranslatedContent());
            assertEquals("EMAIL_TEMPLATE的Liquid HTML翻译", context.getStrategy());
            assertTrue(context.getTranslatedContent().contains(translateValue));
            assertFalse(context.getTranslatedContent().contains("Thank you for your purchase!"));
        }
    }

    @Test
    void testTranslate_EmailTemplate_ShouldTranslateAnyCaptureVariable() {
        // Given
        String htmlContent = ""
                + "{% capture some_other_title %}\n"
                + "  Welcome to our store!\n"
                + "{% endcapture %}\n"
                + "{% assign delivery_method_types = delivery_agreements | map: 'delivery_method_type' | uniq %}\n"
                + "{% if delivery_method_types.size > 1 %}\n"
                + "  {% assign has_split_cart = true %}\n"
                + "{% else %}\n"
                + "  {% assign has_split_cart = false %}\n"
                + "{% endif %}";
        String targetLanguage = "zh";
        String translateValue = "欢迎光临我们的商店！";

        context = new TranslateContext(
                htmlContent,
                targetLanguage,
                "type",
                "key",
                new HashMap<>(),
                testAiModel,
                TranslateConstants.EMAIL_TEMPLATE
        );

        try (MockedStatic<LiquidHtmlTranslatorUtils> mockedUtils = mockStatic(LiquidHtmlTranslatorUtils.class)) {
            mockedUtils.when(() -> LiquidHtmlTranslatorUtils.isHtmlEntity(anyString()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            doAnswer(invocation -> {
                TranslateContext ctx = invocation.getArgument(0);
                for (Map.Entry<Integer, String> entry : ctx.getOriginalTextMap().entrySet()) {
                    if ("Welcome to our store!".equals(entry.getValue())) {
                        ctx.getTranslatedTextMap().put(entry.getKey(), translateValue);
                    } else {
                        ctx.getTranslatedTextMap().put(entry.getKey(), entry.getValue());
                    }
                }
                return null;
            }).when(batchTranslateStrategyService).translate(context);

            // When
            htmlTranslateStrategyService.translate(context);

            // Then
            assertNotNull(context.getTranslatedContent());
            assertTrue(context.getTranslatedContent().contains(translateValue));
            assertFalse(context.getTranslatedContent().contains("Welcome to our store!"));
        }
    }

    // Helper method to create a mock Document for HTML without <html> tag (uses body.childNodes())
    private Document createMockDocumentForBody(String html) {
        Document doc = mock(Document.class);
        Element body = mock(Element.class);
        Element pElement = mock(Element.class);
        TextNode textNode = mock(TextNode.class);
        
        // 设置 body（只在 hasHtmlTag 为 false 时使用）
        when(doc.body()).thenReturn(body);
        when(body.childNodes()).thenReturn(new java.util.ArrayList<>());
        
        // 设置 getAllElements() 返回 Elements（jsoup 的特殊集合类型）
        Elements allElements = new Elements();
        allElements.add(pElement);
        when(doc.getAllElements()).thenReturn(allElements);
        
        // 设置 textNodes() 返回包含 textNode 的列表
        java.util.List<TextNode> textNodes = new java.util.ArrayList<>();
        textNodes.add(textNode);
        when(pElement.textNodes()).thenReturn(textNodes);
        
        // 设置 textNode 的 text() 方法
        when(textNode.text()).thenReturn("Hello World");
        when(textNode.getWholeText()).thenReturn("Hello World");
        
        return doc;
    }

    // Helper method to create a mock Document for HTML with <html> tag (uses doc.outerHtml())
    private Document createMockDocumentForFullHtml(String html) {
        Document doc = mock(Document.class);
        Element pElement = mock(Element.class);
        TextNode textNode = mock(TextNode.class);
        
        // 设置 getAllElements() 返回 Elements（jsoup 的特殊集合类型）
        Elements allElements = new Elements();
        allElements.add(pElement);
        when(doc.getAllElements()).thenReturn(allElements);
        
        // 设置 textNodes() 返回包含 textNode 的列表
        java.util.List<TextNode> textNodes = new java.util.ArrayList<>();
        textNodes.add(textNode);
        when(pElement.textNodes()).thenReturn(textNodes);
        
        // 设置 textNode 的 text() 方法
        when(textNode.text()).thenReturn("Hello World");
        when(textNode.getWholeText()).thenReturn("Hello World");
        
        return doc;
    }

    private Document createMockDocumentForEmptyBody() {
        Document doc = mock(Document.class);
        Element body = mock(Element.class);
        when(doc.body()).thenReturn(body);
        when(body.childNodes()).thenReturn(new java.util.ArrayList<>());

        // 没有任何元素/文本节点，确保 originalTextMap 为空
        when(doc.getAllElements()).thenReturn(new Elements());
        return doc;
    }
}

