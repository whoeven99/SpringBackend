package com.bogda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyExtensions {
    private Cost cost;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cost {
        private Integer requestedQueryCost;
        private Integer actualQueryCost;
        private ThrottleStatus throttleStatus;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ThrottleStatus {
            private Double maximumAvailable;
            private Integer currentlyAvailable;
            private Double restoreRate;
        }
    }
}
