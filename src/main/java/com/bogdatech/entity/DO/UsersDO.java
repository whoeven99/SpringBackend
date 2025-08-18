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
@TableName("Users")
public class UsersDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private String accessToken;
    private String email;
    private String phone;
    private String realAddress; //改为这个用户第一天安装第一次翻译的时间
    private String ipAddress; //改成这个用户第一天安装第一次支付的时间
    private String userTag;
    private String firstName;
    private String lastName;
    private Timestamp uninstallTime;
    private Timestamp loginTime;
    private String encryptionEmail;
}
