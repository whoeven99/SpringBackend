package com.bogda.api.logic.translate.modelStragety;

import com.bogda.api.exception.FatalException;
import com.bogda.api.integration.ALiYunTranslateIntegration;
import com.bogda.api.utils.CaseSensitiveUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import kotlin.Pair;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TranslateModelStrategyFactory {
    private final Map<String, ITranslateModelStrategyService> modelServiceMap;

    @Autowired
    public TranslateModelStrategyFactory(List<ITranslateModelStrategyService> modelServiceMap) {
        this.modelServiceMap = modelServiceMap.stream().filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(ITranslateModelStrategyService::getTranslateModel, Function.identity()));
    }

    public ITranslateModelStrategyService getTranslateModelStrategy(String translateModel) {
        ITranslateModelStrategyService iTranslateModelStrategyService = modelServiceMap.get(translateModel);
        if (iTranslateModelStrategyService == null) {
            throw new FatalException("Unknown translate model: " + translateModel);
        }
        return iTranslateModelStrategyService;
    }

    /**
     * 保底处理，失败后再调用一次qwen-max翻译
     */
    public Pair<String, Integer> guaranteedTranslation(String translateModel, String prompt, String target) {
        // 做一个保底处理，当pair为null的时候，用qwen-max再翻译一次，如果再为null，就直接返回
        CaseSensitiveUtils.appInsights.trackTrace("FatalException  " + translateModel
                + " 翻译失败， 数据如下，用qwen翻译 : " + prompt);
        return getTranslateModelStrategy(ALiYunTranslateIntegration.QWEN_MAX)
                .chooseModelTranslate(prompt, target);
    }
}
