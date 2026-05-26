package com.bogda.service.integration;


import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.NoSpecialTokenExists;
import com.alibaba.dashscope.exception.UnSupportedSpecialTokenMode;
import com.alibaba.dashscope.tokenizers.Tokenizer;
import com.alibaba.dashscope.tokenizers.TokenizerFactory;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import com.bogda.service.Service.IAPGUserCounterService;
import com.bogda.common.contants.TranslateConstants;
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
import static com.bogda.common.utils.TimeOutUtils.*;

@Component
public class ALiYunTranslateIntegration {
    @Autowired
    private IAPGUserCounterService iapgUserCounterService;
    @Autowired
    private FeiShuRobotIntegration feiShuRobotIntegration;

    private static Tokenizer tokenizer;
    public static String TRANSLATE_APP = "TRANSLATE_APP";
    public static String QWEN_PLUS = "qwen3.6-plus";

    public ALiYunTranslateIntegration() {
        tokenizer = TokenizerFactory.qwen();
    }

    public static Integer calculateBaiLianToken(String text) {
        try {
            return tokenizer.encode(text, "all").size();
        } catch (NoSpecialTokenExists | UnSupportedSpecialTokenMode e) {
            ExceptionReporterHolder.report("ALiYunTranslateIntegration.calculateBaiLianToken", e);
            TraceReporterHolder.report("ALiYunTranslateIntegration.calculateBaiLianToken", "FatalException calculateBaiLianToken " + e.getMessage() + " 计数失败 text : " + text);
            return text.length();
        }
    }

    // 根据语言代码切换模型
    public static String switchModel(String languageCode) {
        return switch (languageCode) {
//            case "en", "zh-CN", "de", "ja", "it", "ru", "zh-TW", "da", "nl", "id", "th", "vi", "uk", "fr", "ko", "hi", "bg", "cs", "el", "hr", "lt", "nb", "pl", "ro", "sk", "sv", "ar", "no" -> "qwen-plus";
            default -> QWEN_PLUS; //32k token
        };
//        return "qwen-max";
    }

    public Pair<String, Integer> userTranslate(String prompt, String target, double magnification) {
        String model = switchModel(target);
        MultiModalMessage userMsg = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Collections.singletonList(Map.of("text", prompt)))
                .build();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(Collections.singletonList(userMsg))
                .build();

        MultiModalConversation conv = new MultiModalConversation();
        try {
            MultiModalConversationResult call = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                        try {
                            return conv.call(param);
                        } catch (Exception e) {
                            TraceReporterHolder.report("ALiYunTranslateIntegration.userTranslate", "FatalException 飞书机器人报错 userTranslate call errors ： " + e.getMessage() +
                                    " translateText : " + prompt);
                            ExceptionReporterHolder.report("ALiYunTranslateIntegration.userTranslate", e);
                            feiShuRobotIntegration.sendMessage("FatalException userTranslate call errors ： " + e.getMessage() + " prompt : " + prompt);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return null;
            }
            String content = extractTextContent(call);

            int totalToken = (int) (call.getUsage().getTotalTokens() * magnification);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            TraceReporterHolder.report("ALiYunTranslateIntegration.userTranslate", "userTranslate 提示词 ：" + prompt + " 翻译成： " + content +
                    " token ali  all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            return new Pair<>(content, totalToken);
        } catch (Exception e) {
            TraceReporterHolder.report("ALiYunTranslateIntegration.userTranslate", "FatalException 飞书机器人报错 userTranslate errors ： " + e.getMessage() + " translateText : " + prompt);
            ExceptionReporterHolder.report("ALiYunTranslateIntegration.userTranslate", e);
            feiShuRobotIntegration.sendMessage("FatalException userTranslate call errors ： " + e.getMessage() + " prompt : " + prompt);
            return null;
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
                .model(QWEN_PLUS)
                .message(userMessage)
                .build();
        MultiModalConversationResult result;
        try {
            result = callWithTimeoutAndRetry(() -> {
                        try {
                            return conv.call(param);
                        } catch (Exception e) {
                            TraceReporterHolder.report("ALiYunTranslateIntegration.callWithPicMess", "FatalException 每日须看 callWithPicMess 百炼翻译报错信息 errors ： " + e.getMessage() + " picUrl : " + picUrl + " 用户：" + userId);
                            ExceptionReporterHolder.report("ALiYunTranslateIntegration.callWithPicMess", e);
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
            TraceReporterHolder.report("ALiYunTranslateIntegration.callWithPicMess", "callWithPicMess 用户 " + userId + " token ali-vl : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            //更新用户token计数和对应
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            //更新用户产品计数
            counter.addChars(totalToken);
            return (String) content.get(0).get("text");
        } catch (Exception e) {
            TraceReporterHolder.report("ALiYunTranslateIntegration.callWithPicMess", "FatalException callWithPicMess 用户 " + userId + " 百炼翻译报错信息 errors ： " + e.getMessage() + " prompt: " + prompt);
            ExceptionReporterHolder.report("ALiYunTranslateIntegration.callWithPicMess", e);
            return null;
        }
    }

    /**
     * 调用qwen3.6-plus用户产品描述图片为空的情况
     */
    public String callWithQwenMaxToDes(String prompt, CharacterCountUtils countUtils, Long userId, Integer userMaxLimit) {
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalMessage userMsg = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Collections.singletonList(Map.of("text", prompt)))
                .build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(QWEN_PLUS)
                .messages(Collections.singletonList(userMsg))
                .build();
        String content;
        int totalToken;
        try {
            MultiModalConversationResult call = callWithTimeoutAndRetry(() -> {
                        try {
                            return conv.call(param);
                        } catch (Exception e) {
                            TraceReporterHolder.report("ALiYunTranslateIntegration.callWithQwenMaxToDes", "FatalException 每日须看 callWithQwenMaxToDes 百炼翻译报错信息 errors ： " + e.getMessage() + " prompt : " + prompt + " 用户：" + userId);
                            ExceptionReporterHolder.report("ALiYunTranslateIntegration.callWithQwenMaxToDes", e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return null;
            }
            content = extractTextContent(call);
            totalToken = (int) (call.getUsage().getTotalTokens() * TranslateConstants.MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            iapgUserCounterService.updateUserUsedCount(userId, totalToken, userMaxLimit);
            TraceReporterHolder.report("ALiYunTranslateIntegration.callWithQwenMaxToDes", "用户 token ali-max : " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
            countUtils.addChars(totalToken);
        } catch (Exception e) {
            TraceReporterHolder.report("ALiYunTranslateIntegration.callWithQwenMaxToDes", "FatalException callWithQwenMaxToDes 百炼翻译报错信息 errors ： " + e.getMessage() + " prompt: " + prompt);
            ExceptionReporterHolder.report("ALiYunTranslateIntegration.callWithQwenMaxToDes", e);
            return null;
        }
        return content;

    }

    public String textTranslate(String text, String prompt, String target, String shopName, Integer limitChars) {
        String model = switchModel(target);
        MultiModalConversation conv = new MultiModalConversation();

        MultiModalMessage systemMsg = MultiModalMessage.builder()
                .role(Role.SYSTEM.getValue())
                .content(Collections.singletonList(Map.of("text", prompt)))
                .build();
        MultiModalMessage userMsg = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Collections.singletonList(Map.of("text", text)))
                .build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .build();
        String content;
        int totalToken;
        try {
            MultiModalConversationResult call = callWithTimeoutAndRetry(() -> {
                        try {
                            return conv.call(param);
                        } catch (Exception e) {
                            TraceReporterHolder.report("ALiYunTranslateIntegration.textTranslate", "FatalException 每日须看 textTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text + " 用户：" + shopName);
                            ExceptionReporterHolder.report("ALiYunTranslateIntegration.textTranslate", e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (call == null) {
                return null;
            }
            content = extractTextContent(call);
            totalToken = (int) (call.getUsage().getTotalTokens() * TranslateConstants.MAGNIFICATION);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            TraceReporterHolder.report("ALiYunTranslateIntegration.textTranslate", "textTranslate " + shopName + " 用户 原文本：" + text + " 翻译成： " + content + " token ali: " + content + " all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens);
        } catch (Exception e) {
            TraceReporterHolder.report("ALiYunTranslateIntegration.textTranslate", "FatalException textTranslate 百炼翻译报错信息 errors ： " + e.getMessage() + " translateText : " + text);
            ExceptionReporterHolder.report("ALiYunTranslateIntegration.textTranslate", e);
            return null;
        }
        return content;
    }

    private static String extractTextContent(MultiModalConversationResult result) {
        List<Map<String, Object>> content = result.getOutput().getChoices().get(0).getMessage().getContent();
        return (String) content.get(0).get("text");
    }
}
