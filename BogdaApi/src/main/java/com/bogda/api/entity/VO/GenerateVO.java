package com.bogda.api.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateVO {
    private String pageType;
    private String contentType;
    private String description;
    private String id;
}
