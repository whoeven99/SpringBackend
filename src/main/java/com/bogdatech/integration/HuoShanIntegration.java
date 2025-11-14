package com.bogdatech.integration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.ApiCodeUtils;
import com.volcengine.model.request.translate.TranslateImageRequest;
import com.volcengine.model.request.translate.TranslateTextRequest;
import com.volcengine.model.response.translate.TranslateImageResponse;
import com.volcengine.model.response.translate.TranslateTextResponse;
import com.volcengine.service.translate.ITranslateService;
import com.volcengine.service.translate.impl.TranslateServiceImpl;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class HuoShanIntegration {

    //火山翻译API
    public String huoShanTranslate(TranslateRequest request) {
        ITranslateService translateService = TranslateServiceImpl.getInstance();

        translateService.setAccessKey(System.getenv("HUOSHAN_API_KEY"));
        translateService.setSecretKey(System.getenv("HUOSHAN_API_SECRET"));

        //对火山翻译API的语言进行处理
        String huoShanTarget = ApiCodeUtils.huoShanTransformCode(request.getTarget());
//        appInsights.trackTrace("huoShanTarget: " + huoShanTarget);
        // translate text
        TranslateTextResponse translateText = null;
        String translation = null;
        try {
            TranslateTextRequest translateTextRequest = new TranslateTextRequest();
            translateTextRequest.setSourceLanguage(request.getSource());
            translateTextRequest.setTargetLanguage(huoShanTarget);
            translateTextRequest.setTextList(List.of(request.getContent()));

            translateText = translateService.translateText(translateTextRequest);
            // 将JSON字符串解析为JSONObject对象
            String jsonString = JSON.toJSONString(translateText);
//            appInsights.trackTrace("translateText: " + jsonString);
            JSONObject jsonResponse = JSON.parseObject(jsonString);

            // 直接从jsonResponse中获取TranslationList的第一个元素
            JSONArray translationList = jsonResponse.getJSONArray("TranslationList");
            if (translationList != null && !translationList.isEmpty()) {
                JSONObject firstTranslationItem = translationList.getJSONObject(0);
                translation = firstTranslationItem.getString("Translation");
            } else {
                appInsights.trackTrace("Translation list is empty or not present.");
            }
        } catch (Exception e) {
            appInsights.trackTrace("huoShanTranslate " + e.getMessage());
        }
        return translation;
    }

    // 火山图片翻译
    public void huoShanImageTranslate() throws Exception {
        ITranslateService translateService = TranslateServiceImpl.getInstance();

        translateService.setAccessKey(System.getenv("HUOSHAN_API_KEY"));
        translateService.setSecretKey(System.getenv("HUOSHAN_API_SECRET"));

        String imageUrl = "https://cdn.shopify.com/s/files/1/0728/0948/0215/files/20250904-175349.png?v=1756979649";
        URL url = new URL(imageUrl);
        InputStream inputStream = url.openStream();
        byte[] input = inputStream.readAllBytes();
        inputStream.close();

        // 编码成 Base64
        String base64Image = Base64.getEncoder().encodeToString(input);

        TranslateImageRequest translateImageRequest = new TranslateImageRequest();
        translateImageRequest.setTargetLanguage("zh");
        translateImageRequest.setImage(base64Image);

        TranslateImageResponse translateImageResponse = translateService.translateImage(translateImageRequest);
        System.out.println(JSON.toJSONString(translateImageResponse.getResponseMetadata()));
        System.out.println(JSON.toJSONString(translateImageResponse.getResult()));
        String image = translateImageResponse.getImage();
        System.out.println("iamge: " + image);
        byte[] output = Base64.getDecoder().decode(translateImageResponse.getImage());
        Path filePath = Paths.get(System.getProperty("user.home"), "Desktop", "translated.png");
        Files.write(filePath, output);
        System.out.println("图片已保存: translated.png");
    }
}
