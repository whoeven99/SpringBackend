package com.bogda.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.service.entity.DO.ItemsDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ItemsMapper extends BaseMapper<ItemsDO> {
    @Select("SELECT item_name, target, shop_name, translated_number, total_number, status FROM Items WHERE shop_name = #{shopName} and target = #{target}")
    List<ItemsDO> readItemsInfo(String shopName, String target);
}
