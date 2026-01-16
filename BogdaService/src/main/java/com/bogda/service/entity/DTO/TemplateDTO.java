package com.bogda.service.entity.DTO;

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
    private String templateModel; //模板类型(product or collection)
    private String templateSubtype; //模板子类型 （title， seo， description）
    private Boolean isPayment; //是否付费
    private Boolean isUserUsed; //是否被用户添加过
}
