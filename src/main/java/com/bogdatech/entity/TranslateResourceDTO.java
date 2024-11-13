package com.bogdatech.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateResourceDTO {
    // 创建一个静态的 ArrayList 来存储 TranslateResourceDTO 对象
    public static final List<TranslateResourceDTO> TRANSLATION_RESOURCES = new ArrayList<>(Arrays.asList(
//            new TranslateResourceDTO("PRODUCT", "250", "", "")
//            ,
//            new TranslateResourceDTO("PRODUCT_OPTION", "250", "", ""),
//            new TranslateResourceDTO("PRODUCT_OPTION_VALUE", "250", "", ""),
//            new TranslateResourceDTO("COLLECTION", "250", "", ""),
//            new TranslateResourceDTO("ONLINE_STORE_THEME", "250", "", ""),
//            new TranslateResourceDTO("ONLINE_STORE_THEME_APP_EMBED", "250", "", ""),
//            new TranslateResourceDTO("ONLINE_STORE_THEME_JSON_TEMPLATE", "250", "", ""),
//            new TranslateResourceDTO("ONLINE_STORE_THEME_LOCALE_CONTENT", "250", "", ""),
//            new TranslateResourceDTO("ONLINE_STORE_THEME_SECTION_GROUP", "250", "", ""),
//            new TranslateResourceDTO("ONLINE_STORE_THEME_SETTINGS_CATEGORY", "250", "", ""),
//            new TranslateResourceDTO("ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS", "250", "", "")
//            ,
//            new TranslateResourceDTO("MENU", "250", "", ""),
//            new TranslateResourceDTO("LINK", "250", "", ""),
//            new TranslateResourceDTO("DELIVERY_METHOD_DEFINITION", "250", "", ""),
//            new TranslateResourceDTO("FILTER", "250", "", ""),
//            new TranslateResourceDTO("METAFIELD", "250", "", ""),
//            new TranslateResourceDTO("METAOBJECT", "250", "", ""),
//            new TranslateResourceDTO("PACKING_SLIP_TEMPLATE", "250", "", ""),
//            new TranslateResourceDTO("PAYMENT_GATEWAY", "250", "", ""),
//            new TranslateResourceDTO("SELLING_PLAN", "250", "", ""),
//            new TranslateResourceDTO("SELLING_PLAN_GROUP", "250", "", ""),
//            new TranslateResourceDTO("SHOP", "250", "", ""),
//            new TranslateResourceDTO("SHOP_POLICY", "250", "", ""),
//            new TranslateResourceDTO("EMAIL_TEMPLATE", "250", "", ""),
            new TranslateResourceDTO("ARTICLE", "250", "", ""),
            new TranslateResourceDTO("BLOG", "250", "", "")
//            ,
//            new TranslateResourceDTO("PAGE", "250", "", "")
    ));

    public static final Map<String, List<TranslateResourceDTO>> RESOURCE_MAP = new HashMap<>();

    static {
        RESOURCE_MAP.put("Collection", List.of(new TranslateResourceDTO("COLLECTION", "250", "", "")));
        RESOURCE_MAP.put("Notifications", List.of(new TranslateResourceDTO("EMAIL_TEMPLATE", "250", "", "")));
        RESOURCE_MAP.put("Theme", List.of(new TranslateResourceDTO("ONLINE_STORE_THEME", "250", "", "")));
        RESOURCE_MAP.put("Article", List.of(new TranslateResourceDTO("ARTICLE", "250", "", "")));
        RESOURCE_MAP.put("Blog titles", List.of(new TranslateResourceDTO("BLOG", "250", "250", "")));
        RESOURCE_MAP.put("Filters", List.of(new TranslateResourceDTO("FILTER", "250", "", "")));
        RESOURCE_MAP.put("Metaobjects", List.of(new TranslateResourceDTO("METAOBJECT", "250", "", "")));
        RESOURCE_MAP.put("Pages", List.of(new TranslateResourceDTO("PAGE", "250", "", "")));
        RESOURCE_MAP.put("Policies", List.of(new TranslateResourceDTO("SHOP_POLICY", "250", "", "")));
        RESOURCE_MAP.put("Products", List.of(
                new TranslateResourceDTO("PRODUCT", "250", "", "")
//                ,
//                new TranslateResourceDTO("PRODUCT_OPTION", "250", "", "")
//                new TranslateResourceDTO("PRODUCT_OPTION_VALUE", "250", "", "")
        ));
        RESOURCE_MAP.put("Navigation", Arrays.asList(
                new TranslateResourceDTO("MENU", "250", "", ""),
                new TranslateResourceDTO("LINK", "250", "", "")
        ));
        RESOURCE_MAP.put("Store metadata", Arrays.asList(
                new TranslateResourceDTO("METAFIELD", "250", "", ""),
                new TranslateResourceDTO("SHOP", "250", "", "")
        ));
        RESOURCE_MAP.put("Shipping", List.of(
                new TranslateResourceDTO("PACKING_SLIP_TEMPLATE", "250", "", "")
        ));
        RESOURCE_MAP.put("delivery", List.of(
                new TranslateResourceDTO("DELIVERY_METHOD_DEFINITION", "250", "", "")
        ));

        //        RESOURCE_MAP.put("App embeds", List.of(new TranslateResourceDTO("ONLINE_STORE_THEME_APP_EMBED", "250", "", "")));
        //        RESOURCE_MAP.put("Section groups", List.of(new TranslateResourceDTO("ONLINE_STORE_THEME_SECTION_GROUP", "250", "", "")));
//        RESOURCE_MAP.put("Static sections", List.of(new TranslateResourceDTO("ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS", "250", "", "")));
//        RESOURCE_MAP.put("Templates", List.of(new TranslateResourceDTO("ONLINE_STORE_THEME_JSON_TEMPLATE", "250", "", "")));
//        RESOURCE_MAP.put("Theme settings", List.of(new TranslateResourceDTO("ONLINE_STORE_THEME_SETTINGS_CATEGORY", "250", "", "")));

    }

    private String resourceType;
    private String first;
    private String target;
    private String after;
}
