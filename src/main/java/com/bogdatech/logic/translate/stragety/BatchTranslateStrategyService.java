package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.entity.DO.GlossaryDO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BatchTranslateStrategyService implements ITranslateStrategyService {
    @Override
    public String getType() {
        return "BATCH";
    }

    @Override
    public void initAndSetPrompt(TranslateContext ctx) {

    }

    @Override
    public void replaceGlossary(TranslateContext ctx, Map<String, GlossaryDO> glossaryMap) {

    }

    @Override
    public void executeTranslate(TranslateContext context) {
        // 1. 过glossary

        // 2. 过缓存

        // 3. 调用翻译接口
    }

    @Override
    public String getTranslateValue(TranslateContext context) {
        return null;
    }


//    idToSourceValueMap.forEach((id, sourceValue) -> {
//        String targetCache = redisProcessService.getCacheData(target, sourceValue);
//        if (false) {
//            targetCache = isHtmlEntity(targetCache);
//            cachedMap.put(id, targetCache);
//        } else {
//            unCachedMap.put(id, sourceValue);
//        }
//    });
}
