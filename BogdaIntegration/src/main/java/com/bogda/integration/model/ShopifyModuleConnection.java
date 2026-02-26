package com.bogda.integration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopifyModuleConnection {
    private List<Edge> edges;
    private PageInfo pageInfo;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Edge {
        private Node node;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Node {
        private String id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        private String endCursor;
        private Boolean hasNextPage;
    }
}
