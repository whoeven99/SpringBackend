package com.bogda.service.logic.translate.stragety;

import com.bogda.service.context.TranslateContext;
import com.bogda.service.entity.DO.GlossaryDO;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.translate.ModelTranslateService;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.PlaceholderUtils;
import com.bogda.service.utils.PromptUtils;
import com.bogda.service.utils.StringUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component
public class SingleTranslateStrategyService implements ITranslateStrategyService {
    @Autowired
    private ModelTranslateService modelTranslateService;
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

        if (TranslateConstants.URI.equals(ctx.getShopifyTextType())
                && "handle".equals(ctx.getShopifyTextKey())) {
            String prompt;
            String fixContent = StringUtils.replaceHyphensWithSpaces(value);
            prompt = PlaceholderUtils.getHandlePrompt(target);
            prompt += "The text is: " + fixContent;
            ctx.setStrategy("Handle 长文本翻译");
            ctx.setPrompt(prompt);
//            return;
        }

        // check glossary
        Map<String, GlossaryDO> glossaryMap = ctx.getGlossaryMap();
        if (GlossaryService.hasGlossary(value, glossaryMap, ctx.getUsedGlossaryMap())) {
            String glossaryMappingText = GlossaryService.convertMapToText(ctx.getUsedGlossaryMap(), value);
            String prompt = PromptUtils.GlossarySinglePrompt(target, value, glossaryMappingText);
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

            // 普通 prompt（仅当前面没设置）
            if (ctx.getPrompt() == null) {
                String prompt = PromptUtils.SinglePrompt(target, value);
                ctx.setStrategy("普通单条文本翻译");
                ctx.setPrompt(prompt);
            }
        }

        Pair<String, Integer> pair = modelTranslateService.modelTranslate(ctx.getAiModel(), ctx.getPrompt()
                , ctx.getTargetLanguage(), value);

        if (pair == null) {
            return;
        }

        redisProcessService.setCacheData(target, pair.getFirst(), value);
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
