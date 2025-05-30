package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("UserIPSwitch")
public class UserIPSwitchDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shopName;
    private int switchId;
}
