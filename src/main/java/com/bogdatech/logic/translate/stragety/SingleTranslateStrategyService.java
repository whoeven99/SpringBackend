package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.GlossaryDO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.logic.GlossaryService;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.utils.JsoupUtils;
import com.bogdatech.utils.PlaceholderUtils;
import com.bogdatech.utils.PromptUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.URI;

@Component
public class SingleTranslateStrategyService implements ITranslateStrategyService {
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private RedisProcessService redisProcessService;

    @Override
    public String getType() {
        return "SINGLE";
    }

    @Override
    public void translate(TranslateContext ctx) {
        String value = ctx.getContent();
        String target = ctx.getTargetLanguage();

        if (URI.equals(ctx.getShopifyTextType())
                && "handle".equals(ctx.getShopifyTextKey())) {
            String prompt;
            String fixContent = com.bogdatech.utils.StringUtils.replaceHyphensWithSpaces(value);
            prompt = PlaceholderUtils.getHandlePrompt(target);
            prompt += "The text is: " + fixContent;
            ctx.setStrategy("Handle 长文本翻译");
            ctx.setPrompt(prompt);
            return;
        }

        // check glossary
        Map<String, GlossaryDO> glossaryMap = ctx.getGlossaryMap();
        if (GlossaryService.hasGlossary(value, glossaryMap, ctx.getUsedGlossaryMap())) {
            Map<Integer, String> map = Collections.singletonMap(0, value);
            String glossaryMapping = JsoupUtils.glossaryTextV2(ctx.getUsedGlossaryMap(), map);
            String prompt = PromptUtils.GlossarySinglePrompt(target, value, glossaryMapping);
            ctx.setStrategy("语法表单条翻译");
            ctx.setPrompt(prompt);
        } else {
            String cachedValue = redisProcessService.getCacheData(target, value);
            if (cachedValue != null) {
                ctx.setCached(true);
                ctx.setStrategy("普通单条文本翻译-缓存命中");
                ctx.setTranslatedContent(cachedValue);
                return;
            }

            String prompt = PromptUtils.SinglePrompt(target, value);
            ctx.setStrategy("普通单条文本翻译");
            ctx.setPrompt(prompt);
        }

        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(ctx.getPrompt(), ctx.getTargetLanguage());
        if (pair == null) {
            // fatalException
            return;
        }
        ctx.setUsedToken(pair.getSecond());
        ctx.setTranslatedContent(pair.getFirst());
    }

    public void finishAndGetJsonRecord(TranslateContext ctx) {
        ctx.finish();
        Map<String, String> variable = new HashMap<>();
        variable.put("strategy", ctx.getStrategy());
        variable.put("usedToken", String.valueOf(ctx.getUsedToken()));
        variable.put("translatedTime", String.valueOf(ctx.getTranslatedTime()));
        variable.put("isCached", String.valueOf(ctx.isCached()));
        variable.put("translatedChars", String.valueOf(ctx.getTranslatedChars()));

        if (!ctx.getUsedGlossaryMap().isEmpty()) {
            variable.put("usedGlossary", String.join(",", ctx.getUsedGlossaryMap().keySet()));
        }
        ctx.setTranslateVariables(variable);
    }
}
