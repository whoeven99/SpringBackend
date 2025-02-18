package com.bogdatech.integration;


import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.*;
import com.alibaba.dashscope.tokenizers.Tokenizer;
import com.alibaba.dashscope.tokenizers.TokenizerFactory;
import com.aliyun.alimt20181012.models.TranslateGeneralResponse;
import com.aliyun.tea.TeaException;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.ApiCodeUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ALiYunTranslateIntegration {


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


    public static List<String> callWithMessages(String model, List<String> translateTexts, String cueWord) throws ApiException, NoApiKeyException, InputRequiredException {
        Generation gen = new Generation();
        List<Message> list = new ArrayList<Message>();

        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(cueWord)
                .build();
        list.add(systemMsg);
        for (String text : translateTexts
        ) {
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
        String content = gen.call(param).getOutput().getChoices().get(0).getMessage().getContent();
//        System.out.println("content1: " + content);
        //将content进行处理，转换为List<String>返回。
        return stringToList(content);

    }

    public static String callWithMessage(String model, String translateText, String cueWord) throws ApiException, NoApiKeyException, InputRequiredException {
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
                .apiKey(System.getenv("BAILIAN_API_KEY"))
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
//        return gen.call(param);

        return gen.call(param).getOutput().getChoices().get(0).getMessage().getContent();
    }

    public static List<String> stringToList(String context) {
        //对返回的数据做处理，只要[]里面的数据
        System.out.println("context: " + context);
        String regex = "\\[(.*?)\\]";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL); // 使用 Pattern.DOTALL 让点号匹配换行符
        Matcher matcher = pattern.matcher(context);

        List<String> list = null;
        if (matcher.find()) {
            // 提取并输出方括号中的内容
            String extractedData = matcher.group(1);
            // 使用 Jackson 来解析 JSON 数组为 List<String>
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                // 解析提取出来的 JSON 数组字符串为 List<String>
                list = objectMapper.readValue("[" + extractedData + "]", List.class);

                // 输出 List<String>
                System.out.println("Extracted List: " + list);
            } catch (JsonMappingException e) {
                throw new RuntimeException(e);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        // 打印 List 内容
        assert list != null;
        for (String item : list) {
            System.out.println(item);
        }
        return list;
    }

    public static Integer calculateBaiLianToken(String text){
        Tokenizer tokenizer = TokenizerFactory.qwen();
        try {
            List<Integer> ids = tokenizer.encode(text, "all");
            String decodedString = tokenizer.decode(ids);
            System.out.println(decodedString);
            return tokenizer.encode(text, "all").size();
        } catch (NoSpecialTokenExists | UnSupportedSpecialTokenMode e) {
            throw new RuntimeException(e);
        }
    }
}
