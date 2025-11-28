package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.model.controller.request.LoginAndUninstallRequest;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UsersMapper extends BaseMapper<UsersDO> {
    @Select("SELECT shop_name, first_name, last_name, user_tag, email, access_token  FROM Users WHERE shop_name = #{shopName}")
    UsersDO getUserByName(String shopName);

    @Update("UPDATE Users SET uninstall_time = GETDATE()  WHERE shop_name = #{shopName}")
    void unInstallApp(String shopName);

    @Update("UPDATE Users SET login_time = GETDATE()  WHERE shop_name = #{shopName}")
    void updateUserLoginTime(String shopName);

    @Update("UPDATE Users SET access_token = #{accessToken} WHERE shop_name = #{shopName}")
    void updateUserTokenByShopName(String shopName, String accessToken);
}
