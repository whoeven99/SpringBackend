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
}

