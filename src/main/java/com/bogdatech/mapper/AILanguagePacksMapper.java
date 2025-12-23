package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.AILanguagePacksDO;
import com.bogdatech.entity.DO.UserAlLanguagePacksDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AILanguagePacksMapper extends BaseMapper<AILanguagePacksDO> {
    @Select("SELECT id, pack_name, pack_describe, promot_word FROM AILanguagePacks")
    AILanguagePacksDO[] readAILanguagePacks();

    @Insert("INSERT INTO User_AILanguagePacks (shop_name, pack_id) VALUES (#{shopName}, #{id})")
    Integer addDefaultLanguagePack(String shopName, Integer id);

    @Update("UPDATE User_AILanguagePacks SET pack_id = #{packId} WHERE shop_name = #{shopName}")
    Integer changeLanguagePack(String shopName, Integer packId);

    @Select("SELECT pack_id FROM User_AILanguagePacks WHERE shop_name = #{shopName}")
    Integer getPackIdByShopName(String shopName);

    @Select("SELECT id FROM AILanguagePacks WHERE pack_name = #{packName}")
    Integer getPackIdByPackName(String general);

    @Select("SELECT language_pack FROM User_AILanguagePacks WHERE shop_name = #{shopName}")
    String getLanguagePackByShopName(String shopName);

    @Select("SELECT shop_name, language_pack, pack_id FROM User_AILanguagePacks WHERE shop_name = #{shopName}")
    UserAlLanguagePacksDO getAlLanguageByShopName(String shopName);

    @Insert("INSERT INTO User_AILanguagePacks (shop_name, language_pack, pack_id) VALUES (#{shopName}, #{languagePack}, #{packId})")
    Boolean insertUserAlLanguagePacks(String shopName, String languagePack, Integer packId);

    @Update("UPDATE User_AILanguagePacks SET language_pack = #{categoryText} WHERE shop_name = #{shopName}")
    Boolean updateUserAlLanguagePacks(String shopName, String categoryText);
}
