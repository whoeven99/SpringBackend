package com.bogdatech.controller;

import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.integration.TranslateApiIntegration;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.CloudInsertRequest;
import com.bogdatech.model.controller.request.RegisterTransactionRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.JsoupUtils;
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
    private TelemetryClient appInsights = new TelemetryClient();

    @Autowired
    private JsoupUtils jsoupUtils;
    /*
     * 插入shop信息
     */
    @PostMapping("/translate/insertShopTranslateInfo")
    public BaseResponse<Object> insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        Integer status = (translatesService.readStatus(request));
        if (status != null ) {
            return new BaseResponse<>().CreateErrorResponse(DATA_EXIST);
        } else {
            Integer result = translatesService.insertShopTranslateInfo(request, 0);
            if (result > 0) {
                return new BaseResponse<>().CreateSuccessResponse("Created language");
            }
            return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
        }
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
        //翻译
        translateService.translating(request);
        //返回一个status值
//        int i = translatesService.getStatusInTranslatesByShopName(request);
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
     *  测试存shopify本地
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


    @PostMapping("/test")
    public boolean test(@RequestBody TranslateRequest request) {
       return jsoupUtils.isHtml(request.getContent());
//        return translateService.translateHtmlText(request);
    }

    //测试微软翻译API
    @PostMapping("/testAzure")
    public String testAzure(@RequestBody TranslateRequest request) {
        return translateApiIntegration.microsoftTranslate(request);
    }
}
