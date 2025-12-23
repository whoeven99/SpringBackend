package com.bogda.api.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SingleTranslateVO {
    private String shopName;
    private String source;
    private String target;
    private String resourceType;
    private String context;
    private String key;
    private String type;
}
