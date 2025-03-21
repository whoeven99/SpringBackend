package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.UserPrivateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserPrivateMapper extends BaseMapper<UserPrivateDO> {
    @Insert("INSERT INTO UserPrivate(shop_name,google_key,amount) VALUES(#{shopName},#{googleKey},#{amount})")
    Integer saveGoogleUserData(String shopName, String googleKey, Integer amount);

    @Select("SELECT id FROM UserPrivate WHERE shop_name = #{shopName}")
    Integer getUserIdByShopName(String shopName);
}
