package com.bogdatech.logic;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.*;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.*;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.controller.response.TypeSplitResponse;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.TypeConversionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.applicationinsights.TelemetryClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.alibaba.dashscope.utils.Constants.apiKey;
import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.constants.UserPrivateConstants.GOOGLE;
import static com.bogdatech.entity.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.entity.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.enums.ErrorEnum.SHOPIFY_RETURN_ERROR;
import static com.bogdatech.integration.PrivateIntegration.getGoogleTranslationWithRetry;
import static com.bogdatech.integration.PrivateIntegration.translatePrivateNewHtml;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.utils.CalculateTokenUtils.googleCalculateToken;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JsoupUtils.translateSingleLine;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.PrintUtils.printTranslation;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;
import static com.bogdatech.utils.ResourceTypeUtils.splitByType;
import static com.bogdatech.utils.StringUtils.isNumber;
import static com.bogdatech.utils.StringUtils.replaceDot;
import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

@Component
public class PrivateKeyService {
    private final PrivateIntegration privateIntegration;
    private final TranslateApiIntegration translateApiIntegration;
    private final UserPrivateService userPrivateService;
    private final IUserPrivateService iUserPrivateService;
    private final ITranslatesService iTranslatesService;
    private final IUsersService usersService;
    private final EmailIntegration emailIntegration;
    private final IEmailService emailService;
    private final JsoupUtils jsoupUtils;
    private final IGlossaryService glossaryService;
    private final ShopifyService shopifyService;
    private final ITranslatesService translatesService;
    private final TestingEnvironmentIntegration testingEnvironmentIntegration;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final IVocabularyService vocabularyService;
    private final SecretClient secretClient;
    private final IUserTypeTokenService userTypeTokenService;

    @Autowired
    public PrivateKeyService(PrivateIntegration privateIntegration, TranslateApiIntegration translateApiIntegration, UserPrivateService userPrivateService, IUserPrivateService iUserPrivateService, ITranslatesService iTranslatesService, IUsersService usersService, EmailIntegration emailIntegration, IEmailService emailService, JsoupUtils jsoupUtils, IGlossaryService glossaryService, ShopifyService shopifyService, ITranslatesService translatesService, TestingEnvironmentIntegration testingEnvironmentIntegration, ShopifyHttpIntegration shopifyApiIntegration, IVocabularyService vocabularyService, SecretClient secretClient, IUserTypeTokenService userTypeTokenService) {
        this.privateIntegration = privateIntegration;
        this.translateApiIntegration = translateApiIntegration;
        this.userPrivateService = userPrivateService;
        this.iUserPrivateService = iUserPrivateService;
        this.iTranslatesService = iTranslatesService;
        this.usersService = usersService;
        this.emailIntegration = emailIntegration;
        this.emailService = emailService;
        this.jsoupUtils = jsoupUtils;
        this.glossaryService = glossaryService;
        this.shopifyService = shopifyService;
        this.translatesService = translatesService;
        this.testingEnvironmentIntegration = testingEnvironmentIntegration;
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.vocabularyService = vocabularyService;
        this.secretClient = secretClient;
        this.userTypeTokenService = userTypeTokenService;
    }

    TelemetryClient appInsights = new TelemetryClient();

    private static String PRIVATE_KEY = "private_key";
    /**
     * 私有key翻译前的判断
     *
     * @param clickTranslateRequest 请求对象，包含shopName、target、source，accessToken等信息
     */
    public BaseResponse<Object> judgePrivateKey(ClickTranslateRequest clickTranslateRequest) {
//        将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);

        //根据模型判断是否获取到用户的翻译APIkey
        String apiKey = null;
        if (clickTranslateRequest.getTranslateSettings1().equals(GOOGLE)) {
            //获取用户的google key
            String googleKey = replaceDot(clickTranslateRequest.getShopName());
            KeyVaultSecret secret = secretClient.getSecret(googleKey + "-" + GOOGLE);
            apiKey = secret.getValue();
        }
        if (apiKey == null) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }
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
        startPrivateTranslation(request, remainingChars, counter, usedChars, apiKey);
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
    public void startPrivateTranslation(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars, String apiKey) {
        // 创建并启动翻译任务
        String shopName = request.getShopName();
        String source = request.getSource();
        String target = request.getTarget();
        Future<?> future = executorService.submit(() -> {
            LocalDateTime begin = LocalDateTime.now();
            appInsights.trackTrace("Task submitted at: " + begin + " for shop: " + shopName);
            try {
                translating(request, remainingChars, counter, apiKey);  // 执行翻译任务
            } catch (ClientException e) {
                if (e.getErrorMessage().equals(HAS_TRANSLATED)) {
                    userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
                    appInsights.trackTrace("翻译失败的原因： " + e.getErrorMessage());
                    //更新初始值
                    updateInitialValue(request);
                    return;
                }
                iTranslatesService.updateTranslateStatus(shopName, 5, target, source, request.getAccessToken());
                userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
                //发送报错邮件
                AtomicBoolean emailSent = userEmailStatus.computeIfAbsent(shopName, k -> new AtomicBoolean(false));
                if (emailSent.compareAndSet(false, true)) {
                    translateFailEmail(shopName,counter,begin, usedChars, remainingChars, target, source);
                }
                appInsights.trackTrace("startTranslation " + e.getErrorMessage());
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
                iTranslatesService.updateTranslateStatus(shopName, 5, target, source, request.getAccessToken());
                return;
            }
            //更新数据库中的已使用字符数
            userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
            // 将翻译状态改为“已翻译”//
            iTranslatesService.updateTranslateStatus(shopName, 1, request.getTarget(), source, request.getAccessToken());
            try {
                //翻译成功后发送翻译成功的邮件
                if (!userStopFlags.get(shopName).get()) {
                    translateSuccessEmail(request, counter, begin, usedChars, remainingChars);
                }
                //更新初始值
                updateInitialValue(request);
            } catch (Exception e) {
                appInsights.trackTrace("重新更新token值失败！！！");
            }
        });

        userTasks.put(shopName, future);  // 存储用户的任务
        userEmailStatus.put(shopName, new AtomicBoolean(false)); //重置用户发送的邮件
        userStopFlags.put(shopName, new AtomicBoolean(false));  // 初始化用户的停止标志
    }

    private void translating(TranslateRequest request, int remainingChars, CharacterCountUtils counter, String apiKey) {
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
            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, remainingChars, glossaryMap, request.getSource(), null, apiKey);
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
            startTokenCount(request);
        } catch (Exception e) {
            appInsights.trackTrace("重新更新token值失败！！！");
        }
    }

    /**
     * 根据request获取对应模块的token。如果status为2就不计数，如果为其他就开始计数
     *
     * @param request 请求对象，包含shopName、target、source，accessToken等信息
     */
    public void startTokenCount(TranslateRequest request) {
        try {
            //获取translationId
            Integer translationId = translatesService.getIdByShopNameAndTargetAndSource(request.getShopName(), request.getTarget(), request.getSource());

            //判断数据库中UserTypeToken中translationId对应的status是什么 如果是2，则不获取token；如果是除2以外的其他值，获取token
            Integer status = userTypeTokenService.getStatusByTranslationId(translationId);
            if (status != 2) {
                //将UserTypeToken的status修改为2
                userTypeTokenService.updateStatusByTranslationIdAndStatus(translationId, 2);
                ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
                //循环type获取token
                for (String key : TOKEN_MAP.keySet()
                ) {
                    int tokens = 0;

                    for (TranslateResourceDTO translateResourceDTO : TOKEN_MAP.get(key)) {
                        int token = shopifyService.getTotalWords(shopifyRequest, "tokens", translateResourceDTO);
                        tokens += token;
                    }

                    //将tokens存储到UserTypeToken对应的列里面
                    userTypeTokenService.updateTokenByTranslationId(translationId, tokens, key);
                    if ("collection".equals(key) || "notifications".equals(key) || "theme".equals(key)
                            || "article".equals(key) || "blog_titles".equals(key) || "filters".equals(key) || "metaobjects".equals(key)
                            || "pages".equals(key) || "products".equals(key) || "navigation".equals(key)
                            || "shop".equals(key) || "shipping".equals(key) || "delivery".equals(key) || "metadata".equals(key)) {
                        UpdateWrapper<UserTypeTokenDO> updateWrapper = new UpdateWrapper<>();
                        updateWrapper.eq("translation_id", translationId);

                        // 根据传入的列名动态设置更新的字段
                        updateWrapper.set(key, tokens);
                        userTypeTokenService.update(null, updateWrapper);
                    } else {
                        appInsights.trackTrace("Invalid column name");
                    }
                }
                //token全部获取完之后修改，UserTypeToken的status==1
                userTypeTokenService.updateStatusByTranslationIdAndStatus(translationId, 1);
            }
        } catch (IllegalArgumentException e) {
            appInsights.trackTrace("错误原因： " + e.getMessage());
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
                case METAFIELD:
                    translateMetafield(entry.getValue(), translateContext);
                    break;
                default:
                    appInsights.trackTrace("未知的翻译文本： " + entry.getValue());
                    break;
            }
        }
    }

    //翻译元字段的数据
    private void translateMetafield(List<RegisterTransactionRequest> registerTransactionRequests, TranslateContext translateContext) {
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
            String translatableContentDigest = registerTransactionRequest.getTranslatableContentDigest();
            String key = registerTransactionRequest.getKey();
            String source = registerTransactionRequest.getLocale();
            String resourceId = registerTransactionRequest.getResourceId();
            String type = registerTransactionRequest.getTarget();
            Map<String, Object> translation = createTranslationMap(target, key, translatableContentDigest);

            // 判断是否会超限制
            updateCharsWhenExceedLimit(counter, request.getShopName(), remainingChars, new TranslateRequest(0, null, request.getAccessToken(), source, target, null));

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            if (SINGLE_LINE_TEXT_FIELD.equals(type)) {
                //纯数字字母符号 且有两个  标点符号 不翻译
                if (isValidString(value)) {
                    continue;
                }

                //走翻译流程
                String translatedText = getGoogleTranslationWithRetry(value, source, apiKey, request.getTarget());
                addData(request.getTarget(), value, translatedText);
                saveToShopify(translatedText, translation, resourceId, request);
                continue;
            }

            if (LIST_SINGLE_LINE_TEXT_FIELD.equals(type)) {
                //先将list数据由String转为List<String>，循环判断
                try {
                    //如果符合要求，则翻译，不符合要求则返回原值
                    List<String> resultList = objectMapper.readValue(value, new TypeReference<List<String>>() {
                    });
                    for (int i = 0; i < resultList.size(); i++) {
                        String original = resultList.get(i);
                        if (!isValidString(original) && original != null && !original.trim().isEmpty() && !isHtml(value)) {
                            //走翻译流程
                            String translated = getGoogleTranslationWithRetry(original, source, apiKey, request.getTarget());
                            //将数据填回去
                            resultList.set(i, translated);
                        }
                    }
                    //将list数据转为String 再存储到shopify本地
                    String translatedValue = objectMapper.writeValueAsString(resultList);
                    saveToShopify(translatedValue, translation, resourceId, request);
                } catch (Exception e) {
                    //存原数据到shopify本地
                    saveToShopify(value, translation, resourceId, request);
                    appInsights.trackTrace("LIST错误原因： " + e.getMessage());
//                    System.out.println("LIST错误原因： " + e.getMessage());
                }
            }
            if (checkIsStopped(request.getShopName(), counter))
                return;
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
                    targetText = translateGlossaryHtmlText(translateRequest, counter, keyMap1, keyMap0, translateContext.getApiKey());
                } catch (Exception e) {
                    saveToShopify(value, translation, resourceId, request);
                    continue;
                }
                saveToShopify(targetText, translation, resourceId, request);
                printTranslation(targetText, value, translation, request.getShopName() + PRIVATE_KEY , translateContext.getTranslateResource().getResourceType(), resourceId);
                continue;
            }

            //其他数据类型，对数据做处理再翻译
            counter.addChars(googleCalculateToken(value));
            String updateText = extractKeywords(value, placeholderMap, keyMap1, keyMap0, source);
            translateRequest.setContent(updateText);
            //TODO: 修改翻译调用
            String translatedText = null;
            try {
                counter.addChars(value.length());
                //对文本进行翻译
                translatedText = getGoogleTranslationWithRetry(value, source, translateContext.getApiKey(), request.getTarget());
//                translatedText = translateApiIntegration.microsoftTranslate(new TranslateRequest(0, request.getShopName(), request.getAccessToken(), source, request.getTarget(), value));
            } catch (Exception e) {
                appInsights.trackTrace("翻译问题： " + e.getMessage());
            }
            String finalText = restoreKeywords(translatedText, placeholderMap);
            saveToShopify(finalText, translation, resourceId, request);
            printTranslation(translatedText, value, translation, request.getShopName() + PRIVATE_KEY, translateContext.getTranslateResource().getResourceType(), resourceId);
            addData(request.getTarget(), value, translatedText);

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
            String htmlTranslation;
            try {
                TranslateRequest translateRequest = new TranslateRequest(0, null, request.getAccessToken(), source, target, value);
                htmlTranslation = translatePrivateNewHtml(value, translateRequest, counter, translateContext.getTranslateResource().getResourceType(), translateContext.getApiKey());
            } catch (Exception e) {
                saveToShopify(value, translation, resourceId, request);
                continue;
            }
            saveToShopify(htmlTranslation, translation, resourceId, request);
            printTranslation(htmlTranslation, value, translation, request.getShopName() + PRIVATE_KEY, translateContext.getTranslateResource().getResourceType(), resourceId);

            if (checkIsStopped(request.getShopName(), counter))
                return;
        }
    }

    //翻译词汇表的html文本
    public String translateGlossaryHtmlText(TranslateRequest request, CharacterCountUtils counter, Map<String, String> keyMap, Map<String, String> keyMap0, String apiKey) {
        String html = request.getContent();
        // 解析HTML文档
        Document doc = Jsoup.parse(html);

        // 提取需要翻译的文本
        Map<Element, List<String>> elementTextMap = jsoupUtils.extractTextsToTranslate(doc);
        // 翻译文本
        Map<Element, List<String>> translatedTextMap = translateGlossaryTexts(elementTextMap, request, counter, keyMap, keyMap0, apiKey);
        // 替换原始文本为翻译后的文本
        jsoupUtils.replaceOriginalTextsWithTranslated(doc, translatedTextMap);
        return doc.body().html();
    }

    // 对文本进行翻译（词汇表）
    public Map<Element, List<String>> translateGlossaryTexts(Map<Element, List<String>> elementTextMap, TranslateRequest request,
                                                             CharacterCountUtils counter, Map<String, String> keyMap, Map<String, String> keyMap0, String apiKey) {
        Map<Element, List<String>> translatedTextMap = new HashMap<>();
        for (Map.Entry<Element, List<String>> entry : elementTextMap.entrySet()) {
            Element element = entry.getKey();
            List<String> texts = entry.getValue();
            List<String> translatedTexts = new ArrayList<>();
            for (String text : texts) {
                String translated = translateSingleLineWithProtection(text, request, counter, keyMap, keyMap0, apiKey);
                translatedTexts.add(translated);
            }
            translatedTextMap.put(element, translatedTexts); // 保存翻译后的文本和 alt 属性
        }
        return translatedTextMap;
    }

    /**
     * 翻译单行文本，保护变量、URL和符号
     */
    private String translateSingleLineWithProtection(String text, TranslateRequest request, CharacterCountUtils counter,
                                                     Map<String, String> keyMap, Map<String, String> keyMap0, String apiKey) {
        // 检查缓存
        String translatedCache = translateSingleLine(text, request.getTarget());
        if (translatedCache != null) {
            return translatedCache;
        }

        // 处理文本，保护不翻译的部分
        String translatedText = processTextWithProtection(text, (cleanedText) -> {
            String translated = translateSingleLine(cleanedText, request.getTarget());
            if (translated != null) {
                return translated;
            }

            // 使用谷歌翻译
            counter.addChars(googleCalculateToken(cleanedText));
            Map<String, String> placeholderMap = new HashMap<>();
            String updateText = extractKeywords(cleanedText, placeholderMap, keyMap, keyMap0, request.getSource());
            request.setContent(updateText);
            //String targetString = translateApiIntegration.microsoftTranslate(request);
            String targetString = getGoogleTranslationWithRetry(updateText, request.getSource(), apiKey, request.getTarget());
//            String targetString = translateAndCount(request,counter, resourceType);
            String finalText = restoreKeywords(targetString, placeholderMap);
            addData(request.getTarget(), cleanedText, finalText);
            return finalText;
        });

        addData(request.getTarget(), text, translatedText);
        return translatedText;
    }

    /**
     * 处理文本，保护不翻译的变量、URL和符号
     */
    private String processTextWithProtection(String text, Function<String, String> translator) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        List<Pattern> patterns = Arrays.asList(
                URL_PATTERN,
                VARIABLE_PATTERN,
                CUSTOM_VAR_PATTERN,
                LIQUID_CONDITION_PATTERN,
                ARRAY_VAR_PATTERN
        );

        List<MatchRange> matches = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                matches.add(new MatchRange(matcher.start(), matcher.end(), matcher.group()));
            }
        }

        matches.sort(Comparator.comparingInt(m -> m.start));

        for (MatchRange match : matches) {
            if (match.start > lastEnd) {
                String toTranslate = text.substring(lastEnd, match.start);
                String cleanedText = cleanTextFormat(toTranslate);
                if (!cleanedText.isEmpty()) {
                    if (SYMBOL_PATTERN.matcher(cleanedText).matches()) {
                        result.append(cleanedText); // 纯符号不翻译
                    } else {
                        result.append(translator.apply(cleanedText)); // 普通文本翻译
                    }
                }
            }
            result.append(match.content); // 保留变量或URL
            lastEnd = match.end;
        }

        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            String cleanedText = cleanTextFormat(remaining);
            if (!cleanedText.isEmpty()) {
                if (SYMBOL_PATTERN.matcher(cleanedText).matches()) {
                    result.append(cleanedText);
                } else {
                    result.append(translator.apply(cleanedText));
                }
            }
        }

        return result.toString();
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
                //TODO:根据shopName获取对应的google apiKey
                translateByUser(counter, value, source, request, resourceId, translation, translateContext.getApiKey(), translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                appInsights.trackTrace("翻译失败后的字符数： " + counter.getTotalChars());
                userPrivateService.updateUsedCharsByShopName(request.getShopName(), counter.getTotalChars());
                saveToShopify(value, translation, resourceId, request);
            }
            if (checkIsStopped(request.getShopName(), counter))
                return;
        }
    }

    //根据用户的翻译模型选择翻译
    private void translateByUser(CharacterCountUtils counter, String value, String source, ShopifyRequest request, String resourceId, Map<String, Object> translation, String apiKey, String resourceType) {
        //TODO:根据shopName获取对应的google apiKey
        //计算用户的token数
        counter.addChars(value.length());
        //对文本进行翻译
        String targetValue = getGoogleTranslationWithRetry(value, source, apiKey, request.getTarget());
        addData(request.getTarget(), value, targetValue);
//        String targetValue = translateApiIntegration.microsoftTranslate(new TranslateRequest(0, request.getShopName(), request.getAccessToken(), source, request.getTarget(), value));
        //翻译成功后，将翻译后的数据存shopify本地中
        saveToShopify(targetValue, translation, resourceId, request);
        printTranslation(targetValue, value, translation, request.getShopName() + PRIVATE_KEY, resourceType, resourceId);

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

    //从缓存和数据库中获取数据
    public boolean translateDataByCacheAndDatabase(ShopifyRequest request, String value, Map<String, Object> translation, String resourceId, String target, String source) {
        //获取缓存数据
        String targetCache = translateSingleLine(value, request.getTarget());
        if (targetCache != null) {
            saveToShopify(targetCache, translation, resourceId, request);
            printTranslation(targetCache, value, translation, request.getShopName() + PRIVATE_KEY, "cache", resourceId);
            return true;
        }
        //TODO: 255字符以内才从数据库中获取数据
        String targetText = null;
        try {
            targetText = vocabularyService.getTranslateTextDataInVocabulary(target, value, source);
        } catch (Exception e) {
            //打印错误信息
            appInsights.trackTrace("translateDataByDatabase error: " + e.getMessage());
        }
        if (targetText != null) {
            addData(target, value, targetText);
            saveToShopify(targetText, translation, resourceId, request);
            printTranslation(targetText, value, translation, request.getShopName() + PRIVATE_KEY, "database", resourceId);
            return true;
        }
        return false;
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
                String clearValue = cleanTextFormat(value);
                if (clearValue.isEmpty()){
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
            if (key.contains("metafield:") || key.contains("color")
                    || key.contains("formId:") || key.contains("phone_text") || key.contains("email_text")
                    || key.contains("carousel_easing") || key.contains("_link") || key.contains("general") || key.contains("css:")
                    || key.contains("icon:") || "handle".equals(key) || type.equals("FILE_REFERENCE") || type.equals("URL") || type.equals("LINK")
                    || type.equals("LIST_FILE_REFERENCE") || type.equals("LIST_LINK")
                    || type.equals(("LIST_URL"))
                    || resourceType.equals(SHOP_POLICY)) {
                continue;
            }

            //如果是theme模块的数据
            if (TRANSLATABLE_RESOURCE_TYPES.contains(resourceType)) {
                if (!TRANSLATABLE_KEY_PATTERN.matcher(key).matches()) {
                    continue;
                }
                //如果包含对应key和value，则跳过
                if (!shouldTranslate(key, value) && !isHtml(value)) {
                    continue;
                }
                //如果值为纯数字的话，不翻译
                if (isNumber(value)) {
                    continue;
                }
            }

            //对METAFIELD字段翻译
            if (resourceType.equals(METAFIELD)) {
                judgeData.get(METAFIELD).add(new RegisterTransactionRequest(null, null, locale, key, value, translatableContentDigest, resourceId, type));
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

    public void translateFailEmail(String shopName, CharacterCountUtils counter, LocalDateTime begin, int beginChars, Integer remainingChars, String target, String source) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("language", target);
        templateData.put("user", usersDO.getFirstName());
        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String TargetShop;
        TargetShop = shopName.substring(0, shopName.length() - suffix.length());
        templateData.put("shop_name", TargetShop);
        //获取用户已翻译的和未翻译的文本
        //通过shopName获取翻译到那个文本
        String resourceType = translatesService.getResourceTypeByshopNameAndTargetAndSource(shopName, target, source);
        TypeSplitResponse typeSplitResponse = splitByType(resourceType);
        templateData.put("translated_content", typeSplitResponse.getBefore().toString());
        templateData.put("remaining_content", typeSplitResponse.getAfter().toString());
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
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137439L, templateData, TRANSLATION_FAILED_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
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
        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String TargetShop;
        TargetShop = request.getShopName().substring(0, request.getShopName().length() - suffix.length());
        templateData.put("shop_name", TargetShop);
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
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137353L, templateData, SUCCESSFUL_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
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
