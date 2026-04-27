package com.bogda.repository.container;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Container(containerName = "translate_tasks_v3", autoCreateContainer = false)
public class TranslateTaskV3DO {
    @Id
    private String id;

    @PartitionKey
    private String shopName;

    private String source;
    private String target;
    private Integer status;
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
