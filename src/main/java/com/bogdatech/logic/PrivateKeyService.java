package com.bogdatech.logic;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.*;
import com.bogdatech.exception.ClientException;
import com.bogdatech.integration.*;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.logic.translate.TranslateDataService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.controller.response.TypeSplitResponse;
import com.bogdatech.requestBody.ShopifyRequestBody;
import com.bogdatech.utils.AppInsightsUtils;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.entity.DO.TranslateResourceDTO.ALL_RESOURCES;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.logic.UserTypeTokenService.getUserTranslatedToken;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsoupUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.*;
import static com.bogdatech.utils.ListUtils.convertALL;
import static com.bogdatech.utils.ListUtils.sort;
import static com.bogdatech.utils.PlaceholderUtils.getGlossaryPrompt;
import static com.bogdatech.utils.PlaceholderUtils.getSimplePrompt;
import static com.bogdatech.utils.ResourceTypeUtils.splitByType;
import static com.bogdatech.utils.StringUtils.normalizeHtml;
import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;
import static com.bogdatech.utils.UserPrivateUtils.getApiKey;

@Component
public class PrivateKeyService {
    @Autowired
    private  UserPrivateService userPrivateService;
    @Autowired
    private  ITranslatesService iTranslatesService;
    @Autowired
    private  IUsersService usersService;
    @Autowired
    private  EmailIntegration emailIntegration;
    @Autowired
    private  IEmailService emailService;
    @Autowired
    private  ShopifyService shopifyService;
    @Autowired
    private  ITranslatesService translatesService;
    @Autowired
    private  TestingEnvironmentIntegration testingEnvironmentIntegration;
    @Autowired
    private  ShopifyHttpIntegration shopifyApiIntegration;
    @Autowired
    private  SecretClient secretClient;
    @Autowired
    private  IUserTypeTokenService userTypeTokenService;
    @Autowired
    private  IUserPrivateTranslateService iUserPrivateTranslateService;
    @Autowired
    private  GlossaryService glossaryService;
    @Autowired
    private  RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private  PrivateIntegration privateIntegration;
    @Autowired
    private TranslateDataService translatedDataService;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;

    private static final String PRIVATE_KEY = "private_key";
    public static final Integer GOOGLE_MODEL = 0;
    public static final Integer OPENAI_MODEL = 1;
    private static final Pattern EMOJI_PATTERN = Pattern.compile("[\\p{So}\\p{Cn}]|(?:[\uD83C-\uDBFF\uDC00\uDFFF])+");

    /**
     * 私有key翻译前的判断
     *
     * @param clickTranslateRequest 请求对象，包含shopName、target、source，accessToken等信息
     */
    public BaseResponse<Object> judgePrivateKey(String shopName, ClickTranslateRequest clickTranslateRequest) {
        //做数据检验
        appInsights.trackTrace("translate " + shopName + " click: " + clickTranslateRequest);
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
        appInsights.trackTrace("translate " + shopName + " modelFlag 选择模型: " + modelFlag);
        if (modelFlag < 0) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }

        //判断Google等机器模型的语言
        if (modelFlag.equals(GOOGLE_MODEL) && (LANGUAGE_CODES.contains(clickTranslateRequest.getSource()) || LANGUAGE_CODES.contains(clickTranslateRequest.getTarget()[0]))) {
            return new BaseResponse<>().CreateErrorResponse(GOOGLE_RANGE);
        }

        String apiKey;
        String userKey = getApiKey(shopName, apiName);
        try {
            KeyVaultSecret keyVaultSecret = secretClient.getSecret(userKey);
            apiKey = keyVaultSecret.getValue();
        } catch (Exception e) {
            appInsights.trackException(e);
            return new BaseResponse<>().CreateErrorResponse(request);
        }

        if (apiKey == null) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }
        //判断字符是否超限
        UserPrivateTranslateDO privateData = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>().eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiKey, userKey));
        if (privateData == null) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }
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
                return new BaseResponse<>().CreateSuccessResponse(USER_TRANSLATING);
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
            appInsights.trackTrace("translate " + shopName + " translateModel errors : " + e.getMessage());
        }
//      翻译
        if (translateResourceDTOS == null || translateResourceDTOS.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse(clickTranslateRequest);
        }
        //私有key翻译
        translatesService.updateTranslateStatus(request.getShopName(), 2, clickTranslateRequest.getTarget()[0], request.getSource());
        startPrivateTranslation(request, remainingChars, counter, usedChars, apiKey, translateResourceDTOS, clickTranslateRequest.getIsCover(), modelFlag, privateData.getApiModel(), privateData.getPromptWord(), handleFlag, userKey);
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
     * 私有key翻译前的判断
     *
     * @param request        请求对象，包含shopName、target、source，accessToken等信息
     * @param remainingChars 额度字符数
     * @param counter        字符计数器
     * @param usedChars      已使用字符数
     */
    public void startPrivateTranslation(TranslateRequest request, Long remainingChars, CharacterCountUtils counter, Long usedChars, String apiKey, List<String> translateResourceDTOS, boolean isCover, Integer modelFlag, String apiModel, String userPrompt, boolean handleFlag, String userKey) {
        // 创建并启动翻译任务
        String shopName = request.getShopName();
        String source = request.getSource();
        String target = request.getTarget();
        Future<?> future = executorService.submit(() -> {
            LocalDateTime begin = LocalDateTime.now();
            appInsights.trackTrace("translate Task submitted at: " + begin + " for shop: " + shopName);
            try {
                translating(request, remainingChars, counter, apiKey, translateResourceDTOS, isCover, modelFlag, apiModel, userPrompt, handleFlag);  // 执行翻译任务
            } catch (ClientException e) {
                if (e.getErrorMessage().equals(HAS_TRANSLATED)) {
                    userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
                    appInsights.trackException(e);
                    return;
                }
                iTranslatesService.updateTranslateStatus(shopName, 5, target, source);
                userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
                //发送报错邮件
                AtomicBoolean emailSent = userEmailStatus.computeIfAbsent(shopName, k -> new AtomicBoolean(false));
                if (emailSent.compareAndSet(false, true)) {
                    //将List<String> 转化位 List<TranslateResourceDTO>
                    List<String> sort = sort(translateResourceDTOS);
                    List<TranslateResourceDTO> convertALL = convertALL(sort);
                    translateFailEmail(shopName, begin, Math.toIntExact(usedChars), target, source, userKey, convertALL);
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
                iTranslatesService.updateTranslateStatus(shopName, 5, target, source);
                return;
            }
            //更新数据库中的已使用字符数
            userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
            // 将翻译状态改为“已翻译”//
            iTranslatesService.updateTranslateStatus(shopName, 1, request.getTarget(), source);
            try {
                //翻译成功后发送翻译成功的邮件
                if (translationParametersRedisService.isStopped(shopName)) {
                    translateSuccessEmail(request, begin, Math.toIntExact(usedChars), Math.toIntExact(remainingChars), userKey);
                }
            } catch (Exception e) {
                appInsights.trackException(e);
            }
        });

        userTasks.put(shopName, future);  // 存储用户的任务
        userEmailStatus.put(shopName, new AtomicBoolean(false)); //重置用户发送的邮件
        Boolean stopFlag = translationParametersRedisService.delStopTranslationKey(shopName);
        if (stopFlag) {
            appInsights.trackTrace("startPrivateTranslation 私有key任务启动，删除标识： " + shopName);
        }
    }

    private void translating(TranslateRequest request, Long remainingChars, CharacterCountUtils counter, String apiKey, List<String> translateResourceDTOS, boolean isCover, Integer modelFlag, String apiModel, String userPrompt, boolean handleFlag) {
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);

        //判断是否有同义词
        Map<String, Object> glossaryMap = new HashMap<>();
        glossaryService.getGlossaryByShopName(shopifyRequest.getShopName(), shopifyRequest.getTarget(), glossaryMap);

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
            String query = new ShopifyRequestBody().getFirstQuery(translateResource);
            String shopifyData = shopifyService.getShopifyData(shopifyRequest.getShopName(), shopifyRequest.getAccessToken(), API_VERSION_LAST, query);
            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, Math.toIntExact(remainingChars), glossaryMap, request.getSource(), null, apiKey, handleFlag, isCover, modelFlag, apiModel, userPrompt);
            translateJson(translateContext);

        }
        iTranslatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), request.getSource(), null);
        appInsights.trackTrace("translate " + shopifyRequest.getShopName() + " 翻译结束时间： " + LocalDateTime.now());
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
        if (translationParametersRedisService.isStopped(shopName)) {
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
        appInsights.trackTrace("translate " + request.getShopName() + " 现在翻译到： " + resourceType);
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
            appInsights.trackTrace("translate " + request.getShopName() + " 解析rootNode错误： " + e.getMessage());
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
        Set<TranslateTextDO> needTranslatedData = translatedDataService.translatedDataParse(node, translateContext.getShopifyRequest().getShopName(), translateContext.getIsCover(), null);
        if (needTranslatedData == null) {
            return;
        }
        Set<TranslateTextDO> filterTranslateData = translatedDataService.filterNeedTranslateSet(translateContext.getTranslateResource().getResourceType(), translateContext.getHandleFlag(), needTranslatedData, translateContext.getShopifyRequest().getShopName(), null, translateContext.getShopifyRequest().getAccessToken());
        //将翻译的数据分类，提示词，普通文本，html
        Map<String, Set<TranslateTextDO>> stringSetMap = RabbitMqTranslateService.initTranslateMap();
        //将筛选好的数据分类
        Map<String, Set<TranslateTextDO>> stringSetMap1 = rabbitMqTranslateService.filterTranslateMap(stringSetMap, filterTranslateData, translateContext.getGlossaryMap());
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
                case TITLE:
                case META_TITLE:
                case LIST_SINGLE:
                case LOWERCASE_HANDLE:
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
            if (StringUtils.isValidString(value)) {
                return;
            }

            //走翻译流程
            String translatedText = translateByModel(translateContext, value, request, apiKey);
            //对null的处理, 不翻译
            if (translatedText == null) {
                return;
            }

            shopifyService.saveToShopify(translatedText, translation, resourceId, request);
            AppInsightsUtils.printTranslation(translatedText, value, translation, request.getShopName(), resourceType, resourceId, source);
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
                    if (!StringUtils.isValidString(original) && original != null && !original.trim().isEmpty() && !isHtml(value)) {
                        //走翻译流程
                        String translatedText = translateByModel(translateContext, value, request, apiKey);
                        //将数据填回去
                        resultList.set(i, translatedText);
                    }
                }
                //将list数据转为String 再存储到shopify本地
                String translatedValue = OBJECT_MAPPER.writeValueAsString(resultList);
                shopifyService.saveToShopify(translatedValue, translation, resourceId, request);
                AppInsightsUtils.printTranslation(translatedValue, value, translation, request.getShopName(), resourceType, resourceId, source);
            } catch (Exception e) {
                //存原数据到shopify本地
                shopifyService.saveToShopify(value, translation, resourceId, request);
                appInsights.trackTrace("translate LIST errors 错误原因： " + e.getMessage());
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
            appInsights.trackTrace("translate shopName : " + shopName + " , glossaryDO : " + glossaryDO);
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
            // 根据模块选择翻译方法，先做普通翻译
            // 判断是否停止翻译
            if (checkIsStopped(shopName)) {
                return;
            }

            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String source = translateContext.getSource();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            // 判断是否达到额度限制
            updateCharsWhenExceedLimit(shopName, translateContext.getModel(), new TranslateRequest(0, shopName, accessToken, source, target, null));

            translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName, source, target), String.valueOf(2));
            translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName, source, target), value);

            // 开始翻译
            // 缓存翻译和数据库翻译
            if (rabbitMqTranslateService.cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                continue;
            }

            // 词汇表翻译
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
                    // 用大模型翻译
                    String glossaryString = glossaryText(keyMap1, keyMap0, value);

                    // 根据关键词生成对应的提示词
                    String targetName = getLanguageName(target);
                    String prompt;
                    if (glossaryString != null) {
                        prompt = getGlossaryPrompt(targetName, glossaryString, null);
                        appInsights.trackTrace("translate 私有 普通文本： " + value + " Glossary提示词: " + prompt);
                    } else {
                        prompt = getSimplePrompt(targetName, null);
                        appInsights.trackTrace("translate 私有 普通文本：" + value + " Simple提示词: " + prompt);
                    }

                    // 目前改为openai翻译
                    String finalText = privateIntegration.translateByGpt(value, translateContext.getApiModel(), translateContext.getApiKey(), prompt, shopName, Long.valueOf(translateContext.getRemainingChars()));

                    // 对null处理, 不翻译
                    if (finalText == null) {
                        return;
                    }
                    redisProcessService.setCacheData(shopifyRequest.getTarget(), finalText, value);
                    shopifyService.saveToShopify(finalText, translation, resourceId, shopifyRequest);
                    AppInsightsUtils.printTranslation(finalText, value, translation, shopifyRequest.getShopName(), translateContext.getTranslateResource().getResourceType(), resourceId, source);
                } catch (Exception e) {
                    appInsights.trackTrace("translate glossaryTranslationModel errors " + e);
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
            translatesService.updateTranslateStatus(shopName, 5, translateRequest.getTarget(), translateRequest.getSource());
            throw new ClientException(CHARACTER_LIMIT);
        }
    }

    //对html数据处理
    private void translateHtml(Set<TranslateTextDO> htmlData, TranslateContext translateContext) {
        ShopifyRequest shopifyRequest = translateContext.getShopifyRequest();
        String shopName = shopifyRequest.getShopName();
        String target = shopifyRequest.getTarget();
        String accessToken = shopifyRequest.getAccessToken();

        for (TranslateTextDO translateTextDO : htmlData) {
            // 根据模块选择翻译方法，先做普通翻译
            // 判断是否停止翻译
            if (checkIsStopped(shopName)) {
                return;
            }

            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String source = translateContext.getSource();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            // 判断是否达到额度限制
            updateCharsWhenExceedLimit(shopName, translateContext.getModel(), new TranslateRequest(0, shopName, accessToken, source, target, null));

            translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName, source, target), String.valueOf(2));
            translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName, source, target), value);

            // 开始翻译
            // 缓存翻译和数据库翻译
            if (rabbitMqTranslateService.cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                continue;
            }

            String modelHtml;
            // 判断是google翻译还是openai翻译
            if (translateContext.getModel().equals(GOOGLE_MODEL)) {
                modelHtml = translateGooglePrivateHtml(translateTextDO.getSourceText(), target, shopName, translateContext.getApiKey(), translateContext.getRemainingChars());
            } else if (translateContext.getModel().equals(OPENAI_MODEL)) {
                // 大模型 html翻译
                modelHtml = privateIntegration.translatePrivateNewHtml(translateTextDO.getSourceText(), target, translateContext.getApiKey(), translateContext.getApiModel(), shopName, Long.valueOf(translateContext.getRemainingChars()));
            } else {
                appInsights.trackTrace("translate 私有 html：" + value + " 没有找到对应的翻译api modelHtml : " + translateContext.getModel());
                continue;
            }
            if (modelHtml == null) {
                // 私有key 跳过
                appInsights.trackTrace("translate 私有 html：" + value + " 翻译失败 用户： " + shopName + " sourceText : " + translateTextDO.getSourceText());
                continue;
            }

            if (translateContext.getTranslateResource().getResourceType().equals(METAFIELD)) {
                // 对翻译后的html做格式处理
                modelHtml = normalizeHtml(modelHtml);
            }
            shopifyService.saveToShopify(modelHtml, translation, resourceId, shopifyRequest);
            AppInsightsUtils.printTranslation(modelHtml, translateTextDO.getSourceText(), translation, shopifyRequest.getShopName(), translateContext.getTranslateResource().getResourceType(), resourceId, source);
        }

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
            // 根据模块选择翻译方法，先做普通翻译
            // 判断是否停止翻译
            if (checkIsStopped(request.getShopName())) {
                return;
            }

            String value = translateTextDO.getSourceText();
            String resourceId = translateTextDO.getResourceId();
            String source = translateContext.getSource();
            String key = translateTextDO.getTextKey();
            String digest = translateTextDO.getDigest();
            Map<String, Object> translation = createTranslationMap(target, key, digest);

            // 判断是否达到额度限制
            updateCharsWhenExceedLimit(shopName, translateContext.getModel(), new TranslateRequest(0, shopName, accessToken, source, target, null));

            translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName, source, target), String.valueOf(2));
            translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName, source, target), value);

            // 开始翻译
            // 缓存翻译和数据库翻译
            if (rabbitMqTranslateService.cacheOrDatabaseTranslateData(value, source, translation, resourceId, shopifyRequest)) {
                continue;
            }

            // switch根据m模型类型选择翻译
            try {
                translateByUser(translateContext, value, source, request, resourceId, translation, translateContext.getApiKey(), translateContext.getTranslateResource().getResourceType());
            } catch (Exception e) {
                appInsights.trackTrace("translate value : " + value + " 翻译失败 errors ：" + e.getMessage());
                userPrivateService.updateUsedCharsByShopName(request.getShopName(), counter.getTotalChars());
                saveToShopify(value, translation, resourceId, request);
            }
        }

    }

    /**
     * 根据用户的翻译模型选择翻译
     * */
    private void translateByUser(TranslateContext translateContext, String value, String source, ShopifyRequest request, String resourceId, Map<String, Object> translation, String apiKey, String resourceType) {
        // 根据模型类型选择对应的翻译方法
        // 对元字段数据类型翻译
        if (translateContext.getTranslateResource().getResourceType().equals(METAFIELD)) {
            //特殊处理
            translateMetafield(translateContext, value, source, request, resourceId, translation, apiKey, resourceType);
            return;
        }
        String targetValue = translateByModel(translateContext, value, request, apiKey);
        redisProcessService.setCacheData(request.getTarget(), targetValue, value);

        // 翻译成功后，将翻译后的数据存shopify本地中
        saveToShopify(targetValue, translation, resourceId, request);
        AppInsightsUtils.printTranslation(targetValue, value, translation, request.getShopName() + PRIVATE_KEY, resourceType, resourceId, source);

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
            appInsights.trackTrace("translate " + request.getShopName() + " 用户 model : " + translateContext.getModel() + " 不存在");
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
            appInsights.trackTrace("saveToShopify " + request.getShopName() + " save to Shopify errors : " + e.getMessage());
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
            appInsights.trackTrace("saveToShopify " + request.getShopName() + "Failed to save to Shopify: " + e.getMessage());
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
        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
        String infoByShopify = shopifyService.getShopifyData(request.getShopName(), request.getAccessToken(), API_VERSION_LAST, query);

        try {
            return OBJECT_MAPPER.readTree(infoByShopify);
        } catch (JsonProcessingException e) {
            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
        }
    }

    public void translateFailEmail(String shopName, LocalDateTime begin, int beginChars, String target, String source, String userKey, List<TranslateResourceDTO> resourceList) {
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
        TypeSplitResponse typeSplitResponse = splitByType(resourceType, resourceList);
        templateData.put("translated_content", typeSplitResponse.getBefore().toString());
        templateData.put("remaining_content", typeSplitResponse.getAfter().toString());
        //获取更新前后的时间
        LocalDateTime end = LocalDateTime.now();

        Duration duration = Duration.between(begin, end);
        long costTime = duration.toMinutes();
        templateData.put("time", costTime + " minutes");

        //共消耗的字符数
        UserPrivateTranslateDO privateData = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>().eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiKey, userKey));
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        int endChars = Math.toIntExact(privateData.getUsedToken());
        int costChars = endChars - beginChars;
        String formattedNumber = formatter.format(costChars);
        templateData.put("credit_count", formattedNumber);
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137439L, templateData, TRANSLATION_FAILED_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), TRANSLATION_FAILED_SUBJECT, b ? 1 : 0));
    }

    public void translateSuccessEmail(TranslateRequest request, LocalDateTime begin, int beginChars, Integer remainingChars, String userKey) {
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
        //获取当前已消耗的token
        UserPrivateTranslateDO privateData = iUserPrivateTranslateService.getOne(new LambdaQueryWrapper<UserPrivateTranslateDO>().eq(UserPrivateTranslateDO::getShopName, shopName).eq(UserPrivateTranslateDO::getApiKey, userKey));
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        int endChars = Math.toIntExact(privateData.getUsedToken());
        int costChars = endChars - beginChars;
        String formattedNumber = formatter.format(costChars);
        appInsights.trackTrace(shopName + " formattedNumber = " + formattedNumber);
        templateData.put("credit_count", formattedNumber);

        //还剩下的字符数
        int remaining = remainingChars - endChars;
        if (remaining < 0) {
            templateData.put("remaining_credits", "0");

        } else {
            String formattedNumber2 = formatter.format(remaining);
            templateData.put("remaining_credits", formattedNumber2);
        }
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137353L, templateData, SUCCESSFUL_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUCCESSFUL_TRANSLATION_SUBJECT, b ? 1 : 0));

    }

    /**
     * 私有keyGoogle翻译方法
     * 暂时先简单的改下，具体的后面再做
     * @param html 输入的HTML文本
     * @param target 目标语言
     * @param shopName 店铺名称
     * @param apiKey Google翻译API密钥
     * @param limitChars 限制字符数
     * @return 翻译后的HTML文本
     */
    public String translateGooglePrivateHtml(String html, String target, String shopName, String apiKey, Integer limitChars) {
        // 检查输入是否有效
        if (html == null || html.trim().isEmpty()) {
            return html;
        }

        try {
            // 判断输入是否包含 <html> 标签
            boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();

            if (hasHtmlTag) {
                // 如果有 <html> 标签，按完整文档处理
                Document doc = Jsoup.parse(html);
                if (doc == null) {
                    return html;
                }

                // 获取 <html> 元素并修改 lang 属性
                Element htmlTag = doc.selectFirst("html");
                if (htmlTag != null) {
                    htmlTag.attr("lang", target);
                }

                processNode(doc.body(), target, shopName, apiKey, limitChars);
                String result = doc.outerHtml(); // 返回完整的HTML结构
                result = isHtmlEntity(result);
                return result;
            } else {
                // 如果没有 <html> 标签，作为片段处理
                Document doc = Jsoup.parseBodyFragment(html);
                Element body = doc.body();
                processNode(body, target, shopName, apiKey, limitChars);
                // 只返回子节点内容，不包含 <body>
                StringBuilder result = new StringBuilder();
                for (Node child : body.childNodes()) {
                    result.append(child.toString());
                }

                String output = result.toString();
                output = isHtmlEntity(output);
                return output;
            }

        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("translate " + shopName + " new html errors : " + e);
            return html;
        }
    }

    /**
     * 递归处理节点
     *
     * @param node 当前节点
     */
    private void processNode(Node node, String target, String shopName, String apiKey, Integer limitChars) {
        try {
            // 如果是元素节点
            if (node instanceof Element) {
                Element element = (Element) node;
                String tagName = element.tagName().toLowerCase();

                // 检查是否为不翻译的标签
                if (NO_TRANSLATE_TAGS.contains(tagName)) {
                    return;
                }

                // 属性不翻译，保持原样
                element.attributes().forEach(attr -> {
                });

                // 递归处理子节点
                for (Node child : element.childNodes()) {
                    processNode(child, target, shopName, apiKey, limitChars);
                }
            }
            // 如果是文本节点
            else if (node instanceof TextNode) {
                TextNode textNode = (TextNode) node;
                String text = textNode.getWholeText();

                // 如果文本为空或只有空白字符，跳过
                if (text.trim().isEmpty()) {
                    return;
                }

                // 使用缓存处理文本

                String translatedText = translateTextWithCache(text, target, shopName, apiKey, limitChars);
                textNode.text(translatedText);
            }
        } catch (Exception e) {
            appInsights.trackTrace("translate " + shopName + " 递归处理节点报错： " + e.getMessage());
        }
    }

    /**
     * 使用缓存处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @param target 目标语言
     * @param shopName 店铺名称
     * @param apiKey 翻译API密钥
     * @param limitChars 限制字符数
     * @return 翻译后的文本
     */
    private String translateTextWithCache(String text, String target, String shopName, String apiKey, Integer limitChars) {
        // 检查缓存
        String translated = redisProcessService.getCacheData(target, text);
        if (translated != null) {
            return translated;
        }

        // 处理文本中的变量和URL
        String translatedText = translateTextWithProtection(text, target, shopName, apiKey, limitChars);

        // 存入缓存
        redisProcessService.setCacheData(target, translatedText, text);
        return translatedText;
    }

    /**
     * 处理文本内容，保护变量和URL
     *
     * @param text 输入文本
     * @return 翻译后的文本
     */
    private String translateTextWithProtection(String text, String target, String shopName, String apiKey, Integer limitChars) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        // 合并所有需要保护的模式
        List<Pattern> patterns = Arrays.asList(
                URL_PATTERN,
                SYMBOL_PATTERN,
                EMOJI_PATTERN
        );

        List<MatchRange> matches = new ArrayList<>();
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                matches.add(new MatchRange(matcher.start(), matcher.end(), matcher.group()));
            }
        }

        // 按位置排序
        matches.sort(Comparator.comparingInt(m -> m.start));

        // 处理所有匹配项之间的文本
        for (MatchRange match : matches) {
            // 翻译匹配项之前的文本
            if (match.start > lastEnd) {
                String toTranslate = text.substring(lastEnd, match.start);
                String cleanedText = cleanTextFormat(toTranslate); // 清理格式
                //对特殊符号进行处理
                if (cleanedText.matches("\\p{Zs}+")) {
                    result.append(cleanedText);
                    continue;
                }
                if (!cleanedText.trim().isEmpty()) { // 避免翻译空字符串
                    String targetString;
                    try {
                        targetString = addSpaceAfterTranslated(cleanedText, target, shopName, apiKey, limitChars);
                        result.append(targetString);
                    } catch (ClientException e) {
                        // 如果AI翻译失败，则使用谷歌翻译
                        result.append(cleanedText);
                        continue;
                    }
                } else {
                    result.append(toTranslate); // 保留原始空白
                }
            }
            // 保留匹配到的变量或URL，不翻译
            result.append(match.content);
            lastEnd = match.end;
        }

        // 处理剩余文本
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            String cleanedText = cleanTextFormat(remaining); // 清理格式

            if (cleanedText.matches("\\p{Zs}+")) {
                result.append(cleanedText);
                return result.toString();
            }
            if (!cleanedText.trim().isEmpty() && !cleanedText.matches("\\s*")) {
                String targetString;
                try {
                    targetString = addSpaceAfterTranslated(cleanedText, target, shopName, apiKey, limitChars);
                    result.append(targetString);
                } catch (ClientException e) {
                    result.append(cleanedText);
                }
            } else {
                result.append(remaining);
            }
        }
        return result.toString();
    }

    /**
     * 手动添加空格
     */
    public String addSpaceAfterTranslated(String sourceText, String target, String shopName, String apiKey, Integer limitChars) {
        // Step 1: 记录开头和结尾的空格数量
        int leadingSpaces = countLeadingSpaces(sourceText);
        int trailingSpaces = countTrailingSpaces(sourceText);
        // Step 2: 去除首尾空格，准备翻译
        String textToTranslate = sourceText.trim();

        // Step 3: google翻译操作
        appInsights.trackTrace("translate " + shopName + " textToTranslate: " + "'" + textToTranslate + "'" + " sourceText: " + sourceText + " apiKey: " + apiKey + " limitChars: " + limitChars);
        String targetString = privateIntegration.getGoogleTranslationWithRetry(textToTranslate, apiKey, target, shopName, Long.valueOf(limitChars));

        // Step 4: 恢复开头和结尾空格
        StringBuilder finalResult = new StringBuilder();
        if (leadingSpaces != 0) {
            finalResult.append(" ".repeat(Math.max(0, leadingSpaces)));
        }

        finalResult.append(targetString);

        if (trailingSpaces != 0) {
            finalResult.append(" ".repeat(Math.max(0, trailingSpaces)));
        }

        appInsights.trackTrace("translate " + shopName + " finalResult: " + "'" + finalResult + "'" + " sourceText: " + sourceText);
        return finalResult.toString();
    }
}
