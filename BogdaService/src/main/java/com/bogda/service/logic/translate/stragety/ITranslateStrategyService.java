package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;

public interface ITranslateStrategyService {
    String getType();

    void translate(TranslateContext ctx);

    void finishAndGetJsonRecord(TranslateContext ctx);
}
