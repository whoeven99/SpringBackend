package com.bogdatech.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateDescriptionVO {
    private String pageType; //决定生成的什么类型的描述，目前暂时先做product后做collection
    private String contentType; //决定生成的描述为Description还是SEODescription，优先做Description
    private String id; //相关产品/合集的id，用于查询相关数据
    private String seoKeyword; //由用户输入，具体用途未知可以先不接收，后续有用途之后再解释作用防止错误输入
    private String templateId; //模板id或者模板内容（参考test字段），具体数据存储在后端数据库，目前暂时仅使用固定模板
    private String additionalInformation; //用途未知，同Seo keyword处理
    private String language; //生成的描述语言，传递语言代码或者具体语言待定，建议是传递具体语言
    private String test; //表示用户测试，true或者false，true的话代表template传入的并非模板id而是具体的模板内容
    private String model; //模型数据
}
