package com.bogda.repository.sql;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.common.entity.DO.SubscriptionPlansDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubscriptionPlansMapper extends BaseMapper<SubscriptionPlansDO> {
}
