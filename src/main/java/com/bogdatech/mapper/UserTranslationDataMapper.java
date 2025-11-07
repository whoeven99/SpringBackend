package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.UserTranslationDataDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserTranslationDataMapper extends BaseMapper<UserTranslationDataDO> {
    @Select("""
            SELECT TOP 8 utd.*
                         FROM User_Translation_Data utd
                         JOIN (
                             SELECT shop_name, MAX(created_at) AS max_created_at
                             FROM User_Translation_Data
                             WHERE Status = 0
                             GROUP BY shop_name
                         ) latest
                             ON utd.shop_name = latest.shop_name
                            AND utd.created_at = latest.max_created_at
                         WHERE utd.Status = 0
                         ORDER BY utd.created_at DESC;
            """)
    List<UserTranslationDataDO> selectTranslationDataList();
}
