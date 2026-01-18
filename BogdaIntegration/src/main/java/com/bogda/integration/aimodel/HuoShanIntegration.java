package com.bogda.integration.aimodel;

import com.alibaba.fastjson.JSON;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.ConfigUtils;
import com.volcengine.model.request.translate.TranslateImageRequest;
import com.volcengine.model.response.translate.TranslateImageResponse;
import com.volcengine.service.translate.ITranslateService;
import com.volcengine.service.translate.impl.TranslateServiceImpl;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;

@Component
public class HuoShanIntegration {
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
