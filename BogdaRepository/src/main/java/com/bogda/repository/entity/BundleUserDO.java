package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Bundle_Users")
public class BundleUserDO extends BaseDO{
    private String shopName;
    @TableField("access_token")
    private String accessToken;
    @TableField("storefront_access_token")
    private String storefrontAccessToken;
    @TableField("storefront_id")
    private String storefrontId;
    private String email;
    @TableField("user_tag")
    private String userTag;
    @TableField("first_name")
    private String firstName;
    @TableField("last_name")
    private String lastName;
    @TableField("login_at")
    private Timestamp loginAt;
    @TableField("uninstall_at")
    private Timestamp uninstallAt;
}
