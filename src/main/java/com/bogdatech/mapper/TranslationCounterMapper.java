package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.TranslationCounterDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TranslationCounterMapper extends BaseMapper<TranslationCounterDO> {

    @Select("SELECT id, shop_name, chars, used_chars, google_chars, open_ai_chars, total_chars FROM TranslationCounter WHERE shop_name = #{shopName}")
    TranslationCounterDO readCharsByShopName(String shopName);

    @Insert("INSERT INTO TranslationCounter (shop_name, chars) VALUES (#{shopName}, #{chars})")
    Integer insertCharsByShopName(String shopName, Integer chars);

    @Update("UPDATE TranslationCounter SET used_chars =  #{usedChars} WHERE shop_name = #{shopName}")
    Integer updateUsedCharsByShopName(String shopName, Integer usedChars);

    @Select("SELECT sp.max_translations_month + tc.chars AS total_chars\n" +
            "FROM UserSubscriptions us\n" +
            "JOIN SubscriptionPlans sp ON us.plan_id = sp.plan_id\n" +
            "JOIN TranslationCounter tc ON us.shop_name = tc.shop_name\n" +
            "WHERE us.shop_name = #{shopName}")
    Integer getMaxCharsByShopName(String shopName);

    @Update("UPDATE TranslationCounter  SET chars = chars + #{chars} WHERE shop_name = #{shopName}")
    Boolean updateCharsByShopName(String shopName, int chars);

    @Select("SELECT * FROM TranslationCounter WHERE shop_name = #{shopName}")
    TranslationCounterDO getOneForUpdate(String shopName);

    @Update("UPDATE TranslationCounter SET used_chars = used_chars + #{usedChars} WHERE shop_name = #{shopName} AND used_chars <= #{maxChars}")
    Boolean updateAddUsedCharsByShopName(String shopName, Integer usedChars, Integer maxChars);
}
