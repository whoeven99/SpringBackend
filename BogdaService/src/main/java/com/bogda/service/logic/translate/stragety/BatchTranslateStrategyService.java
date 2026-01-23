package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.logic.translate.ModelTranslateService;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.PromptUtils;
import com.bogda.common.utils.StringUtils;
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

        // 1. 构建 actuallyTranslateMap(序号1..n -> 唯一原文)、translateMappingMap(originalTextMap.key -> actuallyTranslateMap.key)
        Map<Integer, String> actuallyTranslateMap = new LinkedHashMap<>();
        Map<Integer, Integer> translateMappingMap = new HashMap<>();
        Map<String, Integer> textToSeq = new HashMap<>(); // 原文 → 序号，用于构建 translateMappingMap
        Map<String, Long> textOccurrences = new HashMap<>(); // 同一文本出现的次数

        List<String> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String v : originalTextMap.values()) {
            if (v != null && !v.isEmpty()) {
                textOccurrences.merge(v, 1L, Long::sum);
                if (seen.add(v)) {
                    unique.add(v);
                }
            }
        }
        System.out.println("seen 1 : " + seen);
        for (int i = 0; i < unique.size(); i++) {
            int seq = i + 1;
            String t = unique.get(i);
            actuallyTranslateMap.put(seq, t);
            textToSeq.put(t, seq);
        }
        System.out.println("textToSeq 2 : " + textToSeq);
        for (Map.Entry<Integer, String> e : originalTextMap.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                translateMappingMap.put(e.getKey(), textToSeq.get(e.getValue()));
            }
        }
        System.out.println("translateMappingMap 3 : " + translateMappingMap);
        Map<Integer, String> translatedResultMap = new HashMap<>();
        List<Integer> glossarySeqList = new ArrayList<>();
        List<Integer> aiSeqList = new ArrayList<>();

        // 2. 对 actuallyTranslateMap 走缓存、词汇表、分类待 AI
        for (Map.Entry<Integer, String> e : actuallyTranslateMap.entrySet()) {
            int seq = e.getKey();
            String text = e.getValue();
            String cached = redisProcessService.getCacheData(target, text);
            if (cached != null) {
                translatedResultMap.put(seq, cached);
                long occ = textOccurrences.getOrDefault(text, 1L);
                for (int i = 0; i < occ; i++) {
                    translateTaskMonitorV2RedisService.addCacheCount(text);
                    ctx.incrementCachedCount();
                }
                continue;
            }

            String glossaryed = GlossaryService.match(text, glossaryMap);
            if (glossaryed != null) {
                translatedResultMap.put(seq, glossaryed);
                for (int i = 0; i < textOccurrences.getOrDefault(text, 1L); i++) {
                    ctx.incrementGlossaryCount();
                }
                continue;
            }
            if (GlossaryService.hasGlossary(text, glossaryMap, ctx.getUsedGlossaryMap())) {
                glossarySeqList.add(seq);
                continue;
            }
            aiSeqList.add(seq);
        }

        System.out.println("aiSeqList 4 : " + aiSeqList);
        // 3. 翻译 glossarySeqList（词汇表+AI）
        if (!glossarySeqList.isEmpty()) {
            Map<Integer, String> glossMap = new HashMap<>();
            for (Integer seq : glossarySeqList) {
                glossMap.put(seq, actuallyTranslateMap.get(seq));
            }
            String glossaryMapping = GlossaryService.convertMapToText(ctx.getUsedGlossaryMap(), String.join(" ", glossMap.values()));
            String prompt = PromptUtils.GlossaryJsonPrompt(target, glossaryMapping, glossMap);
            Pair<Map<Integer, String>, Integer> pair = batchTranslate(prompt, target, ctx.getAiModel(), glossMap);
            if (pair == null) {
                return;
            }
            ctx.incrementUsedTokenCount(pair.getSecond());
            pair.getFirst().forEach((s, tr) -> {
                translatedResultMap.put(s, tr);
                redisProcessService.setCacheData(target, tr, actuallyTranslateMap.get(s));
            });
            for (Integer seq : glossarySeqList) {
                for (int i = 0; i < textOccurrences.getOrDefault(actuallyTranslateMap.get(seq), 1L); i++) {
                    ctx.incrementGlossaryCount();
                }
            }
        }

        System.out.println("translatedResultMap 5 : " + translatedResultMap);
        // 4. 翻译 aiSeqList（纯AI），按 600 字符分批
        Map<Integer, String> subMap = new HashMap<>();
        int totalChars = 0;
        for (Integer seq : aiSeqList) {
            String t = actuallyTranslateMap.get(seq);
            subMap.put(seq, t);
            totalChars += ALiYunTranslateIntegration.calculateBaiLianToken(t);
            if (totalChars < 600) {
                continue;
            }
            String prompt = PromptUtils.JsonPrompt(target, subMap);
            Pair<Map<Integer, String>, Integer> pair = batchTranslate(prompt, target, ctx.getAiModel(), subMap);
            if (pair == null) {
                return;
            }
            ctx.incrementUsedTokenCount(pair.getSecond());
            pair.getFirst().forEach((s, tr) -> {
                translatedResultMap.put(s, tr);
                redisProcessService.setCacheData(target, tr, actuallyTranslateMap.get(s));
            });
            totalChars = 0;
            subMap.clear();
        }
        System.out.println("translatedResultMap 6 : " + translatedResultMap);
        if (!subMap.isEmpty()) {
            String prompt = PromptUtils.JsonPrompt(target, subMap);
            Pair<Map<Integer, String>, Integer> pair = batchTranslate(prompt, target, ctx.getAiModel(), subMap);
            if (pair == null) {
                return;
            }
            ctx.incrementUsedTokenCount(pair.getSecond());
            pair.getFirst().forEach((s, tr) -> {
                translatedResultMap.put(s, tr);
                redisProcessService.setCacheData(target, tr, actuallyTranslateMap.get(s));
            });
        }
        System.out.println("translatedResultMap 6 : " + translatedResultMap);
        // 5. 通过 translateMappingMap 将 translatedResultMap 与 originalTextMap 对应，写入 TranslatedTextMap
        originalTextMap.forEach((key, value) -> {
            if (value == null || value.isEmpty()) {
                ctx.getTranslatedTextMap().put(key, value);
            } else {
                Integer seq = translateMappingMap.get(key);
                String tr = seq != null ? translatedResultMap.get(seq) : null;
                ctx.getTranslatedTextMap().put(key, tr != null ? tr : value);
            }
        });
        System.out.println("getTranslatedTextMap 7 : "  + ctx.getTranslatedTextMap());
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
