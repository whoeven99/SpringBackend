package com.bogdatech.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RabbitMqTranslateVO implements Serializable {
    private String shopifyData; //用户shopify250条数据
    private String shopName; //用户名
    private String accessToken; //用户token
    private String source; //原语言
    private String target; //目标语言
    private String languagePack; //用户语言包
    private Boolean handleFlag; //用户handle标志
    private Map<String, Object> glossaryMap; //用户词汇表数据
    private String modeType; //模块类型

}
