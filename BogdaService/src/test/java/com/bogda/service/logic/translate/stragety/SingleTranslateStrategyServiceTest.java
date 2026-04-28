package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.logic.translate.ModelTranslateService;
import com.bogda.service.logic.translate.PromptConfigService;
import com.bogda.common.utils.StringUtils;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingleTranslateStrategyServiceTest {

    @Mock
    private ModelTranslateService modelTranslateService;

    @Mock
    private RedisProcessService redisProcessService;

    @Mock
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;

    @Mock
    private PromptConfigService promptConfigService;

    @Mock
    private FeiShuRobotIntegration feiShuRobotIntegration;

    @InjectMocks
    private SingleTranslateStrategyService singleTranslateStrategyService;

    private TranslateContext context;
    private String testContent;
    private String testTarget;
    private String testAiModel;
    private String testTargetContent;

    @BeforeEach
    void setUp() {
        testContent = "Hello, world!";
        testTarget = "zh";
        testAiModel = "gemini-3-flash";
        testTargetContent = "你好， 世界";
        context = new TranslateContext(testContent, testTarget, "TEXT", "test-key", new HashMap<>(), testAiModel, "ARTICLE", testTargetContent);
    }

    @Test
    void testGetType_ShouldReturnSingle() {
        // When
        String type = singleTranslateStrategyService.getType();

        // Then
        assertEquals("SINGLE", type);
    }

    @Test
    void testTranslate_WithHandleType_ShouldSetHandleStrategy() {
        // Given
        context.setShopifyTextType(TranslateConstants.URI);
        context.setShopifyTextKey("handle");
        context.setContent("hello-world-product");

        context.setGlossaryMap(new HashMap<>());

        try (MockedStatic<GlossaryService> mockedGlossary = mockStatic(GlossaryService.class);
             MockedStatic<StringUtils> mockedStringUtils = mockStatic(StringUtils.class)) {

            mockedGlossary.when(() -> GlossaryService.hasGlossary(anyString(), anyMap(), anyMap())).thenReturn(false);
            mockedStringUtils.when(() -> StringUtils.replaceHyphensWithSpaces("hello-world-product"))
                    .thenReturn("hello world product");

            when(promptConfigService.buildHandlePrompt(anyString(), eq(testTarget), eq("hello world product"))).thenReturn("handle-prompt");
            Pair<String, Integer> mockPair = new Pair<>("你好世界产品", 50);
            when(modelTranslateService.modelTranslate(eq(testAiModel), anyString(), eq(testTarget), eq(context.getContent()), isNull()))
                    .thenReturn(mockPair);

            // When
            singleTranslateStrategyService.translate(context);

            // Then
            assertEquals("Handle 长文本翻译", context.getStrategy());
            assertNotNull(context.getPrompt());
        }
    }


    @Test
    void testFinishAndGetJsonRecord_ShouldSetVariables() {
        // Given
        context.setStrategy("普通单条文本翻译");
        context.setUsedToken(100);
        context.setCached(true);
        context.setTranslatedChars(50);
        context.setStartTime(System.currentTimeMillis() - 1000);

        Map<String, GlossaryDO> usedGlossary = new HashMap<>();
        usedGlossary.put("Hello", new GlossaryDO("Hello", "你好", 0));
        context.setUsedGlossaryMap(usedGlossary);

        // When
        singleTranslateStrategyService.finishAndGetJsonRecord(context);

        // Then
        assertNotNull(context.getTranslateVariables());
        assertEquals("普通单条文本翻译", context.getTranslateVariables().get("strategy"));
        assertEquals("100", context.getTranslateVariables().get("usedToken"));
        assertEquals("true", context.getTranslateVariables().get("isCached"));
        assertEquals("50", context.getTranslateVariables().get("translatedChars"));
        assertEquals("Hello", context.getTranslateVariables().get("usedGlossary"));
        assertNotNull(context.getTranslatedTime());
    }

    @Test
    void testFinishAndGetJsonRecord_WithoutGlossary_ShouldNotIncludeGlossary() {
        // Given
        context.setStrategy("普通单条文本翻译");
        context.setUsedToken(100);
        context.setCached(false);
        context.setTranslatedChars(50);
        context.setStartTime(System.currentTimeMillis() - 1000);
        context.setUsedGlossaryMap(new HashMap<>());

        // When
        singleTranslateStrategyService.finishAndGetJsonRecord(context);

        // Then
        assertNotNull(context.getTranslateVariables());
        assertFalse(context.getTranslateVariables().containsKey("usedGlossary"));
    }
}

