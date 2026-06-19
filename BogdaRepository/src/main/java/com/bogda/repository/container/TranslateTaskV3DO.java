package com.bogda.repository.container;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateTaskV3DO {
    private String id;
    private String shopName;
    private String source;
    private String target;
    private Integer status;
    private String statusText;
    private String taskType;
    private String aiModel;
    private boolean isCover;
    private boolean isHandle;
    private String moduleList;
    private String sessionId;

    private Map<String, Object> checkpoint = new HashMap<>();
    private Map<String, Object> metrics = new HashMap<>();

    private String createdAt;
    private String updatedAt;
}
