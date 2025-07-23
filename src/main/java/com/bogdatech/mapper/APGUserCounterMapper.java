package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.utils.CharacterCountUtils;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface APGUserCounterMapper extends BaseMapper<APGUserCounterDO> {

    @Select("""
            select id from APG_Users where shop_name = #{shopName}
""")
    Long selectUserIdByShopName(String shopName);

    @Select("""
            select u.id AS user_id,
            c.chars,
            c.product_counter,
            c.product_seo_counter,
            c.collection_counter,
            c.collection_seo_counter,
            c.all_counter,
            c.extra_counter,
            c.user_token
            from APG_Users u
            JOIN APG_User_Counter c ON u.id = c.user_id
            WHERE u.shop_name = #{shopName}
            """)
    APGUserCounterDO selectUserCounterByShopName(String shopName);

    @Update("UPDATE APG_User_Counter WITH (UPDLOCK, ROWLOCK) SET user_token = user_token + #{counter}, chars = chars + #{counter} WHERE user_id = #{userId} AND user_token <= #{maxLimit}")
    int updateUserUsedCount(Long userId, Integer counter, Integer maxLimit);

    @Update("UPDATE APG_User_Counter WITH (UPDLOCK, ROWLOCK) SET chars = 0 WHERE user_id = #{userId}")
    int updateCharsByUserId(Long userId);
}
