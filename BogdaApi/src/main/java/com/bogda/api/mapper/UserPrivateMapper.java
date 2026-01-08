package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.UserPrivateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserPrivateMapper extends BaseMapper<UserPrivateDO> {

    @Update("UPDATE UserPrivate SET amount = #{amount},google_key = #{googleKey}, used_amount  = #{usedAmount} WHERE shop_name = #{shopName}")
    Boolean updateAmountAndGoogleKey(Integer amount, String googleKey, Integer usedAmount, String shopName);
}
