package com.bogdatech.controller;

import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.entity.TranslationCounterDO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.StringUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.bogdatech.enums.ErrorEnum.*;

@RestController
public class TranslateController {

    @Autowired
    private TranslateService translateService;

    @Autowired
    private ITranslatesService translatesService;

    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    @Autowired
    TranslateApiIntegration translateApiIntegration;

    @Autowired
    ITranslationCounterService translationCounterService;

    @Autowired
    ALiYunTranslateIntegration aliyunTranslateIntegration;
    private TelemetryClient appInsights = new TelemetryClient();

    @Autowired
    private JsoupUtils jsoupUtils;

    /*
     * 插入shop翻译项信息
     */
    @PostMapping("/translate/insertShopTranslateInfo")
    public void insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        translateService.insertLanguageStatus(request);
//        Integer status = translatesService.readStatus(request);
//        if (status != null) {
//            return new BaseResponse<>().CreateErrorResponse(DATA_EXIST);
//        } else {
//            Integer result = translatesService.insertShopTranslateInfo(request, 0);
//            if (result > 0) {
//                return new BaseResponse<>().CreateSuccessResponse("Created language");
//            }
//            return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
//        }
//        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    /*
     * 调用谷歌翻译的API接口
     */
    @PostMapping("/translate/googleTranslate")
    public String googleTranslate(@RequestBody TranslateRequest request) {
        return translateService.googleTranslate(request);
    }

    /*
     * 调用百度翻译的API接口
     */
    @PostMapping("/translate/baiDuTranslate")
    public BaseResponse<Object> baiDuTranslate(@RequestBody TranslateRequest request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.baiDuTranslate(request));
    }

    /*
     * 读取所有的翻译状态信息
     */
    @PostMapping("/translate/readTranslateInfo")
    public BaseResponse<Object> readTranslateInfo(@RequestBody TranslatesDO request) {
        List<TranslatesDO> list = translatesService.readTranslateInfo(request.getStatus());
        if (list != null && !list.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    /*
     * 根据传入的数组获取对应的数据
     */
    @PostMapping("/translate/readTranslateDOByArray")
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
    @PostMapping("/translate/readInfoByShopName")
    public BaseResponse<Object> readInfoByShopName(@RequestBody TranslateRequest request) {
        List<TranslatesDO> translatesDos = translatesService.readInfoByShopName(request);
        if (!translatesDos.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(translatesDos);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    /*
     * 用百度翻译API翻译json格式的数据
     */
    @PostMapping("/translate/userBDTranslateJson")
    public BaseResponse<Object> userBDTranslateJsonObject() {
        return translateService.userBDTranslateJsonObject();
    }

    /*
     *  通过TranslateResourceDTO获取定义好的数组，对其进行for循环，遍历获得query，通过发送shopify的API获得数据，获得数据后再通过百度翻译API翻译数据
     */
    @PostMapping("/translate/clickTranslation")
    public BaseResponse<Object> clickTranslation(@RequestBody TranslateRequest request) {

        //判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, 0, 0, 0, 0));
        int remainingChars = translationCounterService.getMaxCharsByShopName(request.getShopName());
        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse("Character Limit Reached");
        }
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);

        //翻译
        try {
            translateService.translating(request, remainingChars, counter);
        } catch (Exception e) {
            translatesService.updateTranslateStatus(request.getShopName(), 3, request.getTarget(), request.getSource(), request.getAccessToken());
            translationCounterService.updateUsedCharsByShopName(new TranslationCounterRequest(0, request.getShopName(), 0, counter.getTotalChars(), 0, 0, 0));
            throw e;
        }

        return new BaseResponse<>().CreateSuccessResponse(SERVER_SUCCESS);
    }

    /*
     *  一键存储数据库流程
     */
    @PostMapping("/translate/saveTranslateText")
    public void saveTranslateText(@RequestBody TranslateRequest request) {
        translateService.saveTranslateText(request);
    }

    //翻译单个文本数据
    @PostMapping("/translate/translateSingleText")
    public BaseResponse<Object> translateSingleText(@RequestBody RegisterTransactionRequest request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.translateSingleText(request));
    }

    /*
     *  将一条数据存shopify本地
     */
    @PostMapping("/translate/insertTranslatedText")
    public void insertTranslatedText(@RequestBody CloudInsertRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        Map<String, Object> body = cloudServiceRequest.getBody();
        shopifyApiIntegration.registerTransaction(request, body);
    }


    //测试将html语句拆解
    @PostMapping("/test")
    public boolean test(@RequestBody TranslateRequest request) {
        return jsoupUtils.isHtml(request.getContent());
//        return translateService.translateHtmlText(request);
    }

    //微软翻译API
    @PostMapping("/testAzure")
    public String testAzure(@RequestBody TranslateRequest request) {
        return translateApiIntegration.microsoftTranslate(request);
    }

    //对文本进行处理分解为单个单词
    @PostMapping("/split")
    public void splitWords(@RequestBody String sentence) {
        String string = StringUtils.judgeStringType(sentence);
        System.out.println("打印的结果： " + string);
    }

    //阿里云翻译API
    @PostMapping("/testAli")
    public String testAli(@RequestBody TranslateRequest request) {
        return aliyunTranslateIntegration.aliyunTranslate(request);
    }

    //删除翻译状态的语言
    @PostMapping("/translate/deleteFromTranslates")
    public BaseResponse<Object> deleteFromTranslates(@RequestBody TranslateRequest request) {
        Boolean b = translatesService.deleteFromTranslates(request);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
        }
    }

    //火山翻译API
    @PostMapping("/testVolcano")
    public String testVolcano(@RequestBody TranslateRequest request) {
        return translateApiIntegration.huoShanTranslate(request);
    }

    //封装谷歌谷歌翻译
    @PostMapping("/testGoogle")
    public String testGoogle(@RequestBody TranslateRequest request) {
        String googleTranslateData = translateService.getGoogleTranslateData(new TranslateRequest(0, null, null, request.getSource(), request.getTarget(), request.getContent()));
        return googleTranslateData;
    }
}
