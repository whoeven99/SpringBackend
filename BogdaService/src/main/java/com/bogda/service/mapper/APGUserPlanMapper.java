package com.bogda.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.service.entity.DO.APGUserPlanDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface APGUserPlanMapper extends BaseMapper<APGUserPlanDO> {
    @Insert("INSERT INTO APG_User_Plan (user_id, plan_id) VALUES (#{userId}, 2)")
    Boolean initializeFreePlan(Long userId);

    @Select("""
        SELECT sp.max_translations_month + uc.extra_counter
        FROM APG_User_Plan up
        JOIN SubscriptionPlans sp ON up.plan_id = sp.plan_id
        JOIN dbo.APG_User_Counter uc ON uc.user_id = up.user_id
        WHERE up.user_id = #{userId}       

""")
    Integer getUserMaxLimit(Long userId);
}
