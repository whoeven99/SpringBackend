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
import com.aliyun.alimt20181012.Client;
import com.aliyun.alimt20181012.models.*;
import com.bogdatech.Service.IAPGUserCounterService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.AppInsightsUtils.printTranslateCost;
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

    public static com.aliyun.alimt20181012.Client createClient() {
        try {
            com.aliyun.teaopenapi.models.Config credentialConfig = new com.aliyun.teaopenapi.models.Config();
            credentialConfig.setType("access_key");
            credentialConfig.setAccessKeyId(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"));
            credentialConfig.setAccessKeySecret(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"));
            credentialConfig.setRegionId("cn-hangzhou");
            credentialConfig.setEndpoint("mt.cn-hangzhou.aliyuncs.com");
           return new com.aliyun.alimt20181012.Client(credentialConfig);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("createClient 调用报错： " + e.getMessage());
            return null;
        }
    }

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
        String content;
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
            printTranslateCost(totalToken, inputTokens, outputTokens);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            countUtils.addChars(totalToken);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text);
            appInsights.trackException(e);
            return text;
        }
        return content;
    }

    /**
     * qwen 单 user msg 翻译
     *
     * @param text       要翻译的文本
     * @param prompt     提示词
     * @param target     目标语言代码
     * @param countUtils 计数器
     * @param shopName   店铺名称
     * @return 翻译后的文本
     */
    public String userTranslate(String text, String prompt, CharacterCountUtils countUtils, String target, String shopName, Integer limitChars) {
        String model = switchModel(target);
        appInsights.trackTrace("model 用户 " + shopName);
        Generation gen = new Generation();
        appInsights.trackTrace("gen 用户 " + shopName);
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt + text)
                .build();
        appInsights.trackTrace("userMsg 用户 " + shopName);
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model(model)
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        appInsights.trackTrace("param 用户 " + shopName);
        String content;
        int totalToken;
        appInsights.trackTrace("totalToken 用户 " + shopName);
        try {
            GenerationResult call = gen.call(param);
            appInsights.trackTrace("GenerationResult 用户 " + shopName);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            appInsights.trackTrace("content 用户 " + shopName);
            Map<String, Object> translationStatusMap = getTranslationStatusMap(text, 2);
            appInsights.trackTrace("translationStatusMap 用户 " + shopName);
            userTranslate.put(shopName, translationStatusMap);
            appInsights.trackTrace("userTranslate 用户 " + shopName);
            totalToken = (int) (call.getUsage().getTotalTokens() * MAGNIFICATION);
            appInsights.trackTrace("totalToken 用户 " + shopName);
            Integer inputTokens = call.getUsage().getInputTokens();
            appInsights.trackTrace("inputTokens 用户 " + shopName);
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace("userTranslate " + shopName + " 用户 原文本：" + text + " 翻译成： " + content + " token ali: " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            printTranslateCost(totalToken, inputTokens, outputTokens);
            appInsights.trackTrace("printTranslateCost 用户 " + shopName);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            appInsights.trackTrace("updateAddUsedCharsByShopName 用户 " + shopName);
            countUtils.addChars(totalToken);
            appInsights.trackTrace("countUtils 用户 " + shopName);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation userTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text);
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
            appInsights.trackTrace( "clickTranslation 用户： " + shopName +" token ali mt : 原文本- " + translateText + "目标文本： " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            printTranslateCost(totalToken, inputTokens, outputTokens);
            Map<String, Object> translationStatusMap = getTranslationStatusMap(translateText, 2);
            userTranslate.put(shopName, translationStatusMap);
            translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            countUtils.addChars(totalToken);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("clickTranslation " + shopName + " 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + translateText);
        }
        return content;

    }

    /**
     * 调用qwen图片机器翻译
     * */
    public String callWithPic(String source, String target, String picUrl, String shopName, Integer limitChars) {
        Client client = createClient();
        com.aliyun.alimt20181012.models.TranslateImageRequest translateImageRequest = new com.aliyun.alimt20181012.models.TranslateImageRequest()
                .setImageUrl(picUrl)
                .setTargetLanguage(target)
                .setSourceLanguage(source)
                .setField("e-commerce");
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();

        String targetPicUrl = null;
        try {
            if (client == null) {
                appInsights.trackTrace("callWithPic " + shopName + " 百炼翻译报错信息 client is null picUrl : " + picUrl);
                return null;
            }
            TranslateImageResponse translateImageResponse = client.translateImageWithOptions(translateImageRequest, runtime);
            TranslateImageResponseBody body = translateImageResponse.getBody();
            // 打印 body
            appInsights.trackTrace("callWithPic " + shopName + " 图片返回message : " + body.getMessage() + " picUrl : " + body.getData().finalImageUrl + " RequestId: " + body.getRequestId() + " Code: " + body.getCode());
            translationCounterService.updateAddUsedCharsByShopName(shopName, PIC_FEE, limitChars);
            targetPicUrl = body.getData().finalImageUrl;
        } catch (Exception error) {
            appInsights.trackException(error);
            appInsights.trackTrace("callWithPic " + shopName + " 百炼翻译报错信息 errors ： " + error.getMessage() + " picUrl : " + picUrl);
        }

        return targetPicUrl;
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
            printTranslateCost(totalToken, inputTokens, outputTokens);
            appInsights.trackTrace("callWithPicMess 用户 " + userId + " token ali-vl : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
//            appInsights.trackTrace("用户 token ali-vl : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            //更新用户token计数和对应
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            //更新用户产品计数
            counter.addChars(totalToken);
            return (String) content.get(0).get("text");
        } catch (Exception e) {
            appInsights.trackTrace("callWithPicMess 用户 " + userId + " 调用百炼视觉模型报错信息 errors ： " + e.getMessage() + " prompt: " + prompt);
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
            printTranslateCost(totalToken, inputTokens, outputTokens);
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            appInsights.trackTrace("用户 token ali-max : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
//            appInsights.trackTrace("用户 token ali-max : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            countUtils.addChars(totalToken);
        } catch (Exception e) {
            appInsights.trackTrace("百炼翻译报错信息 errors ： " + e.getMessage() + " prompt: " + prompt);
            return null;
//            appInsights.trackTrace("百炼翻译报错信息： " + e.getMessage());
        }
        return content;

    }

}
