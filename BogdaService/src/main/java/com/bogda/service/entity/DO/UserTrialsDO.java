package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("UserTrials")
public class UserTrialsDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private Timestamp trialStart;
    private Timestamp trialEnd;
    private Boolean isTrialExpired;
    private Boolean isTrialShow;
}
