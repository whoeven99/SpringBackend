package com.bogda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopifyCheckMetafieldResponse {
    private Node node;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Node {
        private String id;
        private String type;
        private Owner owner;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Owner {
            private String id;
            private String title;
        }
    }
}
