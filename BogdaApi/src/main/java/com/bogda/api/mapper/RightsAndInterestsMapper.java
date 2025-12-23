package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.RightsAndInterestsDO;
import com.bogda.api.entity.DO.UserRightsAndInterestsDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RightsAndInterestsMapper extends BaseMapper<RightsAndInterestsDO> {
    @Select("select id, right_name, right_describe, right_type, right_text from RightsAndInterests")
    RightsAndInterestsDO[] readRightsAndInterests();

    @Select("select user_id, rightsAndInterests_id " +
            "from User_RightsAndInterests " +
            "where user_id = #{userId} and rightsAndInterests_id = #{raiId};")
    UserRightsAndInterestsDO getUserRightsAndInterests(Integer userId, Integer raiId);

    @Insert("insert into User_RightsAndInterests (user_id, rightsAndInterests_id) values (#{userId}, #{rightsAndInterestsId}) ")
    int addUserRightsAndInterests(Integer userId, Integer rightsAndInterestsId);

    @Select("select id from Users where shop_name = #{shopName}")
    Integer getUserIdByShopName(String shopName);
}
