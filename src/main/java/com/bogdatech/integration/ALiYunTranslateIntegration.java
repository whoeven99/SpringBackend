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
import com.bogdatech.logic.PCApp.PCUserPicturesService;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.bogdatech.repository.repo.PCUsersRepo;
import com.bogdatech.utils.AppInsightsUtils;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.ConfigUtils;
import com.bogdatech.utils.JsonUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.utils.AppInsightsUtils.printTranslateCost;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TimeOutUtils.*;

@Component
public class ALiYunTranslateIntegration {
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IAPGUserCounterService iapgUserCounterService;
    @Autowired
    private TranslationCounterRedisService translationCounterRedisService;
    @Autowired
    private PCUsersRepo pcUsersRepo;

    // 根据语言代码切换模型
    public static String switchModel(String languageCode) {
        return switch (languageCode) {
//            case "en", "zh-CN", "de", "ja", "it", "ru", "zh-TW", "da", "nl", "id", "th", "vi", "uk", "fr", "ko", "hi", "bg", "cs", "el", "hr", "lt", "nb", "pl", "ro", "sk", "sv", "ar", "no" -> "qwen-plus";
            default -> "qwen-max-latest"; //32k token
        };
//        return "qwen-max";
    }

    public static com.aliyun.alimt20181012.Client createClient() {
        try {
            com.aliyun.teaopenapi.models.Config credentialConfig = new com.aliyun.teaopenapi.models.Config();
            credentialConfig.setType("access_key");
            credentialConfig.setAccessKeyId(ConfigUtils.getConfig("ALIBABA_CLOUD_ACCESS_KEY_ID"));
            credentialConfig.setAccessKeySecret(ConfigUtils.getConfig("ALIBABA_CLOUD_ACCESS_KEY_SECRET"));
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
    public String singleTranslate(String text, String prompt, CharacterCountUtils countUtils, String target
            , String shopName, Integer limitChars, boolean isSingleFlag, String translateType) {
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
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        String content;
        int totalToken;
        try {
            GenerationResult call = callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 singleTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return null;
            }
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            totalToken = (int) (call.getUsage().getTotalTokens() * MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace("singleTranslate " + shopName + " 用户 原文本：" + text + " 翻译成： " + content + " token ali: " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            AppInsightsUtils.printTranslateCost(totalToken, inputTokens, outputTokens);
            if (isSingleFlag){
                translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            }else {
                translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
                translationCounterRedisService.increaseLanguage(shopName, target, totalToken, translateType);
            }

            countUtils.addChars(totalToken);
        } catch (Exception e) {
            appInsights.trackTrace("singleTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text);
            appInsights.trackException(e);
            return null;
        }
        return content;
    }

    public Pair<String, Integer> userTranslate(String prompt, String target) {
        String model = switchModel(target);
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();

        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        String content;
        int totalToken;
        try {
            GenerationResult call = callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            appInsights.trackTrace("FatalException userTranslate call errors ： " + e.getMessage() +
                                    " translateText : " + prompt);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return new Pair<>(null, 0);
            }
            content = call.getOutput().getChoices().get(0).getMessage().getContent();

            totalToken = (int) (call.getUsage().getTotalTokens() * MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace("userTranslate 原文本：" + prompt + " 翻译成： " + content +
                    " token ali: " + content + " all: " + totalToken + " input: " + inputTokens + " output: " +
                    outputTokens);
            return new Pair<>(content, totalToken);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException userTranslate all errors ： " + e.getMessage() + " translateText : " + prompt);
            appInsights.trackException(e);
            return new Pair<>(null, 0);
        }
    }

    public Pair<String, Integer> userTranslate(String text, String prompt, String target, String shopName) {
        String model = switchModel(target);
        Generation gen = new Generation();
        Message userMsg = null;
        if (text != null) {
            userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(prompt + text)
                    .build();
        } else {
            userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(prompt)
                    .build();
        }

        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        String content;
        int totalToken;
        try {
            GenerationResult call = callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            appInsights.trackTrace("FatalException userTranslate call errors ： " + e.getMessage() +
                                    " translateText : " + text + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return null;
            }
            content = call.getOutput().getChoices().get(0).getMessage().getContent();

            totalToken = (int) (call.getUsage().getTotalTokens() * MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace("userTranslate " + shopName + " 用户 原文本：" + text + " 翻译成： " + content +
                    " token ali: " + " all: " + totalToken + " input: " + inputTokens + " output: " +
                    outputTokens);
            return new Pair<>(content, totalToken);
        } catch (Exception e) {
            appInsights.trackTrace("FatalException userTranslate all errors ： " + e.getMessage() + " translateText : " + text);
            appInsights.trackException(e);
            return new Pair<>(null, 0);
        }
    }

    public String userTranslate(String text, String prompt, CharacterCountUtils countUtils, String target
            , String shopName, Integer limitChars, boolean isSingleFlag, String translateType) {
        String model = switchModel(target);
        Generation gen = new Generation();

        Message userMsg;
        if (text != null) {
            userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(prompt + text)
                    .build();
        } else {
            userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(prompt)
                    .build();
        }

        appInsights.trackTrace("userMsg 用户 " + shopName);
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        appInsights.trackTrace("param 用户 " + shopName);
        String content;
        int totalToken;
        try {
            GenerationResult call = callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 userTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return null;
            }
            appInsights.trackTrace("GenerationResult 用户 " + shopName);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            appInsights.trackTrace("content 用户 " + shopName);

            appInsights.trackTrace("userTranslate 用户 " + shopName);
            totalToken = (int) (call.getUsage().getTotalTokens() * MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace("userTranslate " + shopName + " 用户 原文本：" + text + " 翻译成： " + content + " token ali: " + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            AppInsightsUtils.printTranslateCost(totalToken, inputTokens, outputTokens);
            if (isSingleFlag) {
                translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            } else {
                translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
                translationCounterRedisService.increaseLanguage(shopName, target, totalToken, translateType);
            }

            countUtils.addChars(totalToken);
            return content;
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation userTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text);
            appInsights.trackException(e);
            return null;
        }
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
    public String callWithMessageMT(String model, String translateText, String source, String target
            , CharacterCountUtils countUtils, String shopName, Integer limitChars, boolean isSingleFlag, String translateType) {
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(translateText)
                .build();
        //根据目标语言
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .parameter("translation_options", "{\"source_lang\":\"" + source + "\",\"target_lang\":\"" + target + "\"}")
                .build();
        String content = null;
        int totalToken;
        try {
            GenerationResult call = callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 callWithMessageMT 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + translateText + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return translateText;
            }
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            totalToken = call.getUsage().getTotalTokens();
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace("callWithMessageMT 用户： " + shopName + " token ali mt : 原文本- " + translateText + "目标文本： " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            AppInsightsUtils.printTranslateCost(totalToken, inputTokens, outputTokens);
            if (isSingleFlag){
                translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
            }else {
                translationCounterService.updateAddUsedCharsByShopName(shopName, totalToken, limitChars);
                translationCounterRedisService.increaseLanguage(shopName, target, totalToken, translateType);
            }
            countUtils.addChars(totalToken);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("callWithMessageMT " + shopName + " 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + translateText);
        }
        return content;

    }

    public static String TRANSLATE_APP = "TRANSLATE_APP";

    /**
     * 调用qwen图片机器翻译
     */
    public String callWithPic(String source, String target, String picUrl, String shopName, Integer limitChars, String appModel) {
        Client client = createClient();
        com.aliyun.alimt20181012.models.TranslateImageRequest translateImageRequest = new com.aliyun.alimt20181012.models.TranslateImageRequest()
                .setImageUrl(picUrl)
                .setTargetLanguage(target)
                .setSourceLanguage(source)
                .setField("e-commerce");
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        runtime.setReadTimeout(40000); // 40 秒读超时
        runtime.setConnectTimeout(40000); // 40 秒连接超时
        String targetPicUrl = null;
        try {
            if (client == null) {
                appInsights.trackTrace("callWithPic " + shopName + " 百炼翻译报错信息 client is null picUrl : " + picUrl);
                return null;
            }
            TranslateImageResponse translateImageResponse = callWithTimeoutAndRetry(() -> {
                        try {
                            return client.translateImageWithOptions(translateImageRequest, runtime);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 callWithPic 百炼翻译报错信息 errors ： " + e.getMessage() + " picUrl : " + picUrl + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );

            appInsights.trackTrace("callWithPic " + shopName + " 百炼翻译报错信息 translateImageResponse : " + JsonUtils.objectToJson(translateImageResponse) + " picUrl : " + picUrl + " target: " + target + " source: " + source);
            if (translateImageResponse == null || translateImageResponse.getBody() == null) {
                return null;
            }

            TranslateImageResponseBody body = translateImageResponse.getBody();

            if (TRANSLATE_APP.equals(appModel)){
                translationCounterService.updateAddUsedCharsByShopName(shopName, PIC_FEE, limitChars);
            }else {
                pcUsersRepo.updateUsedPointsByShopName(shopName, PCUserPicturesService.APP_PIC_FEE);
            }
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
            appInsights.trackException(e);
            appInsights.trackTrace("calculateBaiLianToken " + e.getMessage() + " 计数失败 text : " + text);
            return 0;
        }
    }

    /**
     * 调用qwen视觉模型，根据传入的数据，生成对应的描述数据
     */
    public String callWithPicMess(String prompt, Long userId, CharacterCountUtils counter, String picUrl, Integer userMaxLimit) {
        MultiModalConversation conv = new MultiModalConversation();

        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        Collections.singletonMap("image", picUrl),
                        Collections.singletonMap("text", prompt))).build();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(QWEN_VL_LAST)
                .message(userMessage)
                .build();
        MultiModalConversationResult result;
        try {
            result = callWithTimeoutAndRetry(() -> {
                        try {
                            return conv.call(param);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 callWithPicMess 百炼翻译报错信息 errors ： " + e.getMessage() + " picUrl : " + picUrl + " 用户：" + userId);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (result == null) {
                return null;
            }
            List<Map<String, Object>> content = result.getOutput().getChoices().get(0).getMessage().getContent();
            Integer inputTokens = result.getUsage().getInputTokens();
            Integer outputTokens = result.getUsage().getOutputTokens();
            int totalToken = (int) ((inputTokens + outputTokens) * MAGNIFICATION);
            AppInsightsUtils.printTranslateCost(totalToken, inputTokens, outputTokens);
            appInsights.trackTrace("callWithPicMess 用户 " + userId + " token ali-vl : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            //更新用户token计数和对应
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            //更新用户产品计数
            counter.addChars(totalToken);
            return (String) content.get(0).get("text");
        } catch (Exception e) {
            appInsights.trackTrace("callWithPicMess 用户 " + userId + " 百炼翻译报错信息 errors ： " + e.getMessage() + " prompt: " + prompt);
            appInsights.trackException(e);
            return null;
        }
    }

    /**
     * 调用qwen-max用户产品描述图片为空的情况
     */
    public String callWithQwenMaxToDes(String prompt, CharacterCountUtils countUtils, Long userId, Integer userMaxLimit) {
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model("qwen-max-latest")
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        String content;
        int totalToken;
        try {
            GenerationResult call = callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 callWithQwenMaxToDes 百炼翻译报错信息 errors ： " + e.getMessage() + " prompt : " + prompt + " 用户：" + userId);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return null;
            }
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            totalToken = (int) (call.getUsage().getTotalTokens() * MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            AppInsightsUtils.printTranslateCost(totalToken, inputTokens, outputTokens);
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            appInsights.trackTrace("用户 token ali-max : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            countUtils.addChars(totalToken);
        } catch (Exception e) {
            appInsights.trackTrace("callWithQwenMaxToDes 百炼翻译报错信息 errors ： " + e.getMessage() + " prompt: " + prompt);
            return null;
        }
        return content;

    }

    public String textTranslate(String text, String prompt, String target, String shopName, Integer limitChars) {
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
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        String content;
        int totalToken;
        try {
            GenerationResult call = callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 textTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text + " 用户：" + shopName);
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return null;
            }
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            totalToken = (int) (call.getUsage().getTotalTokens() * MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            appInsights.trackTrace("textTranslate " + shopName + " 用户 原文本：" + text + " 翻译成： " + content + " token ali: " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            printTranslateCost(totalToken, inputTokens, outputTokens);
//            pcUsersRepo.updateUsedPointsByShopName(shopName, PCUserPicturesService.APP_ALT_FEE);

        } catch (Exception e) {
            appInsights.trackTrace("textTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text);
            appInsights.trackException(e);
            return null;
        }
        return content;
    }
}
