package com.bogdatech.controller;

import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserTypeTokenService;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.entity.TranslationCounterDO;
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
import static com.bogdatech.entity.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.logic.TranslateService.SINGLE_LINE_TEXT;
import static com.bogdatech.utils.TypeConversionUtils.*;

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


    /*
     * 插入shop翻译项信息
     */
    @PostMapping("/insertShopTranslateInfo")
    public void insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        translateService.insertLanguageStatus(request);
    }

    /*
     * 调用谷歌翻译的API接口
     */
    @PostMapping("/googleTranslate")
    public String googleTranslate(@RequestBody TranslateRequest request) {
        return translateService.googleTranslate(request);

    }


    /*
     * 调用百度翻译的API接口
     */
    @PostMapping("/baiDuTranslate")
    public BaseResponse<Object> baiDuTranslate(@RequestBody TranslateRequest request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.baiDuTranslate(request));
    }

    /*
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

    /*
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

    /*
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

    /*
     *  通过TranslateResourceDTO获取定义好的数组，对其进行for循环，遍历获得query，通过发送shopify的API获得数据，获得数据后再通过百度翻译API翻译数据
     */
    @PutMapping("/clickTranslation")
    public BaseResponse<Object> clickTranslation(@RequestBody ClickTranslateRequest clickTranslateRequest) {

        //将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);

        //判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(request.getShopName());
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(request.getShopName());

        //经翻译的语言数量，超过 2 种，则返回
        if (translatesService.getLanguageListCounter(request.getShopName()).size() > 2) {
            return new BaseResponse<>().CreateErrorResponse(DATA_IS_LIMIT);
        }
//        一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(request);
        for (Integer integer : integers) {
            if (integer == 2) {
                return new BaseResponse<>().CreateSuccessResponse(HAS_TRANSLATED);
            }
        }

        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }
        //通过判断status和字符判断后 就将状态改为2，则开始翻译流程
        translatesService.updateTranslateStatus(request.getShopName(), 2, request.getTarget(), request.getSource(), request.getAccessToken());
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
//      翻译
        translateService.startTranslation(request, remainingChars, counter, usedChars);
        return new BaseResponse<>().CreateSuccessResponse(clickTranslateRequest);
    }

    //暂停翻译
    @DeleteMapping("/stop")
    public void stop(String shopName) {
        translateService.stopTranslation(shopName);
    }

    /*
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

    /*
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
        if (!targetList.isEmpty()){
            translateRequest.setTarget(targetList.get(0));
            userTypeTokensService.getUserInitToken(translateRequest);
            for (String target : targetList
            ) {
                TranslateRequest request1 = new TranslateRequest(0, request.getShopName(), request.getAccessToken(), request.getSource(), target, null);
                //插入语言状态
                translateService.insertLanguageStatus(request1);
                //获取translates表中shopName和target对应的id
                int idByShopNameAndTarget = translateService.getIdByShopNameAndTargetAndSource(request1.getShopName(), request1.getTarget(), request1.getSource());
                //初始化用户对应token表
                userTypeTokenService.insertTypeInfo(request1, idByShopNameAndTarget);
            }
        }else {
            translateRequest.setTarget("zh-CN");
            userTypeTokensService.getUserInitToken(translateRequest);
            for (String target : targetList
            ) {
                TranslateRequest request1 = new TranslateRequest(0, request.getShopName(), request.getAccessToken(), request.getSource(), target, null);
                //插入语言状态
                translateService.insertLanguageStatus(request1);
                //获取translates表中shopName和target对应的id
                int idByShopNameAndTarget = translateService.getIdByShopNameAndTargetAndSource(request1.getShopName(), request1.getTarget(), request1.getSource());
                //初始化用户对应token表
                userTypeTokenService.insertTypeInfo(request1, idByShopNameAndTarget);
            }
        }


    }

    //异步调用startTokenCount方法获取所有的数据信息
    @PostMapping("/startTokenCount")
    public BaseResponse<Object> startTokenCount(@RequestBody TranslateRequest request) {
        //获取translates表中shopName和target对应的id
        int idByShopNameAndTarget = translateService.getIdByShopNameAndTargetAndSource(request.getShopName(), request.getTarget(), request.getSource());
        //开始token的计数各个类型的token计数
        //判断数据库中UserTypeToken中translationId对应的status是什么 如果是2，则不获取token；如果是除2以外的其他值，获取token
        Integer status = userTypeTokenService.getStatusByTranslationId(idByShopNameAndTarget);
        if (status != 2) {
            //TODO: 这只是大致流程，还需要做异常处理
            //将UserTypeToken的status修改为2
            userTypeTokenService.updateStatusByTranslationIdAndStatus(idByShopNameAndTarget, 2);
            ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
            //循环type获取token
            for (String key : TOKEN_MAP.keySet()
            ) {
                translateService.updateStatusByTranslation(shopifyRequest, key, idByShopNameAndTarget, "token");
            }
            //token全部获取完之后修改，UserTypeToken的status==1
            userTypeTokenService.updateStatusByTranslationIdAndStatus(idByShopNameAndTarget, 1);

        }
        return null;
    }
}
