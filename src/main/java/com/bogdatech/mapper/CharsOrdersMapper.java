package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.CharsOrdersDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CharsOrdersMapper extends BaseMapper<CharsOrdersDO> {
    @Update("UPDATE CharsOrders SET status = #{status} WHERE id = #{id}")
    Boolean updateStatusByShopName(String id, String status);

    @Select("SELECT id FROM CharsOrders WHERE shop_name = #{shopName}")
    List<String> getIdByShopName(String shopName);
}
