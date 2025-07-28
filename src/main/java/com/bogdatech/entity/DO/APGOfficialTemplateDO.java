package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("APG_Official_Template")
public class APGOfficialTemplateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateData;
    private String templateTitle;
    private String templateType;
    private String templateDescription;
    private String templateModel;//模板类型(product or collection)
    private String templateSubtype; //模板子类型 （title， seo， description）
    private Boolean isPayment; //是否是付费模板
    private Integer usedTimes; //使用次数
    private String exampleDate; //示例数据

}
