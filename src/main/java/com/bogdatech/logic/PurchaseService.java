package com.bogdatech.logic;

import com.bogdatech.Service.ICurrenciesService;
import com.bogdatech.exception.ClientException;
import com.bogdatech.model.controller.request.CurrencyRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.bogdatech.integration.RateHttpIntegration.rateMap;

@Component
public class PurchaseService {

    @Autowired
    private ICurrenciesService currenciesService;
    // 付费表单推荐购买字符数（根据商店总字符数推荐）
    public int recommendPurchaseAmount() {
        return 1000000;
    }

    //从缓存中获取数据，根据传入的数据作为判断条件
    public Map<String, Object> getCacheData(CurrencyRequest request){
        //获取对应货币代码符号和国旗图片
        Map<String, Object> currencyWithSymbol = currenciesService.getCurrencyWithSymbol(request);
        //当exchangeRate为Auto时，从缓存中获取对应货币代码数据数据
        if (currencyWithSymbol.get("exchangeRate").equals("Auto")) {
            if (rateMap.isEmpty()){
                throw new ClientException("no rateCache");
            }
            Double rate = rateMap.get(request.getCurrencyCode());
            currencyWithSymbol.put("exchangeRate", rate);
        }
        return currencyWithSymbol;
    }
}
