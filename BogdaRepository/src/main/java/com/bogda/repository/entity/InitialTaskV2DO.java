package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Initial_Translate_Tasks_V2")
public class InitialTaskV2DO extends BaseDO {
    @TableField("shop_name")
    private String shopName;
    private String source;
    private String target;
    private Integer status; // 0: 刚创建需要读取数据, 1: 翻译中, 2: 写入中, 3: 发完邮件
    @TableField("module_list")
    private String moduleList;
    @TableField("is_cover")
    private boolean isCover;
    @TableField("trans_model_type")
    private String transModelType; // 用户选择的翻译模型
    @TableField("send_email")
    private boolean sendEmail;
    @TableField("init_minutes")
    private Integer initMinutes;
    @TableField("translation_minutes")
    private Integer translationMinutes;
    @TableField("saving_shopify_minutes")
    private Integer savingShopifyMinutes;
    @TableField("save_status")
    private boolean saveStatus;
    @TableField("used_token")
    private Integer usedToken;
    @TableField("task_type")
    private String taskType; // manual, auto, private
    @TableField("is_handle")
    private boolean isHandle;
    @TableField("ai_model")
    private String aiModel;
}