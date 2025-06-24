package com.bogdatech.integration;


import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.NoSpecialTokenExists;
import com.alibaba.dashscope.exception.UnSupportedSpecialTokenMode;
import com.alibaba.dashscope.tokenizers.Tokenizer;
import com.alibaba.dashscope.tokenizers.TokenizerFactory;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;

import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.SwitchModelUtils.switchModel;

@Component
public class ALiYunTranslateIntegration {

    private final ITranslationCounterService translationCounterService;

    @Autowired
    public ALiYunTranslateIntegration(ITranslationCounterService translationCounterService) {
        this.translationCounterService = translationCounterService;
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
        Integer totalToken;
        try {
            GenerationResult call = gen.call(param);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            userTranslate.put(shopName, text);
            totalToken = call.getUsage().getTotalTokens();
//        int totalToken = 10;
            countUtils.addChars(totalToken);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace(shopName + " 用户 token ali: " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            System.out.println("翻译源文本: " + content + "counter: " + totalToken);
        } catch (NoApiKeyException | InputRequiredException e) {
            appInsights.trackTrace("百炼翻译报错信息 errors ： " + e.getMessage());
            return text;
//            System.out.println("百炼翻译报错信息： " + e.getMessage());
        }
        return content;


//        return text;
    }

    public String QwenTranslate(String text, String prompt, CharacterCountUtils countUtils) {
        String model = "qwen-max-latest";
        Generation gen = new Generation();

        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model(model)
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        String content = null;
        Integer totalToken;
        try {
            GenerationResult call = gen.call(param);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            totalToken = call.getUsage().getTotalTokens();
            countUtils.addChars(totalToken);
            appInsights.trackTrace("token ali: " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
        } catch (NoApiKeyException | InputRequiredException e) {
            appInsights.trackTrace("百炼翻译报错信息 errors ： " + e.getMessage());
            return text;
//            System.out.println("百炼翻译报错信息： " + e.getMessage());
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
        System.out.println("翻译源文本: " + translateText);

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
        Integer totalToken;
        try {
            GenerationResult call = gen.call(param);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            totalToken = call.getUsage().getTotalTokens();
//        int totalToken = 10;
            countUtils.addChars(totalToken);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace("token ali mt : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            userTranslate.put(shopName, translateText);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
        } catch (NoApiKeyException | InputRequiredException e) {
//            System.out.println("百炼翻译报错信息： " + e.getMessage());
            appInsights.trackTrace("百炼翻译报错信息 errors ： " + e.getMessage());
        }
        return content;


//        return translateText;
    }

    public static Integer calculateBaiLianToken(String text) {
        Tokenizer tokenizer = TokenizerFactory.qwen();
        try {
            return tokenizer.encode(text, "all").size();
        } catch (NoSpecialTokenExists | UnSupportedSpecialTokenMode e) {
            throw new RuntimeException(e);
        }
    }
}
