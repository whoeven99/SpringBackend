package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Bundle_Users_Discount")
public class BundleUsersDiscountDO extends BaseDO{
    private String shopName;
    private String discountId;
    private String discountName;
}
