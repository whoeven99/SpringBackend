//package com.bogdatech.logic;
//
//import com.bogdatech.Service.*;
//import com.bogdatech.context.TranslateContext;
//import com.bogdatech.entity.*;
//import com.bogdatech.exception.ClientException;
//import com.bogdatech.integration.EmailIntegration;
//import com.bogdatech.integration.PrivateIntegration;
//import com.bogdatech.integration.ShopifyHttpIntegration;
//import com.bogdatech.model.controller.request.*;
//import com.bogdatech.model.controller.response.BaseResponse;
//import com.bogdatech.requestBody.ShopifyRequestBody;
//import com.bogdatech.utils.CharacterCountUtils;
//import com.bogdatech.utils.TypeConversionUtils;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ArrayNode;
//import com.microsoft.applicationinsights.TelemetryClient;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.CannotCreateTransactionException;
//
//import java.text.NumberFormat;
//import java.time.Duration;
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.concurrent.Future;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import static com.bogdatech.constants.MailChimpConstants.*;
//import static com.bogdatech.constants.TranslateConstants.*;
//import static com.bogdatech.constants.TranslateConstants.OPENAI;
//import static com.bogdatech.entity.TranslateResourceDTO.ALL_RESOURCES;
//import static com.bogdatech.enums.ErrorEnum.SHOPIFY_RETURN_ERROR;
//import static com.bogdatech.logic.TranslateService.*;
//import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;
//import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;
//
//@Component
//public class PrivateKeyService {
//    private final PrivateIntegration privateIntegration;
//    private final UserPrivateService userPrivateService;
//    private final IUserPrivateService iUserPrivateService;
//    private final ITranslatesService iTranslatesService;
//    private final IUsersService usersService;
//    private final EmailIntegration emailIntegration;
//    private final IEmailService emailService;
//    private final IGlossaryService glossaryService;
//    private final ShopifyService shopifyService;
//    private final ShopifyHttpIntegration shopifyApiIntegration;
//
//    @Autowired
//    public PrivateKeyService(PrivateIntegration privateIntegration, UserPrivateService userPrivateService, IUserPrivateService iUserPrivateService, ITranslatesService iTranslatesService, IUsersService usersService, EmailIntegration emailIntegration, IEmailService emailService, IGlossaryService glossaryService, ShopifyService shopifyService, ShopifyHttpIntegration shopifyApiIntegration) {
//        this.privateIntegration = privateIntegration;
//        this.userPrivateService = userPrivateService;
//        this.iUserPrivateService = iUserPrivateService;
//        this.iTranslatesService = iTranslatesService;
//        this.usersService = usersService;
//        this.emailIntegration = emailIntegration;
//        this.emailService = emailService;
//        this.glossaryService = glossaryService;
//        this.shopifyService = shopifyService;
//        this.shopifyApiIntegration = shopifyApiIntegration;
//    }
//
//    TelemetryClient appInsights = new TelemetryClient();
//
//    //测试google调用
//    public void test(String text, String source, String apiKey, String target) {
//        String s = privateIntegration.translateByGoogle(text, source, apiKey, target);
//        System.out.println("s = " + s);
//    }
//
//
//    /**
//     * 私有key翻译前的判断
//     *
//     * @param clickTranslateRequest 请求对象，包含shopName、target、source，accessToken等信息
//     */
//    public BaseResponse<Object> judgePrivateKey(ClickTranslateRequest clickTranslateRequest) {
////        将ClickTranslateRequest转换为TranslateRequest
//        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);
//
//        //判断字符是否超限
//        UserPrivateDO userPrivateDO = iUserPrivateService.selectOneByShopName(request.getShopName());
//        Integer remainingChars = userPrivateDO.getAmount();
//        Integer usedChars = userPrivateDO.getUsedAmount();
//        // 如果字符超限，则直接返回字符超限
//        if (usedChars >= remainingChars) {
//            return new BaseResponse<>().CreateErrorResponse(request);
//        }
//
////        一个用户当前只能翻译一条语言，根据用户的status判断
//        List<Integer> integers = iTranslatesService.readStatusInTranslatesByShopName(request);
//        for (Integer integer : integers) {
//            if (integer == 2) {
//                return new BaseResponse<>().CreateSuccessResponse(HAS_TRANSLATED);
//            }
//        }
//
//        //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
//        iTranslatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource(), request.getAccessToken());
//        //初始化计数器
//        CharacterCountUtils counter = new CharacterCountUtils();
//        counter.addChars(usedChars);
//        //私有key翻译
//        startPrivateTranslation(request, remainingChars, counter, usedChars);
//        return new BaseResponse<>().CreateSuccessResponse(clickTranslateRequest);
//    }
//
//    /**
//     * 私有key翻译前的判断
//     *
//     * @param request        请求对象，包含shopName、target、source，accessToken等信息
//     * @param remainingChars 额度字符数
//     * @param counter        字符计数器
//     * @param usedChars      已使用字符数
//     */
//    public void startPrivateTranslation(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars) {
//        // 创建并启动翻译任务
//        String shopName = request.getShopName();
//        String source = request.getSource();
//        String target = request.getTarget();
//        Future<?> future = executorService.submit(() -> {
//            LocalDateTime begin = LocalDateTime.now();
//            appInsights.trackTrace("Task submitted at: " + begin + " for shop: " + shopName);
//            try {
//                translating(request, remainingChars, counter, usedChars);  // 执行翻译任务
//            } catch (ClientException e) {
//                if (e.getErrorMessage().equals(HAS_TRANSLATED)) {
//                    userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
//                    appInsights.trackTrace("翻译失败的原因： " + e.getErrorMessage());
//                    //更新初始值
//                    updateInitialValue(request);
//                    return;
//                }
//                iTranslatesService.updateTranslateStatus(shopName, 3, target, source, request.getAccessToken());
//                userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
////                //发送报错邮件
//                AtomicBoolean emailSent = userEmailStatus.computeIfAbsent(shopName, k -> new AtomicBoolean(false));
//                if (emailSent.compareAndSet(false, true)) {
//                    translateFailEmail(shopName, CHARACTER_LIMIT);
//                }
//                appInsights.trackTrace("startTranslation " + e.getErrorMessage());
//                //更新初始值
//                //更新初始值
//                updateInitialValue(request);
//                return;
//            } catch (CannotCreateTransactionException e) {
//                appInsights.trackTrace("Translation task failed: " + e);
//                //更新初始值
//                updateInitialValue(request);
//                return;
//            } catch (Exception e) {
//                appInsights.trackTrace("Translation task failed: " + e);
//                //更新初始值
//                updateInitialValue(request);
//                userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
//                iTranslatesService.updateTranslateStatus(shopName, 3, target, source, request.getAccessToken());
//                return;
//            }
//            //更新数据库中的已使用字符数
//            userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
//            // 将翻译状态改为“已翻译”//
//            iTranslatesService.updateTranslateStatus(shopName, 1, request.getTarget(), source, request.getAccessToken());
//            //翻译成功后发送翻译成功的邮件
//            translateSuccessEmail(request, counter, begin, usedChars, remainingChars);
//            //更新初始值
//            updateInitialValue(request);
//        });
//
//        userTasks.put(shopName, future);  // 存储用户的任务
//        userEmailStatus.put(shopName, new AtomicBoolean(false)); //重置用户发送的邮件
//        userStopFlags.put(shopName, new AtomicBoolean(false));  // 初始化用户的停止标志
//
//
//    }
//
//    private void translating(TranslateRequest request, int remainingChars, CharacterCountUtils counter, int usedChars) {
//        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
//        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(shopifyRequest);
//
//        //判断是否有同义词
//        Map<String, Object> glossaryMap = new HashMap<>();
//        getGlossaryByShopName(shopifyRequest, glossaryMap);
//
//        //TRANSLATION_RESOURCES
//        for (TranslateResourceDTO translateResource : ALL_RESOURCES) {
//            // 定期检查是否停止
//            if (checkIsStopped(request.getShopName(), counter)) return;
//            translateResource.setTarget(request.getTarget());
//            String query = new ShopifyRequestBody().getFirstQuery(translateResource);
//            cloudServiceRequest.setBody(query);
//            String shopifyData;
//            try {
//                String env = System.getenv("ApplicationEnv");
//                if ("prod".equals(env) || "dev".equals(env)) {
//                    shopifyData = String.valueOf(shopifyApiIntegration.getInfoByShopify(shopifyRequest, query));
//                } else {
//                    shopifyData = shopifyService.getShopifyData(cloudServiceRequest);
//                }
//            } catch (Exception e) {
//                // 如果出现异常，则跳过, 翻译其他的内容
//                //更新当前字符数
//                userPrivateService.updateUsedCharsByShopName(request.getShopName(), counter.getTotalChars());
//                continue;
//            }
//            TranslateContext translateContext = new TranslateContext(shopifyData, shopifyRequest, translateResource, counter, remainingChars, glossaryMap, request.getSource(), null);
//            translateJson(translateContext);
//            // 定期检查是否停止
//            if (checkIsStopped(request.getShopName(), counter)) return;
//        }
//        iTranslatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), request.getSource(), null);
//        System.out.println("翻译结束");
//    }
//
//    //更新初始值
//    public void updateInitialValue(TranslateRequest request) {
//        try {
////            startTokenCount(request);
//        } catch (Exception e) {
//            appInsights.trackTrace("重新更新token值失败！！！");
//        }
//    }
//
//    //根据返回的json片段，将符合条件的value翻译,并返回json片段
//    public Future<Void> translateJson(TranslateContext translateContext) {
//        String resourceType = translateContext.getTranslateResource().getResourceType();
//        ShopifyRequest request = translateContext.getShopifyRequest();
//        System.out.println("现在翻译到： " + resourceType);
//        //将目前的状态，添加到数据库中，前端要用这个数据做进度条功能
//        iTranslatesService.updateTranslatesResourceType(request.getShopName(), request.getTarget(), translateContext.getSource(), resourceType);
//
//        if (translateContext.getShopifyData() == null) {
//            // 返回默认值或空结果
//            return null;
//        }
//        ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode rootNode;
//        try {
//            rootNode = objectMapper.readTree(translateContext.getShopifyData());
//        } catch (JsonProcessingException e) {
//            appInsights.trackTrace("rootNode错误： " + e.getMessage());
//            return null;
//        }
//        translateSingleLineTextFieldsRecursively(rootNode, translateContext);
//        // 递归处理下一页数据
//        handlePagination(rootNode, translateContext);
//        return null;
//    }
//
//    // 递归处理下一页数据
//    private void handlePagination(JsonNode translatedRootNode, TranslateContext translateContext) {
//        // 获取translatableResources节点
//        JsonNode translatableResourcesNode = translatedRootNode.path("translatableResources");
//        // 获取pageInfo节点
//        JsonNode pageInfoNode = translatableResourcesNode.path("pageInfo");
//
//        if (translatableResourcesNode.hasNonNull("pageInfo")) {
//            if (pageInfoNode.hasNonNull("hasNextPage") && pageInfoNode.get("hasNextPage").asBoolean()) {
//                JsonNode endCursor = pageInfoNode.path("endCursor");
//                translateContext.getTranslateResource().setAfter(endCursor.asText(null));
//                translateNextPage(translateContext);
//            }
//        }
//    }
//
//    //递归遍历JSON树：使用 translateSingleLineTe
//    //方法递归地遍历整个 JSON 树，并对 translatableContent 字段进行特别处理。
//    private void translateSingleLineTextFieldsRecursively(JsonNode node, TranslateContext translateContext) {
//
//        ShopifyRequest shopifyRequest = translateContext.getShopifyRequest();
//        String source = translateContext.getSource();
//        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils()))
//            return;
//        //定义HashMap存放判断后的对应数据
//        // 初始化 judgeData 用于分类存储数据
//        Map<String, List<RegisterTransactionRequest>> judgeData = initializeJudgeData();
//        // 获取 translatableResources 节点
//        JsonNode translatableResourcesNode = node.path("translatableResources");
//        if (!translatableResourcesNode.isObject()) {
//            return;
//        }
//
//        // 处理 nodes 数组
//        JsonNode nodesNode = translatableResourcesNode.path("nodes");
//        if (!nodesNode.isArray()) {
//            return;
//        }
//
//        ArrayNode nodesArray = (ArrayNode) nodesNode;
//        for (JsonNode nodeElement : nodesArray) {
//            if (nodeElement.isObject()) {
//                processNodeElement(nodeElement, shopifyRequest, translateContext, judgeData);
//            }
//        }
//        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils(), shopifyRequest.getTarget(), source))
//            return;
//        //对judgeData数据进行翻译和存入shopify,除了html
//        try {
//            translateAndSaveData(judgeData, translateContext);
//        } catch (ClientException e) {
//            appInsights.trackTrace("翻译过程中抛出的异常" + e.getErrorMessage());
//            throw e;
//        }
//        translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, shopifyRequest.getShopName(), 0, translateContext.getCharacterCountUtils().getTotalChars(), 0, 0, 0));
//    }
//
//    // 初始化 judgeData，用于存储不同类型的数据
//    private Map<String, List<RegisterTransactionRequest>> initializeJudgeData() {
//        return new HashMap<>() {{
//            put(PLAIN_TEXT, new ArrayList<>());
//            put(HTML, new ArrayList<>());
//            put(DATABASE, new ArrayList<>());
//            put(JSON_TEXT, new ArrayList<>());
//            put(GLOSSARY, new ArrayList<>());
//            put(OPENAI, new ArrayList<>());
//        }};
//    }
//
//    // 处理单个节点元素，提取相关信息并分类存储
//    private void processNodeElement(JsonNode nodeElement, ShopifyRequest shopifyRequest, TranslateContext translateContext,
//                                    Map<String, List<RegisterTransactionRequest>> judgeData) {
//        String resourceId = null;
//        ArrayNode translatableContent = null;
//        Map<String, TranslateTextDO> translatableContentMap = null;
//
//        if (checkIsStopped(shopifyRequest.getShopName(), translateContext.getCharacterCountUtils()))
//            return;
//        // 遍历字段，提取 resourceId 和 translatableContent
//        Iterator<Map.Entry<String, JsonNode>> fields = nodeElement.fields();
//        while (fields.hasNext()) {
//            Map.Entry<String, JsonNode> field = fields.next();
//            String fieldName = field.getKey();
//            JsonNode fieldValue = field.getValue();
//
//            // 根据字段名称进行处理
//            switch (fieldName) {
//                case "resourceId":
//                    if (fieldValue == null) {
//                        break;
//                    }
//                    resourceId = fieldValue.asText(null);
//                    // 提取翻译内容映射
//                    translatableContentMap = extractTranslations(nodeElement, resourceId, shopifyRequest);
//                    translatableContentMap = extractTranslatableContent(nodeElement, translatableContentMap);
//                    break;
//                case "translatableContent":
//                    if (fieldValue.isArray()) {
//                        translatableContent = (ArrayNode) fieldValue;
//                    }
//                    break;
//            }
//
//            // 如果 resourceId 和 translatableContent 都已提取，则存储并准备翻译
//            if (resourceId != null && translatableContent != null) {
//                judgeAndStoreData(translatableContent, resourceId, judgeData, translateContext.getTranslateResource().getResourceType(),
//                        translatableContentMap, translateContext.getGlossaryMap());
//            }
//        }
//    }
//
//    //递归处理下一页数据
//    private void translateNextPage(TranslateContext translateContext) {
//        JsonNode nextPageData;
//        try {
//            nextPageData = fetchNextPage(translateContext.getTranslateResource(), translateContext.getShopifyRequest());
//        } catch (Exception e) {
//            return;
//        }
//        // 重新开始翻译流程
//        translateSingleLineTextFieldsRecursively(nextPageData, translateContext);
//        // 递归处理下一页数据
//        handlePagination(nextPageData, translateContext);
//    }
//
//    //修改getTestQuery里面的testQuery，用获取后的的查询语句进行查询
//    public JsonNode fetchNextPage(TranslateResourceDTO translateResource, ShopifyRequest request) {
//        CloudServiceRequest cloudServiceRequest = TypeConversionUtils.shopifyToCloudServiceRequest(request);
//        String query = new ShopifyRequestBody().getAfterQuery(translateResource);
//        cloudServiceRequest.setBody(query);
//
//        String env = System.getenv("ApplicationEnv");
//        String infoByShopify;
//        if ("prod".equals(env) || "dev".equals(env)) {
//            infoByShopify = String.valueOf(shopifyApiIntegration.getInfoByShopify(request, query));
//        } else {
//            infoByShopify = shopifyService.getShopifyData(cloudServiceRequest);
//        }
//        ObjectMapper objectMapper = new ObjectMapper();
//
//        try {
//            return objectMapper.readTree(infoByShopify);
//        } catch (JsonProcessingException e) {
//            throw new ClientException(SHOPIFY_RETURN_ERROR.getErrMsg());
//        }
//    }
//
//    public void translateFailEmail(String shopName, String errorReason) {
//        UsersDO usersDO = usersService.getUserByName(shopName);
//        Map<String, String> templateData = new HashMap<>();
//        templateData.put("user", usersDO.getFirstName());
//
//        //错误原因
//        templateData.put("reason", errorReason);
//
//        //由腾讯发送邮件
//        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(133321L, templateData, TRANSLATION_FAILED_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
//        //存入数据库中
//        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), TRANSLATION_FAILED_SUBJECT, b ? 1 : 0));
//    }
//
//    public void translateSuccessEmail(TranslateRequest request, CharacterCountUtils counter, LocalDateTime begin, int beginChars, Integer remainingChars) {
//        String shopName = request.getShopName();
//        //通过shopName获取用户信息 需要 {{user}} {{language}} {{credit_count}} {{time}} {{remaining_credits}}
//        UsersDO usersDO = usersService.getUserByName(shopName);
//        Map<String, String> templateData = new HashMap<>();
//        templateData.put("user", usersDO.getFirstName());
//        templateData.put("language", request.getTarget());
//
//        //获取更新前后的时间
//        LocalDateTime end = LocalDateTime.now();
//
//        Duration duration = Duration.between(begin, end);
//        long costTime = duration.toMinutes();
//        templateData.put("time", costTime + " minutes");
//
//        //共消耗的字符数
//        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
//        int endChars = counter.getTotalChars();
//        int costChars = endChars - beginChars;
//        String formattedNumber = formatter.format(costChars);
//        templateData.put("credit_count", formattedNumber);
//
//        //还剩下的字符数
//        int remaining = remainingChars - endChars;
//        if (remaining < 0) {
//            templateData.put("remaining_credits", "0");
//
//        } else {
//            String formattedNumber2 = formatter.format(remaining);
//            templateData.put("remaining_credits", formattedNumber2);
//        }
//        appInsights.trackTrace("templateData" + templateData);
//        //由腾讯发送邮件
//        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(133535L, templateData, SUCCESSFUL_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
//        //存入数据库中
//        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUCCESSFUL_TRANSLATION_SUBJECT, b ? 1 : 0));
//
//    }
//
//    //判断词汇表中要判断的词
//    public void getGlossaryByShopName(ShopifyRequest request, Map<String, Object> glossaryMap) {
//        GlossaryDO[] glossaryDOS = glossaryService.getGlossaryByShopName(request.getShopName());
//        if (glossaryDOS == null) {
//            return; // 如果术语表为空，直接返回
//        }
//
//        for (GlossaryDO glossaryDO : glossaryDOS) {
//            // 判断语言范围是否符合
//            if (glossaryDO.getRangeCode().equals(request.getTarget()) || glossaryDO.getRangeCode().equals("ALL")) {
//                // 判断术语是否启用
//                if (glossaryDO.getStatus() != 1) {
//                    continue;
//                }
//
//                // 存储术语数据
//                glossaryMap.put(glossaryDO.getSourceText(), new GlossaryDO(glossaryDO.getSourceText(), glossaryDO.getTargetText(), glossaryDO.getCaseSensitive()));
//            }
//        }
//    }
//
//    private boolean checkIsStopped(String shopName, CharacterCountUtils counter) {
//        if (userStopFlags.get(shopName).get()) {
//            //更新数据库中的已使用字符数
//            userPrivateService.updateUsedCharsByShopName(shopName, counter.getTotalChars());
//            // 将翻译状态为2改为“部分翻译”//
//            iTranslatesService.updateStatusByShopNameAnd2(shopName);
//            return true;
//        }
//        return false;
//    }
//}
