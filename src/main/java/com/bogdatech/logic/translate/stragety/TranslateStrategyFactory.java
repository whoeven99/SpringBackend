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

    // 后续改成enum
    private static final String STRATEGY_HTML = "HTML";
    private static final String STRATEGY_SINGLE = "SINGLE";

    private final Map<String, ITranslateStrategyService> serviceMap;

    @Autowired
    public TranslateStrategyFactory(List<ITranslateStrategyService> strategyServices) {
        this.serviceMap = strategyServices.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(ITranslateStrategyService::getType, Function.identity()));
    }

    /**
     * 根据策略类型获取对应的服务实现。
     */
    public ITranslateStrategyService getService(String type) {
        if (type == null || type.isBlank()) {
            throw new FatalException("Strategy type must not be null or empty");
        }
        ITranslateStrategyService service = serviceMap.get(type);
        if (service == null) {
            throw new FatalException("Invalid strategy type: " + type);
        }
        return service;
    }

    /**
     * 根据上下文确定使用的策略，并写回 ctx。
     */
    public String determineStrategy(TranslateContext ctx) {
        String strategy;
        if (ctx.getOriginalTextMap() != null) {
            // 批量翻译场景
            strategy = "BATCH";
        } else if (HTML.equals(ctx.getShopifyTextType()) || JsoupUtils.isHtml(ctx.getContent())) {
            strategy = STRATEGY_HTML;
//        } else if (URI.equals(ctx.getShopifyTextType()) && "handle".equals(ctx.getShopifyTextKey())) {
//             特殊处理 handle 的 URI 场景
//            strategy = STRATEGY_SINGLE;
        } else {
            // 默认使用单条翻译策略
            strategy = STRATEGY_SINGLE;
        }

        ctx.setStrategy(strategy);
        return strategy;
    }

    /**
     * 根据上下文自动选择并返回对应的策略服务实现。
     */
    public ITranslateStrategyService getServiceByContext(TranslateContext ctx) {
        String strategy = determineStrategy(ctx);
        return getService(strategy);
    }
}
