package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.TranslateContext;

public interface ITranslateStrategyService {
    String getType();

    void translate(TranslateContext ctx);
}
