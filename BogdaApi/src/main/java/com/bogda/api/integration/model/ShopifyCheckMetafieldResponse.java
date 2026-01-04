package com.bogda.api.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShopifyCheckMetafieldResponse {
    private ShopifyCheckMetafieldResponse.CheckMetafieldResources checkMetafieldResources;
    @Data
    public static class CheckMetafieldResources {
        private ShopifyCheckMetafieldResponse.CheckMetafieldResources.Node node;

        @Data
        public static class Node {
            private String id; // 要查询的元字段resourceId
            private ShopifyCheckMetafieldResponse.CheckMetafieldResources.Node.Owner owner; // 相关联的product数据
            private String type;

            @Data
            public static class Owner {
                private String id; // 相关联的productId
                private String title; // 相关联的产品标题
            }
        }
    }
}
