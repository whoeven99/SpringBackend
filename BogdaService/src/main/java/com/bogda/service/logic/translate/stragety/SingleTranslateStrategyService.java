package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.PromptUtils;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.logic.translate.ModelTranslateService;
import com.bogda.service.logic.translate.PromptConfigService;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.StringUtils;
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
    @Autowired
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;
    @Autowired
    private PromptConfigService promptConfigService;

    @Autowired
    private FeiShuRobotIntegration feiShuRobotIntegration;

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
            prompt = promptConfigService.buildHandlePrompt(ctx.getModule(), target, fixContent);
            ctx.setStrategy("Handle 长文本翻译");
            ctx.setPrompt(prompt);
//            return;
        }

        if (ctx.getLastTranslatedText() == null) {
            // 如果TranslatedContent为空， 先获取redis中的数据
            String cacheData = redisProcessService.getCacheData(ctx.getTargetLanguage(), ctx.getContent());
            if (cacheData != null) {
                ctx.setLastTranslatedText(cacheData);
            }
        }

        // 1. 先检查词汇表
        Map<String, GlossaryDO> glossaryMap = ctx.getGlossaryMap();

        // 1.1 词汇表完全匹配：直接使用词汇表译文，无需AI翻译
        String glossaryMatch = GlossaryService.match(value, glossaryMap);
        if (glossaryMatch != null) {
            ctx.setStrategy("词汇表完全匹配-单条翻译");
            ctx.setTranslatedContent(glossaryMatch);
            redisProcessService.setCacheData(target, glossaryMatch, value);
            return;
        }

        // 1.2 包含词汇表词条：带词汇表的AI翻译
        if (GlossaryService.hasGlossary(value, glossaryMap, ctx.getUsedGlossaryMap())) {
            String glossaryMappingText = GlossaryService.convertMapToText(ctx.getUsedGlossaryMap(), value);
            String prompt = null;
            if (ctx.getLastTranslatedText() != null) {
                prompt = PromptUtils.buildDynamicTargetSinglePrompt(target, value, ctx.getLastTranslatedText(),
                        glossaryMappingText, null);
            } else {
                prompt = promptConfigService.buildSinglePrompt(ctx.getModule(), target, value, glossaryMappingText, null);
            }
            ctx.setStrategy("语法表单条翻译");
            ctx.setPrompt(prompt);
        }

        // 普通 prompt（仅当前面没设置）
        if (ctx.getPrompt() == null) {
            String prompt = null;
            if (ctx.getLastTranslatedText() != null) {
                prompt = PromptUtils.buildDynamicTargetSinglePrompt(target, value, ctx.getLastTranslatedText(), null, null);
            } else {
                prompt = promptConfigService.buildSinglePrompt(ctx.getModule(), target, value, null, null);
            }
            ctx.setStrategy("普通单条文本翻译");
            ctx.setPrompt(prompt);
        }

        Pair<String, Integer> pair = modelTranslateService.modelTranslate(ctx.getAiModel(), ctx.getPrompt()
                , ctx.getTargetLanguage(), value);

        if (pair == null) {
            TraceReporterHolder.report("SingleTranslateStrategyService.translate", "FatalException 飞书机器人报错 翻译解析报错 译文 ： " + ctx.getPrompt());
            feiShuRobotIntegration.sendMessage("FatalException SINGLE shopName : " + ctx.getShopName() + " prompt : "
                    + ctx.getPrompt() + "  module : " + ctx.getModule());
            return;
        }

        TraceReporterHolder.report("debug", "单条翻译提示词： " + ctx.getPrompt());
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
