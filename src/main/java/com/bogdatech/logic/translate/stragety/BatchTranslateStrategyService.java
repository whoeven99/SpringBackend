package com.bogdatech.logic.translate.stragety;

import com.bogdatech.context.BatchContext;
import com.bogdatech.entity.DO.GlossaryDO;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.isHtmlEntity;

@Component
public class BatchTranslateStrategyService implements ITranslateStrategyService<BatchContext> {
    @Override
    public String getType() {
        return "BATCH";
    }

    @Override
    public void initAndSetPrompt(BatchContext ctx) {

    }

    @Override
    public void replaceGlossary(BatchContext ctx, Map<String, GlossaryDO> glossaryMap) {

    }

    @Override
    public void executeTranslate(BatchContext context) {

    }

    @Override
    public String getTranslateValue(BatchContext context) {
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
