package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.GlossaryDO;

import java.util.Map;

public interface ITranslateStrategyService<T extends TranslateContext> {
    String getType();

    void initAndSetPrompt(T ctx);

    void replaceGlossary(T ctx, Map<String, GlossaryDO> glossaryMap);

    void executeTranslate(T context);

    String getTranslateValue(T context);
}
