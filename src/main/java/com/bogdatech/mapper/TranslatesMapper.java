package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.TranslatesDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TranslatesMapper extends BaseMapper<TranslatesDO> {

    @Select("SELECT status FROM Translates WHERE shop_name = #{shopName} and target = #{target}")
    Integer getStatusInTranslates(String shopName, String target);

    @Insert("INSERT INTO Translates (source, access_token, target, shop_name, status) VALUES (#{source}, " +
            "#{accessToken}, #{target}, #{shopName}, #{status})")
    Integer insertShopTranslateInfo(String source,String accessToken, String target, String shopName, int status);

    @Select("SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE status = #{status}")
    List<TranslatesDO> readTranslateInfo(int status);

    @Update("UPDATE Translates SET status = #{status} WHERE shop_name = #{shopName} and target = #{target}")
    Integer updateTranslateStatus(Integer status, String shopName, String target);

    @Select("SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE shop_name = #{shopName}")
    List<TranslatesDO> readInfoByShopName(String shopName);

    @Select("SELECT status FROM Translates WHERE shop_name = #{shopName} and target = #{target}")
    Integer getStatusInTranslatesByShopName(String shopName, String target);

}
