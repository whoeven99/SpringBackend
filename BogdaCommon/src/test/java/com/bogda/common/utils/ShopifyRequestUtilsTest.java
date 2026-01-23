package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ShopifyRequestUtilsTest {

    @Test
    void testGetQueryWithoutAfter() {
        String query = ShopifyRequestUtils.getQuery("PRODUCT", "10", "en");
        assertNotNull(query);
        assertTrue(query.contains("PRODUCT"));
        assertTrue(query.contains("10"));
        assertTrue(query.contains("en"));
        assertFalse(query.contains("after"));
    }

    @Test
    void testGetQueryWithAfter() {
        String query = ShopifyRequestUtils.getQuery("PRODUCT", "10", "en", "cursor123");
        assertNotNull(query);
        assertTrue(query.contains("PRODUCT"));
        assertTrue(query.contains("10"));
        assertTrue(query.contains("en"));
        assertTrue(query.contains("cursor123"));
    }

    @Test
    void testGetQueryWithEmptyAfter() {
        String query = ShopifyRequestUtils.getQuery("PRODUCT", "10", "en", "");
        assertNotNull(query);
        assertFalse(query.contains("after"));
    }

    @Test
    void testGetQueryWithNullAfter() {
        String query = ShopifyRequestUtils.getQuery("PRODUCT", "10", "en", null);
        assertNotNull(query);
        assertFalse(query.contains("after"));
    }

    @Test
    void testGetProductDataQuery() {
        String productId = "gid://shopify/Product/123";
        String query = ShopifyRequestUtils.getProductDataQuery(productId);
        assertNotNull(query);
        assertTrue(query.contains(productId));
        assertTrue(query.contains("product"));
        assertTrue(query.contains("descriptionHtml"));
    }

    @Test
    void testRegisterTransactionQuery() {
        String query = ShopifyRequestUtils.registerTransactionQuery();
        assertNotNull(query);
        assertTrue(query.contains("translationsRegister"));
        assertTrue(query.contains("resourceId"));
        assertTrue(query.contains("translations"));
    }

    @Test
    void testGetShopLanguageQuery() {
        String query = ShopifyRequestUtils.getShopLanguageQuery();
        assertNotNull(query);
        assertTrue(query.contains("shopLocales"));
        assertTrue(query.contains("published"));
        assertTrue(query.contains("locale"));
    }

    @Test
    void testGetLanguagesQuery() {
        String query = ShopifyRequestUtils.getLanguagesQuery();
        assertNotNull(query);
        assertTrue(query.contains("shopLocales"));
        assertTrue(query.contains("locale"));
        assertTrue(query.contains("name"));
        assertTrue(query.contains("primary"));
        assertTrue(query.contains("published"));
    }

    @Test
    void testGetQueryForCheckMetafieldId() {
        String metafieldId = "gid://shopify/Metafield/456";
        String query = ShopifyRequestUtils.getQueryForCheckMetafieldId(metafieldId);
        assertNotNull(query);
        assertTrue(query.contains(metafieldId));
        assertTrue(query.contains("node"));
        assertTrue(query.contains("Metafield"));
        assertTrue(query.contains("Product"));
    }

    @Test
    void testGetSubscriptionQuery() {
        String subscriptionId = "gid://shopify/AppSubscription/789";
        String query = ShopifyRequestUtils.getSubscriptionQuery(subscriptionId);
        assertNotNull(query);
        assertTrue(query.contains(subscriptionId));
        assertTrue(query.contains("AppSubscription"));
        assertTrue(query.contains("status"));
        assertTrue(query.contains("lineItems"));
    }

    @Test
    void testGetSingleQuery() {
        String singleId = "gid://shopify/AppPurchaseOneTime/101";
        String query = ShopifyRequestUtils.getSingleQuery(singleId);
        assertNotNull(query);
        assertTrue(query.contains(singleId));
        assertTrue(query.contains("AppPurchaseOneTime"));
        assertTrue(query.contains("price"));
        assertTrue(query.contains("status"));
    }

    @Test
    void testDeleteQuery() {
        String query = ShopifyRequestUtils.deleteQuery();
        assertNotNull(query);
        assertTrue(query.contains("translationsRemove"));
        assertTrue(query.contains("resourceId"));
        assertTrue(query.contains("translationKeys"));
        assertTrue(query.contains("locales"));
    }
}

