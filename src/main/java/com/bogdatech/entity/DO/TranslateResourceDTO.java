package com.bogdatech.entity.DO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

import static com.bogdatech.constants.TranslateConstants.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateResourceDTO {
    // 创建一个静态的 ArrayList 来存储 TranslateResourceDTO 对象
    public static final List<TranslateResourceDTO> TRANSLATION_RESOURCES = new ArrayList<>(Arrays.asList(
            new TranslateResourceDTO(PRODUCT, MIDDLE_LENGTH, "", "")
            ,
            new TranslateResourceDTO(PRODUCT_OPTION, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(PRODUCT_OPTION_VALUE, MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(COLLECTION, MAX_LENGTH, "", ""),

            new TranslateResourceDTO(ONLINE_STORE_THEME_APP_EMBED, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(ONLINE_STORE_THEME_JSON_TEMPLATE, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(ONLINE_STORE_THEME_SECTION_GROUP, MAX_LENGTH, "", ""),
//            new TranslateResourceDTO(ONLINE_STORE_THEME_SETTINGS_CATEGORY, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS, MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(MENU, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(LINK, MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(DELIVERY_METHOD_DEFINITION, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(FILTER, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(METAFIELD, MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(METAOBJECT, MIDDLE_LENGTH, "", ""),

            new TranslateResourceDTO(PAYMENT_GATEWAY, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(SELLING_PLAN, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(SELLING_PLAN_GROUP, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(SHOP, MAX_LENGTH, "", ""),


            new TranslateResourceDTO(ARTICLE, MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(BLOG, MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(PAGE, MAX_LENGTH, "", "")
    ));

    public static final List<TranslateResourceDTO> DATABASE_RESOURCES = new ArrayList<>(Arrays.asList(
            new TranslateResourceDTO(SHOP_POLICY, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(EMAIL_TEMPLATE, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(PACKING_SLIP_TEMPLATE, MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(ONLINE_STORE_THEME, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(MENU, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(LINK, MAX_LENGTH, "", "")
    ));

    public static final List<TranslateResourceDTO> PRODUCT_RESOURCES = new ArrayList<>(Arrays.asList(
            new TranslateResourceDTO(PRODUCT, MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(PRODUCT_OPTION, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(PRODUCT_OPTION_VALUE, MAX_LENGTH, "", "")
    ));

    public static final List<TranslateResourceDTO> ALL_RESOURCES = new ArrayList<>(Arrays.asList(

            new TranslateResourceDTO(SHOP, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(PAGE, MAX_LENGTH, "", ""),
//            new TranslateResourceDTO(ONLINE_STORE_THEME, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(ONLINE_STORE_THEME_LOCALE_CONTENT, MAX_LENGTH, "", ""),//可以注释掉，先不翻译
            new TranslateResourceDTO(PRODUCT, MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(PRODUCT_OPTION, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(PRODUCT_OPTION_VALUE, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(COLLECTION, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(METAFIELD, MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(ARTICLE, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(BLOG, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(MENU, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(LINK, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(FILTER, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(METAOBJECT, MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(ONLINE_STORE_THEME_JSON_TEMPLATE, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(ONLINE_STORE_THEME_SECTION_GROUP, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(ONLINE_STORE_THEME_SETTINGS_CATEGORY, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(PACKING_SLIP_TEMPLATE, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(DELIVERY_METHOD_DEFINITION, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(SHOP_POLICY, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(EMAIL_TEMPLATE, MAX_LENGTH, "", ""),
//            new TranslateResourceDTO(ONLINE_STORE_THEME_APP_EMBED, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(PAYMENT_GATEWAY, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(SELLING_PLAN, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(SELLING_PLAN_GROUP, MAX_LENGTH, "", "")
    ));
    public static final Map<String, List<TranslateResourceDTO>> RESOURCE_MAP = new HashMap<>();

    static {
        RESOURCE_MAP.put("Collection", List.of(new TranslateResourceDTO(COLLECTION, MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Notifications", List.of(new TranslateResourceDTO(EMAIL_TEMPLATE, MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Theme", List.of(new TranslateResourceDTO(ONLINE_STORE_THEME, MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Article", List.of(new TranslateResourceDTO(ARTICLE, MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Blog titles", List.of(new TranslateResourceDTO(BLOG, MAX_LENGTH, MAX_LENGTH, "")));
        RESOURCE_MAP.put("Filters", List.of(new TranslateResourceDTO(FILTER, MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Metaobjects", List.of(new TranslateResourceDTO(METAOBJECT, MIDDLE_LENGTH, "", "")));
        RESOURCE_MAP.put("Pages", List.of(new TranslateResourceDTO(PAGE, MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Policies", List.of(new TranslateResourceDTO(SHOP_POLICY, MIDDLE_LENGTH, "", "")));
        RESOURCE_MAP.put("Products", List.of(
                new TranslateResourceDTO(PRODUCT, MIDDLE_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Navigation", Arrays.asList(
                new TranslateResourceDTO(MENU, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(LINK, MAX_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Store metadata", List.of(
                new TranslateResourceDTO(METAFIELD, MIDDLE_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Shop", List.of(
                new TranslateResourceDTO(SHOP, MAX_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Shipping", List.of(
                new TranslateResourceDTO(PACKING_SLIP_TEMPLATE, MAX_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Delivery", List.of(
                new TranslateResourceDTO(DELIVERY_METHOD_DEFINITION, MAX_LENGTH, "", "")
        ));
    }

    public static final Map<String, List<TranslateResourceDTO>> TOKEN_MAP = new HashMap<>();
    static {
        TOKEN_MAP.put("collection", List.of(new TranslateResourceDTO(COLLECTION, MAX_LENGTH, "", "")));
        TOKEN_MAP.put("notifications", List.of(new TranslateResourceDTO(EMAIL_TEMPLATE, MAX_LENGTH, "", "")));
        TOKEN_MAP.put("theme", Arrays.asList(
//                new TranslateResourceDTO(ONLINE_STORE_THEME, MAX_LENGTH, "", "")
//                ,
//                new TranslateResourceDTO(ONLINE_STORE_THEME_APP_EMBED, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(ONLINE_STORE_THEME_JSON_TEMPLATE, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(ONLINE_STORE_THEME_SECTION_GROUP, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(ONLINE_STORE_THEME_SETTINGS_CATEGORY, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS, MAX_LENGTH, "", "")
                ,
                new TranslateResourceDTO(ONLINE_STORE_THEME_LOCALE_CONTENT, MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("article", List.of(new TranslateResourceDTO(ARTICLE, MAX_LENGTH, "", "")));
        TOKEN_MAP.put("blog_titles", List.of(new TranslateResourceDTO(BLOG, MAX_LENGTH, "", "")));
        TOKEN_MAP.put("filters", List.of(new TranslateResourceDTO(FILTER, MAX_LENGTH, "", "")));
        TOKEN_MAP.put("metaobjects", List.of(new TranslateResourceDTO(METAOBJECT, MIDDLE_LENGTH, "", "")));
        TOKEN_MAP.put("pages", List.of(new TranslateResourceDTO(PAGE, MAX_LENGTH, "", "")));
        TOKEN_MAP.put("products", Arrays.asList(
                new TranslateResourceDTO(PRODUCT, MIDDLE_LENGTH, "", "")
                ,
                new TranslateResourceDTO(PRODUCT_OPTION, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(PRODUCT_OPTION_VALUE, MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("metadata", List.of(new TranslateResourceDTO(METAFIELD, MIDDLE_LENGTH, "", "")));
        TOKEN_MAP.put("navigation", Arrays.asList(
                new TranslateResourceDTO(MENU, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(LINK, MAX_LENGTH, "", "")
        ));

        TOKEN_MAP.put("shop", List.of(
                new TranslateResourceDTO(SHOP, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(PAYMENT_GATEWAY, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(SELLING_PLAN, MAX_LENGTH, "", ""),
                new TranslateResourceDTO(SELLING_PLAN_GROUP, MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("shipping", List.of(
                new TranslateResourceDTO(PACKING_SLIP_TEMPLATE, MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("delivery", List.of(
                new TranslateResourceDTO(DELIVERY_METHOD_DEFINITION, MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("policies", List.of(
                new TranslateResourceDTO(SHOP_POLICY, MIDDLE_LENGTH, "", ""))
        );
    }

    public static final Map<String, String> EMAIL_MAP = new HashMap<>();
    static {
        EMAIL_MAP.put(COLLECTION,"collection");
        EMAIL_MAP.put(EMAIL_TEMPLATE,"notifications");
        EMAIL_MAP.put(ONLINE_STORE_THEME,"theme");
        EMAIL_MAP.put(ONLINE_STORE_THEME_APP_EMBED,"theme");
        EMAIL_MAP.put(ONLINE_STORE_THEME_JSON_TEMPLATE,"theme");
        EMAIL_MAP.put(ONLINE_STORE_THEME_SECTION_GROUP,"theme");
//        EMAIL_MAP.put(ONLINE_STORE_THEME_SETTINGS_CATEGORY,"theme");
        EMAIL_MAP.put(ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS,"theme");
//        EMAIL_MAP.put(ONLINE_STORE_THEME_LOCALE_CONTENT,"theme");
        EMAIL_MAP.put(ARTICLE,"article");
        EMAIL_MAP.put(BLOG,"blog_titles");
        EMAIL_MAP.put(FILTER,"filters");
        EMAIL_MAP.put(METAOBJECT,"metaobjects");
        EMAIL_MAP.put(PAGE,"pages");
        EMAIL_MAP.put(PRODUCT,"products");
        EMAIL_MAP.put(PRODUCT_OPTION,"products");
        EMAIL_MAP.put(PRODUCT_OPTION_VALUE,"products");
        EMAIL_MAP.put(MENU,"navigation");
        EMAIL_MAP.put(LINK,"navigation");
        EMAIL_MAP.put(SHOP,"shop");
        EMAIL_MAP.put(PAYMENT_GATEWAY,"shop");
        EMAIL_MAP.put(SELLING_PLAN,"shop");
        EMAIL_MAP.put(SELLING_PLAN_GROUP,"shop");
        EMAIL_MAP.put(PACKING_SLIP_TEMPLATE,"shipping");
        EMAIL_MAP.put(DELIVERY_METHOD_DEFINITION,"delivery");
        EMAIL_MAP.put(SHOP_POLICY,"policies");
    }

    //自动翻译模块顺序
    public static final List<String> AUTO_TRANSLATE_MAP = new ArrayList<>(Arrays.asList(
            ARTICLE, PRODUCT, PRODUCT_OPTION, PRODUCT_OPTION_VALUE, COLLECTION,
            ONLINE_STORE_THEME_JSON_TEMPLATE, ONLINE_STORE_THEME_SECTION_GROUP,
            ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS, ONLINE_STORE_THEME_SETTINGS_CATEGORY,
            ONLINE_STORE_THEME_LOCALE_CONTENT, METAFIELD, PAGE
    ));

    private String resourceType;
    private String first;
    private String target;
    private String after;
}
