package com.bogda.api.logic.translate.stragety;

import com.bogda.api.context.TranslateContext;

public interface ITranslateStrategyService {
    String getType();

    void translate(TranslateContext ctx);

    void finishAndGetJsonRecord(TranslateContext ctx);
}
