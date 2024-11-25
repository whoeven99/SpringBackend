package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.UserSubscriptionsDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserSubscriptionsMapper extends BaseMapper<UserSubscriptionsDO> {
    @Select("""
            SELECT sp.plan_name
                            FROM UserSubscriptions us
                            JOIN SubscriptionPlans sp ON us.plan_id = sp.plan_id
                            WHERE us.shop_name = #{shopName}""")
    String getUserSubscriptionPlan(String shopName);
}
