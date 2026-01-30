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

    /** 纯 AI 分批翻译时，每批估算 token 上限（字符维度） */
    private static final int BATCH_CHAR_LIMIT = 600;

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

    /**
     * 批量 JSON 翻译入口。
     * <p>流程：去重 → 缓存/词汇表/待译分类 → 带词汇表批次 AI 翻译 → 纯 AI 分批翻译 → 按原文顺序回填译文。
     *
     * @param ctx 翻译上下文（原文、目标语、词汇表、模型等），结果写入 ctx 的 translatedTextMap
     */
    @Override
    public void translate(TranslateContext ctx) {
        ctx.setStrategy("Batch json 翻译");
        String target = ctx.getTargetLanguage();
        Map<Integer, String> originalTextMap = ctx.getOriginalTextMap();
        Map<String, GlossaryDO> glossaryMap = ctx.getGlossaryMap();

        // 1. 去重并建立映射：唯一原文序号(1..n) ↔ 原文，以及 原文位置 → 序号 actuallyTranslateMap(序号1..n -> 唯一原文)、translateMappingMap(originalTextMap.key -> actuallyTranslateMap.key)
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
        for (int i = 0; i < unique.size(); i++) {
            int seq = i + 1;
            String t = unique.get(i);
            actuallyTranslateMap.put(seq, t);
            textToSeq.put(t, seq);
        }
        for (Map.Entry<Integer, String> e : originalTextMap.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                translateMappingMap.put(e.getKey(), textToSeq.get(e.getValue()));
            }
        }

        Map<Integer, String> translatedResultMap = new HashMap<>();
        List<Integer> glossarySeqList = new ArrayList<>();
        List<Integer> aiSeqList = new ArrayList<>();

        // 2. 按序处理：命中缓存直接填结果；命中词汇表填结果；需带词汇表翻译的入 glossarySeqList；其余入 aiSeqList
        for (Map.Entry<Integer, String> e : actuallyTranslateMap.entrySet()) {
            int seq = e.getKey();
            String text = e.getValue();
            long occ = textOccurrences.getOrDefault(text, 1L);

            String cached = redisProcessService.getCacheData(target, text);
            if (cached != null) {
                translatedResultMap.put(seq, cached);
                for (int i = 0; i < occ; i++) {
                    translateTaskMonitorV2RedisService.addCacheCount(text);
                    ctx.incrementCachedCount();
                }
                continue;
            }

            String glossaryed = GlossaryService.match(text, glossaryMap);
            if (glossaryed != null) {
                translatedResultMap.put(seq, glossaryed);
                for (int i = 0; i < occ; i++) {
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

        // 3. 带词汇表的 AI 批次翻译
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
            applyBatchResult(ctx, pair, actuallyTranslateMap, translatedResultMap, target);
            for (Integer seq : glossarySeqList) {
                long occ = textOccurrences.getOrDefault(actuallyTranslateMap.get(seq), 1L);
                for (int i = 0; i < occ; i++) {
                    ctx.incrementGlossaryCount();
                }
            }
        }

        // 4. 纯 AI 分批翻译（按 BATCH_CHAR_LIMIT 估算 token 分批）
        Map<Integer, String> subMap = new HashMap<>();
        int totalChars = 0;
        for (Integer seq : aiSeqList) {
            String t = actuallyTranslateMap.get(seq);
            subMap.put(seq, t);
            totalChars += ALiYunTranslateIntegration.calculateBaiLianToken(t);
            if (totalChars < BATCH_CHAR_LIMIT) {
                continue;
            }
            Pair<Map<Integer, String>, Integer> pair = batchTranslate(PromptUtils.JsonPrompt(target, subMap), target, ctx.getAiModel(), subMap);
            if (pair == null) {
                return;
            }
            applyBatchResult(ctx, pair, actuallyTranslateMap, translatedResultMap, target);
            totalChars = 0;
            subMap.clear();
        }
        if (!subMap.isEmpty()) {
            Pair<Map<Integer, String>, Integer> pair = batchTranslate(PromptUtils.JsonPrompt(target, subMap), target, ctx.getAiModel(), subMap);
            if (pair == null) {
                return;
            }
            applyBatchResult(ctx, pair, actuallyTranslateMap, translatedResultMap, target);
        }

        // 5. 按 originalTextMap 顺序回填译文到 ctx.translatedTextMap
        originalTextMap.forEach((key, value) -> {
            if (value == null || value.isEmpty()) {
                ctx.getTranslatedTextMap().put(key, value);
            } else {
                Integer seq = translateMappingMap.get(key);
                String tr = seq != null ? translatedResultMap.get(seq) : null;
                ctx.getTranslatedTextMap().put(key, tr != null ? tr : value);
            }
        });
    }

    /** 将单次 AI 批次结果写入 translatedResultMap 并写缓存、累计 token */
    private void applyBatchResult(TranslateContext ctx, Pair<Map<Integer, String>, Integer> pair,
                                  Map<Integer, String> actuallyTranslateMap, Map<Integer, String> translatedResultMap, String target) {
        ctx.incrementUsedTokenCount(pair.getSecond());
        pair.getFirst().forEach((seq, tr) -> {
            translatedResultMap.put(seq, tr);
            redisProcessService.setCacheData(target, tr, actuallyTranslateMap.get(seq));
        });
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
