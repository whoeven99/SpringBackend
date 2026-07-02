package com.bogda.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.common.entity.DO.UserTypeTokenDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserTypeTokenMapper extends BaseMapper<UserTypeTokenDO> {

    @Insert("INSERT INTO userTypeToken(translation_id) VALUES (#{translateId})")
    void insertTypeInfo(int translateId);

    @Insert("INSERT INTO userTypeToken(shop_name) VALUES (#{shopName})")
    void insertTypeInfoByShopName(String shopName);
}
