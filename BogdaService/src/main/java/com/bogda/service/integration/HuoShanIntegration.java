package com.bogda.service.integration;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bogda.service.controller.request.TranslateRequest;
import com.bogda.service.utils.ModuleCodeUtils;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.ConfigUtils;
import com.volcengine.model.request.translate.TranslateImageRequest;
import com.volcengine.model.request.translate.TranslateTextRequest;
import com.volcengine.model.response.translate.TranslateImageResponse;
import com.volcengine.model.response.translate.TranslateTextResponse;
import com.volcengine.service.translate.ITranslateService;
import com.volcengine.service.translate.impl.TranslateServiceImpl;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.List;



@Component
public class HuoShanIntegration {

    //火山翻译API
    public String huoShanTranslate(TranslateRequest request) {
        ITranslateService translateService = TranslateServiceImpl.getInstance();

        translateService.setAccessKey(ConfigUtils.getConfig("HUOSHAN_API_KEY"));
        translateService.setSecretKey(ConfigUtils.getConfig("HUOSHAN_API_SECRET"));

        //对火山翻译API的语言进行处理
        String huoShanTarget = ModuleCodeUtils.huoShanTransformCode(request.getTarget());
//        AppInsightsUtils.trackTrace("huoShanTarget: " + huoShanTarget);
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
//            AppInsightsUtils.trackTrace("translateText: " + jsonString);
            JSONObject jsonResponse = JSON.parseObject(jsonString);

            // 直接从jsonResponse中获取TranslationList的第一个元素
            JSONArray translationList = jsonResponse.getJSONArray("TranslationList");
            if (translationList != null && !translationList.isEmpty()) {
                JSONObject firstTranslationItem = translationList.getJSONObject(0);
                translation = firstTranslationItem.getString("Translation");
            } else {
                AppInsightsUtils.trackTrace("Translation list is empty or not present.");
            }
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException huoShanTranslate " + e.getMessage());
        }
        return translation;
    }

    // 火山图片翻译
    public byte[] huoShanImageTranslate(String imageUrl, String targetLanguage) {
        ITranslateService translateService = TranslateServiceImpl.getInstance();

        translateService.setAccessKey(ConfigUtils.getConfig("HUOSHAN_API_KEY"));
        translateService.setSecretKey(ConfigUtils.getConfig("HUOSHAN_API_SECRET"));

        AppInsightsUtils.trackTrace("huoShanImageTranslate imageUrl : " + imageUrl + " targetLanguage: " + targetLanguage);
        try {
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000); // 连接超时（单位：毫秒）
            connection.setReadTimeout(5000);    // 读取超时
            InputStream inputStream = url.openStream();
            byte[] input = inputStream.readAllBytes();
            inputStream.close();

            // 编码成 Base64
            String base64Image = Base64.getEncoder().encodeToString(input);
            TranslateImageRequest translateImageRequest = new TranslateImageRequest();
            translateImageRequest.setTargetLanguage(targetLanguage);
            translateImageRequest.setImage(base64Image);

            TranslateImageResponse translateImageResponse = translateService.translateImage(translateImageRequest);
            AppInsightsUtils.trackTrace("huoShanImageTranslate 返回的数据 ： " + JSON.toJSONString(translateImageResponse.getResponseMetadata()));
            return Base64.getDecoder().decode(translateImageResponse.getImage());

        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException huoShanImageTranslate " + e.getMessage());
        }
        return null;
    }
}
