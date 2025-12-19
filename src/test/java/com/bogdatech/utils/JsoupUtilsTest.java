package com.bogdatech.utils;

import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.logic.translate.TranslateV2Service;
import com.bogdatech.model.controller.response.TypeSplitResponse;
import com.bogdatech.repository.entity.TranslateTaskV2DO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsoupUtilsTest {

    @Test
    void testIsHtml() {
        // Test case 1: Valid HTML content
        String htmlContent = "<html><body><p>Test</p></body></html>";
        assertTrue(JsoupUtils.isHtml(htmlContent));

        // Test case 2: Plain text without HTML tags
        String plainText = "This is just plain text.";
        assertFalse(JsoupUtils.isHtml(plainText));

        // Test case 3: HTML-like content but not valid HTML
        String invalidHtml = "<html>This is not valid</html";
        assertTrue(JsoupUtils.isHtml(invalidHtml));

        // Test case 4: Empty string
        String emptyContent = "";
        assertFalse(JsoupUtils.isHtml(emptyContent));

        // Test case 5: Null content
//        String nullContent = null;
//        assertFalse(JsoupUtils.isHtml(nullContent));
    }

    @Test
    void testIsHtml2() {
        List<String> moduleList = List.of(
                "PRODUCT",
                "PRODUCT_OPTION",
                "PRODUCT_OPTION_VALUE",
                "COLLECTION",
                "ARTICLE",
                "BLOG",
                "PAGE",
                "FILTER",
                "METAOBJECT",
                "METAFIELD",
                "MENU",
                "LINK",
                "SHOP",
                "PAYMENT_GATEWAY",
                "SELLING_PLAN",
                "SELLING_PLAN_GROUP",
                "ONLINE_STORE_THEME_JSON_TEMPLATE",
                "ONLINE_STORE_THEME_SECTION_GROUP",
                "ONLINE_STORE_THEME_SETTINGS_CATEGORY",
                "ONLINE_STORE_THEME_SETTINGS_DATA_SECTIONS",
                "ONLINE_STORE_THEME_LOCALE_CONTENT",
                "EMAIL_TEMPLATE",
                "DELIVERY_METHOD_DEFINITION",
                "PACKING_SLIP_TEMPLATE"
        );
        List<TranslateResourceDTO> resourceList = TranslateV2Service.convertALL(moduleList);
        String model = "PRODUCT";
        TypeSplitResponse typeSplitResponse = TranslateV2Service.splitByType(model, resourceList);
        System.out.println(typeSplitResponse);
    }
}
