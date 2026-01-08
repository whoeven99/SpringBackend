package com.bogda.service.logic.translate.stragety;

import com.bogda.service.context.TranslateContext;
import com.bogda.service.entity.DO.GlossaryDO;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.logic.translate.ModelTranslateService;
import com.bogda.common.utils.JsonUtils;
import com.bogda.service.utils.PromptUtils;
import com.bogda.service.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BatchTranslateStrategyService implements ITranslateStrategyService {
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private ModelTranslateService modelTranslateService;
    @Autowired
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;

    @Override
    public String getType() {
        return "BATCH";
    }

    @Override
    public void translate(TranslateContext ctx) {
        ctx.setStrategy("Batch json 翻译");
        String target = ctx.getTargetLanguage();
        Map<Integer, String> originalTextMap = ctx.getOriginalTextMap();
        Map<String, GlossaryDO> glossaryMap = ctx.getGlossaryMap();

        // Cache + Glossary
        originalTextMap.forEach((key, value) -> {
            // 1. 先检查缓存
            String cachedValue = redisProcessService.getCacheData(target, value);
            if (cachedValue != null) {
                translateTaskMonitorV2RedisService.addCacheCount(value);
                ctx.getTranslatedTextMap().put(key, cachedValue);
                ctx.incrementCachedCount();
            } else {
                // 2. 再检查词汇表匹配
                String glossaryed = GlossaryService.match(value, glossaryMap);
                if (glossaryed != null) {
                    ctx.incrementGlossaryCount();
                    ctx.getTranslatedTextMap().put(key, glossaryed);
                } else if (GlossaryService.hasGlossary(value, glossaryMap, ctx.getUsedGlossaryMap())) {
                    ctx.getGlossaryTextMap().put(key, value);
                    ctx.incrementGlossaryCount();
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
            Pair<Map<Integer, String>, Integer> pair = batchTranslate(prompt, target, ctx.getAiModel(), ctx.getGlossaryTextMap());
            if (pair == null) {
                // fatalException
                return;
            }
            ctx.incrementUsedTokenCount(pair.getSecond());
            ctx.getTranslatedTextMap().putAll(pair.getFirst());

            // 将词汇表翻译的结果存入缓存
            pair.getFirst().forEach((key, value) -> {
                redisProcessService.setCacheData(target, value, ctx.getGlossaryTextMap().get(key));
            });
        }

        // 翻译普通json
        if (ctx.getUncachedTextMap().isEmpty()) {
            return;
        }

        // 防止ctx.getUncachedTextMap()太多，需要拆分几次调用ai
        Map<Integer, String> subMap = new HashMap<>();
        int totalChars = 0;
        for (Map.Entry<Integer, String> entry : ctx.getUncachedTextMap().entrySet()) {
            subMap.put(entry.getKey(), entry.getValue());
            totalChars += ALiYunTranslateIntegration.calculateBaiLianToken(entry.getValue());
            if (totalChars < 600) {
                continue;
            }

            // 调用一次翻译
            String prompt = PromptUtils.JsonPrompt(target, subMap);
            Pair<Map<Integer, String>, Integer> pair = batchTranslate(prompt, target, ctx.getAiModel(), subMap);
            if (pair == null) {
                // fatalException 返回重新调用翻译，后续有更好的处理办法
                return;
            }
            ctx.getTranslatedTextMap().putAll(pair.getFirst());
            ctx.incrementUsedTokenCount(pair.getSecond());
            pair.getFirst().forEach((key, value) -> {
                redisProcessService.setCacheData(target, value, ctx.getUncachedTextMap().get(key));
            });
            totalChars = 0;
            subMap.clear();
        }

        if (!subMap.isEmpty()) {
            String prompt = PromptUtils.JsonPrompt(target, subMap);
            Pair<Map<Integer, String>, Integer> pair = batchTranslate(prompt, target, ctx.getAiModel(), subMap);
            if (pair == null) {
                // fatalException 返回重新调用翻译，后续有更好的处理办法
                return;
            }
            ctx.getTranslatedTextMap().putAll(pair.getFirst());
            ctx.incrementUsedTokenCount(pair.getSecond());
            pair.getFirst().forEach((key, value) -> {
                redisProcessService.setCacheData(target, value, ctx.getUncachedTextMap().get(key));
            });
        }
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

    private Pair<Map<Integer, String>, Integer> batchTranslate(String prompt, String target, String aiModel, Map<Integer, String> sourceMap) {
        Pair<String, Integer> pair = modelTranslateService.modelTranslate(aiModel, prompt, target, sourceMap);
        if (pair == null) {
            return null;
        }
        String aiResponse = pair.getFirst();

        // 修改解析的逻辑
        Map<Integer, String> translatedValueMap = parseOutput(aiResponse);
        if (translatedValueMap == null || translatedValueMap.isEmpty()) {
            // fatalException
            return null;
        }
        return new Pair<>(translatedValueMap, pair.getSecond());
    }

    public static LinkedHashMap<Integer, String> parseOutput(String input) {
        if (input == null) {
            return null;
        }

        // 预处理 - 提取 JSON 部分
        String jsonPart = StringUtils.extractJsonBlock(input);

        if (jsonPart == null) {
            return null;
        }

        // 解析为 Map
        LinkedHashMap<Integer, String> map = JsonUtils.jsonToObjectWithNull(jsonPart, new TypeReference<LinkedHashMap<Integer, String>>() {
        });

        if (map == null) {
            return null;
        }

        // 过滤空值
        map.entrySet().removeIf(e -> e.getValue() == null || e.getValue().trim().isEmpty());
        return map;
    }
}
