package com.bogda.api.logic.translate.stragety;

import com.bogda.api.context.TranslateContext;
import com.bogda.api.entity.DO.GlossaryDO;
import com.bogda.api.logic.GlossaryService;
import com.bogda.api.logic.RedisProcessService;
import com.bogda.api.logic.translate.modelStragety.TranslateModelStrategyFactory;
import com.bogda.api.utils.PlaceholderUtils;
import com.bogda.api.utils.PromptUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.bogda.api.constants.TranslateConstants.URI;

@Component
public class SingleTranslateStrategyService implements ITranslateStrategyService {
    @Autowired
    private TranslateModelStrategyFactory translateModelStrategyFactory;
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
            String fixContent = com.bogda.api.utils.StringUtils.replaceHyphensWithSpaces(value);
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

        Pair<String, Integer> pair = translateModelStrategyFactory.getTranslateModelStrategy
                (ctx.getAiModel()).chooseModelTranslate(ctx.getPrompt(), ctx.getTargetLanguage());

        if (pair == null) {
            // 做一个保底处理，当pair为null的时候，用qwen-max再翻译一次，如果再为null，就直接返回
            pair = translateModelStrategyFactory.guaranteedTranslation(ctx.getAiModel(), ctx.getPrompt(), target);
            if (pair == null) {
                return;
            }
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
