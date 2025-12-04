package com.bogdatech.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.repository.entity.UserIPCountDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserIPCountMapper extends BaseMapper<UserIPCountDO> {
}
