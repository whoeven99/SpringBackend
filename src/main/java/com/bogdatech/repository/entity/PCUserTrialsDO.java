package com.bogdatech.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("PC_User_Trials")
public class PCUserTrialsDO extends BaseDO {
    @TableField("shop_name")
    private String shopName;
    @TableField("trial_start")
    private Timestamp trialStart;
    @TableField("trial_end")
    private Timestamp trialEnd;
    @TableField("is_trial_expired")
    private Boolean isTrialExpired;
    @TableField("is_trial_show")
    private Boolean isTrialShow;
    @TableField("is_deduct")
    private Boolean isDeduct;
}
