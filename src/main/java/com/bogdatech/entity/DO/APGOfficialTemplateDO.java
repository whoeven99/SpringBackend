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
    private Integer usedTimes;

}
