package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.UserIPSwitchDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserIPSwitchMapper extends BaseMapper<UserIPSwitchDO> {
    @Insert("INSERT INTO UserIPSwitch ( shop_name, switch_id) VALUES ( #{shopName}, #{switchId})")
    int insertSwitch(String shopName , int switchId);

    @Select("SELECT switch_id FROM UserIPSwitch WHERE shop_name = #{shopName}")
    int getSwitchId(String shopName);

    @Select("SELECT shop_name FROM UserIPSwitch WHERE shop_name = #{shopName}")
    String getShopName(String shopName);
}
