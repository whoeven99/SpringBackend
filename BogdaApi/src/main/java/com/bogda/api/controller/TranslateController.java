package com.bogda.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogda.common.controller.request.*;
import com.bogda.common.entity.VO.*;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.service.Service.IUserTypeTokenService;
import com.bogda.service.Service.IUsersService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.integration.shopify.ShopifyHttpIntegration;
import com.bogda.service.logic.TranslateService;
import com.bogda.service.logic.UserTypeTokenService;
import com.bogda.service.logic.redis.TranslateTaskMonitorV3RedisService;
import com.bogda.service.logic.redis.TranslationParametersRedisService;
import com.bogda.service.logic.translate.TranslateV3Service;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.controller.response.ProgressResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.bogda.common.enums.ErrorEnum.*;

@RestController
@RequestMapping("/translate")
public class TranslateController {
    @Autowired
    private TranslateService translateService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private IUserTypeTokenService userTypeTokenService;
    @Autowired
    private UserTypeTokenService userTypeTokensService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private TranslateV3Service translateV3Service;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;
    @Autowired
    private TranslateTaskMonitorV3RedisService translateTaskMonitorV3RedisService;

    // 创建手动翻译任务
    @PutMapping("/clickTranslation")
    public BaseResponse<Object> clickTranslation(@RequestParam String shopName, @RequestBody ClickTranslateRequest request) {
        request.setShopName(shopName);
        return translateV3Service.createInitialTask(request);
    }

    @PostMapping("/getAllProgressData")
    public BaseResponse<ProgressResponse> getAllProgressData(@RequestParam String shopName, @RequestParam String source) {
        return translateV3Service.getProcess(shopName, source);
    }

    // 单条文本翻译 修改返回值类型
    @PostMapping("/singleTextTranslateV2")
    public BaseResponse<SingleReturnVO> singleTextTranslateV2(@RequestParam String shopName, @RequestBody SingleTranslateVO singleTranslateVO) {
        singleTranslateVO.setShopName(shopName);
        return translateV3Service.singleTextTranslate(singleTranslateVO);
    }

    // 用户手动点击停止翻译V2
    @PostMapping("/stopTranslatingTaskV2")
    public BaseResponse<Object> stopTranslatingTaskV2(@RequestParam String shopName, @RequestParam Integer taskId) {
        return translateService.stopTranslatingTaskV2(shopName, taskId);
    }

    // 用户手动点击继续翻译V2
    @PostMapping("/continueTranslatingV2")
    public BaseResponse<Object> continueTranslatingV2(@RequestParam String shopName, @RequestParam Integer taskId) {
        return translateV3Service.continueTranslatingV3(shopName, taskId);
    }

    /**
     * 与 BogdaTask 中 {@code TranslateTaskV3Scheduled#initialToTranslateTaskV3} 相同逻辑：拉取 status=0 的 v3 任务并执行初始化（转译前置阶段）。
     */
    @PostMapping("/v3/triggerInitialTasks")
    public BaseResponse<Object> triggerInitialTasksV3() {
        try {
            translateV3Service.processInitialTasksV3();
            return BaseResponse.SuccessResponse("processInitialTasksV3 invoked");
        } catch (Exception e) {
            TraceReporterHolder.report("TranslateController.triggerInitialTasksV3",
                    "FatalException manual trigger initial tasks failed: " + e);
            return BaseResponse.FailedResponse("Trigger initial tasks failed: " + e.getMessage());
        }
    }

    /**
     * 手动触发 v3 任务 AI 质量评分
     * module 为空时，对任务内所有模块评分；否则只评分指定模块。
     */
    @PostMapping("/triggerV3AiScore")
    public BaseResponse<Object> triggerV3AiScore(@RequestParam String taskId,
                                                 @RequestParam String shopName,
                                                 @RequestParam(required = false) String module) {
        return translateV3Service.triggerAiScoreReport(taskId, shopName, module);
    }

    /**
     * 从 Redis 读取某个商店下的全部 v3 任务监控信息
     */
    @GetMapping("/v3/redisTasksByShop")
    public BaseResponse<Object> getV3RedisTasksByShop(@RequestParam String shopName) {
        if (shopName == null || shopName.isEmpty()) {
            return BaseResponse.FailedResponse("Missing parameters: shopName");
        }
        List<Map<String, String>> taskMonitors = translateTaskMonitorV3RedisService.listByShopName(shopName);
        return BaseResponse.SuccessResponse(taskMonitors);
    }

    /**
     * JSON 翻译执行 Agent（Runtime Worker）
     */
    @PostMapping("/v3/runtimeJsonTranslate")
    public Map<String, Object> runtimeJsonTranslate(@RequestBody JsonRuntimeTranslateRequest request) {
        if (request == null) {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("taskId", "");
            failed.put("status", "FAILED");
            failed.put("inputBlobUri", "");
            failed.put("outputBlobUri", "");
            failed.put("reportBlobUri", "");
            failed.put("total", 0);
            failed.put("done", 0);
            failed.put("failed", 0);
            failed.put("durationMs", 0);
            return failed;
        }
        return translateV3Service.executeJsonRuntimeTask(request);
    }

    /**
     * 查看 JSON runtime 任务：Cosmos 文档、Redis 进度、checkpoint 中三个 Blob 的存在性/大小；可选返回预览（前缀读取，避免整文件下载）。
     *
     * @param taskId            Cosmos 任务 id
     * @param shopName          可选，传入则单点读取分区；不传则扫描 status 查找（较慢）
     * @param redisPrefix       可选，默认任务 checkpoint.redisPrefix，再否则 tr:v1
     * @param includeBlobPreview 为 true 时读取各 Blob 前 maxPreviewBytes 字节为 UTF-8 预览
     * @param maxPreviewBytes   预览最大字节数，默认 8192，上限 512KB
     */
    @GetMapping("/v3/jsonRuntimeTaskDetail")
    public BaseResponse<Object> jsonRuntimeTaskDetail(@RequestParam String taskId,
                                                      @RequestParam(required = false) String shopName,
                                                      @RequestParam(required = false) String redisPrefix,
                                                      @RequestParam(required = false, defaultValue = "false") boolean includeBlobPreview,
                                                      @RequestParam(required = false, defaultValue = "8192") int maxPreviewBytes) {
        return translateV3Service.getJsonRuntimeTaskDetail(taskId, shopName, redisPrefix, includeBlobPreview, maxPreviewBytes);
    }

    // 当支付成功后，调用该方法，将该用户的状态3，改为状态6
    // 支付之后，前端调用api，停止状态改为继续翻译
    @PostMapping("/updateStatusV2")
    public BaseResponse<Object> updateStatus3To6V2(@RequestParam String shopName) {
        if (translatesService.updateStatus3To6(shopName)) {
            // 付费后继续翻译：仅恢复自动停止（token limit）任务
            translateV3Service.continueAutoStoppedTranslatingByShopName(shopName);
            return new BaseResponse<>().CreateSuccessResponse(true);
        } else {
            return new BaseResponse<>().CreateErrorResponse("updateStatus3To6 error");
        }
    }

    /**
     * 插入shop翻译项信息
     */
    @PostMapping("/insertShopTranslateInfo")
    public void insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        translatesService.insertLanguageStatus(request);
    }

    /**
     * 读取所有的翻译状态信息
     */
    @GetMapping("/readTranslateInfo")
    public BaseResponse<Object> readTranslateInfo(Integer status) {
        List<TranslatesDO> list = translatesService.readTranslateInfo(status);
        if (list != null && !list.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    /**
     * 根据传入的数组获取对应的数据
     */
    @PostMapping("/readTranslateDOByArray")
    public BaseResponse<Object> readTranslateDOByArray(@RequestBody TranslatesDO[] translatesDOS) {
        if (translatesDOS != null && translatesDOS.length > 0) {
            TranslateArrayVO translateArrayVO = new TranslateArrayVO();
            TranslatesDO[] translatesDOResult = new TranslatesDO[translatesDOS.length];
            int i = 0;

            // 初始化 flag用于判断前端是否要继续请求
            for (TranslatesDO translatesDO : translatesDOS
            ) {
                translatesDOResult[i] = translatesService.readTranslateDOByArray(translatesDO);

                // 获取模块类型数据， 然后存到translatesDOResult[i]里面
                Map<String, String> progressTranslationKey = translationParametersRedisService.getProgressTranslationKey(TranslationParametersRedisService.generateProgressTranslationKey(translatesDOResult[i].getShopName(), translatesDOResult[i].getSource(), translatesDOResult[i].getTarget()));
                String translatingModule = progressTranslationKey.get("translating_module");
                if (translatingModule != null) {
                    translatesDOResult[i].setResourceType(translatingModule);
                }

                i++;
            }

            translateArrayVO.setTranslatesDOResult(translatesDOResult);
            TraceReporterHolder.report("TranslateController.readTranslateDOByArray", "readTranslateDOByArray : " + Arrays.toString(translatesDOS));
            return new BaseResponse<>().CreateSuccessResponse(translateArrayVO);
        } else {
            return new BaseResponse<>().CreateErrorResponse(DATA_IS_EMPTY);
        }
    }

    /**
     * 读取shopName的所有翻译状态信息
     */
    @GetMapping("/readInfoByShopName")
    public BaseResponse<Object> readInfoByShopName(String shopName, String source) {
        List<TranslatesDO> translatesDos = translatesService.readInfoByShopName(shopName, source);
        if (!translatesDos.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(translatesDos);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    /**
     * 将一条数据存shopify本地
     */
    @PostMapping("/insertTranslatedText")
    public void insertTranslatedText(@RequestBody CloudInsertRequest cloudServiceRequest) {
        Map<String, Object> body = cloudServiceRequest.getBody();
        String s = shopifyHttpIntegration.registerTransaction(cloudServiceRequest.getShopName(), cloudServiceRequest.getAccessToken(), body);
        TraceReporterHolder.report("TranslateController.insertTranslatedText", "insertTranslatedText 用户 ： " + cloudServiceRequest.getShopName() + " insertTranslatedText : " + s);
    }


    //删除翻译状态的语言
    @PostMapping("/deleteFromTranslates")
    public BaseResponse<Object> deleteFromTranslates(@RequestBody TranslateRequest request) {
        Boolean b = translatesService.deleteFromTranslates(request);
        TraceReporterHolder.report("TranslateController.insertTranslatedText", "deleteFromTranslates 用户删除翻译语言： " + request);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
        }
    }

    //将target以集合的形式插入到数据库中
    @PostMapping("/insertTargets")
    public void insertTargets(@RequestBody TargetListRequest request) {
        List<String> targetList = request.getTargetList();
        TranslateRequest translateRequest = TargetListRequestToTranslateRequest(request);
        if (!targetList.isEmpty()) {
            translateRequest.setTarget(targetList.get(0));
            userTypeTokensService.getUserInitToken(translateRequest);
            for (String target : targetList
            ) {
                TranslateRequest request1 = new TranslateRequest(0, request.getShopName(), request.getAccessToken(), request.getSource(), target, null);
                //插入语言状态
                translatesService.insertLanguageStatus(request1);
                //获取translates表中shopName和target对应的id
                int idByShopNameAndTarget = translateService.getIdByShopNameAndTargetAndSource(request1.getShopName(), request1.getTarget(), request1.getSource());
                //初始化用户对应token表
                userTypeTokenService.insertTypeInfo(request1, idByShopNameAndTarget);
            }
        } else {
            translateRequest.setTarget("asdf");
            userTypeTokensService.getUserInitToken(translateRequest);
        }
    }

    private TranslateRequest TargetListRequestToTranslateRequest(TargetListRequest targetListRequest) {
        TranslateRequest translateRequest = new TranslateRequest();
        translateRequest.setAccessToken(targetListRequest.getAccessToken());
        translateRequest.setShopName(targetListRequest.getShopName());
        translateRequest.setSource(targetListRequest.getSource());
        return translateRequest;
    }

    //用户是否选择定时任务的方法
    @PostMapping("/updateAutoTranslateByData")
    public BaseResponse<Object> updateStatusByShopName(@RequestBody AutoTranslateRequest request) {
        // 判断用户的语言是否在数据库中，在不做操作，不在，进行同步
        TranslatesDO one = translatesService.getOne(new QueryWrapper<TranslatesDO>().eq("shop_name", request.getShopName()).eq("source", request.getSource()).eq("target", request.getTarget()));

        // 获取用户token
        UsersDO usersDO = iUsersService.getUserByName(request.getShopName());
        if (one == null) {
            //走同步逻辑
            translateService.syncShopifyAndDatabase(request.getShopName(), usersDO.getAccessToken(), request.getSource());
        }
        return translatesService.updateAutoTranslateByShopName(request.getShopName(), request.getAutoTranslate(), request.getSource(), request.getTarget());
    }

    /**
     * 图片翻译
     */
    @PutMapping("/imageTranslate")
    public BaseResponse<Object> imageTranslate(@RequestParam String shopName, @RequestBody ImageTranslateVO imageTranslateVO) {
        String targetPic = translateService.imageTranslate(imageTranslateVO.getSourceCode(), imageTranslateVO.getTargetCode(), imageTranslateVO.getImageUrl(), shopName, imageTranslateVO.getAccessToken());

        if (targetPic != null) {
            return new BaseResponse<>().CreateSuccessResponse(targetPic);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
