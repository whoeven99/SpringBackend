package com.bogda.api.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("APG_User_Template")
public class APGUserTemplateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    @NotBlank(message = "userId is null")
    private Long userId;
    @NotBlank(message = "templateData is null")
    private String templateData;
    @NotBlank(message = "templateDescription is null")
    private String templateDescription;
    @NotBlank(message = "templateTitle is null")
    private String templateTitle;
    @NotBlank(message = "templateType is null")
    private String templateType;
    @NotBlank(message = "templateSubtype is null")
    private String templateModel;//模板类型(product or collection)
    @NotBlank(message = "templateSubtype is null")
    private String templateSubtype; //模板子类型 （title， seo， description）
    private Boolean isDelete;
    private LocalDateTime updateTime;
}
