package com.bogdatech.integration;

import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.utils.CharacterCountUtils;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.hunyuan.v20230901.HunyuanClient;
import com.tencentcloudapi.hunyuan.v20230901.models.ChatCompletionsRequest;
import com.tencentcloudapi.hunyuan.v20230901.models.ChatCompletionsResponse;
import com.tencentcloudapi.hunyuan.v20230901.models.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.MAGNIFICATION;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;

@Component
public class HunYuanIntegration {

    private final ITranslationCounterService translationCounterService;

    @Autowired
    public HunYuanIntegration(ITranslationCounterService translationCounterService) {
        this.translationCounterService = translationCounterService;
    }

    // 静态初始化的 Credential 和 HunyuanClient
    private static final Credential CREDENTIAL;
    private static final HunyuanClient CLIENT;
    private static final String HUNYUAN_APP_ID = "HUNYUAN_APP_ID";
    private static final String HUNYUAN_APP_KEY = "HUNYUAN_APP_KEY";

    static {
        // 初始化 Credential（替换为你的 SecretId 和 SecretKey）
        CREDENTIAL = new Credential(System.getenv(HUNYUAN_APP_ID), System.getenv(HUNYUAN_APP_KEY));
        // 初始化 ClientProfile
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setSignMethod(ClientProfile.SIGN_TC3_256); // 使用 TC3-HMAC-SHA256 签名方法

        // 初始化 HunyuanClient，指定区域（例如 "ap-guangzhou"）
        CLIENT = new HunyuanClient(CREDENTIAL, "ap-guangzhou", clientProfile);
    }

    /**
     * 混元model翻译
     *
     * @param sourceText 原文本
     * @param prompt     提示词
     * @param countUtils 字符统计工具
     * @param model      模型
     * @param shopName   店铺名称
     * @return 翻译后的文本
     **/
    public String hunYuanTranslate(String sourceText, String prompt, CharacterCountUtils countUtils, String model, String shopName, Integer limitChars) {
        final int maxRetries = 3;
        final long baseDelayMillis = 1000; // 初始重试延迟为 1 秒

        // 1. 创建 ChatCompletions 请求
        ChatCompletionsRequest req = new ChatCompletionsRequest();
        // 设置模型名称（请确认具体名称，假设为 "hunyuan-turbo-s"）
        req.setModel(model); //28k token
        // 设置对话消息
        Message[] messages = new Message[2];
        messages[0] = new Message();
        messages[0].setRole("system");
        messages[0].setContent(prompt);
        messages[1] = new Message();
        messages[1].setRole("user");
        messages[1].setContent(sourceText);
        req.setMessages(messages);
        req.setStream(false); // 非流式调用，设为 true 可启用流式返回

        // 4. 发送请求并获取响应
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ChatCompletionsResponse resp = CLIENT.ChatCompletions(req);
//            appInsights.trackTrace("resp: " + resp.toString() + "resp_id: " + resp.getRequestId());
                // 5. 输出结果
                if (resp.getChoices() != null && resp.getChoices().length > 0) {
                    String targetText = resp.getChoices()[0].getMessage().getContent();
                    if (resp.getUsage() != null && resp.getUsage().getTotalTokens() != null) {
                        countUtils.addChars(resp.getUsage().getTotalTokens().intValue());
                    }
                    int totalToken = (int) (resp.getUsage().getTotalTokens().intValue() * MAGNIFICATION);
                    long completionTokens = resp.getUsage().getCompletionTokens();
                    long promptTokens = resp.getUsage().getPromptTokens();
                    Map<String, Object> translationStatusMap = getTranslationStatusMap(sourceText, 2);
                    userTranslate.put(shopName, translationStatusMap);
                    appInsights.trackTrace(shopName + " 用户 token hunyuan: " + sourceText + " all: " + totalToken + " input: " + promptTokens + " output: " + completionTokens);
                    translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
                    countUtils.addChars(totalToken);
                    return targetText;
                } else {
                    appInsights.trackTrace("重试 Hunyuan errors " + attempt);
                }

            } catch (TencentCloudSDKException e) {
                appInsights.trackTrace("hunyuan errors : " + e + " resp_id: " + e.getRequestId());
                if (attempt == maxRetries) {
                    throw new RuntimeException("errors Failed after " + maxRetries + " attempts", e);
                }
            }
            try {
                long delay = baseDelayMillis * (1L << (attempt - 1)); // 1s, 2s, 4s...
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // restore interrupt flag
                throw new RuntimeException("Hunyuan errors Retry interrupted", ie);
            }
        }
        return sourceText;
    }

}
