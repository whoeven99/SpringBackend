package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.UserPrivateTranslateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserPrivateTranslateMapper extends BaseMapper<UserPrivateTranslateDO> {
    @Update("UPDATE User_Private_Translate WITH (UPDLOCK, ROWLOCK) SET used_token = used_token + #{length} WHERE shop_name = #{shopName} AND api_name = #{apiName} AND used_token <= #{limitChars}")
    Boolean updateUserUsedCount(int length, String shopName, Integer apiName, Long limitChars);
}
