package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("APG_Template")
public class APGTemplateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateData;
    private String templateTitle;
    private Integer templateType;
    private Integer templateSeo;
    private String shopName;
    private String templateDescription;
}
