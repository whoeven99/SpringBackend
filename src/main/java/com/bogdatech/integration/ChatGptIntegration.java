package com.bogdatech.integration;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.AzureKeyCredential;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.bogdatech.constants.TranslateConstants.OPENAI_MAGNIFICATION;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;

@Component
public class ChatGptIntegration {

    @Value("${gpt.endpoint}")
    private String endpoint;
    @Value("${gpt.apiKey}")
    private String apiKey;
    @Value("${gpt.deploymentName}")
    private String deploymentName;

    private final ITranslationCounterService translationCounterService;
    @Autowired
    public ChatGptIntegration(ITranslationCounterService translationCounterService) {
        this.translationCounterService = translationCounterService;
    }

    /**
     * Azure服务器调用gpt
     * */
    public String chatWithGpt(String prompt, String sourceText, TranslateRequest request, CharacterCountUtils counter, Integer limitChars) {
        // 使用基于密钥的身份验证来初始化 OpenAI 客户端
        OpenAIClient client = new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .buildClient();

        // 模拟聊天交互
        ChatMessage messagereq = new ChatMessage(ChatRole.USER);
        ChatMessage messagersy= new ChatMessage(ChatRole.SYSTEM);
        messagersy.setContent(prompt);
        messagereq.setContent(sourceText);

        List<ChatMessage> prompts = new ArrayList<> ();
        prompts.add(messagereq);
        prompts.add(messagersy);
        ChatCompletionsOptions options = new ChatCompletionsOptions(prompts)
                .setMaxTokens(16000)
                .setTemperature(0.7)
                .setTopP(0.95)
                .setFrequencyPenalty(0.0)
                .setPresencePenalty(0.0)
                .setStream(false);

        String content = null;
        int retryCount = 0;
        final int maxRetries = 3;
        while (retryCount < maxRetries) {
            try {

                ChatCompletions chatCompletions = client.getChatCompletions(deploymentName, options);
                content = chatCompletions.getChoices().get(0).getMessage().getContent();
                int allToken = chatCompletions.getUsage().getTotalTokens() * OPENAI_MAGNIFICATION;
                int promptToken = chatCompletions.getUsage().getPromptTokens();
                int completionToken = chatCompletions.getUsage().getCompletionTokens();
                appInsights.trackTrace( "用户： " + request.getShopName() + " 翻译的文本： " + sourceText + " token openai : " + request.getTarget() + " all: " + allToken + " promptToken : " + promptToken + " completionToken : " + completionToken);
                Map<String, Object> translationStatusMap = getTranslationStatusMap(sourceText, 2);
                userTranslate.put(request.getShopName(), translationStatusMap);
                translationCounterService.updateAddUsedCharsByShopName(request.getShopName(), allToken, limitChars);
                counter.addChars(allToken);
                return content;
            } catch (Exception e) {
                retryCount++;
                appInsights.trackTrace("Error occurred while calling GPT: " + e.getMessage());
                if (retryCount >= 2){
                        // 如果重试次数超过2次，则修改翻译状态为4 ：翻译异常，终止翻译流程。
                        throw new ClientException("Translation openai exception errors ");
                    }
            }
        }
        return content;
    }

}
