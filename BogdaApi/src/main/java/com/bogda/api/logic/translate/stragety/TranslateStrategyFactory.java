package com.bogda.api.logic.translate.stragety;

import com.bogda.api.constants.TranslateConstants;
import com.bogda.api.context.TranslateContext;
import com.bogda.api.exception.FatalException;
import com.bogda.api.utils.JsonUtils;
import com.bogda.common.utils.JsoupUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TranslateStrategyFactory {
    private final Map<String, ITranslateStrategyService> serviceMap;

    @Autowired
    public TranslateStrategyFactory(List<ITranslateStrategyService> strategyServices) {
        this.serviceMap = strategyServices.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(ITranslateStrategyService::getType, Function.identity()));
    }

    public ITranslateStrategyService getServiceByStrategy(String strategy) {
        ITranslateStrategyService service = serviceMap.get(strategy);
        if (service == null) {
            throw new FatalException("Invalid strategy type: " + strategy);
        }
        return service;
    }

    public ITranslateStrategyService getServiceByContext(TranslateContext ctx) {
        String strategy;
        if (ctx.getOriginalTextMap() != null && !ctx.getOriginalTextMap().isEmpty()) {
            strategy = "BATCH";
        } else if (TranslateConstants.JSON.equals(ctx.getShopifyTextType()) || JsonUtils.isJson(ctx.getContent())) {
            strategy = "JSON";
        }else if (TranslateConstants.HTML.equals(ctx.getShopifyTextType()) || JsoupUtils.isHtml(ctx.getContent())) {
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
