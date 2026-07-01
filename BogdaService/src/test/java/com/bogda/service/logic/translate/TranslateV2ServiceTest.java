package com.bogda.service.logic.translate;

import com.bogda.common.entity.VO.SingleReturnVO;
import com.bogda.common.entity.VO.SingleTranslateVO;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.TranslateContext;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.token.UserTokenService;
import com.bogda.service.logic.translate.stragety.ITranslateStrategyService;
import com.bogda.service.logic.translate.stragety.TranslateStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslateV2ServiceTest {

    @Mock
    private UserTokenService userTokenService;

    @Mock
    private GlossaryService glossaryService;

    @Mock
    private TranslateStrategyFactory translateStrategyFactory;

    @Mock
    private AiModelConfigService aiModelConfigService;

    @Mock
    private ITranslateStrategyService translateStrategyService;

    @InjectMocks
    private TranslateV2Service translateV2Service;

    private SingleTranslateVO singleTranslateVO;
    private String testShopName;
    private String testTarget;
    private String testContext;
    private String testTargetContent;

    @BeforeEach
    void setUp() {
        testShopName = "test-shop";
        testTarget = "zh";
        testContext = "Hello, world!";
        testTargetContent = "你好， 世界";
        singleTranslateVO = new SingleTranslateVO();
        singleTranslateVO.setShopName(testShopName);
        singleTranslateVO.setTarget(testTarget);
        singleTranslateVO.setContext(testContext);
        singleTranslateVO.setType("TEXT");
        singleTranslateVO.setKey("test-key");
    }

    @Test
    void testSingleTextTranslate_WithMissingParameters_ShouldReturnFailedResponse() {
        SingleTranslateVO request = new SingleTranslateVO();
        request.setShopName(null);

        BaseResponse<SingleReturnVO> response = translateV2Service.singleTextTranslate(request);

        assertFalse(response.getSuccess());
        assertEquals("Missing parameters", response.getErrorMsg());
        verifyNoInteractions(userTokenService);
    }

    @Test
    void testSingleTextTranslate_WithEmptyShopName_ShouldReturnFailedResponse() {
        singleTranslateVO.setShopName("");

        BaseResponse<SingleReturnVO> response = translateV2Service.singleTextTranslate(singleTranslateVO);

        assertFalse(response.getSuccess());
        assertEquals("Missing parameters", response.getErrorMsg());
    }

    @Test
    void testSingleTextTranslate_WithTokenLimitReached_ShouldReturnFailedResponse() {
        when(userTokenService.getMaxToken(testShopName)).thenReturn(1000);
        when(userTokenService.getUsedToken(testShopName)).thenReturn(1000);

        BaseResponse<SingleReturnVO> response = translateV2Service.singleTextTranslate(singleTranslateVO);

        assertFalse(response.getSuccess());
        assertEquals("Token limit reached", response.getErrorMsg());
        verify(userTokenService).getMaxToken(testShopName);
        verify(userTokenService).getUsedToken(testShopName);
        verifyNoInteractions(translateStrategyFactory);
    }

    @Test
    void testSingleTextTranslate_WithValidRequest_ShouldReturnSuccessResponse() {
        when(userTokenService.getMaxToken(testShopName)).thenReturn(1000);
        when(userTokenService.getUsedToken(testShopName)).thenReturn(500);
        when(aiModelConfigService.getSingleTranslateModel()).thenReturn(GeminiIntegration.GEMINI_3_FLASH);
        when(glossaryService.getGlossaryDoByShopName(testShopName, testTarget)).thenReturn(new HashMap<>());
        when(translateStrategyFactory.getServiceByContext(any(TranslateContext.class))).thenReturn(translateStrategyService);

        doAnswer(invocation -> {
            TranslateContext ctx = invocation.getArgument(0);
            ctx.setTranslatedContent("你好，世界！");
            ctx.setUsedToken(100);
            return null;
        }).when(translateStrategyService).translate(any(TranslateContext.class));

        doAnswer(invocation -> {
            TranslateContext ctx = invocation.getArgument(0);
            ctx.setTranslateVariables(new HashMap<>());
            return null;
        }).when(translateStrategyService).finishAndGetJsonRecord(any(TranslateContext.class));

        BaseResponse<SingleReturnVO> response = translateV2Service.singleTextTranslate(singleTranslateVO);

        assertTrue(response.getSuccess());
        assertNotNull(response.getResponse());
        verify(translateStrategyService).translate(any(TranslateContext.class));
        verify(translateStrategyService).finishAndGetJsonRecord(any(TranslateContext.class));
        verify(userTokenService).addUsedToken(testShopName, 100);
    }

    @Test
    void testSortTranslateData_WithValidList_ShouldSortCorrectly() {
        List<String> inputList = Arrays.asList("PRODUCT", "COLLECTION", "PAGE");

        List<String> result = TranslateV2Service.sortTranslateData(inputList);

        assertNotNull(result);
        assertTrue(result.size() > 0);
    }

    @Test
    void testSortTranslateData_WithEmptyList_ShouldReturnEmptyList() {
        List<String> inputList = new ArrayList<>();

        List<String> result = TranslateV2Service.sortTranslateData(inputList);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSortTranslateData_WithNullList_ShouldHandleGracefully() {
        assertThrows(NullPointerException.class, () -> TranslateV2Service.sortTranslateData(null));
    }

    @Test
    void testTestTranslate_WithQwenModel_ShouldCallAliYunIntegration() {
        Map<String, Object> map = new HashMap<>();
        map.put("model", "qwen3.6-plus");
        map.put("prompt", "Translate: {{SOURCE_LANGUAGE_LIST}} to {{TARGET_LANGUAGE}}");
        map.put("target", "zh");
        map.put("json", "{\"1\":\"English\"}");

        Map<String, Object> result = translateV2Service.testTranslate(map);

        assertNotNull(result);
    }

    @Test
    void testTestTranslate_WithGeminiModel_ShouldCallGeminiIntegration() {
        Map<String, Object> map = new HashMap<>();
        map.put("model", "gemini-3-flash");
        map.put("prompt", "Translate this");
        map.put("target", "zh");
        map.put("json", "{}");

        Map<String, Object> result = translateV2Service.testTranslate(map);

        assertNotNull(result);
    }

    @Test
    void testTestTranslate_WithInvalidJson_ShouldReturnDefaultNullMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("model", "qwen3.6-plus");
        map.put("prompt", "Translate");
        map.put("target", "zh");
        map.put("json", "invalid-json");

        Map<String, Object> result = translateV2Service.testTranslate(map);

        assertNotNull(result);
        assertEquals("error", result.get("content"));
        assertEquals(0, result.get("allToken"));
    }

    @Test
    void testTestTranslate_WithEmptyLanguageMap_ShouldReturnDefaultNullMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("model", "qwen3.6-plus");
        map.put("prompt", "Translate");
        map.put("target", "zh");
        map.put("json", "{}");

        Map<String, Object> result = translateV2Service.testTranslate(map);

        assertNotNull(result);
        assertEquals("error", result.get("content"));
        assertEquals(0, result.get("allToken"));
    }
}
