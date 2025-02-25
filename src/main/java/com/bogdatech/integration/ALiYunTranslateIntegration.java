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
import com.aliyun.alimt20181012.models.TranslateGeneralResponse;
import com.aliyun.tea.TeaException;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.ApiCodeUtils;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.ValueWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.CONTENTS;
import static com.bogdatech.constants.TranslateConstants.TOTAL_TOKEN;

@Component
public class ALiYunTranslateIntegration {
    static TelemetryClient appInsights = new TelemetryClient();

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

    public String aliyunTranslate(TranslateRequest translateRequest) {
        //将传入的target转为阿里云要用的target
        String aliTarget = ApiCodeUtils.aliyunTransformCode(translateRequest.getTarget());
        if (aliTarget.equals("#N/A")) {
            return "Alibaba Cloud does not support this language";
        }
        com.aliyun.alimt20181012.Client client = createClient();
        com.aliyun.alimt20181012.models.TranslateGeneralRequest translateGeneralRequest = new com.aliyun.alimt20181012.models.TranslateGeneralRequest()
                .setFormatType("text")
                .setSourceLanguage(translateRequest.getSource())
                .setTargetLanguage(aliTarget)
                .setSourceText(translateRequest.getContent())
                .setScene("general")
//                .setContext("")
                ;
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        try {
            // 复制代码运行请自行打印 API 的返回值
            TranslateGeneralResponse translateGeneralResponse = client.translateGeneralWithOptions(translateGeneralRequest, runtime);
//            System.out.println("translateGeneralResponse: " + translateGeneralResponse.getBody().getData().translated);
            return translateGeneralResponse.getBody().getData().translated;
        } catch (TeaException error) {
            // 此处仅做打印展示，请谨慎对待异常处理，在工程项目中切勿直接忽略异常。
            // 错误 message
//            System.out.println(error.getMessage());
//            // 诊断地址
//            System.out.println(error.getData().get("Recommend"));
            com.aliyun.teautil.Common.assertAsString(error.message);
            throw new RuntimeException(error);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 此处仅做打印展示，请谨慎对待异常处理，在工程项目中切勿直接忽略异常。
            // 错误 message
//            System.out.println(error.getMessage());
//            // 诊断地址
//            System.out.println(error.getData().get("Recommend"));
            com.aliyun.teautil.Common.assertAsString(error.message);
            throw new RuntimeException(error);
        }
    }


    //设定好提示词
    public static String cueWord(String target, String type) {
        return "Translate "+ type + " data from e-commerce websites accurately into language code: " + target + ". Return the results as a List<String> where each translated text field is enclosed in double quotes \"\", containing only the translated text without additional text; if translation is not possible, output the original value unchanged";
    }

    //单文本翻译的提示词
    public static String cueWordSingle(String target, String type) {
        return "Translate " + type +" data from e-commerce websites accurately into language code: " + target + " . Return the results as a String containing only the translated text fields without additional text or punctuation, ensuring complete translation of all content.";
    }

    //单文本翻译

    public static String singleTranslate(String model, String text, String type,  CharacterCountUtils countUtils, String target) {
        Generation gen = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(cueWordSingle(target , type))
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(text)
                .build();
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey("sk-f19edb7ec87f46d2913cf50eb44e1781")
//                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        String content = null;
        Integer totalToken = null;
        try {
            GenerationResult call = gen.call(param);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            System.out.println("single_content: " + content);
            totalToken = call.getUsage().getTotalTokens();
            countUtils.addChars(totalToken);
        } catch (NoApiKeyException | InputRequiredException e) {
            appInsights.trackTrace("百炼翻译报错信息： " + e.getMessage());
            System.out.println("百炼翻译报错信息： " + e.getMessage());
//            throw new RuntimeException(e);
        }
        //获得该list的size
        //将content进行处理，转换为List<String>返回。

        return content;
    }
    //多文本翻译
    public static Map<String, ValueWrapper> callWithMessages(String model, List<String> translateTexts, String cueWord) {
        Generation gen = new Generation();
        List<Message> list = new ArrayList<Message>();

        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(cueWord)
                .build();
        list.add(systemMsg);
        for (String text : translateTexts
        ) {
            System.out.println("text : " + text);
            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(text)
                    .build();
            list.add(userMsg);
        }

        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model(model)
                .messages(list)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        String content = null;
        Integer totalToken = null;
        try {
            GenerationResult call = gen.call(param);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            System.out.println("content: " + content);
            totalToken = call.getUsage().getTotalTokens();
            System.out.println("totalToken: " + totalToken);
        } catch (NoApiKeyException | InputRequiredException e) {
            System.out.println("error: " + e.getMessage());
            //TODO： 需要做一个尝试机制 3次
//            throw new RuntimeException(e);
        }
        //获得该list的size
        //将content进行处理，转换为List<String>返回。
        List<String> strings = stringToList(content);
        Map<String, ValueWrapper> map = new HashMap<>();
        map.put(CONTENTS, new ValueWrapper(strings));
        map.put(TOTAL_TOKEN, new ValueWrapper(totalToken));
        return map;
    }

    //多文本翻译
    public static List<String> callWithMessage(String model, String translateText, String cueWord, CharacterCountUtils countUtils) {
        System.out.println("打印是否是集合[]: " + translateText);

        Generation gen = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(cueWord)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(translateText)
                .build();
        GenerationParam param = GenerationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey("sk-f19edb7ec87f46d2913cf50eb44e1781")
//                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        String content = null;
        Integer totalToken = null;
        try {
            GenerationResult call = gen.call(param);
            content = call.getOutput().getChoices().get(0).getMessage().getContent();
            System.out.println("content: " + content);
            totalToken = call.getUsage().getTotalTokens();
            countUtils.addChars(totalToken);
            System.out.println("totalToken: " + totalToken);
        } catch (NoApiKeyException | InputRequiredException e) {
            System.out.println("百炼翻译报错信息： " + e.getMessage());
            appInsights.trackTrace("百炼翻译报错信息： " + e.getMessage());
//            throw new RuntimeException(e);
        }
        //获得该list的size
        //将content进行处理，转换为List<String>返回。

        return stringToList(content);

    }

    //单文本翻译

    /**
     * 将字符串转换为 List<String>，提取方括号中的内容并解析为列表
     *
     * @param context 输入字符串，预期包含方括号包裹的内容
     * @return 解析后的字符串列表
     * @throws RuntimeException 如果解析失败，抛出运行时异常
     */
    public static List<String> stringToList(String context) {
        // 如果输入为空，直接返回空列表
        if (context == null || context.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 定义正则表达式，提取方括号中的内容
        String regex = "\\[(.*?)\\]";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL); // DOTALL 让点号匹配换行符
        Matcher matcher = pattern.matcher(context);

        List<String> lists = new ArrayList<>(); // 初始化列表，避免 null
        ObjectMapper objectMapper = new ObjectMapper(); // 创建 Jackson 的 ObjectMapper

        if (matcher.find()) {

            // 提取方括号中的内容
            String extractedData = matcher.group(1);
            System.out.println("extractedData: " + extractedData);

            //先直接转换成list，如果不行，改为手动拆分
            try {
                lists = objectMapper.readValue("[" + extractedData + "]", List.class);
                System.out.println("first_list: " + lists);
            } catch (JsonProcessingException e) {
                // 手动拆分，按逗号分隔并清理引号
                String[] items = extractedData.split("\\n,\\s*");

                //返回手动拆分的结果
                for (String item : items) {
                    String cleanedItem = item.trim().replaceAll("^\"|\"$", "");
                    lists.add(cleanedItem);
                }

            }
        } else {
            throw new RuntimeException("No valid content found between brackets in: " + context);
        }

        // 打印列表内容
        for (String item : lists) {
            System.out.println("item: " + item);
        }
        return lists;
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
