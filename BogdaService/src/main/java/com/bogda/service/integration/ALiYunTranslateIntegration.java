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
    public static String QWEN_MAX = "qwen-max";

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
            default -> "qwen-max-latest"; //32k token
        };
//        return "qwen-max";
    }

    public Pair<String, Integer> userTranslate(String prompt, String target, double magnification) {
        List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", prompt));
        return userTranslate(messages, target, magnification, null);
    }

    public Pair<String, Integer> userTranslate(List<Map<String, String>> messageItems, String target,
                                               double magnification, String sessionId) {
        String model = switchModel(target);
        Generation gen = new Generation();
        List<Message> messages = new java.util.ArrayList<>();
        for (Map<String, String> item : messageItems) {
            if (item == null) {
                continue;
            }
            String content = item.get("content");
            if (content == null || content.isEmpty()) {
                continue;
            }
            String role = item.getOrDefault("role", "user");
            if (!"assistant".equalsIgnoreCase(role) && !"system".equalsIgnoreCase(role)) {
                role = Role.USER.getValue();
            }
            Message message = Message.builder()
                    .role(role)
                    .content(content)
                    .build();
            messages.add(message);
        }
        if (messages.isEmpty()) {
            return null;
        }

        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(ConfigUtils.getConfig("BAILIAN_API_KEY"))
                .model(model)
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        try {
            GenerationResult call = TimeOutUtils.callWithTimeoutAndRetry(() -> {
                        try {
                            return gen.call(param);
                        } catch (Exception e) {
                            TraceReporterHolder.report("ALiYunTranslateIntegration.userTranslate", "FatalException 飞书机器人报错 userTranslate call errors ： " + e.getMessage() +
                                    " sessionId : " + sessionId);
                            ExceptionReporterHolder.report("ALiYunTranslateIntegration.userTranslate", e);
                            feiShuRobotIntegration.sendMessage("FatalException userTranslate call errors ： " + e.getMessage() + " sessionId : " + sessionId);
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

            int totalToken = (int) (call.getUsage().getTotalTokens() * magnification);
            Integer inputTokens = call.getUsage().getInputTokens();
            Integer outputTokens = call.getUsage().getOutputTokens();
            TraceReporterHolder.report("ALiYunTranslateIntegration.userTranslate", "userTranslate 翻译成： " + content +
                    " token ali  all: " + totalToken + " input: " + inputTokens + " output: " + outputTokens
                    + " sessionId: " + sessionId + " messagesSize: " + messages.size());
            return new Pair<>(content, totalToken);
        } catch (Exception e) {
            TraceReporterHolder.report("ALiYunTranslateIntegration.userTranslate", "FatalException 飞书机器人报错 userTranslate errors ： " + e.getMessage() + " sessionId : " + sessionId);
            ExceptionReporterHolder.report("ALiYunTranslateIntegration.userTranslate", e);
            feiShuRobotIntegration.sendMessage("FatalException userTranslate call errors ： " + e.getMessage() + " sessionId : " + sessionId);
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
                .model(TranslateConstants.QWEN_VL_LAST)
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
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
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
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
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
}
