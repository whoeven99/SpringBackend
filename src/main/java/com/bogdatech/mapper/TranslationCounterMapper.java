package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.TranslationCounterDO;
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
}
