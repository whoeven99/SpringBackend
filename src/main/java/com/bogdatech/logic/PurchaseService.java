package com.bogdatech.logic;

import com.bogdatech.Service.ICurrenciesService;
import com.bogdatech.entity.DO.CurrenciesDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.logic.RateDataService.getRateByRateMap;

@Component
public class PurchaseService {

    @Autowired
    private ICurrenciesService currenciesService;
    // 付费表单推荐购买字符数（根据商店总字符数推荐）
    public int recommendPurchaseAmount() {
        return 1000000;
    }

    //从缓存中获取数据，根据传入的数据作为判断条件
    public Map<String, Object> getCacheData(CurrenciesDO currencyDO){
        //获取对应货币代码符号和国旗图片
        Map<String, Object> currencyWithSymbol = currenciesService.getCurrencyWithSymbol(currencyDO);
        if (currencyWithSymbol == null ){
            return null;
        }
        String defaultCurrencyCode = currenciesService.getCurrencyCodeByPrimaryStatusAndShopName(currencyDO.getShopName());
        //当exchangeRate为Auto时，从缓存中获取对应货币代码数据数据
        if (currencyWithSymbol.get("primaryStatus").equals(0) && "Auto".equals(currencyWithSymbol.get("exchangeRate"))) {
            if (rateMap.isEmpty()){
                return currencyWithSymbol;
            }
            //与默认货币代码的汇率
            if (defaultCurrencyCode.isEmpty()){
                return currencyWithSymbol;
            }
            double rateByRateMap = getRateByRateMap(defaultCurrencyCode, currencyDO.getCurrencyCode());
            currencyWithSymbol.put("exchangeRate", rateByRateMap);
        }
        return currencyWithSymbol;
    }
}
