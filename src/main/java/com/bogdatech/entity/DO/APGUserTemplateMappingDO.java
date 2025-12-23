package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("APG_User_Template_Mapping")
public class APGUserTemplateMappingDO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long templateId;
    private Boolean templateType;
    private Boolean isDelete;
    private LocalDateTime updateTime;
}
