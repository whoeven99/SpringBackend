package com.bogdatech.integration;

import com.bogdatech.utils.CharacterCountUtils;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.hunyuan.v20230901.HunyuanClient;
import com.tencentcloudapi.hunyuan.v20230901.models.ChatCompletionsRequest;
import com.tencentcloudapi.hunyuan.v20230901.models.ChatCompletionsResponse;
import com.tencentcloudapi.hunyuan.v20230901.models.Message;
import org.springframework.stereotype.Component;

import static com.bogdatech.integration.ALiYunTranslateIntegration.cueWordSingle;

@Component
public class HunYuanIntegration {

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
     * @param sourceText 原文本
     * @param type       类型
     * @param countUtils 字符统计工具
     * @param target     目标
     * @param model      模型
     * @return 翻译后的文本
     **/
    public static String hunYuanTranslate(String sourceText, String type, CharacterCountUtils countUtils, String target, String model) {
        // 1. 创建 ChatCompletions 请求
        ChatCompletionsRequest req = new ChatCompletionsRequest();
        // 设置模型名称（请确认具体名称，假设为 "hunyuan-turbo-s"）
        req.setModel(model);
        // 设置对话消息
        Message[] messages = new Message[1];
        messages[0] = new Message();
        messages[0].setRole("user");
        messages[0].setContent(cueWordSingle(target, type) + " \n " + sourceText);
        req.setMessages(messages);
        req.setStream(false); // 非流式调用，设为 true 可启用流式返回

        // 4. 发送请求并获取响应
        ChatCompletionsResponse resp;
        try {
            resp = CLIENT.ChatCompletions(req);
        } catch (TencentCloudSDKException e) {
            throw new RuntimeException(e);
        }

        // 5. 输出结果
        String targetText = null;
        if (resp.getChoices() != null && resp.getChoices().length > 0) {
            targetText = resp.getChoices()[0].getMessage().getContent();
//            System.out.println("targetText: " + targetText);
            int totalToken = resp.getUsage().getTotalTokens().intValue();
            countUtils.addChars(totalToken);
        }
        return targetText;
    }
}
