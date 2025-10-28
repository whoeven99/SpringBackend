package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.PCUsersDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PCUsersMapper extends BaseMapper<PCUsersDO> {
}
