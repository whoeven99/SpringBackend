package com.bogda.service.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateDescriptionVO {
    private String productId; // 产品id
    private String textTone; //语言风格
    private String brandTone; //品牌文案风格
    private Long templateId; //文案模板
    private Boolean templateType; //模板类型(官方/用户)
    private String model; //模型数据
    private String language; //语言
    private String seoKeywords; //SEO 关键词
    private String brandWord; //品牌词
    private String brandSlogan; //品牌口号
    private String pageType; //pro col类型
    private String contentType; //title seo类型
}
