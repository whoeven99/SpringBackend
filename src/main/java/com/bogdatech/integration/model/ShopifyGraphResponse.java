package com.bogdatech.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopifyGraphResponse {
    private TranslatableResources translatableResources;

    @Data
    public static class TranslatableResources {
        private List<Node> nodes;
        private PageInfo pageInfo;

        @Data
        public static class Node {
            private String resourceId;
            private List<Translation> translations; // 翻译后的数据
            private List<TranslatableContent> translatableContent; // 待翻译的数据

            @Data
            public static class Translation {
                private boolean outdated;
                private String locale;
                private String value;
                private String key; // title, body_html, handle
            }

            @Data
            public static class TranslatableContent {
                private String digest;
                private String type;
                private String locale;
                private String value;
                private String key;
            }
        }

        @Data
        public static class PageInfo {
            private boolean hasNextPage;
            private String endCursor;
        }
    }
}
