package com.bogda.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.service.entity.DO.UsersDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UsersMapper extends BaseMapper<UsersDO> {
    @Update("UPDATE Users SET uninstall_time = GETDATE()  WHERE shop_name = #{shopName}")
    void unInstallApp(String shopName);

    @Update("UPDATE Users SET login_time = GETDATE()  WHERE shop_name = #{shopName}")
    void updateUserLoginTime(String shopName);

    @Update("UPDATE Users SET access_token = #{accessToken} WHERE shop_name = #{shopName}")
    void updateUserTokenByShopName(String shopName, String accessToken);
}
