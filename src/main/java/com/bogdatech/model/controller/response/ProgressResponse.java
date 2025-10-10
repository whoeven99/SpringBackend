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
        private String resourceType;
        private String value;
        private Map<String, Integer> progressData;
    }
}
