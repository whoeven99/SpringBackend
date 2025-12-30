package com.bogda.api.logic.translate.modelStragety;

import kotlin.Pair;

public interface ITranslateModelStrategyService {
    String getTranslateModel();
    Pair<String, Integer> chooseModelTranslate(String prompt, String target);
}
