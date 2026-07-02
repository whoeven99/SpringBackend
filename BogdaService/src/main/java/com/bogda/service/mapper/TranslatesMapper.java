package com.bogda.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.common.entity.DO.TranslatesDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TranslatesMapper extends BaseMapper<TranslatesDO> {

    @Select("SELECT status FROM Translates WHERE shop_name = #{shopName} and target = #{target} and source = #{source}")
    Integer getStatusInTranslates(String shopName, String target, String source);

    @Insert("INSERT INTO Translates (source, access_token, target, shop_name, status) VALUES (#{source}, " +
            "#{accessToken}, #{target}, #{shopName}, #{status})")
    Integer insertShopTranslateInfo(String source,String accessToken, String target, String shopName, int status);

    @Update("UPDATE Translates SET status = 3 WHERE shop_name = #{shopName} and status = 2")
    Integer updateStatusByShopNameAnd2(String shopName);

    @Select("SELECT id FROM Translates WHERE shop_name = #{shopName} and target = #{target} and source = #{source}")
    Integer getIdByShopNameAndTarget(String shopName, String target, String source);

}
