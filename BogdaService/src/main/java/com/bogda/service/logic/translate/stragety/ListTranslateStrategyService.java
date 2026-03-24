package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.JsoupUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ListTranslateStrategyService implements ITranslateStrategyService {

    @Autowired
    private BatchTranslateStrategyService batchTranslateStrategyService;

    @Autowired
    private HtmlTranslateStrategyService htmlTranslateStrategyService;

    @Override
    public String getType() {
        return "LIST";
    }

    @Override
    public void translate(TranslateContext ctx) {
        ctx.setStrategy("LIST 逐元素翻译");
        String content = ctx.getContent();

        List<String> inputList = JsonUtils.jsonToObject(content, new TypeReference<List<String>>() {});
        if (inputList == null || inputList.isEmpty()) {
            ctx.setTranslatedContent(content);
            return;
        }

        int size = inputList.size();
        String[] resultArray = new String[size];

        Map<Integer, String> plainTextMap = new LinkedHashMap<>();
        List<Integer> htmlIndices = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            String element = inputList.get(i);
            if (element == null || element.isEmpty()) {
                resultArray[i] = element;
                continue;
            }
            if (JsoupUtils.isHtml(element)) {
                htmlIndices.add(i);
            } else {
                plainTextMap.put(i, element);
            }
        }

        translatePlainTextBatch(ctx, plainTextMap, resultArray);
        translateHtmlElements(ctx, inputList, htmlIndices, resultArray);

        for (int i = 0; i < size; i++) {
            if (resultArray[i] == null) {
                resultArray[i] = inputList.get(i);
            }
        }

        ctx.setTranslatedContent(JsonUtils.objectToJson(Arrays.asList(resultArray)));
    }

    private void translatePlainTextBatch(TranslateContext ctx,
                                         Map<Integer, String> plainTextMap,
                                         String[] resultArray) {
        if (plainTextMap.isEmpty()) {
            return;
        }
        try {
            TranslateContext batchCtx = new TranslateContext(
                    plainTextMap, ctx.getTargetLanguage(), ctx.getGlossaryMap(), ctx.getAiModel());
            batchCtx.setModule(ctx.getModule());
            batchCtx.setShopName(ctx.getShopName());

            batchTranslateStrategyService.translate(batchCtx);

            for (Map.Entry<Integer, String> entry : batchCtx.getTranslatedTextMap().entrySet()) {
                String translated = entry.getValue();
                resultArray[entry.getKey()] = (translated != null && !translated.isEmpty())
                        ? translated : plainTextMap.get(entry.getKey());
            }
            mergeStats(ctx, batchCtx);
        } catch (Exception e) {
            ExceptionReporterHolder.report("ListTranslateStrategyService.translatePlainTextBatch", e);
            for (Map.Entry<Integer, String> entry : plainTextMap.entrySet()) {
                if (resultArray[entry.getKey()] == null) {
                    resultArray[entry.getKey()] = entry.getValue();
                }
            }
        }
    }

    private void translateHtmlElements(TranslateContext ctx,
                                       List<String> inputList,
                                       List<Integer> htmlIndices,
                                       String[] resultArray) {
        for (int index : htmlIndices) {
            String htmlContent = inputList.get(index);
            try {
                TranslateContext htmlCtx = new TranslateContext(
                        htmlContent, ctx.getTargetLanguage(), ctx.getGlossaryMap(), ctx.getAiModel());
                htmlCtx.setModule(ctx.getModule());
                htmlCtx.setShopName(ctx.getShopName());

                htmlTranslateStrategyService.translate(htmlCtx);

                String translated = htmlCtx.getTranslatedContent();
                resultArray[index] = (translated != null && !translated.isEmpty()) ? translated : htmlContent;
                mergeStats(ctx, htmlCtx);
            } catch (Exception e) {
                ExceptionReporterHolder.report("ListTranslateStrategyService.translateHtmlElements", e);
                resultArray[index] = htmlContent;
            }
        }
    }

    private void mergeStats(TranslateContext main, TranslateContext sub) {
        main.incrementUsedTokenCount(sub.getUsedToken());
        main.setCachedCount(main.getCachedCount() + sub.getCachedCount());
        main.setGlossaryCount(main.getGlossaryCount() + sub.getGlossaryCount());
    }

    @Override
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
}
