package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.GlossaryDO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.logic.GlossaryService;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.utils.PromptUtils;
import com.bogdatech.utils.StringUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class BatchTranslateStrategyService implements ITranslateStrategyService {
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;

    @Override
    public String getType() {
        return "BATCH";
    }

    @Override
    public void translate(TranslateContext ctx) {
        String target = ctx.getTargetLanguage();
        Map<Integer, String> originalTextMap = ctx.getOriginalTextMap();
        Map<String, GlossaryDO> glossaryMap = ctx.getGlossaryMap();

        // Glossary + Cache
        originalTextMap.forEach((key, value) -> {
            // 1. 先过滤和提取glossary的
            if (GlossaryService.hasGlossary(value, glossaryMap, ctx.getUsedGlossaryMap())) {
                ctx.getGlossaryTextMap().put(key, value);
                ctx.incrementGlossaryCount();
            } else {
                // 2. 再过缓存
                String cachedValue = redisProcessService.getCacheData(target, value);
                if (cachedValue != null) {
                    ctx.getTranslatedTextMap().put(key, cachedValue);
                    ctx.incrementCachedCount();
                } else {
                    ctx.getUncachedTextMap().put(key, value);
                }
            }
        });

        // 翻译 glossary
        if (!ctx.getGlossaryTextMap().isEmpty()) {
            // 处理下词汇表的映射关系
            String glossaryMapping = GlossaryService.convertMapToText(ctx.getUsedGlossaryMap(),
                    String.join(" ", ctx.getGlossaryTextMap().values()));
            String prompt = PromptUtils.GlossaryJsonPrompt(target, glossaryMapping, ctx.getGlossaryTextMap());
            Pair<Map<Integer, String>, Integer> pair = batchTranslate(prompt, target);
            if (pair == null) {
                // fatalException
                return;
            }
            ctx.incrementUsedTokenCount(pair.getSecond());
            ctx.getTranslatedTextMap().putAll(pair.getFirst());
        }

        // 翻译普通json
        if (ctx.getUncachedTextMap().isEmpty()) {
            return;
        }
        String prompt = PromptUtils.JsonPrompt(target, ctx.getUncachedTextMap());
        Pair<Map<Integer, String>, Integer> pair = batchTranslate(prompt, target);
        if (pair == null) {
            // fatalException
            return;
        }
        ctx.setStrategy("Batch json 翻译");
        ctx.incrementUsedTokenCount(pair.getSecond());
        ctx.getTranslatedTextMap().putAll(pair.getFirst());
    }

    public void finishAndGetJsonRecord(TranslateContext ctx) {
        ctx.finish();
        Map<String, String> variable = new HashMap<>();
        variable.put("strategy", ctx.getStrategy());
        variable.put("usedToken", String.valueOf(ctx.getUsedToken()));
        variable.put("translatedTime", String.valueOf(ctx.getTranslatedTime()));
        variable.put("cachedCount", String.valueOf(ctx.getCachedCount()));
        variable.put("glossaryCount", String.valueOf(ctx.getGlossaryCount()));
        variable.put("translatedChars", String.valueOf(ctx.getTranslatedChars()));
        ctx.setTranslateVariables(variable);
    }

    private Pair<Map<Integer, String>, Integer> batchTranslate(String prompt, String target) {
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(prompt, target);
        if (pair == null) {
            // fatalException
            return null;
        }
        String aiResponse = pair.getFirst();

        // 修改解析的逻辑
        Map<Integer, String> translatedValueMap = StringUtils.parseOutputTransactionV2(aiResponse);
        if (translatedValueMap == null || translatedValueMap.isEmpty()) {
            // fatalException
            return null;
        }
        return new Pair<>(translatedValueMap, pair.getSecond());
    }
}
