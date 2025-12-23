package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.UserTranslationDataDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserTranslationDataMapper extends BaseMapper<UserTranslationDataDO> {
    @Select("""
            WITH RankedData AS (
                SELECT *,
                       ROW_NUMBER() OVER (PARTITION BY shop_name ORDER BY created_at DESC) AS rn
                FROM User_Translation_Data
                WHERE Status = 0
            )
            SELECT TOP 8 *
            FROM RankedData
            WHERE rn = 1;

            """)
    List<UserTranslationDataDO> selectTranslationDataList();
}
