package com.bogda.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.common.entity.DO.AILanguagePacksDO;
import com.bogda.common.entity.DO.UserAlLanguagePacksDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AILanguagePacksMapper extends BaseMapper<AILanguagePacksDO> {
    @Insert("INSERT INTO User_AILanguagePacks (shop_name, pack_id) VALUES (#{shopName}, #{id})")
    Integer addDefaultLanguagePack(String shopName, Integer id);

    @Select("SELECT pack_id FROM User_AILanguagePacks WHERE shop_name = #{shopName}")
    Integer getPackIdByShopName(String shopName);

    @Select("SELECT id FROM AILanguagePacks WHERE pack_name = #{packName}")
    Integer getPackIdByPackName(String general);
}
