package com.bogda.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogda.api.entity.DO.ItemsDO;
import com.bogda.api.model.controller.request.ItemsRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ItemsMapper extends BaseMapper<ItemsDO> {
    @Select("SELECT item_name, target, shop_name, translated_number, total_number, status FROM Items WHERE shop_name = #{shopName} and target = #{target}")
    List<ItemsDO> readItemsInfo(String shopName, String target);

    @Update("UPDATE Items SET translated_number = #{totalChars1}, total_number = #{totalChars}, status = 1 WHERE shop_name = #{shopName} and target = #{target} and item_name = #{key}")
    Integer updateItemsByShopName(String shopName, String target, String key, int totalChars, int totalChars1);

    @Select("SELECT item_name, target, shop_name, translated_number, total_number FROM Items WHERE shop_name = #{shopName} and target = #{target} and item_name = #{key}")
    List<ItemsRequest> readSingleItemInfo(String shopName, String target, String key);

    @Update("UPDATE Items SET total_number = #{totalChars}, status = 1 WHERE shop_name = #{shopName} and item_name = #{key}")
    Integer updateItemsTotalData(String shopName, int totalChars, String key);
}
