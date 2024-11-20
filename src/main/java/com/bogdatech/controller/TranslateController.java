package com.bogdatech.controller;

import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.CloudInsertRequest;
import com.bogdatech.model.controller.request.RegisterTransactionRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import com.bogdatech.utils.JsoupUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_SELECT_ERROR;

@RestController

public class TranslateController {

    @Autowired
    private TranslateService translateService;

    @Autowired
    private JdbcRepository jdbcRepository;

    @Autowired
    private ShopifyHttpIntegration shopifyApiIntegration;

    private TelemetryClient appInsights = new TelemetryClient();

    @Autowired
    private JsoupUtils jsoupUtils;
    /*
     * 插入shop信息
     */
    @PostMapping("/translate/insertShopTranslateInfo")
    public BaseResponse<Object> insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        int result = jdbcRepository.insertShopTranslateInfo(request);
        if (result > 0) {
            return new BaseResponse<>().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
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
        List<TranslatesDO> list = jdbcRepository.readTranslateInfo(request.getStatus());
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
        List<TranslatesDO> translatesDos = jdbcRepository.readInfoByShopName(request);
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
    public int clickTranslation(@RequestBody TranslateRequest request) {
        //翻译
        translateService.translating(request);
        //返回一个status值
        int i = Integer.parseInt(jdbcRepository.readStatus(request));
//        //存数据库
//        translateService.saveTranslateText(request);
        return 1;
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

}
