package com.bogda.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopifyUserResponse {
    private DataNode data;
    private Extensions extensions;

    @Data
    public static class DataNode {
        private Shop shop;

        @Data
        public static class Shop {
            private String shopOwnerName;
            private String email;
        }

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
