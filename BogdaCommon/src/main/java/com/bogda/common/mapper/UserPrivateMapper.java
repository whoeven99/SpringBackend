package com.bogda.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.common.entity.DO.UserPrivateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserPrivateMapper extends BaseMapper<UserPrivateDO> {
    @Insert("INSERT INTO UserPrivate(shop_name,google_key,amount) VALUES(#{shopName},#{googleKey},#{amount})")
    Integer saveGoogleUserData(String shopName, String googleKey, Integer amount);

    @Select("SELECT id FROM UserPrivate WHERE shop_name = #{shopName}")
    Integer getUserIdByShopName(String shopName);

    @Update("UPDATE UserPrivate SET amount = #{amount},google_key = #{googleKey}, used_amount  = #{usedAmount} WHERE shop_name = #{shopName}")
    Boolean updateAmountAndGoogleKey(Integer amount, String googleKey, Integer usedAmount, String shopName);
}
