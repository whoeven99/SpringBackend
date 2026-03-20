package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Translate_Save_Failed_Tasks")
public class TranslateSaveFailedTaskDO extends BaseDO {
    @TableField("translate_task_id")
    private Integer translateTaskId;
    @TableField("initial_task_id")
    private Integer initialTaskId;
    @TableField("shop_name")
    private String shopName;
    @TableField("error_message")
    private String errorMessage;
    @TableField("retry_count")
    private Integer retryCount;
    private Boolean retried;
}
