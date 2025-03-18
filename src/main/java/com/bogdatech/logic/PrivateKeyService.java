package com.bogdatech.logic;

import com.bogdatech.Service.*;
import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.*;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.integration.PrivateIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TestingEnvironmentIntegration;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.constants.TranslateConstants.OPENAI;
import static com.bogdatech.entity.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.SHOPIFY_RETURN_ERROR;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.utils.CalculateTokenUtils.googleCalculateToken;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.CaseSensitiveUtils.restoreKeywords;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JsoupUtils.translateAndCount;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.translateNewHtml;
import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

@Component
public class PrivateKeyService {
    private final PrivateIntegration privateIntegration;
    private final UserPrivateService userPrivateService;
    private final IUserPrivateService iUserPrivateService;
    private final ITranslatesService iTranslatesService;
    private final IUsersService usersService;
    private final EmailIntegration emailIntegration;
    private final IEmailService emailService;
    private final IGlossaryService glossaryService;
    private final ShopifyService shopifyService;
    private final ITranslatesService translatesService;
    private final TestingEnvironmentIntegration testingEnvironmentIntegration;
    private final ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    public PrivateKeyService(PrivateIntegration privateIntegration, UserPrivateService userPrivateService, IUserPrivateService iUserPrivateService, ITranslatesService iTranslatesService, IUsersService usersService, EmailIntegration emailIntegration, IEmailService emailService, IGlossaryService glossaryService, ShopifyService shopifyService, ITranslatesService translatesService, TestingEnvironmentIntegration testingEnvironmentIntegration, ShopifyHttpIntegration shopifyApiIntegration) {
        this.privateIntegration = privateIntegration;
        this.userPrivateService = userPrivateService;
        this.iUserPrivateService = iUserPrivateService;
        this.iTranslatesService = iTranslatesService;
        this.usersService = usersService;
        this.emailIntegration = emailIntegration;
        this.emailService = emailService;
        this.glossaryService = glossaryService;
        this.shopifyService = shopifyService;
        this.translatesService = translatesService;
        this.testingEnvironmentIntegration = testingEnvironmentIntegration;
        this.shopifyApiIntegration = shopifyApiIntegration;
    }

    TelemetryClient appInsights = new TelemetryClient();

    //测试google调用
    public void test(String text, String source, String apiKey, String target) {
        String s = privateIntegration.translateByGoogle(text, source, apiKey, target);
        System.out.println("s = " + s);
    }


    /**
     * 私有key翻译前的判断
     *
     * @param clickTranslateRequest 请求对象，包含shopName、target、source，accessToken等信息
     */
    public BaseResponse<Object> judgePrivateKey(ClickTranslateRequest clickTranslateRequest) {
//        将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);

        //判断字符是否超限
        UserPrivateDO userPrivateDO = iUserPrivateService.selectOneByShopName(request.getShopName());
        Integer remainingChars = userPrivateDO.getAmount();
        Integer usedChars = userPrivateDO.getUsedAmount();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }

//        一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = iTranslatesService.readStatusInTranslatesByShopName(request);
        for (Integer integer : integers) {
            if (integer == 2) {
                return new BaseResponse<>().CreateSuccessResponse(HAS_TRANSLATED);
            }
        }

        //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
        iTranslatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource(), request.getAccessToken());
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
        //私有key翻译
        startPrivateTranslation(request, remainingChars, counter, usedChars);
        return new BaseResponse<>().CreateSuccessResponse(clickTranslateRequest);
    }

    /**
     * 私有key翻译前的判断
     *
     * @param request        请求对象，包含shopName、target、source，accessToken等信息
     * @param remainingChars 额度字符数
     * @param counter        字符计数器
     * @param usedChars      已使用字符数
     */
    public void startPrivateTranslation(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars) {
        // 创建并启动翻译任务
        String shopName = request.getShopName();
        String source = request.getSource();
        String target = request.getTarget();
        Future<?> future = executorService.submit(() -> {
            LocalDateTime begin = LocalDateTime.now();
            appInsights.trackTrace("Task submitted at: " + begin + " for shop: " + shopName);
            try {
                translating(request, remainingChars, counter, usedChars);  // 执行翻译任务
            } catch (ClientException e) {
                if (e.getErrorMessage().equals(HAS_TRANSLATED)) {
                    userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
                    appInsights.trackTrace("翻译失败的原因： " + e.getErrorMessage());
                    //更新初始值
                    updateInitialValue(request);
                    return;
                }
                iTranslatesService.updateTranslateStatus(shopName, 3, target, source, request.getAccessToken());
                userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
//                //发送报错邮件
                AtomicBoolean emailSent = userEmailStatus.computeIfAbsent(shopName, k -> new AtomicBoolean(false));
                if (emailSent.compareAndSet(false, true)) {
                    translateFailEmail(shopName, CHARACTER_LIMIT);
                }
                appInsights.trackTrace("startTranslation " + e.getErrorMessage());
                //更新初始值
                //更新初始值
                updateInitialValue(request);
                return;
            } catch (CannotCreateTransactionException e) {
                appInsights.trackTrace("Translation task failed: " + e);
                //更新初始值
                updateInitialValue(request);
                return;
            } catch (Exception e) {
                appInsights.trackTrace("Translation task failed: " + e);
                //更新初始值
                updateInitialValue(request);
                userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
                iTranslatesService.updateTranslateStatus(shopName, 3, target, source, request.getAccessToken());
                return;
            }
            //更新数据库中的已使用字符数
            userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
            // 将翻译状态改为“已翻译”//
            iTranslatesService.updateTranslateStatus(shopName, 1, request.getTarget(), source, request.getAccessToken());
            //翻译成功后发送翻译成功的邮件
            translateSuccessEmail(request, counter, begin, usedChars, remainingChars);
            //更新初始值
            updateInitialValue(request);
        });

        userTasks.put(shopName, future);  // 存储用户的任务
        userEmailStatus.put(shopName, new AtomicBoolean(false)); //重置用户发送的邮件
        userStopFlags.put(shopName, new AtomicBoolean(false));  // 初始化用户的停止标志


    }

    private void translating(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars) {
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);

        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        getGlossaryByShopName(shopifyRequest, glossaryMap);

        //TRANSLATION_RESOURCES
        for (TranslateResourceDTO translateResource : ALL_RESOURCES) {
            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter)) return;
            translateResource.setTarget(request.getTarget());
            String query = new ShopifyRequestBody().getFirstQuery(translateResource);
            cloudServiceRequest.setBody(query);
            String shopifyData;
            try {
                String env = System.getenv("ApplicationEnv");
                if ("prod".equals(env) || "dev".equals(env)) {
                    shopifyData = String.valueOf(shopifyApiIntegration.getInfoByShopify(shopifyRequest, query));
                } else {
                    shopifyData = shopifyService.getShopifyData(cloudServiceRequest);
                }
            } catch (Exception e) {
                // 如果出现异常，则跳过, 翻译其他的内容
                //更新当前字符数
                userPrivateService.updateUsedCharsByShopName(request.getShopName(), counter.getTotalChars());
                continue;
            }
            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, remainingChars, glossaryMap, request.getSource(), null);
            translateJson(translateContext);
            // 定期检查是否停止
            if (checkIsStopped(request.getShopName(), counter)) return;
        }
        iTranslatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), request.getSource(), null);
        System.out.println("翻译结束");
    }

    //更新初始值
    public void updateInitialValue(TranslateRequest request) {
        try {
//            startTokenCount(request);
        } catch (Exception e) {
            appInsights.trackTrace("重新更新token值失败！！！");
        }
    }

    //根据返回的json片段，将符合条件的value翻译,并返回json片段
    public Future<Void> translateJson(TranslateContext translateContext) {
        String resourceType = translateContext.getTranslateResource().getResourceType();
        ShopifyRequest request = translateContext.getShopifyRequest();
        System.out.println("现在翻译到： " + resourceType);
        //将目前的状态，添加到数据库中，前端要用这个数据做进度条功能
        iTranslatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), translateContext.getSource(), resourceType);

        if (translateContext.getShopifyData() == null) {
            // 返回默认值或空结果
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(translateContext.getShopifyData());
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("rootNode错误： " + e.getMessage());
            return null;
        }
        translateSingleLineTextFieldsRecursively(rootNode, translateContext);
        // 递归处理下一页数据
        handlePagination(rootNode, translateContext);
        return null;
    }

    // 递归处理下一页数据
    private void handlePagination(JsonNode translatedRootNode, TranslateContext translateContext) {
        // 获取translatableResources节点
        JsonNode translatableResourcesNode = translatedRootNode.path("translatableResources");
        // 获取pageInfo节点
        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");

        if (translatableResourcesNode.hasNonNull("pageInfo")) {
            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
                JsonNode endCursor = pageInfoNode.path("endCursor");
                translateContext.getTranslateResource().setAfter(endCursor.asText(null));
                translateNextPage(translateContext);
            }
        }
    }

    //递归遍历JSON树：使用 translateSingleLineTe
    //方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
    private void translateSingleLineTextFieldsRecursively(JsonNode node, TranslateContext translateContext) {

        ShopifyRequest shopifyRequest = translateContext.getShopifyRequest();
        String source = translateContext.getSource();
        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils()))
            return;
        //定义HashMap存放判断后的对应数据
        // 初始化 judgeData 用于分类存储数据
        Map<String, List<RegisterTransactionRequest>> judgeData = initializeJudgeData();
        // 获取 translatableResources 节点
        JsonNode translatableResourcesNode = node.path("translatableResources");
        if (!translatableResourcesNode.isObject()) {
            return;
        }

        // 处理 nodes 数组
        JsonNode nodesNode = translatableResourcesNode.path("nodes");
        if (!nodesNode.isArray()) {
            return;
        }

        ArrayNode nodesArray = (ArrayNode) nodesNode;
        for (JsonNode nodeElement : nodesArray) {
            if (nodeElement.isObject()) {
                processNodeElement(nodeElement, shopifyRequest, translateContext, judgeData);
            }
        }
        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils()))
            return;
        //对judgeData数据进行翻译和存入shopify,除了html
        try {
            translateAndSaveData(judgeData, translateContext);
        } catch (ClientException e) {
            appInsights.trackTrace("翻译过程中抛出的异常" + e.getErrorMessage());
            throw e;
        }
        userPrivateService.updateUsedCharsByShopName(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils().getTotalChars());
    }

    private void translateAndSaveData(Map<String, List<RegisterTransactionRequest>> judgeData, TranslateContext translateContext) {
        for (Map.Entry<String, List<RegisterTransactionRequest>> entry : judgeData.entrySet()) {
            if (checkIsStopped(translateContext.getShopifyRequest().getShopName(), translateContext.getCharacterCountUtils()))
                return;
            switch (entry.getKey()) {
                case PLAIN_TEXT:
                    translateDataByAPI(entry.getValue(), translateContext);
                    break;
                case HTML:
                    translateHtml(entry.getValue(), translateContext);
                    break;
                case JSON_TEXT:
                    translateJsonText(entry.getValue(), translateContext);
                    break;
                case GLOSSARY:
                    //区分大小写
                    translateDataByGlossary(entry.getValue(), translateContext);
                    break;
                default:
                    appInsights.trackTrace("未知的翻译文本： " + entry.getValue());
                    break;
            }
        }
    }

    //对词汇表数据进行处理
    public void translateDataByGlossary(List<RegisterTransactionRequest> registerTransactionRequests,
                                        TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter)) return;
        int remainingChars = translateContext.getRemainingChars();
        Map<String, Object> glossaryMap = translateContext.getGlossaryMap();
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        //关键词
        Map<String, String> keyMap1 = new HashMap<>();
        Map<String, String> keyMap0 = new HashMap<>();
        //占位符
        Map<String, String> placeholderMap = new HashMap<>();
        //将glossaryMap中所有caseSensitive为1的数据存到一个Map集合里面
        for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
            GlossaryDO glossaryDO = (GlossaryDO) entry.getValue();
            if (glossaryDO.getCaseSensitive() == 1) {
                keyMap1.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
                continue;
            }
            if (glossaryDO.getCaseSensitive() == 0) {
                keyMap0.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
            }
        }
        //对caseSensitiveMap集合中的数据进行翻译
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter))
                return;
            String value = registerTransactionRequest.getValue();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            translation.put("locale", target);
            translation.put("key", registerTransactionRequest.getKey());
            translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            String targetText;
            TranslateRequest translateRequest = new TranslateRequest(0, null, request.getAccessToken(), source, target, value);
            //判断是否为HTML
            if (registerTransactionRequest.getTarget().equals(HTML) || isHtml(value)) {
                try {
                    targetText = translateGlossaryHtmlText(translateRequest, counter, keyMap1, keyMap0, translateContext.getTranslateResource().getResourceType());
                } catch (Exception e) {
                    saveToShopify(value, translation, resourceId, request);
                    continue;
                }
                saveToShopify(targetText, translation, resourceId, request);
                continue;
            }

            //其他数据类型，对数据做处理再翻译
            counter.addChars(googleCalculateToken(value));
            String updateText = extractKeywords(value, placeholderMap, keyMap1, keyMap0);
            translateRequest.setContent(updateText);
            //TODO: 修改翻译调用
            String translatedText = null;
            try {
                translatedText = translateAndCount(translateRequest, counter, translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                appInsights.trackTrace("翻译问题： " + e.getMessage());
            }
            String finalText = restoreKeywords(translatedText, placeholderMap);
            saveToShopify(finalText, translation, resourceId, request);
            if (checkIsStopped(request.getShopName(), counter))
                return;
        }
    }

    //达到字符限制，更新用户剩余字符数，终止循环
    public void updateCharsWhenExceedLimit(CharacterCountUtils counter, String shopName, int remainingChars, TranslateRequest translateRequest) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);

        if (counter.getTotalChars() >= remainingChars) {
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource(), translateRequest.getAccessToken());
            userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
            throw new ClientException(CHARACTER_LIMIT);
        }
    }

    //对html数据处理
    private void translateHtml(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter)) return;

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter))
                return;
            String value = registerTransactionRequest.getValue();
            String resourceId = registerTransactionRequest.getResourceId();
            String source = registerTransactionRequest.getLocale();
            String key = registerTransactionRequest.getKey();
            String digest = registerTransactionRequest.getTranslatableContentDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            //存放在html的list集合里面
            // 解析HTML文档
            String htmlTranslation = null;
            try {
                TranslateRequest translateRequest = new TranslateRequest(0, null, request.getAccessToken(), source, target, value);
                htmlTranslation = translateNewHtml(value, translateRequest, counter, translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                saveToShopify(value, translation, resourceId, request);
                continue;
            }
            saveToShopify(htmlTranslation, translation, resourceId, request);
            if (checkIsStopped(request.getShopName(), counter))
                return;
        }
    }


    //对不同的数据使用不同的翻译api
    private void translateDataByAPI(List<RegisterTransactionRequest> registerTransactionRequests,
                                    TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        int remainingChars = translateContext.getRemainingChars();
        String target = request.getTarget();
        if (checkIsStopped(request.getShopName(), counter)) return;
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            if (checkIsStopped(request.getShopName(), counter))
                return;
            String value = registerTransactionRequest.getValue();
            String resourceId = registerTransactionRequest.getResourceId();
            String source = registerTransactionRequest.getLocale();
            String key = registerTransactionRequest.getKey();
            String digest = registerTransactionRequest.getTranslatableContentDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);
            //判断是否超限
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), registerTransactionRequest.getLocale(), target, null));

            //获取缓存数据和数据库数据
            if (translateDataByCacheAndDatabase(request, value, translation, resourceId, target, source)) {
                continue;
            }

//            首选谷歌翻译，翻译不了用AI翻译
            try {
                translateByGoogleOrAI(request, counter, registerTransactionRequest, translation, translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                appInsights.trackTrace("翻译失败后的字符数： " + counter.getTotalChars());
                userPrivateService.updateUsedCharsByShopName(request.getShopName(), counter.getTotalChars());
                saveToShopify(value, translation, resourceId, request);
            }
            if (checkIsStopped(request.getShopName(), counter))
                return;
        }
    }

    //将翻译后的数据存shopify本地中
    public void saveToShopify(String translatedValue, Map<String, Object> translation, String resourceId, ShopifyRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("resourceId", resourceId);
        translation.put("value", translatedValue);
        Object[] translations = new Object[]{
                translation // 将HashMap添加到数组中
        };
        variables.put("translations", translations);
        //将翻译后的内容通过ShopifyAPI记录到shopify本地
        saveToShopify(new CloudInsertRequest(request.getShopName(), request.getAccessToken(), request.getApiVersion(), request.getTarget(), variables));
    }

    //封装调用云服务器实现将数据存入shopify本地的方法
    public void saveToShopify(CloudInsertRequest cloudServiceRequest) {
        ObjectMapper objectMapper = new ObjectMapper();
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        Map<String, Object> body = cloudServiceRequest.getBody();

        try {
            String requestBody = objectMapper.writeValueAsString(cloudServiceRequest);
            String env = System.getenv("ApplicationEnv");
            if ("prod".equals(env) || "dev".equals(env)) {
                shopifyApiIntegration.registerTransaction(request, body);
            } else {
                testingEnvironmentIntegration.sendShopifyPost("translate/insertTranslatedText", requestBody);
            }

        } catch (JsonProcessingException | ClientException e) {
            appInsights.trackTrace("Failed to save to Shopify: " + e.getMessage());
        }
    }


    //处理JSON_TEXT类型的数据
    private void translateJsonText(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {

        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();
        //判断是否停止翻译
        if (checkIsStopped(request.getShopName(), counter)) return;
        String target = request.getTarget();
        Map<String, Object> translation = new HashMap<>();
        for (RegisterTransactionRequest registerTransactionRequest : registerTransactionRequests) {
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName(), counter))
                return;
            translation.put("locale", target);
            translation.put("key", registerTransactionRequest.getKey());
            translation.put("translatableContentDigest", registerTransactionRequest.getTranslatableContentDigest());
            //直接存放到shopify本地
            saveToShopify(registerTransactionRequest.getValue(), translation, registerTransactionRequest.getResourceId(), request);
            if (checkIsStopped(request.getShopName(), counter))
                return;
        }
    }

    // 初始化 judgeData，用于存储不同类型的数据
    private Map<String, List<RegisterTransactionRequest>> initializeJudgeData() {
        return new HashMap<>() {{
            put(PLAIN_TEXT, new ArrayList<>());
            put(HTML, new ArrayList<>());
            put(DATABASE, new ArrayList<>());
            put(JSON_TEXT, new ArrayList<>());
            put(GLOSSARY, new ArrayList<>());
            put(OPENAI, new ArrayList<>());
        }};
    }

    // 处理单个节点元素，提取相关信息并分类存储
    private void processNodeElement(JsonNode nodeElement, ShopifyRequest shopifyRequest, TranslateContext translateContext,
                                    Map<String, List<RegisterTransactionRequest>> judgeData) {
        String resourceId = null;
        ArrayNode translatableContent = null;
        Map<String, TranslateTextDO> translatableContentMap = null;

        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils()))
            return;
        // 遍历字段，提取 resourceId 和 translatableContent
        Iterator<Map.Entry<String, JsonNode>> fields = nodeElement.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            // 根据字段名称进行处理
            switch (fieldName) {
                case "resourceId":
                    if (fieldValue == null) {
                        break;
                    }
                    resourceId = fieldValue.asText(null);
                    // 提取翻译内容映射
                    translatableContentMap = extractTranslations(nodeElement, resourceId, shopifyRequest);
                    translatableContentMap = extractTranslatableContent(nodeElement, translatableContentMap);
                    break;
                case "translatableContent":
                    if (fieldValue.isArray()) {
                        translatableContent = (ArrayNode) fieldValue;
                    }
                    break;
            }

            // 如果 resourceId 和 translatableContent 都已提取，则存储并准备翻译
            if (resourceId != null && translatableContent != null) {
                judgeAndStoreData(translatableContent, resourceId, judgeData, translateContext.getTranslateResource().getResourceType(),
                        translatableContentMap, translateContext.getGlossaryMap());
            }
        }
    }

    //将获得的TRANSLATION_RESOURCES数据进行判断 存储到不同集合， 对不同集合的数据进行特殊处理
    private void judgeAndStoreData(ArrayNode contentNode, String resourceId, Map<String, List<RegisterTransactionRequest>> judgeData,
                                   String resourceType, Map<String, TranslateTextDO> translatableContentMap, Map<String, Object> glossaryMap) {

        for (JsonNode contentItem : contentNode) {

            ObjectNode contentItemNode = (ObjectNode) contentItem;
            // 跳过 value 为空的项

            if (contentItemNode == null) {
                continue;
            }

            String value;
            String locale;
            String translatableContentDigest;
            String key;
            String type;
            try {
                JsonNode valueNode = contentItemNode.path("value");
                if (valueNode == null) {
                    continue;
                }
                value = contentItemNode.path("value").asText(null);
                locale = contentItemNode.path("locale").asText(null);
                translatableContentDigest = contentItemNode.path("digest").asText(null);
                key = contentItemNode.path("key").asText(null);
                type = contentItemNode.path("type").asText(null);
                if (value == null) {
                    continue;  // 跳过当前项
                }
                if (value.matches("\\p{Zs}")) {
                    continue;
                }
            } catch (Exception e) {
                appInsights.trackTrace("失败的原因： " + e.getMessage());
                continue;
            }

            //如果translatableContentMap里面有该key则不翻译，没有则翻译
            if (translatableContentMap.containsKey(key)) {
                continue;
            }

            //如果包含相对路径则跳过
            if ("handle".equals(key) || type.equals("FILE_REFERENCE") || type.equals("URL") || type.equals("LINK")
                    || type.equals("LIST_FILE_REFERENCE") || type.equals("LIST_LINK")
                    || type.equals(("LIST_URL")) || resourceType.equals(METAFIELD) || resourceType.equals(SHOP_POLICY)) {
                continue;
            }

            //对于json和json_string的数据直接存原文
            if ("JSON".equals(type)
                    || "JSON_STRING".equals(type)) {
                //存放在json的集合里面
                judgeData.get(JSON_TEXT).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
                continue;
            }

            //对词汇表数据做判断
            //判断词汇表里面是否有数据
            if (!glossaryMap.isEmpty()) {
                boolean success = false;
                for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
                    String glossaryKey = entry.getKey();
                    if (containsValue(value, glossaryKey) || containsValueIgnoreCase(value, glossaryKey)) {
                        judgeData.get(GLOSSARY).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, type));
                        success = true;
                        break;
                    }
                }
                if (success) {
                    continue;
                }
            }

            //对value进行判断 plainText
            if ("HTML".equals(type) || isHtml(value)) {
                //存放在html的list集合里面
                judgeData.get(HTML).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
            } else {
                //存放在plainText的list集合里面
                judgeData.get(PLAIN_TEXT).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, null));
            }


        }

    }


    //递归处理下一页数据
    private void translateNextPage(TranslateContext translateContext) {
        JsonNode nextPageData;
        try {
            nextPageData = fetchNextPage(translateContext.getTranslateResource(), translateContext.getShopifyRequest());
        } catch (Exception e) {
            return;
        }
        // 重新开始翻译流程
        translateSingleLineTextFieldsRecursively(nextPageData, translateContext);
        // 递归处理下一页数据
        handlePagination(nextPageData, translateContext);
    }

    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        cloudServiceRequest.setBody(query);

        String env = System.getenv("ApplicationEnv");
        String infoByShopify;
        if ("prod".equals(env) || "dev".equals(env)) {
            infoByShopify = String.valueOf(shopifyApiIntegration.getInfoByShopify(request, query));
        } else {
            infoByShopify = shopifyService.getShopifyData(cloudServiceRequest);
        }
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
    }

    public void translateFailEmail(String shopName, String errorReason) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());

        //错误原因
        templateData.put("reason", errorReason);

        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(133321L, templateData, TRANSLATION_FAILED_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), TRANSLATION_FAILED_SUBJECT, b ? 1 : 0));
    }

    public void translateSuccessEmail(TranslateRequest request, CharacterCountUtils counter, LocalDateTime begin, int beginChars, Integer remainingChars) {
        String shopName = request.getShopName();
        //通过shopName获取用户信息 需要 {{user}} {{language}} {{credit_count}} {{time}} {{remaining_credits}}
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());
        templateData.put("language", request.getTarget());

        //获取更新前后的时间
        LocalDateTime end = LocalDateTime.now();

        Duration duration = Duration.between(begin, end);
        long costTime = duration.toMinutes();
        templateData.put("time", costTime + " minutes");

        //共消耗的字符数
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        int endChars = counter.getTotalChars();
        int costChars = endChars - beginChars;
        String formattedNumber = formatter.format(costChars);
        templateData.put("credit_count", formattedNumber);

        //还剩下的字符数
        int remaining = remainingChars - endChars;
        if (remaining < 0) {
            templateData.put("remaining_credits", "0");

        } else {
            String formattedNumber2 = formatter.format(remaining);
            templateData.put("remaining_credits", formattedNumber2);
        }
        appInsights.trackTrace("templateData" + templateData);
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(133535L, templateData, SUCCESSFUL_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUCCESSFUL_TRANSLATION_SUBJECT, b ? 1 : 0));

    }

    //判断词汇表中要判断的词
    public void getGlossaryByShopName(ShopifyRequest request, Map<String, Object> glossaryMap) {
        GlossaryDO[] glossaryDOS = glossaryService.getGlossaryByShopName(request.getShopName());
        if (glossaryDOS == null) {
            return; // 如果术语表为空，直接返回
        }

        for (GlossaryDO glossaryDO : glossaryDOS) {
            // 判断语言范围是否符合
            if (glossaryDO.getRangeCode().equals(request.getTarget()) || glossaryDO.getRangeCode().equals("ALL")) {
                // 判断术语是否启用
                if (glossaryDO.getStatus() != 1) {
                    continue;
                }

                // 存储术语数据
                glossaryMap.put(glossaryDO.getSourceText(), new GlossaryDO(glossaryDO.getSourceText(), glossaryDO.getTargetText(), glossaryDO.getCaseSensitive()));
            }
        }
    }

    private boolean checkIsStopped(String shopName, CharacterCountUtils counter) {
        if (userStopFlags.get(shopName).get()) {
            //更新数据库中的已使用字符数
            userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
            // 将翻译状态为2改为“部分翻译”//
            iTranslatesService.updateStatusByShopNameAnd2(shopName);
            return true;
        }
        return false;
    }
}
