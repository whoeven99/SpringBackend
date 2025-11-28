package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.exception.FatalException;
import com.bogdatech.utils.JsoupUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bogdatech.constants.TranslateConstants.HTML;

@Component
public class TranslateStrategyFactory {
    private final Map<String, ITranslateStrategyService> serviceMap;

    @Autowired
    public TranslateStrategyFactory(List<ITranslateStrategyService> strategyServices) {
        this.serviceMap = strategyServices.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(ITranslateStrategyService::getType, Function.identity()));
    }

    public ITranslateStrategyService getServiceByContext(TranslateContext ctx) {
        String strategy;
        if (ctx.getOriginalTextMap() != null && !ctx.getOriginalTextMap().isEmpty()) {
            strategy = "BATCH";
        } else if (HTML.equals(ctx.getShopifyTextType()) || JsoupUtils.isHtml(ctx.getContent())) {
            strategy = "HTML";
        } else {
            strategy = "SINGLE";
        }

        ITranslateStrategyService service = serviceMap.get(strategy);
        if (service == null) {
            throw new FatalException("Invalid strategy type: " + strategy);
        }
        return service;
    }
}
