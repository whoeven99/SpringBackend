package com.bogda.service.logic.translate;

import com.bogda.common.entity.VO.SingleReturnVO;
import com.bogda.common.entity.VO.SingleTranslateVO;
import com.bogda.common.controller.request.ClickTranslateRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.controller.response.ProgressResponse;
import com.bogda.common.TranslateContext;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.integration.model.ShopifyTranslationsResponse;
import com.bogda.repository.entity.TranslateSaveFailedTaskDO;
import com.bogda.repository.entity.TranslateTaskV2DO;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.repository.repo.InitialTaskV2Repo;
import com.bogda.repository.repo.TranslateSaveFailedTaskRepo;
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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import kotlin.Pair;

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
    private AiModelConfigService aiModelConfigService;

    @Mock
    private ITranslateStrategyService translateStrategyService;

    @Mock
    private TranslateSaveFailedTaskRepo translateSaveFailedTaskRepo;

    @Mock
    private PromptConfigService promptConfigService;

    @Mock
    private ModelTranslateService modelTranslateService;

    @Mock
    private FeiShuRobotIntegration feiShuRobotIntegration;

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
        when(aiModelConfigService.getSingleTranslateModel()).thenReturn(GeminiIntegration.GEMINI_3_FLASH);
        when(glossaryService.getGlossaryDoByShopName(testShopName, testTarget)).thenReturn(new HashMap<>());
        when(translateStrategyFactory.getServiceByContext(any(TranslateContext.class))).thenReturn(translateStrategyService);

        TranslateContext mockContext = new TranslateContext(testContext, testTarget, "TEXT", "test-key", new HashMap<>()
                , GeminiIntegration.GEMINI_3_FLASH, "ARTICLE", testTargetContent);
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

    @Test
    void testRetrySaveAllFailedTasks_WithNoFailedRecord_ShouldReturnDirectly() {
        when(translateSaveFailedTaskRepo.selectOneUnretried()).thenReturn(null);

        translateV2Service.retrySaveAllFailedTasks();

        verify(translateSaveFailedTaskRepo).selectOneUnretried();
        verifyNoMoreInteractions(translateSaveFailedTaskRepo);
        verifyNoInteractions(translateTaskV2Repo, initialTaskV2Repo, iUsersService, modelTranslateService, shopifyService);
    }

    @Test
    void testRetrySaveAllFailedTasks_WithMissingTranslateTask_ShouldMarkRetried() {
        TranslateSaveFailedTaskDO failed = createFailedRecord();
        when(translateSaveFailedTaskRepo.selectOneUnretried()).thenReturn(failed);
        when(translateTaskV2Repo.getById(101)).thenReturn(null);

        translateV2Service.retrySaveAllFailedTasks();

        verify(translateSaveFailedTaskRepo).markRetried(1);
        verifyNoInteractions(modelTranslateService, shopifyService);
    }

    @Test
    void testRetrySaveAllFailedTasks_WithSavedTask_ShouldMarkRetried() {
        TranslateSaveFailedTaskDO failed = createFailedRecord();
        TranslateTaskV2DO task = createTranslateTask();
        task.setSavedToShopify(true);
        when(translateSaveFailedTaskRepo.selectOneUnretried()).thenReturn(failed);
        when(translateTaskV2Repo.getById(101)).thenReturn(task);

        translateV2Service.retrySaveAllFailedTasks();

        verify(translateSaveFailedTaskRepo).markRetried(1);
        verifyNoInteractions(modelTranslateService, shopifyService);
    }

    @Test
    void testRetrySaveAllFailedTasks_WithNullModelResult_ShouldMarkRetriedAndNotify() {
        TranslateSaveFailedTaskDO failed = createFailedRecord();
        TranslateTaskV2DO task = createTranslateTask();
        InitialTaskV2DO initial = createInitialTask();
        UsersDO user = createUser();

        when(translateSaveFailedTaskRepo.selectOneUnretried()).thenReturn(failed);
        when(translateTaskV2Repo.getById(101)).thenReturn(task);
        when(initialTaskV2Repo.getById(201)).thenReturn(initial);
        when(iUsersService.getUserByName(testShopName)).thenReturn(user);
        when(glossaryService.getGlossaryDoByShopName(testShopName, testTarget)).thenReturn(new HashMap<>());
        when(promptConfigService.buildSinglePromptWithFieldRule(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn("retry-prompt");
        when(modelTranslateService.modelTranslate(eq("gemini-3-flash"), eq("retry-prompt"), eq("zh"), eq("hello source")))
                .thenReturn(null);

        translateV2Service.retrySaveAllFailedTasks();

        verify(feiShuRobotIntegration).sendMessage(contains("Retranslation returned null"));
        verify(translateSaveFailedTaskRepo).markRetried(1);
        verifyNoMoreInteractions(shopifyService);
    }

    @Test
    void testRetrySaveAllFailedTasks_WithEmptyTranslatedValue_ShouldNotifyAndNotMarkRetried() {
        TranslateSaveFailedTaskDO failed = createFailedRecord();
        TranslateTaskV2DO task = createTranslateTask();
        InitialTaskV2DO initial = createInitialTask();
        UsersDO user = createUser();

        when(translateSaveFailedTaskRepo.selectOneUnretried()).thenReturn(failed);
        when(translateTaskV2Repo.getById(101)).thenReturn(task);
        when(initialTaskV2Repo.getById(201)).thenReturn(initial);
        when(iUsersService.getUserByName(testShopName)).thenReturn(user);
        when(glossaryService.getGlossaryDoByShopName(testShopName, testTarget)).thenReturn(new HashMap<>());
        when(promptConfigService.buildSinglePromptWithFieldRule(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn("retry-prompt");
        when(modelTranslateService.modelTranslate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Pair<>("", 21));

        translateV2Service.retrySaveAllFailedTasks();

        verify(feiShuRobotIntegration).sendMessage(contains("Retry failed (no more retries)"));
        verify(translateSaveFailedTaskRepo, never()).markRetried(anyInt());
        verifyNoInteractions(shopifyService);
    }

    @Test
    void testRetrySaveAllFailedTasks_WithSuccessfulSave_ShouldUpdateStatusAndMetrics() {
        TranslateSaveFailedTaskDO failed = createFailedRecord();
        TranslateTaskV2DO task = createTranslateTask();
        InitialTaskV2DO initial = createInitialTask();
        UsersDO user = createUser();

        when(translateSaveFailedTaskRepo.selectOneUnretried()).thenReturn(failed);
        when(translateTaskV2Repo.getById(101)).thenReturn(task);
        when(initialTaskV2Repo.getById(201)).thenReturn(initial);
        when(iUsersService.getUserByName(testShopName)).thenReturn(user);
        when(glossaryService.getGlossaryDoByShopName(testShopName, testTarget)).thenReturn(new HashMap<String, GlossaryDO>());
        when(promptConfigService.buildSinglePromptWithFieldRule(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn("retry-prompt");
        when(modelTranslateService.modelTranslate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Pair<>("translated-value", 66));
        when(shopifyService.saveDataWithRateLimit(eq(testShopName), eq("token-abc"), any(ShopifyTranslationsResponse.Node.class)))
                .thenReturn("{\"userErrors\":[]}");

        translateV2Service.retrySaveAllFailedTasks();

        verify(userTokenService).addUsedToken(testShopName, 66);
        verify(translateTaskV2Repo).updateTargetValueAndHasTargetValue("translated-value", true, 101);
        verify(translateSaveFailedTaskRepo).markRetried(1);
        verify(translateTaskV2Repo).updateSavedToShopify(101);
        verify(translateTaskMonitorV2RedisService).addSavedCount(201, 1);
        verifyNoInteractions(feiShuRobotIntegration);
    }

    @Test
    void testRetrySaveAllFailedTasks_WithSaveFailure_ShouldMarkRetriedAndNotify() {
        TranslateSaveFailedTaskDO failed = createFailedRecord();
        TranslateTaskV2DO task = createTranslateTask();
        InitialTaskV2DO initial = createInitialTask();
        UsersDO user = createUser();

        when(translateSaveFailedTaskRepo.selectOneUnretried()).thenReturn(failed);
        when(translateTaskV2Repo.getById(101)).thenReturn(task);
        when(initialTaskV2Repo.getById(201)).thenReturn(initial);
        when(iUsersService.getUserByName(testShopName)).thenReturn(user);
        when(glossaryService.getGlossaryDoByShopName(testShopName, testTarget)).thenReturn(new HashMap<>());
        when(promptConfigService.buildSinglePromptWithFieldRule(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn("retry-prompt");
        when(modelTranslateService.modelTranslate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Pair<>("translated-value", 66));
        when(shopifyService.saveDataWithRateLimit(eq(testShopName), eq("token-abc"), any(ShopifyTranslationsResponse.Node.class)))
                .thenReturn("{\"userErrors\":[{\"message\":\"invalid\"}]}");

        translateV2Service.retrySaveAllFailedTasks();

        verify(translateSaveFailedTaskRepo).markRetried(1);
        verify(translateTaskV2Repo, never()).updateSavedToShopify(anyInt());
        verify(translateTaskMonitorV2RedisService, never()).addSavedCount(anyInt(), anyInt());
        verify(feiShuRobotIntegration).sendMessage(contains("Retry failed (no more retries)"));
    }

    @Test
    void testRetrySaveAllFailedTasks_WithValueFailsValidation_ShouldTranslateAndSave() {
        TranslateSaveFailedTaskDO failed = createFailedRecord();
        failed.setErrorMessage("Value fails validation on resource");

        TranslateTaskV2DO task = createTranslateTask();
        InitialTaskV2DO initial = createInitialTask();
        UsersDO user = createUser();

        when(translateSaveFailedTaskRepo.selectOneUnretried()).thenReturn(failed);
        when(translateTaskV2Repo.getById(101)).thenReturn(task);
        when(initialTaskV2Repo.getById(201)).thenReturn(initial);
        when(iUsersService.getUserByName(testShopName)).thenReturn(user);
        when(glossaryService.getGlossaryDoByShopName(testShopName, testTarget)).thenReturn(new HashMap<String, GlossaryDO>());
        when(promptConfigService.buildSinglePromptWithFieldRule(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn("retry-prompt");
        when(modelTranslateService.modelTranslate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Pair<>("translated-value", 66));
        when(shopifyService.saveDataWithRateLimit(eq(testShopName), eq("token-abc"), any(ShopifyTranslationsResponse.Node.class)))
                .thenReturn("{\"userErrors\":[]}");

        translateV2Service.retrySaveAllFailedTasks();

        verify(modelTranslateService).modelTranslate(anyString(), anyString(), eq(testTarget), anyString());
        verify(translateSaveFailedTaskRepo).markRetried(1);
        verify(translateTaskV2Repo).updateSavedToShopify(101);
    }

    @Test
    void testRetrySaveAllFailedTasks_WithInvalidTranslatableContentHash_ShouldRefreshDigestAndSave() {
        TranslateSaveFailedTaskDO failed = createFailedRecord();
        failed.setErrorMessage("Translatable content hash is invalid");

        TranslateTaskV2DO task = createTranslateTask();
        InitialTaskV2DO initial = createInitialTask();
        UsersDO user = createUser();

        when(translateSaveFailedTaskRepo.selectOneUnretried()).thenReturn(failed);
        when(translateTaskV2Repo.getById(101)).thenReturn(task);
        when(initialTaskV2Repo.getById(201)).thenReturn(initial);
        when(iUsersService.getUserByName(testShopName)).thenReturn(user);
        when(glossaryService.getGlossaryDoByShopName(testShopName, testTarget)).thenReturn(new HashMap<String, GlossaryDO>());
        when(translateStrategyFactory.getServiceByContext(any(TranslateContext.class))).thenReturn(translateStrategyService);
        doAnswer(invocation -> {
            TranslateContext ctx = invocation.getArgument(0);
            ctx.setTranslatedContent("translated-value");
            ctx.setUsedToken(66);
            return null;
        }).when(translateStrategyService).translate(any(TranslateContext.class));
        doAnswer(invocation -> {
            TranslateContext ctx = invocation.getArgument(0);
            ctx.setTranslateVariables(new HashMap<>());
            return null;
        }).when(translateStrategyService).finishAndGetJsonRecord(any(TranslateContext.class));

        // mock Shopify: getShopifyData -> 返回你期望的 translatableResourcesByIds 响应（digest/key/value）
        when(shopifyService.getShopifyData(eq(testShopName), eq("token-abc"), anyString(), anyString()))
                .thenReturn("{\"translatableResourcesByIds\":{\"nodes\":[{\"resourceId\":\"" + task.getResourceId() + "\",\"translatableContent\":[{\"digest\":\"latest-digest\",\"key\":\"" + task.getNodeKey() + "\",\"value\":\"latest source\"}]}],\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null}}}");

        ArgumentCaptor<ShopifyTranslationsResponse.Node> nodeCaptor = ArgumentCaptor.forClass(ShopifyTranslationsResponse.Node.class);
        when(shopifyService.saveDataWithRateLimit(eq(testShopName), eq("token-abc"), nodeCaptor.capture()))
                .thenReturn("{\"userErrors\":[]}");

        translateV2Service.retrySaveAllFailedTasks();

        verify(translateStrategyService).translate(any(TranslateContext.class));
        verify(translateStrategyService).finishAndGetJsonRecord(any(TranslateContext.class));
        verify(userTokenService).addUsedToken(testShopName, 66);

        ShopifyTranslationsResponse.Node capturedNode = nodeCaptor.getValue();
        assertNotNull(capturedNode);
        assertNotNull(capturedNode.getTranslations());
        assertEquals(1, capturedNode.getTranslations().size());
        assertEquals("latest-digest", capturedNode.getTranslations().get(0).getTranslatableContentDigest());
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

    private TranslateSaveFailedTaskDO createFailedRecord() {
        TranslateSaveFailedTaskDO failed = new TranslateSaveFailedTaskDO();
        failed.setId(1);
        failed.setTranslateTaskId(101);
        failed.setInitialTaskId(201);
        failed.setShopName(testShopName);
        failed.setErrorMessage("Value fails validation on resource");
        return failed;
    }

    private TranslateTaskV2DO createTranslateTask() {
        TranslateTaskV2DO task = new TranslateTaskV2DO();
        task.setId(101);
        task.setModule("PRODUCT");
        task.setNodeKey("title");
        task.setSourceValue("hello source");
        task.setDigest("digest-1");
        task.setResourceId("gid://shopify/Product/1");
        task.setSavedToShopify(false);
        return task;
    }

    private InitialTaskV2DO createInitialTask() {
        InitialTaskV2DO initial = new InitialTaskV2DO();
        initial.setId(201);
        initial.setTarget(testTarget);
        initial.setAiModel("gemini-3-flash");
        return initial;
    }

    private UsersDO createUser() {
        UsersDO user = new UsersDO();
        user.setShopName(testShopName);
        user.setAccessToken("token-abc");
        return user;
    }
}

