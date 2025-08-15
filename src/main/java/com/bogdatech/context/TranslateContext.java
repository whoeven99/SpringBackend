package com.bogdatech.context;

import com.bogdatech.entity.DO.AILanguagePacksDO;
import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.utils.CharacterCountUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TranslateContext {
    private String shopifyData; //shopify要翻译数据
    private ShopifyRequest shopifyRequest; // shopify请求参数
    private TranslateResourceDTO translateResource; // 翻译模块类型数据
    private CharacterCountUtils characterCountUtils; // 计数器
    private Integer remainingChars; //最大限制
    private Map<String, Object> glossaryMap; //词汇表相关数据
    private String source; // 源语言
    private String languagePackId; //语言包
    private String apiKey; // 用户私有key
    private Boolean handleFlag; // 是否翻译handle标志
    private Boolean isCover; // 是否覆盖翻译标志
    private Integer model; // 选的的主模型，googel， openai
    private String apiModel; // 主模型下的细分支 gpt4.1  等等。
    private String userPrompt; // 用户自定义提示词
}
