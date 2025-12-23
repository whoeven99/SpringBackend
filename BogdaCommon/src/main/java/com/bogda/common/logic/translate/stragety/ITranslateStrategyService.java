package com.bogda.common.logic.translate.stragety;

import com.bogda.common.context.TranslateContext;

public interface ITranslateStrategyService {
    String getType();

    void translate(TranslateContext ctx);

    void finishAndGetJsonRecord(TranslateContext ctx);
}
