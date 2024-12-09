package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.UsersDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UsersMapper extends BaseMapper<UsersDO> {
    @Select("SELECT shop_name FROM Users WHERE shop_name = #{shopName}")
    UsersDO getUserByName(String shopName);
}
