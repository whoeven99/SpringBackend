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
     * <p>
     * 翻译流程：
     * 1. 去重原文，减少重复翻译
     * 2. 分类处理：缓存命中 / 词汇表匹配 / 需词汇表翻译 / 纯AI翻译
     * 3. 带词汇表的批次翻译
     * 4. 纯AI分批翻译
     * 5. 按原始顺序回填译文结果
     *
     * @param ctx 翻译上下文（包含原文、目标语言、词汇表、AI模型等），结果写入 ctx.translatedTextMap
     */
    @Override
    public void translate(TranslateContext ctx) {
        ctx.setStrategy("Batch json 翻译");
        
        // 步骤1: 去重原文并建立映射关系
        DeduplicationResult deduplicationResult = deduplicateTexts(ctx.getOriginalTextMap());
        
        // 步骤2: 分类处理文本（缓存/词汇表/AI翻译）
        TranslationClassification classification = classifyTexts(
            ctx, 
            deduplicationResult.actuallyTranslateMap, 
            deduplicationResult.textOccurrences
        );
        
        // 步骤3: 执行带词汇表的AI翻译
        translateWithGlossary(
            ctx, 
            classification.glossarySeqList, 
            deduplicationResult.actuallyTranslateMap,
            deduplicationResult.textOccurrences,
            classification.translatedResultMap
        );
        
        // 步骤4: 执行纯AI分批翻译
        translateWithAI(
            ctx, 
            classification.aiSeqList, 
            deduplicationResult.actuallyTranslateMap,
            classification.translatedResultMap
        );
        
        // 步骤5: 按原始顺序回填译文
        fillTranslatedResults(
            ctx, 
            deduplicationResult.translateMappingMap, 
            classification.translatedResultMap
        );
    }

    /**
     * 步骤1: 去重原文并建立映射关系
     * <p>
     * 目的：避免重复翻译相同的文本，节省API调用和成本
     * <p>
     * 返回值包含：
     * - actuallyTranslateMap: 序号 -> 唯一原文（去重后需要翻译的文本）
     * - translateMappingMap: 原始位置 -> 序号（用于最后回填结果）
     * - textOccurrences: 原文 -> 出现次数（用于统计缓存/词汇表命中次数）
     */
    private DeduplicationResult deduplicateTexts(Map<Integer, String> originalTextMap) {
        // 存储去重后的唯一文本（序号 -> 文本）
        Map<Integer, String> actuallyTranslateMap = new LinkedHashMap<>();
        // 原始位置到去重序号的映射（原始key -> 去重序号）
        Map<Integer, Integer> translateMappingMap = new HashMap<>();
        // 文本到序号的映射（文本 -> 去重序号）
        Map<String, Integer> textToSeq = new HashMap<>();
        // 每个文本出现的次数（用于统计）
        Map<String, Long> textOccurrences = new HashMap<>();

        // 第一遍遍历：去重并统计出现次数
        List<String> uniqueTexts = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();
        for (String text : originalTextMap.values()) {
            if (text != null && !text.isEmpty()) {
                // 统计文本出现次数
                textOccurrences.merge(text, 1L, Long::sum);
                // 去重：只保留第一次出现的文本
                if (seenTexts.add(text)) {
                    uniqueTexts.add(text);
                }
            }
        }

        // 第二遍遍历：为每个唯一文本分配序号（从1开始）
        for (int i = 0; i < uniqueTexts.size(); i++) {
            int seq = i + 1;
            String text = uniqueTexts.get(i);
            actuallyTranslateMap.put(seq, text);
            textToSeq.put(text, seq);
        }

        // 第三遍遍历：建立原始位置到序号的映射
        for (Map.Entry<Integer, String> entry : originalTextMap.entrySet()) {
            String text = entry.getValue();
            if (text != null && !text.isEmpty()) {
                translateMappingMap.put(entry.getKey(), textToSeq.get(text));
            }
        }

        return new DeduplicationResult(actuallyTranslateMap, translateMappingMap, textOccurrences);
    }

    /**
     * 步骤2: 分类处理文本
     * <p>
     * 按优先级分类：
     * 1. 缓存命中：直接使用缓存结果，无需翻译
     * 2. 词汇表完全匹配：直接使用词汇表结果，无需翻译
     * 3. 包含词汇表词条：需要带词汇表的AI翻译
     * 4. 其他：纯AI翻译
     */
    private TranslationClassification classifyTexts(
            TranslateContext ctx,
            Map<Integer, String> actuallyTranslateMap,
            Map<String, Long> textOccurrences) {
        
        String targetLanguage = ctx.getTargetLanguage();
        Map<String, GlossaryDO> glossaryMap = ctx.getGlossaryMap();
        
        // 存储已翻译的结果
        Map<Integer, String> translatedResultMap = new HashMap<>();
        // 需要带词汇表翻译的序号列表
        List<Integer> glossarySeqList = new ArrayList<>();
        // 需要纯AI翻译的序号列表
        List<Integer> aiSeqList = new ArrayList<>();

        // 遍历每个唯一文本，按优先级分类
        for (Map.Entry<Integer, String> entry : actuallyTranslateMap.entrySet()) {
            int seq = entry.getKey();
            String text = entry.getValue();
            long occurrenceCount = textOccurrences.getOrDefault(text, 1L);

            // 优先级1: 检查缓存
            String cachedTranslation = redisProcessService.getCacheData(targetLanguage, text);
            if (cachedTranslation != null) {
                handleCacheHit(ctx, seq, text, cachedTranslation, occurrenceCount, translatedResultMap);
                continue;
            }

            // 优先级2: 检查词汇表完全匹配
            String glossaryMatch = GlossaryService.match(text, glossaryMap);
            if (glossaryMatch != null) {
                handleGlossaryMatch(ctx, seq, glossaryMatch, occurrenceCount, translatedResultMap);
                continue;
            }

            // 优先级3: 检查是否包含词汇表词条（需要带词汇表翻译）
            if (GlossaryService.hasGlossary(text, glossaryMap, ctx.getUsedGlossaryMap())) {
                glossarySeqList.add(seq);
                continue;
            }

            // 优先级4: 纯AI翻译
            aiSeqList.add(seq);
        }

        return new TranslationClassification(translatedResultMap, glossarySeqList, aiSeqList);
    }

    /**
     * 处理缓存命中的情况
     * <p>
     * 直接使用缓存结果，并更新统计信息
     */
    private void handleCacheHit(
            TranslateContext ctx,
            int seq,
            String originalText,
            String cachedTranslation,
            long occurrenceCount,
            Map<Integer, String> translatedResultMap) {
        
        // 保存翻译结果
        translatedResultMap.put(seq, cachedTranslation);
        
        // 更新统计：该文本出现了多少次，就统计多少次缓存命中
        for (int i = 0; i < occurrenceCount; i++) {
            translateTaskMonitorV2RedisService.addCacheCount(originalText);
            ctx.incrementCachedCount();
        }
    }

    /**
     * 处理词汇表完全匹配的情况
     * <p>
     * 直接使用词汇表结果，并更新统计信息
     */
    private void handleGlossaryMatch(
            TranslateContext ctx,
            int seq,
            String glossaryTranslation,
            long occurrenceCount,
            Map<Integer, String> translatedResultMap) {
        
        // 保存翻译结果
        translatedResultMap.put(seq, glossaryTranslation);
        
        // 更新统计：该文本出现了多少次，就统计多少次词汇表命中
        for (int i = 0; i < occurrenceCount; i++) {
            ctx.incrementGlossaryCount();
        }
    }

    /**
     * 步骤3: 执行带词汇表的AI翻译
     * <p>
     * 对包含词汇表词条的文本，一次性批量翻译（带词汇表提示）
     */
    private void translateWithGlossary(
            TranslateContext ctx,
            List<Integer> glossarySeqList,
            Map<Integer, String> actuallyTranslateMap,
            Map<String, Long> textOccurrences,
            Map<Integer, String> translatedResultMap) {
        
        if (glossarySeqList.isEmpty()) {
            return;
        }

        // 构建需要翻译的文本Map（序号 -> 文本）
        Map<Integer, String> textsToTranslate = new HashMap<>();
        for (Integer seq : glossarySeqList) {
            textsToTranslate.put(seq, actuallyTranslateMap.get(seq));
        }

        // 生成词汇表映射文本
        String glossaryMapping = GlossaryService.convertMapToText(
            ctx.getUsedGlossaryMap(), 
            String.join(" ", textsToTranslate.values())
        );

        // 构建带词汇表的翻译提示词
        String prompt = PromptUtils.GlossaryJsonPrompt(
            ctx.getTargetLanguage(), 
            glossaryMapping, 
            textsToTranslate
        );

        // 调用AI翻译
        Pair<Map<Integer, String>, Integer> result = batchTranslate(
            prompt, 
            ctx.getTargetLanguage(), 
            ctx.getAiModel(), 
            textsToTranslate
        );

        if (result == null) {
            return;
        }

        // 应用翻译结果（保存到结果Map并写入缓存）
        applyBatchResult(ctx, result, actuallyTranslateMap, translatedResultMap, ctx.getTargetLanguage());

        // 更新词汇表命中统计
        for (Integer seq : glossarySeqList) {
            long occurrenceCount = textOccurrences.getOrDefault(actuallyTranslateMap.get(seq), 1L);
            for (int i = 0; i < occurrenceCount; i++) {
                ctx.incrementGlossaryCount();
            }
        }
    }

    /**
     * 步骤4: 执行纯AI分批翻译
     * <p>
     * 按照字符数限制（BATCH_CHAR_LIMIT）将文本分批，逐批调用AI翻译
     * 目的：避免单次请求过大，超出AI模型的token限制
     */
    private void translateWithAI(
            TranslateContext ctx,
            List<Integer> aiSeqList,
            Map<Integer, String> actuallyTranslateMap,
            Map<Integer, String> translatedResultMap) {
        
        if (aiSeqList.isEmpty()) {
            return;
        }

        // 当前批次的文本Map
        Map<Integer, String> currentBatch = new HashMap<>();
        // 当前批次累计的字符数
        int currentBatchChars = 0;

        for (Integer seq : aiSeqList) {
            String text = actuallyTranslateMap.get(seq);
            int textChars = ALiYunTranslateIntegration.calculateBaiLianToken(text);

            // 将文本加入当前批次
            currentBatch.put(seq, text);
            currentBatchChars += textChars;

            // 如果当前批次未达到限制，继续累积
            if (currentBatchChars < BATCH_CHAR_LIMIT) {
                continue;
            }

            // 达到限制，执行翻译并清空当前批次
            executeBatchTranslation(ctx, currentBatch, translatedResultMap, actuallyTranslateMap);
            currentBatch.clear();
            currentBatchChars = 0;
        }

        // 处理最后一个批次（可能未达到限制但需要翻译）
        if (!currentBatch.isEmpty()) {
            executeBatchTranslation(ctx, currentBatch, translatedResultMap, actuallyTranslateMap);
        }
    }

    /**
     * 执行单个批次的AI翻译
     */
    private void executeBatchTranslation(
            TranslateContext ctx,
            Map<Integer, String> batchTexts,
            Map<Integer, String> translatedResultMap,
            Map<Integer, String> actuallyTranslateMap) {
        
        // 构建翻译提示词
        String prompt = PromptUtils.JsonPrompt(ctx.getTargetLanguage(), batchTexts);

        // 调用AI翻译
        Pair<Map<Integer, String>, Integer> result = batchTranslate(
            prompt, 
            ctx.getTargetLanguage(), 
            ctx.getAiModel(), 
            batchTexts
        );

        if (result == null) {
            return;
        }

        // 应用翻译结果
        applyBatchResult(ctx, result, actuallyTranslateMap, translatedResultMap, ctx.getTargetLanguage());
    }

    /**
     * 步骤5: 按原始顺序回填译文结果
     * <p>
     * 将翻译结果按照原始输入的顺序填充到 ctx.translatedTextMap
     */
    private void fillTranslatedResults(
            TranslateContext ctx,
            Map<Integer, Integer> translateMappingMap,
            Map<Integer, String> translatedResultMap) {
        
        Map<Integer, String> originalTextMap = ctx.getOriginalTextMap();
        
        originalTextMap.forEach((originalKey, originalText) -> {
            // 处理空文本：直接保留
            if (originalText == null || originalText.isEmpty()) {
                ctx.getTranslatedTextMap().put(originalKey, originalText);
                return;
            }

            // 通过映射关系找到翻译结果
            Integer seq = translateMappingMap.get(originalKey);
            String translation = (seq != null) ? translatedResultMap.get(seq) : null;

            // 如果翻译失败，使用原文作为兜底
            ctx.getTranslatedTextMap().put(originalKey, translation != null ? translation : originalText);
        });
    }

    // ==================== 内部数据类 ====================

    /**
     * 去重结果数据类
     */
    private static class DeduplicationResult {
        /** 去重后的文本Map（序号 -> 唯一文本） */
        final Map<Integer, String> actuallyTranslateMap;
        /** 原始位置到序号的映射（原始key -> 序号） */
        final Map<Integer, Integer> translateMappingMap;
        /** 文本出现次数统计（文本 -> 次数） */
        final Map<String, Long> textOccurrences;

        DeduplicationResult(
                Map<Integer, String> actuallyTranslateMap,
                Map<Integer, Integer> translateMappingMap,
                Map<String, Long> textOccurrences) {
            this.actuallyTranslateMap = actuallyTranslateMap;
            this.translateMappingMap = translateMappingMap;
            this.textOccurrences = textOccurrences;
        }
    }

    /**
     * 文本分类结果数据类
     */
    private static class TranslationClassification {
        /** 已翻译的结果Map（序号 -> 译文） */
        final Map<Integer, String> translatedResultMap;
        /** 需要带词汇表翻译的序号列表 */
        final List<Integer> glossarySeqList;
        /** 需要纯AI翻译的序号列表 */
        final List<Integer> aiSeqList;

        TranslationClassification(
                Map<Integer, String> translatedResultMap,
                List<Integer> glossarySeqList,
                List<Integer> aiSeqList) {
            this.translatedResultMap = translatedResultMap;
            this.glossarySeqList = glossarySeqList;
            this.aiSeqList = aiSeqList;
        }
    }

    /**
     * 应用批次翻译结果
     * <p>
     * 将AI翻译结果保存到结果Map，并写入Redis缓存，同时累计使用的token数
     *
     * @param ctx 翻译上下文
     * @param result AI翻译结果（包含译文Map和使用的token数）
     * @param actuallyTranslateMap 原文Map（序号 -> 原文）
     * @param translatedResultMap 译文结果Map（序号 -> 译文）
     * @param targetLanguage 目标语言
     */
    private void applyBatchResult(
            TranslateContext ctx,
            Pair<Map<Integer, String>, Integer> result,
            Map<Integer, String> actuallyTranslateMap,
            Map<Integer, String> translatedResultMap,
            String targetLanguage) {
        
        // 累计使用的token数
        ctx.incrementUsedTokenCount(result.getSecond());

        // 遍历翻译结果
        result.getFirst().forEach((seq, translation) -> {
            // 保存到结果Map
            translatedResultMap.put(seq, translation);
            
            // 写入Redis缓存（原文 -> 译文）
            String originalText = actuallyTranslateMap.get(seq);
            redisProcessService.setCacheData(targetLanguage, translation, originalText);
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

    /**
     * 执行批次翻译
     * <p>
     * 调用AI模型进行翻译，并解析返回的JSON结果
     *
     * @param prompt 翻译提示词
     * @param targetLanguage 目标语言
     * @param aiModel AI模型名称
     * @param sourceMap 待翻译的文本Map（序号 -> 原文）
     * @return 翻译结果（译文Map + 使用的token数），失败返回null
     */
    private Pair<Map<Integer, String>, Integer> batchTranslate(
            String prompt,
            String targetLanguage,
            String aiModel,
            Map<Integer, String> sourceMap) {
        
        // 调用AI模型翻译
        Pair<String, Integer> aiResult = modelTranslateService.modelTranslate(aiModel, prompt, targetLanguage, sourceMap);
        if (aiResult == null) {
            return null;
        }

        // 解析AI返回的JSON结果
        String aiResponse = aiResult.getFirst();
        Map<Integer, String> translatedMap = parseOutput(aiResponse);
        
        // 检查解析结果是否有效
        if (translatedMap == null || translatedMap.isEmpty()) {
            // 解析失败，可能是AI返回格式错误
            return null;
        }

        // 返回译文Map和token数
        return new Pair<>(translatedMap, aiResult.getSecond());
    }

    /**
     * 解析AI返回的翻译结果
     * <p>
     * AI返回的格式通常是：{"1": "译文1", "2": "译文2", ...}
     * 需要从可能包含其他文本的响应中提取JSON部分，并解析为Map
     *
     * @param input AI返回的原始响应文本
     * @return 解析后的译文Map（序号 -> 译文），解析失败返回null
     */
    public static LinkedHashMap<Integer, String> parseOutput(String input) {
        if (input == null) {
            return null;
        }

        // 步骤1: 从响应中提取JSON块（可能包含其他说明文字）
        String jsonPart = StringUtils.extractJsonBlock(input);
        if (jsonPart == null) {
            return null;
        }

        // 步骤2: 将JSON字符串解析为Map对象
        LinkedHashMap<Integer, String> resultMap = JsonUtils.jsonToObjectWithNull(
            jsonPart, 
            new TypeReference<LinkedHashMap<Integer, String>>() {}
        );
        if (resultMap == null) {
            return null;
        }

        // 步骤3: 过滤掉空值或空白译文
        resultMap.entrySet().removeIf(entry -> 
            entry.getValue() == null || entry.getValue().trim().isEmpty()
        );

        return resultMap;
    }
}
