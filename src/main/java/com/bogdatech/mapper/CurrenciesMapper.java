package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.CurrenciesDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CurrenciesMapper extends BaseMapper<CurrenciesDO> {

    @Insert("INSERT INTO Currencies (shop_name, currency_name, currency_code, rounding, exchange_rate) " +
            "VALUES (#{shopName}, #{countryName}, #{currencyCode}, #{rounding}, #{exchangeRate})")
    Integer insertCurrency(String shopName, String countryName, String currencyCode, String rounding, String exchangeRate);

    @Update("UPDATE Currencies SET rounding = #{rounding}, exchange_rate = #{exchangeRate} WHERE id = #{id}")
    Integer updateCurrency(Integer id, String rounding, String exchangeRate);

    @Delete("DELETE FROM Currencies  WHERE id = #{id}")
    Integer deleteCurrency(Integer id);

    @Select("SELECT id, shop_name, currency_name, currency_code, rounding, exchange_rate FROM Currencies  WHERE shop_name = #{shopName}")
    CurrenciesDO[] getCurrencyByShopName(String shopName);

    @Select("SELECT id, shop_name, currency_name, currency_code, rounding, exchange_rate FROM Currencies  WHERE shop_name = #{shopName} AND currency_code = #{currencyCode}")
    CurrenciesDO getCurrencyByShopNameAndCurrencyCode(String shopName, String currencyCode);
}
