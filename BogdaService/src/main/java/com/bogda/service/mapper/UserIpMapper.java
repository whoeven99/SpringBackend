package com.bogda.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.service.entity.DO.UserIpDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserIpMapper extends BaseMapper<UserIpDO> {
    /**
     * 加悲观锁查询（UPDLOCK + ROWLOCK）
     */
    @Select("SELECT * FROM UserIp WITH (ROWLOCK, UPDLOCK) WHERE shop_name = #{shopName}")
    UserIpDO selectByShopNameForUpdate(@Param("shopName") String shopName);
}
