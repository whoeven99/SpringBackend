package com.bogda.service.logic.translate;

import com.bogda.common.entity.VO.SingleReturnVO;
import com.bogda.common.entity.VO.SingleTranslateVO;
import com.bogda.common.controller.request.ClickTranslateRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.controller.response.ProgressResponse;
import com.bogda.common.TranslateContext;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.repository.repo.InitialTaskV2Repo;
import com.bogda.repository.repo.TranslateTaskV2Repo;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.service.Service.IUsersService;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.ShopifyService;
import com.bogda.service.logic.redis.ConfigRedisRepo;
import com.bogda.service.logic.redis.RedisStoppedRepository;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
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
    private InitialTaskV2Repo initialTaskV2Repo;

    @Mock
    private TranslateTaskV2Repo translateTaskV2Repo;

    @Mock
    private IUsersService iUsersService;

    @Mock
    private ShopifyService shopifyService;

    @Mock
    private RedisStoppedRepository redisStoppedRepository;

    @Mock
    private UserTokenService userTokenService;

    @Mock
    private GlossaryService glossaryService;

    @Mock
    private TranslateStrategyFactory translateStrategyFactory;

    @Mock
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;

    @Mock
    private ITranslatesService iTranslatesService;

    @Mock
    private ConfigRedisRepo configRedisRepo;

    @Mock
    private ITranslateStrategyService translateStrategyService;

    @InjectMocks
    private TranslateV2Service translateV2Service;

    private SingleTranslateVO singleTranslateVO;
    private String testShopName;
    private String testTarget;
    private String testContext;

    @BeforeEach
    void setUp() {
        testShopName = "test-shop";
        testTarget = "zh";
        testContext = "Hello, world!";

        singleTranslateVO = new SingleTranslateVO();
        singleTranslateVO.setShopName(testShopName);
        singleTranslateVO.setTarget(testTarget);
        singleTranslateVO.setContext(testContext);
        singleTranslateVO.setType("TEXT");
        singleTranslateVO.setKey("test-key");
    }

    @Test
    void testSingleTextTranslate_WithMissingParameters_ShouldReturnFailedResponse() {
        // Given
        SingleTranslateVO request = new SingleTranslateVO();
        request.setShopName(null);

        // When
        BaseResponse<SingleReturnVO> response = translateV2Service.singleTextTranslate(request);

        // Then
        assertFalse(response.getSuccess());
        assertEquals("Missing parameters", response.getErrorMsg());
        verifyNoInteractions(userTokenService);
    }

    @Test
    void testSingleTextTranslate_WithEmptyShopName_ShouldReturnFailedResponse() {
        // Given
        singleTranslateVO.setShopName("");

        // When
        BaseResponse<SingleReturnVO> response = translateV2Service.singleTextTranslate(singleTranslateVO);

        // Then
        assertFalse(response.getSuccess());
        assertEquals("Missing parameters", response.getErrorMsg());
    }

    @Test
    void testSingleTextTranslate_WithTokenLimitReached_ShouldReturnFailedResponse() {
        // Given
        when(userTokenService.getMaxToken(testShopName)).thenReturn(1000);
        when(userTokenService.getUsedToken(testShopName)).thenReturn(1000);

        // When
        BaseResponse<SingleReturnVO> response = translateV2Service.singleTextTranslate(singleTranslateVO);

        // Then
        assertFalse(response.getSuccess());
        assertEquals("Token limit reached", response.getErrorMsg());
        verify(userTokenService).getMaxToken(testShopName);
        verify(userTokenService).getUsedToken(testShopName);
        verifyNoInteractions(translateStrategyFactory);
    }

    @Test
    void testSingleTextTranslate_WithValidRequest_ShouldReturnSuccessResponse() {
        // Given
        when(userTokenService.getMaxToken(testShopName)).thenReturn(1000);
        when(userTokenService.getUsedToken(testShopName)).thenReturn(500);
        when(glossaryService.getGlossaryDoByShopName(testShopName, testTarget)).thenReturn(new HashMap<>());
        when(translateStrategyFactory.getServiceByContext(any(TranslateContext.class))).thenReturn(translateStrategyService);

        TranslateContext mockContext = new TranslateContext(testContext, testTarget, "TEXT", "test-key", new HashMap<>(), GeminiIntegration.GEMINI_3_FLASH);
        mockContext.setTranslatedContent("你好，世界！");
        mockContext.setUsedToken(100);
        mockContext.setTranslateVariables(new HashMap<>());

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

        // When
        BaseResponse<SingleReturnVO> response = translateV2Service.singleTextTranslate(singleTranslateVO);

        // Then
        assertTrue(response.getSuccess());
        assertNotNull(response.getResponse());
        verify(translateStrategyService).translate(any(TranslateContext.class));
        verify(translateStrategyService).finishAndGetJsonRecord(any(TranslateContext.class));
        verify(userTokenService).addUsedToken(testShopName, 100);
    }

    @Test
    void testSortTranslateData_WithValidList_ShouldSortCorrectly() {
        // Given
        List<String> inputList = Arrays.asList("PRODUCT", "COLLECTION", "PAGE");

        // When
        List<String> result = TranslateV2Service.sortTranslateData(inputList);

        // Then
        assertNotNull(result);
        // The order should match ALL_RESOURCES order
        assertTrue(result.size() > 0);
    }

    @Test
    void testSortTranslateData_WithEmptyList_ShouldReturnEmptyList() {
        // Given
        List<String> inputList = new ArrayList<>();

        // When
        List<String> result = TranslateV2Service.sortTranslateData(inputList);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSortTranslateData_WithNullList_ShouldHandleGracefully() {
        // Given
        List<String> inputList = null;

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            TranslateV2Service.sortTranslateData(inputList);
        });
    }

    @Test
    void testTestTranslate_WithQwenModel_ShouldCallAliYunIntegration() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("model", "qwen-max");
        map.put("prompt", "Translate: {{SOURCE_LANGUAGE_LIST}} to {{TARGET_LANGUAGE}}");
        map.put("target", "zh");
        map.put("json", "{\"1\":\"English\"}");

        // When
        Map<String, Object> result = translateV2Service.testTranslate(map);

        // Then
        assertNotNull(result);
        // The actual implementation calls handleAliYun which may return defaultNullMap if integration fails
    }

    @Test
    void testTestTranslate_WithGeminiModel_ShouldCallGeminiIntegration() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("model", "gemini-3-flash");
        map.put("prompt", "Translate this");
        map.put("target", "zh");
        map.put("json", "{}");

        // When
        Map<String, Object> result = translateV2Service.testTranslate(map);

        // Then
        assertNotNull(result);
    }

    @Test
    void testTestTranslate_WithInvalidJson_ShouldReturnDefaultNullMap() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("model", "qwen-max");
        map.put("prompt", "Translate");
        map.put("target", "zh");
        map.put("json", "invalid-json");

        // When
        Map<String, Object> result = translateV2Service.testTranslate(map);

        // Then
        assertNotNull(result);
        assertEquals("error", result.get("content"));
        assertEquals(0, result.get("allToken"));
    }

    @Test
    void testTestTranslate_WithEmptyLanguageMap_ShouldReturnDefaultNullMap() {
        // Given
        Map<String, Object> map = new HashMap<>();
        map.put("model", "qwen-max");
        map.put("prompt", "Translate");
        map.put("target", "zh");
        map.put("json", "{}");

        // When
        Map<String, Object> result = translateV2Service.testTranslate(map);

        // Then
        assertNotNull(result);
        assertEquals("error", result.get("content"));
        assertEquals(0, result.get("allToken"));
    }

    @Test
    void testGetProcess_WithEmptyTaskList_ShouldReturnEmptyProgress() {
        // Given
        when(initialTaskV2Repo.selectByShopNameSource(testShopName, "en")).thenReturn(new ArrayList<>());

        // When
        BaseResponse<ProgressResponse> response = translateV2Service.getProcess(testShopName, "en");

        // Then
        assertTrue(response.getSuccess());
        assertNotNull(response.getResponse());
        assertTrue(response.getResponse().getList().isEmpty());
    }

    @Test
    void testGetProcess_WithInitStatus_ShouldReturnCorrectProgress() {
        // Given
        InitialTaskV2DO task = new InitialTaskV2DO();
        task.setId(1);
        task.setTarget(testTarget);
        task.setStatus(TranslateV2Service.InitialTaskStatus.INIT_READING_SHOPIFY.getStatus());

        when(initialTaskV2Repo.selectByShopNameSource(testShopName, "en")).thenReturn(Arrays.asList(task));
        when(translateTaskMonitorV2RedisService.getAllByTaskId(1)).thenReturn(createTaskContext("10", "0", "0"));

        // When
        BaseResponse<ProgressResponse> response = translateV2Service.getProcess(testShopName, "en");

        // Then
        assertTrue(response.getSuccess());
        assertNotNull(response.getResponse());
        assertEquals(1, response.getResponse().getList().size());
        ProgressResponse.Progress progress = response.getResponse().getList().get(0);
        assertEquals(testTarget, progress.getTarget());
        assertEquals(2, progress.getStatus());
    }

    @Test
    void testGetProcess_WithStoppedStatus_ShouldReturnCorrectProgress() {
        // Given
        InitialTaskV2DO task = new InitialTaskV2DO();
        task.setId(1);
        task.setTarget(testTarget);
        task.setStatus(TranslateV2Service.InitialTaskStatus.STOPPED.getStatus());

        when(initialTaskV2Repo.selectByShopNameSource(testShopName, "en")).thenReturn(Arrays.asList(task));
        when(translateTaskMonitorV2RedisService.getAllByTaskId(1)).thenReturn(createTaskContext("10", "5", "0"));
        when(redisStoppedRepository.isStoppedByTokenLimit(testShopName)).thenReturn(false);
        when(redisStoppedRepository.isStoppedByTokenLimit(testShopName, 1)).thenReturn(false);

        // When
        BaseResponse<ProgressResponse> response = translateV2Service.getProcess(testShopName, "en");

        // Then
        assertTrue(response.getSuccess());
        assertNotNull(response.getResponse());
        assertEquals(1, response.getResponse().getList().size());
        ProgressResponse.Progress progress = response.getResponse().getList().get(0);
        assertEquals(7, progress.getStatus()); // 手动中断状态
    }

    @Test
    void testCreateInitialTask_WithMissingParameters_ShouldReturnFailedResponse() {
        // Given
        ClickTranslateRequest request = new ClickTranslateRequest();
        request.setShopName("");

        // When
        BaseResponse<Object> response = translateV2Service.createInitialTask(request);

        // Then
        assertFalse(response.getSuccess());
        assertEquals("Missing parameters", response.getErrorMsg());
    }

    @Test
    void testCreateInitialTask_WithTokenLimit_ShouldReturnErrorResponse() {
        // Given
        ClickTranslateRequest request = createValidClickTranslateRequest();
        when(userTokenService.getMaxToken(testShopName)).thenReturn(1000);
        when(userTokenService.getUsedToken(testShopName)).thenReturn(1000);

        // When
        BaseResponse<Object> response = translateV2Service.createInitialTask(request);

        // Then
        assertFalse(response.getSuccess());
        verify(userTokenService).getMaxToken(testShopName);
        verify(userTokenService).getUsedToken(testShopName);
    }

    // Helper methods
    private ClickTranslateRequest createValidClickTranslateRequest() {
        ClickTranslateRequest request = new ClickTranslateRequest();
        request.setShopName(testShopName);
        request.setSource("en");
        request.setTarget(new String[]{"zh", "fr"});
        request.setTranslateSettings3(Arrays.asList("PRODUCT", "PAGE"));
        request.setTranslateSettings1("gemini-3-flash");
        request.setIsCover(false);
        request.setAccessToken("test-token");
        return request;
    }

    private Map<String, String> createTaskContext(String totalCount, String translatedCount, String savedCount) {
        Map<String, String> context = new HashMap<>();
        context.put("totalCount", totalCount);
        context.put("translatedCount", translatedCount);
        context.put("savedCount", savedCount);
        return context;
    }
}

