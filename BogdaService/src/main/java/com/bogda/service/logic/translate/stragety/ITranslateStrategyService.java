package com.bogda.service.logic.translate.stragety;

import com.bogda.service.context.TranslateContext;

public interface ITranslateStrategyService {
    String getType();

    void translate(TranslateContext ctx);

    void finishAndGetJsonRecord(TranslateContext ctx);
}
