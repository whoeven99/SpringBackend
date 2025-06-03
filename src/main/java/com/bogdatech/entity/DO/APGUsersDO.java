package com.bogdatech.entity.DO;

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
@TableName("APG_Users")
public class APGUsersDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shopName;
    private String accessToken;
    private String email;
    private String phone;
    private String realAddress;
    private String ipAddress;
    private String userTag;
    private String firstName;
    private String lastName;
    private Timestamp uninstallTime;
    private Timestamp loginTime;
}
