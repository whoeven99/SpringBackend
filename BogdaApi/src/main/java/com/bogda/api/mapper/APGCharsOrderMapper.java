package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.APGCharsOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface APGCharsOrderMapper extends BaseMapper<APGCharsOrderDO> {
    @Update("UPDATE APG_Chars_Order SET status = #{status} WHERE id = #{id}")
    Boolean updateStatusByShopName(String id, String status);
}
