package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.AILanguagePacksDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AILanguagePacksMapper extends BaseMapper<AILanguagePacksDO> {
    @Select("SELECT id, pack_name, pack_describe, pack_price, promot_word FROM AILanguagePacks")
    AILanguagePacksDO[] readAILanguagePacks();

    @Insert("INSERT INTO User_AILanguagePacks (shop_name, pack_id) VALUES (#{shopName}, 2)")
    Integer addDefaultLanguagePack(String shopName);

    @Update("UPDATE User_AILanguagePacks SET pack_id = #{packId} WHERE shop_name = #{shopName}")
    Integer changeLanguagePack(String shopName, Integer packId);

    @Select("SELECT promot_word FROM AILanguagePacks WHERE id = #{packId}")
    String getPromotByShopName(Integer packId);

    @Select("SELECT pack_id FROM User_AILanguagePacks WHERE shop_name = #{shopName}")
    Integer getPackIdByShopName(String shopName);

}
