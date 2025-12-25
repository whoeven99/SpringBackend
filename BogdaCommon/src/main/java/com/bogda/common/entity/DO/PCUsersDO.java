package com.bogda.common.entity.DO;

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
@TableName("PC_Users")
public class PCUsersDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private String accessToken;
    private Integer purchasePoints;
    private Integer usedPoints;
    private String email;
    private String phone;
    private String realAddress;
    private String ipAddress;
    private String userTag;
    private String firstName;
    private String lastName;
    private Timestamp uninstallTime;
    private Timestamp loginTime;
    private Integer isDeleted;
    private Timestamp createAt;
    private Timestamp updateAt;
}
