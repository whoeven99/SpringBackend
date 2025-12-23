package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.SubscriptionPlansDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubscriptionPlansMapper extends BaseMapper<SubscriptionPlansDO> {
}
