package com.bogda.service.logic;

import com.bogda.service.Service.ICurrenciesService;
import com.bogda.common.entity.DO.CurrenciesDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PurchaseService {

    @Autowired
    private ICurrenciesService currenciesService;

    //从缓存中获取数据，根据传入的数据作为判断条件
    public Map<String, Object> getCacheData(CurrenciesDO currencyDO){
        //获取对应货币代码符号和国旗图片
        Map<String, Object> currencyWithSymbol = currenciesService.getCurrencyWithSymbol(currencyDO);
        if (currencyWithSymbol == null ){
            return null;
        }
        return currencyWithSymbol;
    }
}
