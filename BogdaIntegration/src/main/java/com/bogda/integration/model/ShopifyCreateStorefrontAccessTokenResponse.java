package com.bogda.integration.model;
import lombok.Data;
import java.util.List;

@Data
public class ShopifyCreateStorefrontAccessTokenResponse {

    private DataNode data;
    private Extensions extensions;

    @Data
    public static class DataNode {
        private StorefrontAccessTokenCreate storefrontAccessTokenCreate;
    }

    @Data
    public static class StorefrontAccessTokenCreate {
        private Shop shop;
        private StorefrontAccessToken storefrontAccessToken;
        private List<UserError> userErrors;
    }

    @Data
    public static class Shop {
        private String id;
    }

    @Data
    public static class StorefrontAccessToken {
        private String accessToken;
        private String title;
    }

    @Data
    public static class UserError {
        private String field;
        private String message;
    }

    @Data
    public static class Extensions {
        private Cost cost;
    }

    @Data
    public static class Cost {
        private int requestedQueryCost;
        private int actualQueryCost;
        private ThrottleStatus throttleStatus;
    }

    @Data
    public static class ThrottleStatus {
        private double maximumAvailable;
        private int currentlyAvailable;
        private double restoreRate;
    }
}
