package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Translate_Tasks_V2")
public class TranslateTaskV2DO extends BaseDO {
    @TableField("initial_task_id")
    private int initialTaskId;
    private String module;
    @TableField("resource_id")
    private String resourceId; // node -> resourceId
    @TableField("node_key")
    private String nodeKey; // node -> content -> key
    private String type; // node -> content -> type
    private String digest; // node -> content -> digest
    @TableField("source_value")
    private String sourceValue; // node -> content -> value
    @TableField("target_value")
    private String targetValue;
    @TableField("saved_to_shopify")
    private boolean savedToShopify;
}