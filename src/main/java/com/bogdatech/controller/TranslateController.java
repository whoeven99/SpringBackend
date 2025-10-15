package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserTypeTokenService;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.VO.ImageTranslateVO;
import com.bogdatech.entity.VO.SingleTranslateVO;
import com.bogdatech.entity.VO.TranslateArrayVO;
import com.bogdatech.entity.VO.TranslatingStopVO;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.logic.UserTypeTokenService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.controller.response.ProgressResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.integration.ShopifyHttpIntegration.registerTransaction;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;
import static com.bogdatech.utils.TypeConversionUtils.*;

@RestController
@RequestMapping("/translate")
public class TranslateController {
    @Autowired
    private TranslateService translateService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IUserTypeTokenService userTypeTokenService;
    @Autowired
    private UserTypeTokenService userTypeTokensService;
    @Autowired
    private RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private ITranslateTasksService iTranslateTasksService;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;

    // 创建手动翻译任务
    @PutMapping("/clickTranslation")
    public BaseResponse<Object> clickTranslation(@RequestParam String shopName, @RequestBody ClickTranslateRequest request) {
        return translateService.createInitialTask(request);
    }

    /**
     * 插入shop翻译项信息
     */
    @PostMapping("/insertShopTranslateInfo")
    public void insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        translatesService.insertLanguageStatus(request);
    }

    /**
     * 调用谷歌翻译的API接口
     */
    @PostMapping("/googleTranslate")
    public String googleTranslate(@RequestBody TranslateRequest request) {
        return translateService.googleTranslate(request);
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
    // TODO delete
    @PostMapping("/readTranslateDOByArray")
    public BaseResponse<Object> readTranslateDOByArray(@RequestBody TranslatesDO[] translatesDOS) {
        if (translatesDOS != null && translatesDOS.length > 0) {
            TranslateArrayVO translateArrayVO = new TranslateArrayVO();
            TranslatesDO[] translatesDOResult = new TranslatesDO[translatesDOS.length];
            int i = 0;
            // 初始化 flag用于判断前端是否要继续请求
            boolean flag = false;
            for (TranslatesDO translatesDO : translatesDOS
            ) {
                translatesDOResult[i] = translatesService.readTranslateDOByArray(translatesDO);

                // 获取模块类型数据， 然后存到translatesDOResult[i]里面
                Map<Object, Object> progressTranslationKey = translationParametersRedisService.getProgressTranslationKey(generateProgressTranslationKey(translatesDOResult[i].getShopName(), translatesDOResult[i].getSource(), translatesDOResult[i].getTarget()));
                Object translatingModule = progressTranslationKey.get("translating_module");
                if (translatingModule != null) {
                    translatesDOResult[i].setResourceType((String) translatingModule);
                }

                i++;
            }

            // 判断任务表里面是否存在该任务，存在将flag改为true
            List<TranslateTasksDO> list = iTranslateTasksService.list(new LambdaQueryWrapper<TranslateTasksDO>().eq(TranslateTasksDO::getShopName, translatesDOS[0].getShopName()).in(TranslateTasksDO::getStatus, 0, 2));
            if (!list.isEmpty()) {
                flag = true;
            }
            translateArrayVO.setTranslatesDOResult(translatesDOResult);
            translateArrayVO.setFlag(flag);
            appInsights.trackTrace("readTranslateDOByArray : " + Arrays.toString(translatesDOS));
            return new BaseResponse<>().CreateSuccessResponse(translateArrayVO);
        } else {
            return new BaseResponse<>().CreateErrorResponse(DATA_IS_EMPTY);
        }
    }

    /**
     * 根据传入的shopName和source，返回一个最新时间的翻译项数据，status为1-3
     */
    // TODO delete
    @PostMapping("/getTranslateDOByShopNameAndSource")
    public BaseResponse<Object> getTranslateDOByShopNameAndSource(@RequestBody TranslateRequest request) {
        if (request != null) {
            // 改为返回状态为2的所有相关数据
            List<TranslatesDO> list = translatesService.list(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, request.getShopName()).eq(TranslatesDO::getSource, request.getSource()).eq(TranslatesDO::getStatus, 2).orderByAsc(TranslatesDO::getUpdateAt));
            appInsights.trackTrace("getTranslateDOByShopNameAndSource 获取到的相关数据： " + list.toString());
            if (list.isEmpty()) {
                TranslatesDO translatesDO = translatesService.selectLatestOne(request);
                translatesDO.setAccessToken(null);
                list.add(translatesDO);
                return new BaseResponse<>().CreateSuccessResponse(list);
            }
            return new BaseResponse<>().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse(DATA_IS_EMPTY);
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

    //暂停翻译
    @DeleteMapping("/stop")
    public void stop(@RequestParam String shopName) {
        translateService.stopTranslationManually(shopName);
    }


    //翻译单个文本数据
    @PostMapping("/translateSingleText")
    public BaseResponse<Object> translateSingleText(@RequestBody RegisterTransactionRequest request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.translateSingleText(request));
    }

    //手动停止用户的翻译任务
    @PutMapping("/stopTranslation")
    public String stopTranslation(@RequestBody TranslateRequest request) {
        return translateService.stopTranslationManually(request.getShopName());
    }

    /**
     * 将一条数据存shopify本地
     */
    @PostMapping("/insertTranslatedText")
    public void insertTranslatedText(@RequestBody CloudInsertRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        Map<String, Object> body = cloudServiceRequest.getBody();
        String s = registerTransaction(request, body);
        appInsights.trackTrace("insertTranslatedText 用户 ： " + cloudServiceRequest.getShopName() + " insertTranslatedText : " + s);
    }


    //删除翻译状态的语言
    @PostMapping("/deleteFromTranslates")
    public BaseResponse<Object> deleteFromTranslates(@RequestBody TranslateRequest request) {
        Boolean b = translatesService.deleteFromTranslates(request);
        appInsights.trackTrace("deleteFromTranslates 用户删除翻译语言： " + request);
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

    //当支付成功后，调用该方法，将该用户的状态3，改为状态6
    @PostMapping("/updateStatus")
    public void updateStatus3To6(@RequestBody TranslateRequest request) {
        translatesService.updateStatus3To6(request.getShopName());
    }

    //用户是否选择定时任务的方法
    @PostMapping("/updateAutoTranslateByData")
    public BaseResponse<Object> updateStatusByShopName(@RequestBody AutoTranslateRequest request) {

        //判断用户的语言是否在数据库中，在不做操作，不在，进行同步
        TranslatesDO one = translatesService.getOne(new QueryWrapper<TranslatesDO>().eq("shop_name", request.getShopName()).eq("source", request.getSource()).eq("target", request.getTarget()));
        if (one == null) {
            //走同步逻辑
            translateService.syncShopifyAndDatabase(new TranslateRequest(0, request.getShopName(), null, request.getSource(), request.getTarget(), null));
        }
        return translatesService.updateAutoTranslateByShopName(request.getShopName(), request.getAutoTranslate(), request.getSource(), request.getTarget());
    }

    //单条文本翻译
    @PostMapping("/singleTextTranslate")
    public BaseResponse<Object> singleTextTranslate(@RequestBody SingleTranslateVO singleTranslateVO) {
        return translateService.singleTextTranslate(singleTranslateVO);
    }

    /**
     * 获取当前用户的value值
     */
    @GetMapping("/getUserValue")
    public BaseResponse<Object> getUserValue(@RequestParam String shopName) {
        // TODO: 改多进度条展示的话，这个就没有用了， 这个目的是用来是测试的
        // 获取该用户正在翻译语言
        Map<String, Object> userTranslate = new HashMap<>();
        List<TranslatesDO> list = translatesService.list(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getStatus, 2));
        if (list.isEmpty()) {
            userTranslate.put("value", "There is currently no language being translated");
            userTranslate.put("status", "4");
        }

        TranslatesDO firstTranslatesDO = list.get(0);

        Map<Object, Object> progressTranslationKey = translationParametersRedisService.getProgressTranslationKey(generateProgressTranslationKey(firstTranslatesDO.getShopName(), firstTranslatesDO.getSource(), firstTranslatesDO.getTarget()));
        appInsights.trackTrace("getUserValue translation_status :  " + progressTranslationKey);
        userTranslate.put("value", progressTranslationKey.get("translating_string"));
        userTranslate.put("status", progressTranslationKey.get("translation_status"));

        return new BaseResponse<>().CreateSuccessResponse(userTranslate);
    }

    /**
     * 停止翻译按钮
     * 将所有状态0和状态2的任务改成7
     */
    @PutMapping("/stopTranslatingTask")
    public BaseResponse<Object> stopTranslatingTask(@RequestParam String shopName, @RequestBody TranslatingStopVO translatingStopVO) {
        Boolean stopFlag = translationParametersRedisService.setStopTranslationKey(shopName);
        if (!stopFlag) {
           return new BaseResponse<>().CreateErrorResponse("stopTranslatingTask 已经有停止标识， 需要删除后再次停止");
        }

        // 获取所有的status为2的target
        List<TranslatesDO> list = translatesService.list(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getStatus, 2).eq(TranslatesDO::getSource, translatingStopVO.getSource()).orderByAsc(TranslatesDO::getUpdateAt));

        // 将所有状态2的任务改成7
        translatesService.updateStopStatus(shopName, translatingStopVO.getSource());

        // 将所有状态为0和2的task任务，改为7
        Boolean flag = iTranslateTasksService.updateStatus0And2To7(shopName);
        if (flag) {
            // 将redis进度条 total 和 done的数据初始化为0
            for (TranslatesDO translatesDO : list
            ) {
                redisProcessService.initProcessData(generateProcessKey(shopName, translatesDO.getTarget()));
            }
            appInsights.trackTrace("stopTranslatingTask " + shopName + " 停止成功");
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 用于获取进度条的相关数据
     */
    @PostMapping("/getProgressData")
    public BaseResponse<Object> getProgressData(@RequestParam String shopName, @RequestParam String target, @RequestParam String source) {
        Map<String, Integer> progressData = translateService.getProgressData(shopName, target, source);
        appInsights.trackTrace("getProgressData " + shopName + " target : " + target + " " + source + " " + progressData);
        if (progressData != null) {
            return new BaseResponse<>().CreateSuccessResponse(progressData);
        }

        return new BaseResponse<>().CreateErrorResponse(false);
    }

    @PostMapping("/getAllProgressData")
    public BaseResponse<ProgressResponse> getAllProgressData(@RequestParam String shopName, @RequestParam String source) {
        List<InitialTranslateTasksDO> initialTranslateTasksDOS = initialTranslateTasksMapper.selectList(new LambdaQueryWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getShopName, shopName).eq(InitialTranslateTasksDO::getSource, source).eq(InitialTranslateTasksDO::isDeleted, false).orderByAsc(InitialTranslateTasksDO::getCreatedAt));

        // 获取所有的TranslatesDO
        ProgressResponse response = new ProgressResponse();
        List<ProgressResponse.Progress> list = new ArrayList<>();
        response.setList(list);
        if (initialTranslateTasksDOS.isEmpty()) {
            return new BaseResponse<ProgressResponse>().CreateSuccessResponse(response);
        }

        for (InitialTranslateTasksDO initialTranslateTasksDO : initialTranslateTasksDOS) {
            // 获取对应Translates表里面 对应语言的status
            TranslatesDO translatesDO = translatesService.getOne(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getTarget, initialTranslateTasksDO.getTarget()).eq(TranslatesDO::getSource, source));

            // 不返回状态为0的数据
            if (translatesDO.getStatus() == 0) {
                continue;
            }

            ProgressResponse.Progress progress = new ProgressResponse.Progress();
            progress.setStatus(translatesDO.getStatus());
            progress.setTarget(translatesDO.getTarget());

            Map<String, String> map = translationParametersRedisService.hgetAll(generateProgressTranslationKey(shopName, source, translatesDO.getTarget()));
            progress.setResourceType(map.get(TranslationParametersRedisService.TRANSLATING_MODULE));
            progress.setValue(map.get(TranslationParametersRedisService.TRANSLATING_STRING));
            progress.setTranslateStatus("3".equals(map.get(TranslationParametersRedisService.TRANSLATION_STATUS)) ? "translation_process_saving_shopify"
                    : "2".equals(map.get(TranslationParametersRedisService.TRANSLATION_STATUS)) ? "translation_process_translating"
                    : "translation_process_init");

            // 进度条数字
            Map<String, Integer> progressData = translateService.getProgressData(shopName, translatesDO.getTarget(), source);
            appInsights.trackTrace("getAllProgressData " + shopName + " target : " + translatesDO.getTarget() + " " + source + " " + progressData);
            progress.setProgressData(progressData);

            list.add(progress);
        }
        return new BaseResponse<ProgressResponse>().CreateSuccessResponse(response);
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
