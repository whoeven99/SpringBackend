package com.bogdatech.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
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
    private Integer limitChars; //用户最大限制字符
    private Integer startChars; //用户翻译前获取的字符
    private String startTime; //用户翻译开始时间
    private List<String> translateList; //用户翻译列表
    private String translationModel; //用户翻译模型
    private Boolean isCover; //用户是否覆盖翻译
    private String customKey; //用户自定义key

    public RabbitMqTranslateVO copy(RabbitMqTranslateVO rabbitMqTranslateVO) {
        this.shopifyData = rabbitMqTranslateVO.getShopifyData();
        this.shopName = rabbitMqTranslateVO.getShopName();
        this.accessToken = rabbitMqTranslateVO.getAccessToken();
        this.source = rabbitMqTranslateVO.getSource();
        this.target = rabbitMqTranslateVO.getTarget();
        this.languagePack = rabbitMqTranslateVO.getLanguagePack();
        this.handleFlag = rabbitMqTranslateVO.getHandleFlag();
        this.glossaryMap = rabbitMqTranslateVO.getGlossaryMap();
        this.modeType = rabbitMqTranslateVO.getModeType();
        this.limitChars = rabbitMqTranslateVO.getLimitChars();
        this.startChars = rabbitMqTranslateVO.getStartChars();
        this.startTime = rabbitMqTranslateVO.getStartTime();
        this.translateList = rabbitMqTranslateVO.getTranslateList();
        this.isCover = rabbitMqTranslateVO.getIsCover();
        this.translationModel = rabbitMqTranslateVO.getTranslationModel();
        this.customKey = rabbitMqTranslateVO.getCustomKey();
        return this;
    }
}
