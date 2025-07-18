package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.APGUserCounterDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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

}
