package com.bogdatech.controller;

import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.JdbcRepository;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @PostMapping("/translate")
    public BaseResponse translate(@RequestBody TranslatesDO request) {
        return translateService.translate(request);
    }

    /*
     * 插入shop信息
     */
    @PostMapping("/translate/insertShopTranslateInfo")
    public BaseResponse insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        int result = jdbcRepository.insertShopTranslateInfo(request);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    /*
     * 调用谷歌翻译的API接口
     */
    @PostMapping("/translate/googleTranslate")
    public BaseResponse googleTranslate(@RequestBody TranslateRequest request) {
        return translateService.googleTranslate(request);
    }

    /*
     * 调用百度翻译的API接口
     */
    @PostMapping("/translate/baiDuTranslate")
    public BaseResponse baiDuTranslate(@RequestBody TranslateRequest request) {
        return translateService.baiDuTranslate(request);
    }

    /*
     * 读取所有的翻译状态信息
     */
    @PostMapping("/translate/readTranslateInfo")
    public BaseResponse readTranslateInfo(@RequestBody TranslatesDO request) {
        List<TranslatesDO> list = jdbcRepository.readTranslateInfo(request.getStatus());
        if (list != null && list.size() > 0) {
            return new BaseResponse().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    /*
     * 读取shopName的所有翻译状态信息
     */
    @PostMapping("/translate/readInfoByShopName")
    public BaseResponse readInfoByShopName(@RequestBody TranslateRequest request) {
        List<TranslatesDO> translatesDOS = jdbcRepository.readInfoByShopName(request);
        if (translatesDOS.size() > 0) {
            return new BaseResponse().CreateSuccessResponse(translatesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    /*
     * 用百度翻译API翻译json格式的数据
     */
    @PostMapping("/translate/userBDTranslateJson")
    public BaseResponse userBDTranslateJsonObject() {
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
//        //存数据库
//        translateService.saveTranslateText(request);
        return 2;
    }

    /*
     *  测试一键存储数据库流程
     */
    @GetMapping("/translate/saveTranslateText")
    public void saveTranslateText(@RequestBody TranslateRequest request) {
        translateService.saveTranslateText(request);
    }

    //测试String格式数据获取是否正常
}
