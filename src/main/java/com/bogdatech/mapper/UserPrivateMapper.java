package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.UserPrivateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPrivateMapper extends BaseMapper<UserPrivateDO> {
    @Insert("INSERT INTO UserPrivate(shop_name,google_key) VALUES(#{shopName},#{googleKey})")
    Integer saveGoogleUserData(String shopName, String googleKey);
}
