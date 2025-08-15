package com.bogdatech.logic;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.*;
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
import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.SHOPIFY_RETURN_ERROR;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.RabbitMqTranslateService.*;
import static com.bogdatech.logic.ShopifyService.getShopifyDataByCloud;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.logic.UserTypeTokenService.getUserTranslatedToken;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CalculateTokenUtils.googleCalculateToken;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsoupUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.ModelUtils.translateModel;
import static com.bogdatech.utils.PlaceholderUtils.getGlossaryPrompt;
import static com.bogdatech.utils.PlaceholderUtils.getSimplePrompt;
import static com.bogdatech.utils.PrintUtils.printTranslation;
import static com.bogdatech.utils.RegularJudgmentUtils.isValidString;
import static com.bogdatech.utils.ResourceTypeUtils.splitByType;
import static com.bogdatech.utils.StringUtils.normalizeHtml;
import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;
import static com.bogdatech.utils.UserPrivateUtils.getApiKey;

@Component
public class PrivateKeyService {
    private final UserPrivateService userPrivateService;
    private final ITranslatesService iTranslatesService;
    private final IUsersService usersService;
    private final EmailIntegration emailIntegration;
    private final IEmailService emailService;
    private final JsoupUtils jsoupUtils;
    private final ShopifyService shopifyService;
    private final ITranslatesService translatesService;
    private final TestingEnvironmentIntegration testingEnvironmentIntegration;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final IVocabularyService vocabularyService;
    private final SecretClient secretClient;
    private final IUserTypeTokenService userTypeTokenService;
    private final ChatGptByOpenaiIntegration chatGptByOpenaiIntegration;
    private final IUserPrivateTranslateService iUserPrivateTranslateService;
    private final GlossaryService glossaryService;
    private final RabbitMqTranslateService rabbitMqTranslateService;
    private final PrivateIntegration privateIntegration;

    @Autowired
    public PrivateKeyService(UserPrivateService userPrivateService, ITranslatesService iTranslatesService, IUsersService usersService, EmailIntegration emailIntegration, IEmailService emailService, JsoupUtils jsoupUtils, GlossaryService glossaryService, ShopifyService shopifyService, ITranslatesService translatesService, TestingEnvironmentIntegration testingEnvironmentIntegration, ShopifyHttpIntegration shopifyApiIntegration, IVocabularyService vocabularyService, SecretClient secretClient, IUserTypeTokenService userTypeTokenService, ChatGptByOpenaiIntegration chatGptByOpenaiIntegration, IUserPrivateTranslateService iUserPrivateTranslateService, RabbitMqTranslateService rabbitMqTranslateService, PrivateIntegration privateIntegration) {
        this.userPrivateService = userPrivateService;
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
        this.chatGptByOpenaiIntegration = chatGptByOpenaiIntegration;
        this.iUserPrivateTranslateService = iUserPrivateTranslateService;
        this.rabbitMqTranslateService = rabbitMqTranslateService;
        this.privateIntegration = privateIntegration;
    }


    private static final String PRIVATE_KEY = "private_key";
    public static final Integer GOOGLE_MODEL = 0;
    public static final Integer OPENAI_MODEL = 1;

    /**
     * 私有key翻译前的判断
     *
     * @param clickTranslateRequest 请求对象，包含shopName、target、source，accessToken等信息
     */
    public BaseResponse<Object> judgePrivateKey(String shopName, ClickTranslateRequest clickTranslateRequest) {
        //做数据检验
        appInsights.trackTrace("click: " + clickTranslateRequest);
        //判断前端传的数据是否完整，如果不完整，报错
        if (shopName == null || shopName.isEmpty()
                || clickTranslateRequest.getAccessToken() == null || clickTranslateRequest.getAccessToken().isEmpty()
                || clickTranslateRequest.getSource() == null || clickTranslateRequest.getSource().isEmpty()
                || clickTranslateRequest.getTarget() == null || clickTranslateRequest.getTarget().length == 0) {
            return new BaseResponse<>().CreateErrorResponse("Missing parameters");
        }
//        将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);
        Integer apiName = Integer.valueOf(clickTranslateRequest.getTranslateSettings1());
        //判断是google还是openai
        Integer modelFlag = judgeModelByValue(clickTranslateRequest.getTranslateSettings1());
        appInsights.trackTrace("modelFlag: " + modelFlag);
        if (modelFlag < 0) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }

        //判断Google等机器模型的语言
        if (modelFlag.equals(GOOGLE_MODEL) && LANGUAGE_CODES.contains(clickTranslateRequest.getSource())) {
           return new BaseResponse<>().CreateErrorResponse("Google cannot translate this language");
        }

        String apiKey;
        String userKey = getApiKey(shopName, apiName);
        try {
            KeyVaultSecret keyVaultSecret = secretClient.getSecret(userKey);
            apiKey = keyVaultSecret.getValue();
//            Object aiClient = initAiModel(clickTranslateRequest.getTranslateSettings1(), apiKey);
        } catch (Exception e) {
            appInsights.trackException(e);
            return new BaseResponse<>().CreateErrorResponse(request);
        }

        if (apiKey == null) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }
        //判断字符是否超限
        UserPrivateTranslateDO privateData = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>().eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiKey, userKey));
        Long remainingChars = privateData.getTokenLimit();
        Long usedChars = privateData.getUsedToken();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }

//        一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = iTranslatesService.readStatusInTranslatesByShopName(request.getShopName());
        for (Integer integer : integers) {
            if (integer == 2) {
                return new BaseResponse<>().CreateSuccessResponse(HAS_TRANSLATED);
            }
        }

        boolean handleFlag = false;
        List<String> translateModel = clickTranslateRequest.getTranslateSettings3();
        if (translateModel.contains("handle")) {
            translateModel.removeIf("handle"::equals);
            handleFlag = true;
        }

        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(Math.toIntExact(usedChars));
        //修改模块的排序
        List<String> translateResourceDTOS = null;
        try {
            translateResourceDTOS = translateModel(clickTranslateRequest.getTranslateSettings3());
        } catch (Exception e) {
            appInsights.trackTrace("translateModel errors : " + e.getMessage());
        }
//      翻译
        if (translateResourceDTOS == null || translateResourceDTOS.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse(clickTranslateRequest);
        }
        //私有key翻译
        translatesService.updateTranslateStatus(request.getShopName(), 2, clickTranslateRequest.getTarget()[0], request.getSource(), request.getAccessToken());
        startPrivateTranslation(request, remainingChars, counter, usedChars, apiKey, translateResourceDTOS, clickTranslateRequest.getIsCover(), modelFlag, privateData.getApiModel(), privateData.getPromptWord(), handleFlag);
        return new BaseResponse<>().CreateSuccessResponse(clickTranslateRequest);
    }

    /**
     * 根据值判断调用什么模型
     */
    public static Integer judgeModelByValue(String translateSettings1) {
        return switch (translateSettings1) {
            case "0" -> 0;
            case "1" -> 1;
            default -> -1;
        };
    }

    /**
     * 大模型初始化
     * 目前只有openai
     */
    public Object initAiModel(String translateSettings1, String apiKey) {
        return switch (translateSettings1) {
            case "1" -> chatGptByOpenaiIntegration.initOpenAIClient(apiKey);
            default -> null;
        };
    }


    /**
     * 私有key翻译前的判断
     *
     * @param request        请求对象，包含shopName、target、source，accessToken等信息
     * @param remainingChars 额度字符数
     * @param counter        字符计数器
     * @param usedChars      已使用字符数
     */
    public void startPrivateTranslation(TranslateRequest request, Long remainingChars, CharacterCountUtils counter, Long usedChars, String apiKey, List<String> translateResourceDTOS, boolean isCover, Integer modelFlag, String apiModel, String userPrompt, boolean handleFlag) {
        // 创建并启动翻译任务
        String shopName = request.getShopName();
        String source = request.getSource();
        String target = request.getTarget();
        Future<?> future = executorService.submit(() -> {
            LocalDateTime begin = LocalDateTime.now();
            appInsights.trackTrace("Task submitted at: " + begin + " for shop: " + shopName);
            try {
                translating(request, remainingChars, counter, apiKey, translateResourceDTOS, isCover, modelFlag, apiModel, userPrompt, handleFlag);  // 执行翻译任务
            } catch (ClientException e) {
                if (e.getErrorMessage().equals(HAS_TRANSLATED)) {
                    userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
                    appInsights.trackException(e);
                    return;
                }
                iTranslatesService.updateTranslateStatus(shopName, 5, target, source, request.getAccessToken());
                userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
                //发送报错邮件
                AtomicBoolean emailSent = userEmailStatus.computeIfAbsent(shopName, k -> new AtomicBoolean(false));
                if (emailSent.compareAndSet(false, true)) {
                    translateFailEmail(shopName, counter, begin, Math.toIntExact(usedChars), target, source);
                }
                appInsights.trackException(e);
                return;
            } catch (CannotCreateTransactionException e) {
                appInsights.trackException(e);
                return;
            } catch (Exception e) {
                appInsights.trackException(e);
                //更新初始值
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
                    translateSuccessEmail(request, counter, begin, Math.toIntExact(usedChars), Math.toIntExact(remainingChars));
                }
            } catch (Exception e) {
                appInsights.trackException(e);
            }
        });

        userTasks.put(shopName, future);  // 存储用户的任务
        userEmailStatus.put(shopName, new AtomicBoolean(false)); //重置用户发送的邮件
        userStopFlags.put(shopName, new AtomicBoolean(false));  // 初始化用户的停止标志
    }

    private void translating(TranslateRequest request, Long remainingChars, CharacterCountUtils counter, String apiKey, List<String> translateResourceDTOS, boolean isCover, Integer modelFlag, String apiModel, String userPrompt, boolean handleFlag) {
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);

        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        glossaryService.getGlossaryByShopName(shopifyRequest, glossaryMap);

        for (TranslateResourceDTO translateResource : ALL_RESOURCES
        ) {
            if (!translateResourceDTOS.contains(translateResource.getResourceType())) {
                continue;
            }
            if (translateResource.getResourceType().equals(PAYMENT_GATEWAY)) {
                continue;
            }

            if (EXCLUDED_SHOPS.contains(request.getShopName()) && PRODUCT_MODEL.contains(translateResource.getResourceType())) {
                continue;
            }

            // 定期检查是否停止
            if (checkIsStopped(request.getShopName())) {
                return;
            }
            translateResource.setTarget(request.getTarget());
            String shopifyData = getShopifyData(shopifyRequest, translateResource);
            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, Math.toIntExact(remainingChars), glossaryMap, request.getSource(), null, apiKey, handleFlag, isCover, modelFlag, apiModel, userPrompt);
            translateJson(translateContext);

        }
        iTranslatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), request.getSource(), null);
        appInsights.trackTrace("翻译结束");
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
     * 判断停止标识
     */
    private boolean checkIsStopped(String shopName) {
        if (userStopFlags.get(shopName).get()) {
            // 将翻译状态为2改为“部分翻译”//
            iTranslatesService.updateStatusByShopNameAnd2(shopName);
            return true;
        }
        return false;
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
                getUserTranslatedToken(request, translationId, userTypeTokenService, shopifyService);
            }
        } catch (IllegalArgumentException e) {
            appInsights.trackTrace("错误原因： " + e.getMessage());
        }
    }

    //根据返回的json片段，将符合条件的value翻译,并返回json片段
    public Future<Void> translateJson(TranslateContext translateContext) {
        String resourceType = translateContext.getTranslateResource().getResourceType();
        ShopifyRequest request = translateContext.getShopifyRequest();
        appInsights.trackTrace("现在翻译到： " + resourceType);
        //将目前的状态，添加到数据库中，前端要用这个数据做进度条功能
        iTranslatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), translateContext.getSource(), resourceType);

        if (translateContext.getShopifyData() == null) {
            // 返回默认值或空结果
            return null;
        }
        JsonNode rootNode;
        try {
            rootNode = OBJECT_MAPPER.readTree(translateContext.getShopifyData());
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
        //将node转换为json字符串
        Set<TranslateTextDO> needTranslatedData = translatedDataParse(node, translateContext.getShopifyRequest().getShopName(), translateContext.getIsCover());
        if (needTranslatedData == null) {
            return;
        }
        Set<TranslateTextDO> filterTranslateData = filterNeedTranslateSet(translateContext.getTranslateResource().getResourceType(), translateContext.getHandleFlag(), needTranslatedData);
        //将翻译的数据分类，提示词，普通文本，html
        Map<String, Set<TranslateTextDO>> stringSetMap = initTranslateMap();
        //将筛选好的数据分类
        Map<String, Set<TranslateTextDO>> stringSetMap1 = filterTranslateMap(stringSetMap, filterTranslateData, translateContext.getGlossaryMap());
        //实现功能： 分析三种类型数据， 添加模块标识，开始翻译

        try {
            translateAndSaveData(stringSetMap1, translateContext);
        } catch (ClientException e) {
            appInsights.trackException(e);
            throw e;
        }
    }

    private void translateAndSaveData(Map<String, Set<TranslateTextDO>> stringSetMap, TranslateContext translateContext) {
        if (stringSetMap == null || stringSetMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<TranslateTextDO>> entry : stringSetMap.entrySet()) {
            switch (entry.getKey()) {
                case HTML:
                    translateHtml(entry.getValue(), translateContext);
                    break;
                case PLAIN_TEXT:
                    translateDataByAPI(entry.getValue(), translateContext);
                    break;
                case GLOSSARY:
                    translateDataByGlossary(entry.getValue(), translateContext);
                    break;
                default:
                    break;
            }
        }
    }

    //翻译元字段的数据
    private void translateMetafield(TranslateContext translateContext, String value, String source, ShopifyRequest request, String resourceId, Map<String, Object> translation, String apiKey, String resourceType) {
        String handleType = "null";
        if (translateContext.getHandleFlag()) {
            handleType = HANDLE;
        }

        if (SINGLE_LINE_TEXT_FIELD.equals(resourceType) && !isHtml(value)) {
            //纯数字字母符号 且有两个  标点符号 以#开头，长度为10 不翻译
            if (isValidString(value)) {
                return;
            }

            //走翻译流程
            String translatedText = translateByModel(translateContext, value, request, apiKey);
            shopifyService.saveToShopify(translatedText, translation, resourceId, request);
            printTranslation(translatedText, value, translation, request.getShopName(), resourceType, resourceId, source);
            //存到数据库中
            try {
                if (handleType.equals(HANDLE)) {
                    return;
                }
                // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
                vocabularyService.InsertOne(request.getTarget(), translatedText, source, value);
            } catch (Exception e) {
                appInsights.trackTrace("translateMetafield存储失败 errors ： " + e.getMessage() + " ，继续翻译");
            }
            return;
        }

        if (LIST_SINGLE_LINE_TEXT_FIELD.equals(resourceType)) {
            //翻译list类型文本
            try {
                //如果符合要求，则翻译，不符合要求则返回原值
                List<String> resultList = OBJECT_MAPPER.readValue(value, new TypeReference<>() {
                });
                for (int i = 0; i < resultList.size(); i++) {
                    String original = resultList.get(i);
                    if (!isValidString(original) && original != null && !original.trim().isEmpty() && !isHtml(value)) {
                        //走翻译流程
                        String translatedText = translateByModel(translateContext, value, request, apiKey);
                        //将数据填回去
                        resultList.set(i, translatedText);
                    }
                }
                //将list数据转为String 再存储到shopify本地
                String translatedValue = OBJECT_MAPPER.writeValueAsString(resultList);
                shopifyService.saveToShopify(translatedValue, translation, resourceId, request);
                printTranslation(translatedValue, value, translation, request.getShopName(), resourceType, resourceId, source);

                //存到数据库中
                try {
                    // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
                    vocabularyService.InsertOne(request.getTarget(), translatedValue, source, value);
                } catch (Exception e) {
                    appInsights.trackTrace("translateMetafield存储失败 errors ： " + e.getMessage() + " ，继续翻译");
                }
            } catch (Exception e) {
                //存原数据到shopify本地
                shopifyService.saveToShopify(value, translation, resourceId, request);
                appInsights.trackTrace("LIST errors 错误原因： " + e.getMessage());
            }
        }
    }


    //对词汇表数据进行处理
    public void translateDataByGlossary(Set<TranslateTextDO> glossaryData,
                                        TranslateContext translateContext) {
        ShopifyRequest shopifyRequest = translateContext.getShopifyRequest();
        String shopName = shopifyRequest.getShopName();
        String target = shopifyRequest.getTarget();
        String accessToken = shopifyRequest.getAccessToken();

        Map<String, Object> glossaryMap = translateContext.getGlossaryMap();
        if (glossaryMap == null || glossaryMap.isEmpty()) {
            return;
        }

        //关键词
        Map<String, String> keyMap1 = new HashMap<>();
        Map<String, String> keyMap0 = new HashMap<>();
        //将glossaryMap中所有caseSensitive为1的数据存到一个Map集合里面
        for (Map.Entry<String, Object> entry : glossaryMap.entrySet()) {
            GlossaryDO glossaryDO = OBJECT_MAPPER.convertValue(entry.getValue(), GlossaryDO.class);
            appInsights.trackTrace("shopName : " + shopName + " , glossaryDO : " + glossaryDO);
//            GlossaryDO glossaryDO = (GlossaryDO) entry.getValue();
            if (glossaryDO.getCaseSensitive() == 1) {
                keyMap1.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
                continue;
            }
            if (glossaryDO.getCaseSensitive() == 0) {
                keyMap0.put(glossaryDO.getSourceText(), glossaryDO.getTargetText());
            }
        }

        for (TranslateTextDO translateTextDO : glossaryData) {
            //根据模块选择翻译方法，先做普通翻译
            //判断是否停止翻译
            if (checkIsStopped(shopName)) {
                return;
            }

            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String source = translateContext.getSource();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            //判断是否达到额度限制
            updateCharsWhenExceedLimit(shopName, translateContext.getModel(), new TranslateRequest(0, shopName, accessToken, source, target, null));

            //开始翻译
            //缓存翻译和数据库翻译
            if (rabbitMqTranslateService.cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                continue;
            }

            //词汇表翻译
            // html数据暂时先不翻译
            if (isHtml(value)) {
                continue;
            }

            // 机器模型不翻译
            if (translateContext.getModel().equals(GOOGLE_MODEL)) {
                continue;
            }

            if (translateContext.getModel().equals(OPENAI_MODEL)) {
                try {
                    //用大模型翻译
                    String glossaryString = glossaryText(keyMap1, keyMap0, value);
                    //根据关键词生成对应的提示词
                    String targetName = getLanguageName(target);
                    String prompt;
                    if (glossaryString != null) {
                        prompt = getGlossaryPrompt(targetName, glossaryString, null);
                        appInsights.trackTrace("私有 普通文本： " + value + " Glossary提示词: " + prompt);
                    } else {
                        prompt = getSimplePrompt(targetName, null);
                        appInsights.trackTrace("私有 普通文本：" + value + " Simple提示词: " + prompt);
                    }
                    //目前改为openai翻译
                    String finalText = privateIntegration.translateByGpt(value, translateContext.getApiModel(), translateContext.getApiKey(), prompt, shopName, Long.valueOf(translateContext.getRemainingChars()));
                    addData(shopifyRequest.getTarget(), value, finalText);
                    shopifyService.saveToShopify(finalText, translation, resourceId, shopifyRequest);
                    printTranslation(finalText, value, translation, shopifyRequest.getShopName(), translateContext.getTranslateResource().getResourceType(), resourceId, source);
                } catch (Exception e) {
                    appInsights.trackTrace("glossaryTranslationModel errors " + e);
                    shopifyService.saveToShopify(value, translation, resourceId, shopifyRequest);
                }
            }
        }
    }

    //达到字符限制，更新用户剩余字符数，终止循环
    public void updateCharsWhenExceedLimit(String shopName, Integer apiName, TranslateRequest translateRequest) {
        TranslationCounterRequest request = new TranslationCounterRequest();
        request.setShopName(shopName);

        // 以用户数据进行判断
        UserPrivateTranslateDO userData = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>().eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiName, apiName));
        if (userData.getUsedToken() >= userData.getTokenLimit()) {
            translatesService.updateTranslateStatus(shopName, 3, translateRequest.getTarget(), translateRequest.getSource(), translateRequest.getAccessToken());
            throw new ClientException(CHARACTER_LIMIT);
        }
    }

    //对html数据处理
    private void translateHtml(Set<TranslateTextDO> htmlData, TranslateContext translateContext) {
        ShopifyRequest shopifyRequest = translateContext.getShopifyRequest();
        String shopName = shopifyRequest.getShopName();
        String target = shopifyRequest.getTarget();
        String accessToken = shopifyRequest.getAccessToken();

        if (translateContext.getModel().equals(GOOGLE_MODEL)) {
            appInsights.trackTrace("google skip!!!");
            return;
        }

        if (translateContext.getModel().equals(OPENAI_MODEL)) {
            for (TranslateTextDO translateTextDO : htmlData) {
                //根据模块选择翻译方法，先做普通翻译
                //判断是否停止翻译
                if (checkIsStopped(shopName)) {
                    return;
                }

                String value = translateTextDO.getSourceText();
                String resourceId = translateTextDO.getResourceId();
                String source = translateContext.getSource();
                String key = translateTextDO.getTextKey();
                String digest = translateTextDO.getDigest();
                Map<String, Object> translation = createTranslationMap(target, key, digest);

                //判断是否达到额度限制
                updateCharsWhenExceedLimit(shopName, translateContext.getModel(), new TranslateRequest(0, shopName, accessToken, source, target, null));

                //开始翻译
                //缓存翻译和数据库翻译
                if (rabbitMqTranslateService.cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                    continue;
                }

                //大模型 html翻译
                String modelHtml = privateIntegration.translatePrivateNewHtml(translateTextDO.getSourceText(), target, translateContext.getApiKey(), translateContext.getApiModel(), shopName, Long.valueOf(translateContext.getRemainingChars()));
                if (translateContext.getTranslateResource().getResourceType().equals(METAFIELD)) {
                    //对翻译后的html做格式处理
                    modelHtml = normalizeHtml(modelHtml);
                }
                shopifyService.saveToShopify(modelHtml, translation, resourceId, shopifyRequest);
                printTranslation(modelHtml, translateTextDO.getSourceText(), translation, shopifyRequest.getShopName(), translateContext.getTranslateResource().getResourceType(), resourceId, source);
                //如果翻译数据小于255，存到数据库
                try {
                    // 255字符以内 和 数据库内有该数据类型 文本才能插入数据库
                    vocabularyService.InsertOne(target, modelHtml, source, translateTextDO.getSourceText());
                } catch (Exception e) {
                    appInsights.trackTrace("存储失败： " + e.getMessage() + " ，继续翻译");
                }
            }
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
            String targetString = privateIntegration.getGoogleTranslationWithRetry(updateText, apiKey, request.getTarget(), request.getShopName(), null);
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
//                VARIABLE_PATTERN,
//                CUSTOM_VAR_PATTERN
//                ,
//                LIQUID_CONDITION_PATTERN,
//                ARRAY_VAR_PATTERN
                SYMBOL_PATTERN
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
    private void translateDataByAPI(Set<TranslateTextDO> plainTextData,
                                    TranslateContext translateContext) {
        ShopifyRequest request = translateContext.getShopifyRequest();
        CharacterCountUtils counter = translateContext.getCharacterCountUtils();

        ShopifyRequest shopifyRequest = translateContext.getShopifyRequest();
        String shopName = shopifyRequest.getShopName();
        String target = shopifyRequest.getTarget();
        String accessToken = shopifyRequest.getAccessToken();
        for (TranslateTextDO translateTextDO : plainTextData) {
            //根据模块选择翻译方法，先做普通翻译
            //判断是否停止翻译
            if (checkIsStopped(request.getShopName())) {
                return;
            }

            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String source = translateContext.getSource();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            //判断是否达到额度限制
            updateCharsWhenExceedLimit(shopName, translateContext.getModel(), new TranslateRequest(0, shopName, accessToken, source, target, null));

            //开始翻译
            //缓存翻译和数据库翻译
            if (rabbitMqTranslateService.cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                continue;
            }

            //switch根据m模型类型选择翻译
            try {
                translateByUser(translateContext, value, source, request, resourceId, translation, translateContext.getApiKey(), translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                appInsights.trackTrace("value : " + value + " 翻译失败 errors ：" + e.getMessage());
                userPrivateService.updateUsedCharsByShopName(request.getShopName(), counter.getTotalChars());
                saveToShopify(value, translation, resourceId, request);
            }
        }

    }

    //根据用户的翻译模型选择翻译
    private void translateByUser(TranslateContext translateContext, String value, String source, ShopifyRequest request, String resourceId, Map<String, Object> translation, String apiKey, String resourceType) {
        //根据模型类型选择对应的翻译方法
        //对元字段数据类型翻译
        if (translateContext.getTranslateResource().getResourceType().equals(METAFIELD)) {
            //特殊处理
            translateMetafield(translateContext, value, source, request, resourceId, translation, apiKey, resourceType);
            return;
        }
        String targetValue = translateByModel(translateContext, value, request, apiKey);
        addData(request.getTarget(), value, targetValue);
        //翻译成功后，将翻译后的数据存shopify本地中
        saveToShopify(targetValue, translation, resourceId, request);
        printTranslation(targetValue, value, translation, request.getShopName() + PRIVATE_KEY, resourceType, resourceId, source);

    }

    /**
     * 根据model类型选择对应模型翻译
     */
    public String translateByModel(TranslateContext translateContext, String value, ShopifyRequest request, String apiKey) {
        if (translateContext.getModel().equals(GOOGLE_MODEL)) {
            return privateIntegration.getGoogleTranslationWithRetry(value, apiKey, request.getTarget(), request.getShopName(), Long.valueOf(translateContext.getRemainingChars()));
        } else if (translateContext.getModel().equals(OPENAI_MODEL)) {
            return privateIntegration.translateByGpt(value, translateContext.getApiModel(), apiKey, translateContext.getUserPrompt(), translateContext.getShopifyRequest().getShopName(), Long.valueOf(translateContext.getRemainingChars()));
        } else {
            appInsights.trackTrace(request.getShopName() + " 用户 model : " + translateContext.getModel() + " 不存在");
            return null;
        }
    }

    //将翻译后的数据存shopify本地中
    public void saveToShopify(String translatedValue, Map<String, Object> translation, String resourceId, ShopifyRequest request) {
        try {
            // 创建一个新的映射，避免修改原始的 translation
            Map<String, Object> newTranslation = new HashMap<>(translation);
            newTranslation.put("value", translatedValue);

            // 构建 variables 映射
            Map<String, Object> variables = new HashMap<>();
            variables.put("resourceId", resourceId);

            // 创建 translations 数组
            Object[] translations = new Object[]{newTranslation};
            variables.put("translations", translations);

            // 调用 saveToShopify 方法
            saveToShopify(new CloudInsertRequest(request.getShopName(), request.getAccessToken(), request.getApiVersion(), request.getTarget(), variables));
        } catch (Exception e) {
            appInsights.trackTrace(request.getShopName() + " save to Shopify errors : " + e.getMessage());
        }
    }

    //封装调用云服务器实现将数据存入shopify本地的方法
    public void saveToShopify(CloudInsertRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        Map<String, Object> body = cloudServiceRequest.getBody();

        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(cloudServiceRequest);
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
            infoByShopify = String.valueOf(getInfoByShopify(request, query));
        } else {
            infoByShopify = getShopifyDataByCloud(cloudServiceRequest);
        }

        try {
            return OBJECT_MAPPER.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
    }

    public void translateFailEmail(String shopName, CharacterCountUtils counter, LocalDateTime begin, int beginChars, String target, String source) {
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
        TypeSplitResponse typeSplitResponse = splitByType(resourceType, ALL_RESOURCES);
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
}
