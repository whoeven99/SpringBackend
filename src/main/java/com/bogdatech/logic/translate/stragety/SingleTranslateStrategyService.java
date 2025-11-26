package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.GlossaryDO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.logic.GlossaryService;
import com.bogdatech.utils.PlaceholderUtils;
import kotlin.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bogdatech.constants.TranslateConstants.URI;
import static com.bogdatech.utils.PlaceholderUtils.*;

@Component
public class SingleTranslateStrategyService implements ITranslateStrategyService<TranslateContext> {
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;

    @Override
    public String getType() {
        return "SINGLE";
    }

    @Override
    public void initAndSetPrompt(TranslateContext ctx) {
        String value = ctx.getContent();
        String target = ctx.getTargetLanguage();

        if (URI.equals(ctx.getShopifyTextType()) && "handle".equals(ctx.getShopifyTextKey())) {
            String prompt;
            if (value.length() <= 20) {
                if (PlaceholderUtils.hasPlaceholders(value)) {
                    String variableString = getOuterString(value);
                    prompt = getVariablePrompt(target, variableString, null);
                    ctx.setStrategy("Handle 短文本含变量翻译");
                } else {
                    prompt = PlaceholderUtils.getShortPrompt(value);
                    ctx.setStrategy("Handle 短文本翻译");
                }
            } else {
                String fixContent = com.bogdatech.utils.StringUtils.replaceHyphensWithSpaces(value);
                prompt = PlaceholderUtils.getHandlePrompt(target);
                prompt += "The text is: " + fixContent;
                ctx.setStrategy("Handle 长文本翻译");
            }
            ctx.setPrompt(prompt);
            return;
        }

        // 普通文本，有变量的处理
        if (hasPlaceholders(value)) {
            String variableString = getOuterString(value);
            String prompt = getVariablePrompt(target, variableString, null);
            prompt += "The text is: " + value;
            ctx.setStrategy("普通文本含变量翻译");
            ctx.setPrompt(prompt);
        } else {
            String prompt =  PlaceholderUtils.getSimplePrompt(target, null);
            prompt += "The text is: " + value;
            ctx.setStrategy("普通文本翻译");
            ctx.setPrompt(prompt);
        }
    }

    @Override
    public void replaceGlossary(TranslateContext ctx, Map<String, GlossaryDO> glossaryMap) {
        Pair<String, Boolean> pair = GlossaryService.replaceWithGlossary(ctx.getContent(), glossaryMap);
        if (pair.getSecond()) {
            ctx.setGlossaryReplaceContent(pair.getFirst());
            ctx.setHasGlossary(true);
        }
    }

    @Override
    public void executeTranslate(TranslateContext context) {
        Pair<String, Integer> pair = aLiYunTranslateIntegration.userTranslate(context.getPrompt(), context.getTargetLanguage());
        if (pair == null) {
            // fatalException
            return;
        }
        context.setUsedToken(pair.getSecond());
        context.setTranslatedContent(pair.getFirst());
    }

    @Override
    public String getTranslateValue(TranslateContext context) {
        return context.getTranslatedContent();
    }
}
