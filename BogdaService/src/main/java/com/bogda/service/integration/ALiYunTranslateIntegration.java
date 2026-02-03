package com.bogda.service.integration;


import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoSpecialTokenExists;
import com.alibaba.dashscope.exception.UnSupportedSpecialTokenMode;
import com.alibaba.dashscope.tokenizers.Tokenizer;
import com.alibaba.dashscope.tokenizers.TokenizerFactory;
import com.bogda.service.Service.IAPGUserCounterService;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.model.AiTranslateResult;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.CharacterCountUtils;
import com.bogda.common.utils.ConfigUtils;
import com.bogda.common.utils.TimeOutUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.bogda.common.utils.AppInsightsUtils.printTranslateCost;
import static com.bogda.common.utils.TimeOutUtils.*;

@Component
public class ALiYunTranslateIntegration {
    @Autowired
    private IAPGUserCounterService iapgUserCounterService;

    private static Tokenizer tokenizer;
    public static String TRANSLATE_APP = "TRANSLATE_APP";
    public static String QWEN_MAX = "qwen-max";

    public ALiYunTranslateIntegration() {
        tokenizer = TokenizerFactory.qwen();
    }

    public static Integer calculateBaiLianToken(String text) {
        try {
            return tokenizer.encode(text, "all").size();
        } catch (NoSpecialTokenExists | UnSupportedSpecialTokenMode e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("calculateBaiLianToken " + e.getMessage() + " 计数失败 text : " + text);
            return text.length();
        }
    }

    // 根据语言代码切换模型
    public static String switchModel(String languageCode) {
        return switch (languageCode) {
//            case "en", "zh-CN", "de", "ja", "it", "ru", "zh-TW", "da", "nl", "id", "th", "vi", "uk", "fr", "ko", "hi", "bg", "cs", "el", "hr", "lt", "nb", "pl", "ro", "sk", "sv", "ar", "no" -> "qwen-plus";
            default -> "qwen-max-latest"; //32k token
        };
//        return "qwen-max";
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

        try {
            GenerationResult call = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            AppInsightsUtils.trackTrace("FatalException userTranslate call errors ： " + e.getMessage() +
                                    " translateText : " + prompt);
                            AppInsightsUtils.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return null;
            }
            String content = call.getOutput().getChoices().get(0).getMessage().getContent();

            int totalToken = (int) (call.getUsage().getTotalTokens() * TranslateConstants.MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            AppInsightsUtils.trackTrace("userTranslate 原文本：" + prompt + " 翻译成： " + content +
                    " token ali  all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            return new Pair<>(content, totalToken);
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException userTranslate errors ： " + e.getMessage() + " translateText : " + prompt);
            AppInsightsUtils.trackException(e);
            return null;
        }
    }

    /**
     * 带错误码的翻译，用于链式轮换与 400 直接走 Google 判断。
     */
    public AiTranslateResult userTranslateWithResult(String prompt, String target) {
        String model = switchModel(target);
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();

        GenerationParam param = GenerationParam.builder()
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        try {
            GenerationResult call = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            AppInsightsUtils.trackTrace("FatalException userTranslate call errors ： " + e.getMessage() + " translateText : " + prompt);
                            AppInsightsUtils.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,
                    DEFAULT_MAX_RETRIES);
            if (call == null) {
                return AiTranslateResult.fail(0);
            }
            String content = call.getOutput().getChoices().get(0).getMessage().getContent();
            int totalToken = (int) (call.getUsage().getTotalTokens() * TranslateConstants.MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            AppInsightsUtils.trackTrace("userTranslate 原文本：" + prompt + " 翻译成： " + content +
                    " token ali  all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            return AiTranslateResult.success(content, totalToken);
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException userTranslate errors ： " + e.getMessage() + " translateText : " + prompt);
            AppInsightsUtils.trackException(e);
            int errorCode = isHttp400(e) ? 400 : 0;
            return AiTranslateResult.fail(errorCode);
        }
    }

    private static boolean isHttp400(Throwable e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg != null && (msg.contains("400") || msg.contains("InvalidParameter") || msg.contains("Bad Request"))) {
            return true;
        }
        return isHttp400(e.getCause());
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
                .model(TranslateConstants.QWEN_VL_LAST)
                .message(userMessage)
                .build();
        MultiModalConversationResult result;
        try {
            result = callWithTimeoutAndRetry(() -> {
                        try {
                            return conv.call(param);
                        } catch (Exception e) {
                            AppInsightsUtils.trackTrace("FatalException 每日须看 callWithPicMess 百炼翻译报错信息 errors ： " + e.getMessage() + " picUrl : " + picUrl + " 用户：" + userId);
                            AppInsightsUtils.trackException(e);
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
            int totalToken = (int) ((inputTokens + outputTokens) * TranslateConstants.MAGNIFICATION);
            AppInsightsUtils.printTranslateCost(totalToken, inputTokens, outputTokens);
            AppInsightsUtils.trackTrace("callWithPicMess 用户 " + userId + " token ali-vl : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            //更新用户token计数和对应
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            //更新用户产品计数
            counter.addChars(totalToken);
            return (String) content.get(0).get("text");
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException callWithPicMess 用户 " + userId + " 百炼翻译报错信息 errors ： " + e.getMessage() + " prompt: " + prompt);
            AppInsightsUtils.trackException(e);
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
                            AppInsightsUtils.trackTrace("FatalException 每日须看 callWithQwenMaxToDes 百炼翻译报错信息 errors ： " + e.getMessage() + " prompt : " + prompt + " 用户：" + userId);
                            AppInsightsUtils.trackException(e);
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
            totalToken = (int) (call.getUsage().getTotalTokens() * TranslateConstants.MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            AppInsightsUtils.printTranslateCost(totalToken, inputTokens, outputTokens);
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            AppInsightsUtils.trackTrace("用户 token ali-max : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            countUtils.addChars(totalToken);
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException callWithQwenMaxToDes 百炼翻译报错信息 errors ： " + e.getMessage() + " prompt: " + prompt);
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
                            AppInsightsUtils.trackTrace("FatalException 每日须看 textTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text + " 用户：" + shopName);
                            AppInsightsUtils.trackException(e);
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
            totalToken = (int) (call.getUsage().getTotalTokens() * TranslateConstants.MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            AppInsightsUtils.trackTrace("textTranslate " + shopName + " 用户 原文本：" + text + " 翻译成： " + content + " token ali: " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            printTranslateCost(totalToken, inputTokens, outputTokens);
//            pcUsersRepo.updateUsedPointsByShopName(shopName, PCUserPicturesService.APP_ALT_FEE);

        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException textTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text);
            AppInsightsUtils.trackException(e);
            return null;
        }
        return content;
    }
}
