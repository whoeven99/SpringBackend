package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("APG_User_Plan")
public class APGUserPlanDO {
    private Long id;
    private Long userId;
    private Long planId;
}
