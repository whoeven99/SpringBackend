package com.bogdatech.model.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProgressResponse {
    private List<Progress> list;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Progress {
        private String target;
        private Integer status;
        private String translateStatus; // 初始化 translation_process_init 翻译中 translation_process_translating 写入中 translation_process_saving_shopify 写入完成  translation_process_saved
        private String resourceType;
        private String value;
        private Map<String, Integer> progressData;
        private Map<String, Integer> writingData;
    }
}
