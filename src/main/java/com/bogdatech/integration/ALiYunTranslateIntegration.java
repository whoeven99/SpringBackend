package com.bogdatech.integration;

import com.aliyun.alimt20181012.models.TranslateGeneralResponse;
import com.aliyun.tea.TeaException;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.ApiCodeUtils;
import org.springframework.stereotype.Component;

@Component
public class ALiYunTranslateIntegration {


    public com.aliyun.alimt20181012.Client createClient() {
        // 工程代码泄露可能会导致 AccessKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考。
        // 建议使用更安全的 STS 方式，更多鉴权访问方式请参见：https://help.aliyun.com/document_detail/378657.html。
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                // 必填，请确保代码运行环境设置了环境变量 ALIBABA_CLOUD_ACCESS_KEY_ID。
                .setAccessKeyId(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"))
                // 必填，请确保代码运行环境设置了环境变量 ALIBABA_CLOUD_ACCESS_KEY_SECRET。
                .setAccessKeySecret(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"));
        // Endpoint 请参考 https://api.aliyun.com/product/alimt
        config.endpoint = "mt.cn-hangzhou.aliyuncs.com";
        try {
            return new com.aliyun.alimt20181012.Client(config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String aliyunTranslate(TranslateRequest translateRequest) {
        //将传入的target转为阿里云要用的target
        String aliTarget = ApiCodeUtils.aliyunTransformCode(translateRequest.getTarget());
        if (aliTarget.equals("#N/A")) {
            return "Alibaba Cloud does not support this language";
        }
        com.aliyun.alimt20181012.Client client = createClient();
        com.aliyun.alimt20181012.models.TranslateGeneralRequest translateGeneralRequest = new com.aliyun.alimt20181012.models.TranslateGeneralRequest()
                .setFormatType("text")
                .setSourceLanguage(translateRequest.getSource())
                .setTargetLanguage(aliTarget)
                .setSourceText(translateRequest.getContent())
                .setScene("general")
//                .setContext("")
                ;
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        try {
            // 复制代码运行请自行打印 API 的返回值
            TranslateGeneralResponse translateGeneralResponse = client.translateGeneralWithOptions(translateGeneralRequest, runtime);
//            System.out.println("translateGeneralResponse: " + translateGeneralResponse.getBody().getData().translated);
            return translateGeneralResponse.getBody().getData().translated;
        } catch (TeaException error) {
            // 此处仅做打印展示，请谨慎对待异常处理，在工程项目中切勿直接忽略异常。
            // 错误 message
//            System.out.println(error.getMessage());
//            // 诊断地址
//            System.out.println(error.getData().get("Recommend"));
            com.aliyun.teautil.Common.assertAsString(error.message);
            throw new RuntimeException(error);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 此处仅做打印展示，请谨慎对待异常处理，在工程项目中切勿直接忽略异常。
            // 错误 message
//            System.out.println(error.getMessage());
//            // 诊断地址
//            System.out.println(error.getData().get("Recommend"));
            com.aliyun.teautil.Common.assertAsString(error.message);
            throw new RuntimeException(error);
        }
    }
}
