package com.bogda.api.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("UserIp")
public class UserIpDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private Long times;
    private Boolean firstEmail;
    private Boolean secondEmail;
    private Long allTimes;
}
