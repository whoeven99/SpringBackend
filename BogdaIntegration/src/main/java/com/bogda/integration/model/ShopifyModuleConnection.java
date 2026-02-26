package com.bogda.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用结构：articles/products/pages/collections 查询返回的 edges + pageInfo。
 * 用于解析 Shopify Admin API 的 data.articles / data.products / data.pages / data.collections。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyModuleConnection {
    private List<Edge> edges;
    private PageInfo pageInfo;

    @Data
    public static class Edge {
        private Node node;
    }

    @Data
    public static class Node {
        private String id;
    }

    @Data
    public static class PageInfo {
        private String endCursor;
        private Boolean hasNextPage;
    }
}
