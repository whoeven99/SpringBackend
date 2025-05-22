package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.DO.CurrenciesDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CurrenciesMapper extends BaseMapper<CurrenciesDO> {

    @Insert("INSERT INTO Currencies (shop_name, currency_name, currency_code, rounding, exchange_rate, primary_status) " +
            "VALUES (#{shopName}, #{countryName}, #{currencyCode}, #{rounding}, #{exchangeRate}, #{primaryStatus})")
    Integer insertCurrency(String shopName, String countryName, String currencyCode, String rounding, String exchangeRate, int primaryStatus);

    @Update("UPDATE Currencies SET rounding = #{rounding}, exchange_rate = #{exchangeRate} WHERE id = #{id}")
    Integer updateCurrency(Integer id, String rounding, String exchangeRate);

    @Delete("DELETE FROM Currencies  WHERE id = #{id}")
    Integer deleteCurrency(Integer id);

    @Select("SELECT id, shop_name, currency_name, currency_code, rounding, exchange_rate, primary_status FROM Currencies  WHERE shop_name = #{shopName}")
    CurrenciesDO[] getCurrencyByShopName(String shopName);

    @Select("SELECT id, shop_name, currency_name, currency_code, rounding, exchange_rate, primary_status FROM Currencies  WHERE shop_name = #{shopName} AND currency_code = #{currencyCode}")
    CurrenciesDO getCurrencyByShopNameAndCurrencyCode(String shopName, String currencyCode);

    @Select("SELECT id, shop_name, currency_name, currency_code, rounding, exchange_rate, primary_status FROM Currencies  WHERE shop_name = #{shopName} AND primary_status = 1")
    CurrenciesDO getPrimaryStatusByShopName(String shopName);

    @Select("SELECT currency_code FROM Currencies WHERE primary_status = 1 AND shop_name = #{shopName}")
    String getCurrencyCodeByPrimaryStatusAndShopName(String shopName);
}
