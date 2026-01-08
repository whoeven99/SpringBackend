package com.bogda.api.entity.DO;

import com.bogda.common.contants.TranslateConstants;
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
            new TranslateResourceDTO(TranslateConstants.PRODUCT, TranslateConstants.MIDDLE_LENGTH, "", "")
            ,
            new TranslateResourceDTO(TranslateConstants.PRODUCT_OPTION, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PRODUCT_OPTION_VALUE, TranslateConstants.MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(TranslateConstants.COLLECTION, TranslateConstants.MAX_LENGTH, "", ""),

            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_APP_EMBED, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_JSON_TEMPLATE, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_SECTION_GROUP, TranslateConstants.MAX_LENGTH, "", ""),
//            new TranslateResourceDTO(ONLINE_STORE_THEME_SETTINGS_CATEGORY, MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS, TranslateConstants.MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(TranslateConstants.MENU, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.LINK, TranslateConstants.MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(TranslateConstants.DELIVERY_METHOD_DEFINITION, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.FILTER, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.METAFIELD, TranslateConstants.MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.METAOBJECT, TranslateConstants.MIDDLE_LENGTH, "", ""),

            new TranslateResourceDTO(TranslateConstants.PAYMENT_GATEWAY, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.SELLING_PLAN, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.SELLING_PLAN_GROUP, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.SHOP, TranslateConstants.MAX_LENGTH, "", ""),


            new TranslateResourceDTO(TranslateConstants.ARTICLE, TranslateConstants.MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(TranslateConstants.BLOG, TranslateConstants.MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(TranslateConstants.PAGE, TranslateConstants.MAX_LENGTH, "", "")
    ));

    public static final List<TranslateResourceDTO> DATABASE_RESOURCES = new ArrayList<>(Arrays.asList(
            new TranslateResourceDTO(TranslateConstants.SHOP_POLICY, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.EMAIL_TEMPLATE, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PACKING_SLIP_TEMPLATE, TranslateConstants.MAX_LENGTH, "", "")
            ,
            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.MENU, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.LINK, TranslateConstants.MAX_LENGTH, "", "")
    ));

    public static final List<TranslateResourceDTO> PRODUCT_RESOURCES = new ArrayList<>(Arrays.asList(
            new TranslateResourceDTO(TranslateConstants.PRODUCT, TranslateConstants.MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PRODUCT_OPTION, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PRODUCT_OPTION_VALUE, TranslateConstants.MAX_LENGTH, "", "")
    ));

    public static final List<TranslateResourceDTO> ALL_RESOURCES = new ArrayList<>(Arrays.asList(

            new TranslateResourceDTO(TranslateConstants.SHOP, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.MENU, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.LINK, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.FILTER, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PACKING_SLIP_TEMPLATE, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.DELIVERY_METHOD_DEFINITION, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.METAOBJECT, TranslateConstants.MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_JSON_TEMPLATE, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_SECTION_GROUP, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_SETTINGS_CATEGORY, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_LOCALE_CONTENT, TranslateConstants.MAX_LENGTH, "", ""),//可以注释掉，先不翻译
            new TranslateResourceDTO(TranslateConstants.COLLECTION, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PRODUCT, TranslateConstants.MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PRODUCT_OPTION, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PRODUCT_OPTION_VALUE, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.BLOG, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.ARTICLE, TranslateConstants.MAX_LENGTH, "", ""),
//            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_APP_EMBED, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PAGE, TranslateConstants.MAX_LENGTH, "", ""),
//            new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.METAFIELD, TranslateConstants.MIDDLE_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.SHOP_POLICY, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.EMAIL_TEMPLATE, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.PAYMENT_GATEWAY, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.SELLING_PLAN, TranslateConstants.MAX_LENGTH, "", ""),
            new TranslateResourceDTO(TranslateConstants.SELLING_PLAN_GROUP, TranslateConstants.MAX_LENGTH, "", "")
    ));
    public static final Map<String, List<TranslateResourceDTO>> RESOURCE_MAP = new HashMap<>();

    static {
        RESOURCE_MAP.put("Collection", List.of(new TranslateResourceDTO(TranslateConstants.COLLECTION, TranslateConstants.MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Notifications", List.of(new TranslateResourceDTO(TranslateConstants.EMAIL_TEMPLATE, TranslateConstants.MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Theme", List.of(new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME, TranslateConstants.MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Article", List.of(new TranslateResourceDTO(TranslateConstants.ARTICLE, TranslateConstants.MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Blog titles", List.of(new TranslateResourceDTO(TranslateConstants.BLOG, TranslateConstants.MAX_LENGTH, TranslateConstants.MAX_LENGTH, "")));
        RESOURCE_MAP.put("Filters", List.of(new TranslateResourceDTO(TranslateConstants.FILTER, TranslateConstants.MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Metaobjects", List.of(new TranslateResourceDTO(TranslateConstants.METAOBJECT, TranslateConstants.MIDDLE_LENGTH, "", "")));
        RESOURCE_MAP.put("Pages", List.of(new TranslateResourceDTO(TranslateConstants.PAGE, TranslateConstants.MAX_LENGTH, "", "")));
        RESOURCE_MAP.put("Policies", List.of(new TranslateResourceDTO(TranslateConstants.SHOP_POLICY, TranslateConstants.MIDDLE_LENGTH, "", "")));
        RESOURCE_MAP.put("Products", List.of(
                new TranslateResourceDTO(TranslateConstants.PRODUCT, TranslateConstants.MIDDLE_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Navigation", Arrays.asList(
                new TranslateResourceDTO(TranslateConstants.MENU, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.LINK, TranslateConstants.MAX_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Store metadata", List.of(
                new TranslateResourceDTO(TranslateConstants.METAFIELD, TranslateConstants.MIDDLE_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Shop", List.of(
                new TranslateResourceDTO(TranslateConstants.SHOP, TranslateConstants.MAX_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Shipping", List.of(
                new TranslateResourceDTO(TranslateConstants.PACKING_SLIP_TEMPLATE, TranslateConstants.MAX_LENGTH, "", "")
        ));
        RESOURCE_MAP.put("Delivery", List.of(
                new TranslateResourceDTO(TranslateConstants.DELIVERY_METHOD_DEFINITION, TranslateConstants.MAX_LENGTH, "", "")
        ));
    }

    public static final Map<String, List<TranslateResourceDTO>> TOKEN_MAP = new HashMap<>();
    static {
        TOKEN_MAP.put("TranslateConstants.COLLECTION", List.of(new TranslateResourceDTO(TranslateConstants.COLLECTION, TranslateConstants.MAX_LENGTH, "", "")));
        TOKEN_MAP.put("notifications", List.of(new TranslateResourceDTO(TranslateConstants.EMAIL_TEMPLATE, TranslateConstants.MAX_LENGTH, "", "")));
        TOKEN_MAP.put("theme", Arrays.asList(
//                new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME, TranslateConstants.MAX_LENGTH, "", "")
//                ,
//                new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_APP_EMBED, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_JSON_TEMPLATE, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_SECTION_GROUP, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_SETTINGS_CATEGORY, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS, TranslateConstants.MAX_LENGTH, "", "")
                ,
                new TranslateResourceDTO(TranslateConstants.ONLINE_STORE_THEME_LOCALE_CONTENT, TranslateConstants.MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("article", List.of(new TranslateResourceDTO(TranslateConstants.ARTICLE, TranslateConstants.MAX_LENGTH, "", "")));
        TOKEN_MAP.put("blog_titles", List.of(new TranslateResourceDTO(TranslateConstants.BLOG, TranslateConstants.MAX_LENGTH, "", "")));
        TOKEN_MAP.put("filters", List.of(new TranslateResourceDTO(TranslateConstants.FILTER, TranslateConstants.MAX_LENGTH, "", "")));
        TOKEN_MAP.put("metaobjects", List.of(new TranslateResourceDTO(TranslateConstants.METAOBJECT, TranslateConstants.MIDDLE_LENGTH, "", "")));
        TOKEN_MAP.put("pages", List.of(new TranslateResourceDTO(TranslateConstants.PAGE, TranslateConstants.MAX_LENGTH, "", "")));
        TOKEN_MAP.put("products", Arrays.asList(
                new TranslateResourceDTO(TranslateConstants.PRODUCT, TranslateConstants.MIDDLE_LENGTH, "", "")
                ,
                new TranslateResourceDTO(TranslateConstants.PRODUCT_OPTION, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.PRODUCT_OPTION_VALUE, TranslateConstants.MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("metadata", List.of(new TranslateResourceDTO(TranslateConstants.METAFIELD, TranslateConstants.MIDDLE_LENGTH, "", "")));
        TOKEN_MAP.put("navigation", Arrays.asList(
                new TranslateResourceDTO(TranslateConstants.MENU, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.LINK, TranslateConstants.MAX_LENGTH, "", "")
        ));

        TOKEN_MAP.put("TranslateConstants.SHOP", List.of(
                new TranslateResourceDTO(TranslateConstants.SHOP, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.PAYMENT_GATEWAY, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.SELLING_PLAN, TranslateConstants.MAX_LENGTH, "", ""),
                new TranslateResourceDTO(TranslateConstants.SELLING_PLAN_GROUP, TranslateConstants.MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("shipping", List.of(
                new TranslateResourceDTO(TranslateConstants.PACKING_SLIP_TEMPLATE, TranslateConstants.MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("delivery", List.of(
                new TranslateResourceDTO(TranslateConstants.DELIVERY_METHOD_DEFINITION, TranslateConstants.MAX_LENGTH, "", "")
        ));
        TOKEN_MAP.put("policies", List.of(
                new TranslateResourceDTO(TranslateConstants.SHOP_POLICY, TranslateConstants.MIDDLE_LENGTH, "", ""))
        );
    }

    public static final Map<String, String> EMAIL_MAP = new HashMap<>();
    static {
        EMAIL_MAP.put(TranslateConstants.COLLECTION,"collection");
        EMAIL_MAP.put(TranslateConstants.EMAIL_TEMPLATE,"notifications");
        EMAIL_MAP.put(TranslateConstants.ONLINE_STORE_THEME,"theme");
        EMAIL_MAP.put(TranslateConstants.ONLINE_STORE_THEME_APP_EMBED,"theme");
        EMAIL_MAP.put(TranslateConstants.ONLINE_STORE_THEME_JSON_TEMPLATE,"theme");
        EMAIL_MAP.put(TranslateConstants.ONLINE_STORE_THEME_SECTION_GROUP,"theme");
//        EMAIL_MAP.put(TranslateConstants.ONLINE_STORE_THEME_SETTINGS_CATEGORY,"theme");
        EMAIL_MAP.put(TranslateConstants.ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS,"theme");
//        EMAIL_MAP.put(TranslateConstants.ONLINE_STORE_THEME_LOCALE_CONTENT,"theme");
        EMAIL_MAP.put(TranslateConstants.ARTICLE,"article");
        EMAIL_MAP.put(TranslateConstants.BLOG,"blog_titles");
        EMAIL_MAP.put(TranslateConstants.FILTER,"filters");
        EMAIL_MAP.put(TranslateConstants.METAOBJECT,"metaobjects");
        EMAIL_MAP.put(TranslateConstants.PAGE,"pages");
        EMAIL_MAP.put(TranslateConstants.PRODUCT,"products");
        EMAIL_MAP.put(TranslateConstants.PRODUCT_OPTION,"products");
        EMAIL_MAP.put(TranslateConstants.PRODUCT_OPTION_VALUE,"products");
        EMAIL_MAP.put(TranslateConstants.MENU,"navigation");
        EMAIL_MAP.put(TranslateConstants.LINK,"navigation");
        EMAIL_MAP.put(TranslateConstants.SHOP,"shop");
        EMAIL_MAP.put(TranslateConstants.PAYMENT_GATEWAY,"shop");
        EMAIL_MAP.put(TranslateConstants.SELLING_PLAN,"shop");
        EMAIL_MAP.put(TranslateConstants.SELLING_PLAN_GROUP,"shop");
        EMAIL_MAP.put(TranslateConstants.PACKING_SLIP_TEMPLATE,"shipping");
        EMAIL_MAP.put(TranslateConstants.DELIVERY_METHOD_DEFINITION,"delivery");
        EMAIL_MAP.put(TranslateConstants.SHOP_POLICY,"policies");
    }

    private String resourceType;
    private String first;
    private String target;
    private String after;
}
