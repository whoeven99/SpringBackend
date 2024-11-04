package com.bogdatech.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateResourceDTO {
    // 创建一个静态的 ArrayList 来存储 TranslateResourceDTO 对象
    public static final List<TranslateResourceDTO> translationResources = new ArrayList<>(Arrays.asList(
            new TranslateResourceDTO("ARTICLE", "2", "",""),
            new TranslateResourceDTO("BLOG", "2", "",""),
            new TranslateResourceDTO("COLLECTION", "10", "", ""),
            new TranslateResourceDTO("DELIVERY_METHOD_DEFINITION", "2", "", "")
//            new TranslateResourceDTO("FILTER", "10", "", ""),
//            new TranslateResourceDTO("LINK", "2", "", ""),
//            new TranslateResourceDTO("EMAIL_TEMPLATE", "2", "", ""),
//            new TranslateResourceDTO("MENU", "2", "", ""),
//            new TranslateResourceDTO("METAFIELD", "2", "", ""),
//            new TranslateResourceDTO("METAOBJECT", "2", "", ""),
//            new TranslateResourceDTO("ONLINE_STORE_THEME", "10", "", ""),
//            new TranslateResourceDTO("PACKING_SLIP_TEMPLATE", "2", "", ""),
//            new TranslateResourceDTO("PAGE", "10", "", ""),
//            new TranslateResourceDTO("PAYMENT_GATEWAY", "10", "", ""),
//            new TranslateResourceDTO("PRODUCT", "2", "", ""),
//            new TranslateResourceDTO("SHOP", "2", "", ""),
//            new TranslateResourceDTO("PRODUCT_OPTION", "2", "", ""),
//            new TranslateResourceDTO("PRODUCT_OPTION_VALUE", "2", "", ""),
//            new TranslateResourceDTO("SELLING_PLAN", "2", "", ""),
//            new TranslateResourceDTO("SELLING_PLAN_GROUP", "2", "", ""),
//            new TranslateResourceDTO("SHOP_POLICY", "2", "", "")
            //TODO 还有其他类型需要添加 ONLINE_STORE_THEME系列还没加
    ));

    private String resourceType;
    private String first;
    private String target;
    private String after;
}
