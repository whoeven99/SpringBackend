package com.bogda.api.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("User_Translation_Data")
public class UserTranslationDataDO {
    @TableId(type = IdType.AUTO)
    private String taskId;
    private Integer status;
    private String payload;
    private String shopName;
}
