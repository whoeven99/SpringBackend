package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.GlossaryDO;

import java.util.Map;

public interface ITranslateStrategyService {
    String getType();

    void initAndSetPrompt(TranslateContext ctx);

    void replaceGlossary(TranslateContext ctx, Map<String, GlossaryDO> glossaryMap);

    void executeTranslate(TranslateContext context);

    String getTranslateValue(TranslateContext context);
}
