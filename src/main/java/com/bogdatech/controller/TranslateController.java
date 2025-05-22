package com.bogdatech.controller;

import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserTypeTokenService;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.VO.SingleTranslateVO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.logic.UserTypeTokenService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.HAS_TRANSLATED;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.ModelUtils.translateModel;
import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;
import static com.bogdatech.utils.TypeConversionUtils.TargetListRequestToTranslateRequest;

@RestController
@RequestMapping("/translate")
public class TranslateController {
    private final TranslateService translateService;
    private final ITranslatesService translatesService;
    private final ShopifyHttpIntegration shopifyApiIntegration;
    private final ITranslationCounterService translationCounterService;
    private final IUserTypeTokenService userTypeTokenService;
    private final UserTypeTokenService userTypeTokensService;

    @Autowired
    public TranslateController(
            TranslateService translateService,
            ITranslatesService translatesService,
            ShopifyHttpIntegration shopifyApiIntegration,
            ITranslationCounterService translationCounterService,
            IUserTypeTokenService userTypeTokenService, UserTypeTokenService userTypeTokensService) {
        this.translateService = translateService;
        this.translatesService = translatesService;
        this.shopifyApiIntegration = shopifyApiIntegration;
        this.translationCounterService = translationCounterService;
        this.userTypeTokenService = userTypeTokenService;
        this.userTypeTokensService = userTypeTokensService;
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
     * 调用百度翻译的API接口
     */
    @PostMapping("/baiDuTranslate")
    public BaseResponse<Object> baiDuTranslate(@RequestBody TranslateRequest request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.baiDuTranslate(request));
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
            TranslatesDO[] translatesDOResult = new TranslatesDO[translatesDOS.length];
            int i = 0;
            for (TranslatesDO translatesDO : translatesDOS
            ) {
                translatesDOResult[i] = translatesService.readTranslateDOByArray(translatesDO);
                i++;
            }
            return new BaseResponse<>().CreateSuccessResponse(translatesDOResult);
        } else {
            return new BaseResponse<>().CreateErrorResponse(DATA_IS_EMPTY);
        }
    }

    /**
     * 根据传入的shopName和source，返回一个最新时间的翻译项数据，status为1-3
     */
    @PostMapping("/getTranslateDOByShopNameAndSource")
    public BaseResponse<Object> getTranslateDOByShopNameAndSource(@RequestBody TranslateRequest request) {
        if (request != null) {
            TranslatesDO translatesDO = translatesService.selectLatestOne(request);
            return new BaseResponse<>().CreateSuccessResponse(translatesDO);
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

    /**
     *  通过TranslateResourceDTO获取定义好的数组，对其进行for循环，遍历获得query，通过发送shopify的API获得数据，获得数据后再通过百度翻译API翻译数据
     */
    @PutMapping("/clickTranslation")
    public BaseResponse<Object> clickTranslation(@RequestBody ClickTranslateRequest clickTranslateRequest) {
        //判断前端传的数据是否完整，如果不完整，报错
        if (clickTranslateRequest.getShopName() == null || clickTranslateRequest.getShopName().isEmpty()
                || clickTranslateRequest.getAccessToken() == null || clickTranslateRequest.getAccessToken().isEmpty()
                || clickTranslateRequest.getSource() == null || clickTranslateRequest.getSource().isEmpty()
                || clickTranslateRequest.getTarget() == null || clickTranslateRequest.getTarget().isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("Missing parameters");
        }
        //将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);

        //判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(request.getShopName());
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(request.getShopName());

//        一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(request.getShopName());
        for (Integer integer : integers) {
            if (integer == 2) {
                return new BaseResponse<>().CreateErrorResponse(HAS_TRANSLATED);
            }
        }

        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }

        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);

        //修改模块的排序
        List<String> translateResourceDTOS = null;
        try {
            translateResourceDTOS = translateModel(clickTranslateRequest.getTranslateSettings3());
        } catch (Exception e) {
            appInsights.trackTrace("translateModel error: " + e.getMessage());
        }
//      翻译
        if (translateResourceDTOS == null || translateResourceDTOS.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse(clickTranslateRequest);
        }
        //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
        translatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource(), request.getAccessToken());
        translateService.startTranslation(request, remainingChars, counter, usedChars, false, translateResourceDTOS);
        translateService.startTranslation(request, remainingChars, counter, usedChars, false, translateResourceDTOS, clickTranslateRequest.getTranslateSettings2());//translateSettings2 是语言包,值为1，2，3
        return new BaseResponse<>().CreateSuccessResponse(clickTranslateRequest);
    }

    //暂停翻译
    @DeleteMapping("/stop")
    public void stop(String shopName) {
        translateService.stopTranslation(shopName);
    }

    /**
     *  一键存储数据库流程
     */
    @PostMapping("/saveTranslateText")
    public void saveTranslateText(@RequestBody TranslateRequest request) {
        translateService.saveTranslateText(request);
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
     *  将一条数据存shopify本地
     */
    @PostMapping("/insertTranslatedText")
    public void insertTranslatedText(@RequestBody CloudInsertRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        Map<String, Object> body = cloudServiceRequest.getBody();
        shopifyApiIntegration.registerTransaction(request, body);
    }


    //删除翻译状态的语言
    @PostMapping("/deleteFromTranslates")
    public BaseResponse<Object> deleteFromTranslates(@RequestBody TranslateRequest request) {
        Boolean b = translatesService.deleteFromTranslates(request);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
        }
    }

    //封装谷歌谷歌翻译
    @PostMapping("/testGoogle")
    public String testGoogle(@RequestBody TranslateRequest request) {
        return translateService.getGoogleTranslateData(new TranslateRequest(0, null, null, request.getSource(), request.getTarget(), request.getContent()));
    }

    //将缓存的数据存到数据库中
    @PostMapping("/saveCacheToTranslates")
    public String saveToTranslates() {
        translateService.saveToTranslates();
        return SINGLE_LINE_TEXT.toString();
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
        return translatesService.updateAutoTranslateByShopName(request.getShopName(), request.getAutoTranslate(),request.getSource(), request.getTarget());
    }

    //单条文本翻译
    @PostMapping("/singleTextTranslate")
    public BaseResponse<Object> singleTextTranslate(@RequestBody SingleTranslateVO singleTranslateVO) {
        return translateService.singleTextTranslate(singleTranslateVO);
    }

}
