package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Delete_Tasks")
public class DeleteTasksDO extends BaseDO{
    @TableField("initial_task_id")
    private Integer initialTaskId;
    @TableField("resource_id")
    private String resourceId;
    @TableField("node_key")
    private String nodeKey;
    @TableField("deleted_to_shopify")
    private Boolean deletedToShopify;
}
