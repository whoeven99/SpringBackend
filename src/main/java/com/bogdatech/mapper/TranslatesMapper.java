package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.TranslatesDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TranslatesMapper extends BaseMapper<TranslatesDO> {

    @Select("SELECT status FROM Translates WHERE shop_name = #{shopName} and target = #{target} and source = #{source}")
    Integer getStatusInTranslates(String shopName, String target, String source);

    @Insert("INSERT INTO Translates (source, access_token, target, shop_name, status) VALUES (#{source}, " +
            "#{accessToken}, #{target}, #{shopName}, #{status})")
    Integer insertShopTranslateInfo(String source,String accessToken, String target, String shopName, int status);

    @Select("SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE status = #{status}")
    List<TranslatesDO> readTranslateInfo(int status);

    @Update("UPDATE Translates SET status = #{status}, access_token = #{accessToken} WHERE shop_name = #{shopName} and target = #{target} and source = #{source}")
    Integer updateTranslateStatus(Integer status, String shopName, String target, String source, String accessToken);

    @Select("SELECT id,source,target,shop_name,status,create_at,update_at, auto_translate FROM Translates WHERE shop_name = #{shopName} and source = #{source}")
    List<TranslatesDO> readInfoByShopName(String shopName, String source);

    @Select("SELECT status FROM Translates WHERE shop_name = #{shopName}")
    List<Integer> readStatusInTranslatesByShopName(String shopName);

    @Select("SELECT source,target,shop_name,status,resource_type,auto_translate FROM Translates WHERE shop_name = #{shopName} and source = #{source} and target = #{target}")
    TranslatesDO readTranslatesDOByArray(String shopName, String source, String target);

    @Select("SELECT shop_name FROM Translates WHERE shop_name = #{shopName} and source = #{source} and target = #{target}")
    String getShopName(String shopName, String target, String source);

    @Delete("DELETE FROM Translates WHERE shop_name = #{shopName} and source = #{source} and target = #{target}")
    Boolean deleteFromTranslates(String shopName, String source, String target);

    @Update("UPDATE Translates SET status = 3 WHERE shop_name = #{shopName} and status = 2")
    Integer updateStatusByShopNameAnd2(String shopName);

    @Select("SELECT * FROM Translates WHERE shop_name = #{shopName} AND status > 0")
    List<TranslatesDO> getLanguageListCounter(String shopName);

    @Update("UPDATE Translates SET resource_type = #{resourceType} WHERE shop_name = #{shopName} and target = #{target} and source = #{source}")
    void updateTranslatesResourceType(String shopName, String target, String source, String resourceType);

    @Select("SELECT status FROM Translates WHERE shop_name = #{shopName} and target = #{target} and source = #{source}")
    Integer getStatusByShopNameAndTargetAndSource(String shopName, String target, String source);

    @Select("SELECT id FROM Translates WHERE shop_name = #{shopName} and target = #{target} and source = #{source}")
    Integer getIdByShopNameAndTarget(String shopName, String target, String source);

    @Select("SELECT resource_type FROM Translates WHERE shop_name = #{shopName} and target = #{target} and source = #{source}")
    String getResourceTypeByshopNameAndTarget(String shopName, String target, String source);
}
