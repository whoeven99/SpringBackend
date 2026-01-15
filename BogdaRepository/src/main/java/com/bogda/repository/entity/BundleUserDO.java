package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Bundle_Users")
public class BundleUserDO extends BaseDO{
    private String shopName;
    @TableField("resource_id")
    private String accessToken;
    private String email;
    @TableField("user_tag")
    private String userTag;
    @TableField("first_name")
    private String firstName;
    @TableField("last_name")
    private String lastName;

}
