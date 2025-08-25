package com.bogdatech.integration;


import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.*;
import com.alibaba.dashscope.tokenizers.Tokenizer;
import com.alibaba.dashscope.tokenizers.TokenizerFactory;
import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.MAGNIFICATION;
import static com.bogdatech.constants.TranslateConstants.QWEN_VL_LAST;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;
import static com.bogdatech.utils.SwitchModelUtils.switchModel;

@Component
public class ALiYunTranslateIntegration {

    private final ITranslationCounterService translationCounterService;
    private final IAPGUserCounterService iapgUserCounterService;

    @Autowired
    public ALiYunTranslateIntegration(ITranslationCounterService translationCounterService, IAPGUserCounterService iapgUserCounterService) {
        this.translationCounterService = translationCounterService;
        this.iapgUserCounterService = iapgUserCounterService;
    }

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


    //单文本翻译的提示词
    public static String cueWordSingle(String target, String type) {
        return "Translate the following text into " + target + ". Do not output any notes, annotations, explanations, corrections, or bilingual text. Even if you detect an error in the original, do not mention it—only output the final correct translation.";
    }

    //单文本翻译的提示词(用具体语言而不是语言代码)

    /**
     * 用qwen-MT的部分代替google翻译。
     *
     * @param text       要翻译的文本
     * @param prompt     提示词
     * @param target     目标语言代码
     * @param countUtils 计数器
     * @param shopName   店铺名称
     * @return 翻译后的文本
     */
    public String singleTranslate(String text, String prompt, CharacterCountUtils countUtils, String target, String shopName, Integer limitChars) {
        String model = switchModel(target);
        Generation gen = new Generation();

        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(prompt)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(text)
                .build();
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        String content = null;
        int totalToken;
        try {
            GenerationResult call = gen.call(param);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            Map<String, Object> translationStatusMap = getTranslationStatusMap(text, 2);
            userTranslate.put(shopName, translationStatusMap);
            totalToken = (int) (call.getUsage().getTotalTokens() * MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace("singleTranslate " + shopName + " 用户 原文本：" + text + " 翻译成： " + content + " token ali: " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            countUtils.addChars(totalToken);
        } catch (NoApiKeyException | InputRequiredException e) {
            appInsights.trackTrace("singleTranslate 百炼翻译报错信息 errors ： " + e.getMessage());
            appInsights.trackException(e);
            return text;
        }
        return content;

    }

    /**
     * 用qwen-MT的部分代替google翻译。
     *
     * @param model         模型的类型 turbo和plus
     * @param translateText 要翻译的文本
     * @param source        源语言代码
     * @param target        目标语言代码
     * @param countUtils    计数器
     * @return 翻译后的文本
     */
    public String callWithMessage(String model, String translateText, String source, String target, CharacterCountUtils countUtils, String shopName, Integer limitChars) {
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(translateText)
                .build();
        //根据目标语言
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model(model)
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .parameter("translation_options", "{\"source_lang\":\"" + source + "\",\"target_lang\":\"" + target + "\"}")
                .build();
        String content = null;
        int totalToken;
        try {
            GenerationResult call = gen.call(param);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            totalToken =  call.getUsage().getTotalTokens();
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace( "clickTranslation 用户： " + shopName +" token ali mt : 原文本- " + source + "目标文本： " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            Map<String, Object> translationStatusMap = getTranslationStatusMap(translateText, 2);
            userTranslate.put(shopName, translationStatusMap);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            countUtils.addChars(totalToken);
        } catch (NoApiKeyException | InputRequiredException e) {
//            appInsights.trackTrace("百炼翻译报错信息： " + e.getMessage());
            appInsights.trackTrace("clickTranslation " + shopName + " 百炼翻译报错信息 errors ： " + e.getMessage());
        }
        return content;

    }

    public static Integer calculateBaiLianToken(String text) {
        Tokenizer tokenizer = TokenizerFactory.qwen();
        try {
            return tokenizer.encode(text, "all").size();
        } catch (NoSpecialTokenExists | UnSupportedSpecialTokenMode e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 调用qwen视觉模型，根据传入的数据，生成对应的描述数据
     * */
    public String callWithPicMess(String prompt, Long userId, CharacterCountUtils counter, String picUrl, Integer userMaxLimit){
        MultiModalConversation conv = new MultiModalConversation();

        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        Collections.singletonMap("image", picUrl),
                        Collections.singletonMap("text", prompt))).build();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model(QWEN_VL_LAST)
                .message(userMessage)
                .build();
        MultiModalConversationResult result = null;
        try {
            result = conv.call(param);
            List<Map<String, Object>> content = result.getOutput().getChoices().get(0).getMessage().getContent();
            Integer inputTokens = result.getUsage().getInputTokens();
            Integer outputTokens = result.getUsage().getOutputTokens();
            int totalToken = (int) ((inputTokens + outputTokens) * MAGNIFICATION);
            appInsights.trackTrace("用户 token ali-vl : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
//            appInsights.trackTrace("用户 token ali-vl : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            //更新用户token计数和对应
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            //更新用户产品计数
            counter.addChars(totalToken);
            return (String) content.get(0).get("text");
        } catch (Exception e) {
            appInsights.trackTrace("调用百炼视觉模型报错信息 errors ： " + e.getMessage());
            appInsights.trackException(e);
            return null;
        }
    }

    /**
     * 调用qwen-max用户产品描述图片为空的情况
     * */
    public String callWithQwenMaxToDes(String prompt, CharacterCountUtils countUtils, Long userId, Integer userMaxLimit) {
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model("qwen-max-latest")
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        String content;
        int totalToken;
        try {
            GenerationResult call = gen.call(param);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            totalToken = (int) (call.getUsage().getTotalTokens() * MAGNIFICATION);
//        int totalToken = 10;
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            appInsights.trackTrace("用户 token ali-max : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
//            appInsights.trackTrace("用户 token ali-max : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            countUtils.addChars(totalToken);
        } catch (NoApiKeyException | InputRequiredException e) {
            appInsights.trackTrace("百炼翻译报错信息 errors ： " + e.getMessage());
            return null;
//            appInsights.trackTrace("百炼翻译报错信息： " + e.getMessage());
        }
        return content;

    }

}
