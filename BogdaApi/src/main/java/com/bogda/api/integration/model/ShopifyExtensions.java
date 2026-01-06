package com.bogda.api.integration.model;

import lombok.Data;

@Data
public class ShopifyExtensions {
    private Cost cost;

    @Data
    public static class Cost {
        private Integer requestedQueryCost;
        private Integer actualQueryCost;
        private ThrottleStatus throttleStatus;

        @Data
        public static class ThrottleStatus {
            private Double maximumAvailable;
            private Integer currentlyAvailable;
            private Double restoreRate;
        }
    }
}
