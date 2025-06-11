package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.UserIpDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserIpMapper extends BaseMapper<UserIpDO> {
}
