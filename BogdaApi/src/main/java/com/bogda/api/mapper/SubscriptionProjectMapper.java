package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.SubscriptionProjectDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SubscriptionProjectMapper extends BaseMapper<SubscriptionProjectDO> {

    @Select("select project_id, project_key, name, characters, current_price, currency_code from subscriptionProject")
    SubscriptionProjectDO[] readSubscriptionProject();
}
