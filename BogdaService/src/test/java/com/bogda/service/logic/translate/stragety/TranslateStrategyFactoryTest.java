package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.exception.FatalException;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.JsoupUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslateStrategyFactoryTest {

    @Mock
    private BatchTranslateStrategyService batchStrategyService;

    @Mock
    private HtmlTranslateStrategyService htmlStrategyService;

    @Mock
    private JsonTranslateStrategyService jsonStrategyService;

    @Mock
    private SingleTranslateStrategyService singleStrategyService;

    private TranslateStrategyFactory factory;

    @BeforeEach
    void setUp() {
        when(batchStrategyService.getType()).thenReturn("BATCH");
        when(htmlStrategyService.getType()).thenReturn("HTML");
        when(jsonStrategyService.getType()).thenReturn("JSON");
        when(singleStrategyService.getType()).thenReturn("SINGLE");

        List<ITranslateStrategyService> services = Arrays.asList(
                batchStrategyService,
                htmlStrategyService,
                jsonStrategyService,
                singleStrategyService
        );

        factory = new TranslateStrategyFactory(services);
    }

    @Test
    void testGetServiceByStrategy_WithValidStrategy_ShouldReturnCorrectService() {
        // When
        ITranslateStrategyService service = factory.getServiceByStrategy("BATCH");

        // Then
        assertNotNull(service);
        assertEquals(batchStrategyService, service);
    }

    @Test
    void testGetServiceByStrategy_WithInvalidStrategy_ShouldThrowException() {
        // When & Then
        assertThrows(FatalException.class, () -> {
            factory.getServiceByStrategy("INVALID");
        });
    }

    @Test
    void testGetServiceByContext_WithBatchContext_ShouldReturnBatchService() {
        // Given
        Map<Integer, String> originalTextMap = new HashMap<>();
        originalTextMap.put(1, "Hello");
        originalTextMap.put(2, "World");
        TranslateContext ctx = new TranslateContext(originalTextMap, "zh", new HashMap<>(), "gemini-3-flash");

        // When
        ITranslateStrategyService service = factory.getServiceByContext(ctx);

        // Then
        assertNotNull(service);
        assertEquals(batchStrategyService, service);
    }

    @Test
    void testGetServiceByContext_WithJsonType_ShouldReturnJsonService() {
        // Given
        TranslateContext ctx = new TranslateContext("{\"type\":\"text\",\"value\":\"Hello\"}", "zh", new HashMap<>(), "gemini-3-flash");
        ctx.setShopifyTextType(TranslateConstants.JSON);

        // When
        ITranslateStrategyService service = factory.getServiceByContext(ctx);

        // Then
        assertNotNull(service);
        assertEquals(jsonStrategyService, service);
    }

    @Test
    void testGetServiceByContext_WithJsonContent_ShouldReturnJsonService() {
        // Given
        String jsonContent = "{\"type\":\"text\",\"value\":\"Hello\"}";
        TranslateContext ctx = new TranslateContext(jsonContent, "zh", new HashMap<>(), "gemini-3-flash");

        // Mock JsonUtils.isJson to return true
        try (var mockedStatic = mockStatic(JsonUtils.class)) {
            mockedStatic.when(() -> JsonUtils.isJson(jsonContent)).thenReturn(true);

            // When
            ITranslateStrategyService service = factory.getServiceByContext(ctx);

            // Then
            assertNotNull(service);
            assertEquals(jsonStrategyService, service);
        }
    }

    @Test
    void testGetServiceByContext_WithHtmlType_ShouldReturnHtmlService() {
        // Given
        TranslateContext ctx = new TranslateContext("<p>Hello</p>", "zh", new HashMap<>(), "gemini-3-flash");
        ctx.setShopifyTextType(TranslateConstants.HTML);

        // When
        ITranslateStrategyService service = factory.getServiceByContext(ctx);

        // Then
        assertNotNull(service);
        assertEquals(htmlStrategyService, service);
    }

    @Test
    void testGetServiceByContext_WithHtmlContent_ShouldReturnHtmlService() {
        // Given
        String htmlContent = "<div>Hello World</div>";
        TranslateContext ctx = new TranslateContext(htmlContent, "zh", new HashMap<>(), "gemini-3-flash");

        // Mock JsoupUtils.isHtml to return true
        try (var mockedStatic = mockStatic(JsoupUtils.class)) {
            mockedStatic.when(() -> JsoupUtils.isHtml(htmlContent)).thenReturn(true);

            // When
            ITranslateStrategyService service = factory.getServiceByContext(ctx);

            // Then
            assertNotNull(service);
            assertEquals(htmlStrategyService, service);
        }
    }

    @Test
    void testGetServiceByContext_WithSingleText_ShouldReturnSingleService() {
        // Given
        TranslateContext ctx = new TranslateContext("Hello World", "zh", new HashMap<>(), "gemini-3-flash");

        // When
        ITranslateStrategyService service = factory.getServiceByContext(ctx);

        // Then
        assertNotNull(service);
        assertEquals(singleStrategyService, service);
    }

    @Test
    void testGetServiceByContext_WithEmptyOriginalTextMap_ShouldReturnSingleService() {
        // Given
        Map<Integer, String> emptyMap = new HashMap<>();
        TranslateContext ctx = new TranslateContext(emptyMap, "zh", new HashMap<>(), "gemini-3-flash");
        // 设置 content 以避免 NPE，因为 getServiceByContext 会检查 content
        ctx.setContent("Hello World");

        // When
        ITranslateStrategyService service = factory.getServiceByContext(ctx);

        // Then
        assertNotNull(service);
        assertEquals(singleStrategyService, service);
    }

    @Test
    void testGetServiceByContext_WithNullContext_ShouldThrowException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            factory.getServiceByContext(null);
        });
    }
}

