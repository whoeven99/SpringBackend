package com.bogda.service.logic;

import com.bogda.service.logic.redis.RateRedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateDataService {
    @Autowired
    private RateRedisService rateRedisService;
    private static final Map<String, Object> rateRule = new ConcurrentHashMap<>();

    static {
        rateRule.put("No decimal", "直接将小数点抹去，例如13.76变为13");
        rateRule.put("1.00", "四舍五入，例如12.34变为12.00；13.76变为14.00");
        rateRule.put("0.99", "小数部分直接变为0.99，例如12.34变为12.99；13.76变为13.99");
        rateRule.put("0.95", "小数部分直接变为0.95，例如12.34变为12.95；13.76变为13.95");
        rateRule.put("0.75", "小数部分直接变为0.75，例如12.34变为12.75；13.76变为13.75");
        rateRule.put("0.50", "小数部分直接变为0.50，例如12.34变为12.50；13.76变为13.50");
        rateRule.put("0.25", "小数部分直接变为0.25，例如12.34变为12.25；13.76变为13.25");
    }

    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(rateRedisService.getRatesAsObject());
    }

    //前端传入两个货币代码，返回他们对应的汇率。获取rateMap数据，因为是以欧元为基础，所以要做处理
    public double getRateByRateMap(String from, String to) {
        Double fromRate = getRate(from);
        Double toRate = getRate(to);
        if (fromRate == null || toRate == null || fromRate == 0D) {
            return 0D;
        }
        return toRate / fromRate;
    }

    public Double getRate(String currencyCode) {
        return rateRedisService.getRate(currencyCode);
    }

    public boolean isRateCacheEmpty() {
        return rateRedisService.isEmpty();
    }

    //获取自定义汇率
    public Object getRateRule() {
        return rateRule;
    }
}
