package com.bogdatech.entity.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TemplateDTO {
    private Long id;
    private String templateData;
    private String templateTitle;
    private String templateType;
    private String templateDescription;
    private Boolean templateClass; //是否为公共模板
}
