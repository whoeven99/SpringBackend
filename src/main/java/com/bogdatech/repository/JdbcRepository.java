package com.bogdatech.repository;

import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CamelToUnderscoreUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.enums.ErrorEnum.SQL_DELETE_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;

@Component
public class JdbcRepository {

    @Autowired
    private Connection connection;

    //对sql增删改的操作
    public int CUDInfo(Object[] info, String sql) {
        try (PreparedStatement cudStatement = connection.prepareStatement(sql)) {
            for (int i = 0; i < info.length; i++) {
                cudStatement.setObject(i + 1, info[i]);
            }
            // 执行插入
            return cudStatement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //对sql查询的操作
    public <T> List<T> readInfo(Object[] info, String sql, Class<T> clazz) {

        try (PreparedStatement selectStatement = connection.prepareStatement(sql)) {
            for (int i = 0; i < info.length; i++) {
                selectStatement.setObject(i + 1, info[i]);
            }
            // 执行查询
            ResultSet resultSet = selectStatement.executeQuery();
            // 处理结果集
            List<T> list = new ArrayList<T>();
            while (resultSet.next()) {
                if (clazz.equals(String.class)) {
                    // 直接从 ResultSet 中获取字符串值
                    String value = resultSet.getString(1);
                    list.add((T) value);
                } else {
                    T t = clazz.getDeclaredConstructor().newInstance();
                    for (Field field : clazz.getDeclaredFields()) {
                        field.setAccessible(true);
                        String columnName = (String) CamelToUnderscoreUtils.camelToUnderscore(field.getName());
                        Object value = resultSet.getObject(columnName);
                        field.set(t, value);
                    }
                    list.add(t);
                }
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int insertShopTranslateInfo(TranslateRequest request) {
        String sql = "INSERT INTO Translates (shop_name, access_token, source, target, status) VALUES (?, ?, ?, ?, ?)";//TODO token应该加密存入sql
        Object[] info = {request.getShopName(), request.getAccessToken(), request.getSource(), request.getTarget(), 2};
        return CUDInfo(info, sql);
    }

    public List<TranslatesDO> readTranslateInfo(int status) {
        String sql = "SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE status = ?";
        Object[] info = {status};
        return readInfo(info, sql, TranslatesDO.class);
    }

    public int updateTranslateStatus(String shopName, int status) {
        String sql = "UPDATE Translates SET status = ? WHERE shop_name = ?";
        Object[] info = {status, shopName};
        return CUDInfo(info, sql);
    }

    public List<TranslatesDO> readInfoByShopName(TranslateRequest request) {
        String sql = "SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        return readInfo(info, sql, TranslatesDO.class);
    }

    public List<TranslationCounterRequest> readCharsByShopName(TranslationCounterRequest request) {
        String sql = "SELECT id, shop_name, chars, used_chars, google_chars, open_ai_chars, total_chars FROM TranslationCounter WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        return readInfo(info, sql, TranslationCounterRequest.class);
    }

    public int insertCharsByShopName(TranslationCounterRequest translationCounterRequest) {
        String sql = "INSERT INTO TranslationCounter (shop_name, chars) VALUES (?, ?)";
        Object[] info = {translationCounterRequest.getShopName(), translationCounterRequest.getChars()};
        return CUDInfo(info, sql);
    }

    public int updateUsedCharsByShopName(TranslationCounterRequest translationCounterRequest) {
        String sql = "UPDATE TranslationCounter " +
                "SET used_chars = used_chars + ?" +
                "WHERE shop_name = ?";
        Object[] info = {translationCounterRequest.getUsedChars(), translationCounterRequest.getShopName()};
        return CUDInfo(info, sql);
    }

    public BaseResponse<Object> insertCurrency(CurrencyRequest request) {
        // 准备SQL插入语句
        String sql = "INSERT INTO Currencies (shop_name, country_name, currency_code, rounding, exchange_rate) VALUES (?, ?, ?, ?, ?)";
        Object[] info = {request.getShopName(), request.getCountryName(), request.getCurrencyCode(), request.getRounding(), request.getExchangeRate()};
        int result = CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse<>().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    public BaseResponse<Object> updateCurrency(CurrencyRequest request) {
        String sql = "UPDATE Currencies SET rounding = ?, exchange_rate = ? WHERE id = ?";
        Object[] info = {request.getRounding(), request.getExchangeRate(), request.getId()};
        int result = CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse<>().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);

    }

    public BaseResponse<Object> deleteCurrency(CurrencyRequest request) {
        String sql = "DELETE FROM Currencies  WHERE id = ?";
        Object[] info = {request.getId()};
        int result = CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse<>().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);

    }

    public BaseResponse<Object> getCurrencyByShopName(CurrencyRequest request) {
        String sql = "SELECT id, shop_name, country_name, currency_code, rounding, exchange_rate FROM Currencies  WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<CurrencyRequest> list = readInfo(info, sql, CurrencyRequest.class);
        return new BaseResponse<>().CreateSuccessResponse(list);
    }

    public BaseResponse<Object> test(CurrencyRequest request) {
        String sql = "SELECT id, shop_name, country_name, currency_code, rounding, exchange_rate FROM Currencies  WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<CurrencyRequest> list = readInfo(info, sql, CurrencyRequest.class);
        return new BaseResponse<>().CreateSuccessResponse(list);
    }

    public int insertTranslateText(TranslateTextRequest request) {
        String sql = "INSERT INTO TranslateTextTable (shop_name, resource_id, text_Type, digest, text_key, source_text, target_text, source_code, " +
                "target_code) VALUES (?,?,?,?,?,?,?,?,?)";
        Object[] info = {request.getShopName(), request.getResourceId(), request.getTextType(), request.getDigest(), request.getTextKey()
                , request.getSourceText(), request.getTargetText(), request.getSourceCode(), request.getTargetCode()};
        return CUDInfo(info, sql);
    }

    public List<TranslateTextRequest> getTranslateText(TranslateTextRequest request) {
        String sql = "SELECT shop_name, resource_id, text_Type, digest, text_key, source_text, target_text, source_code, target_code FROM TranslateTextTable " +
                "WHERE shop_name = ? AND resource_id = ? AND text_key = ? AND source_code = ? AND target_code = ? ";
        Object[] info = {request.getShopName(), request.getResourceId(), request.getTextKey(), request.getSourceCode(), request.getTargetCode()};
        return readInfo(info, sql, TranslateTextRequest.class);
    }

    public int updateTranslateText(TranslateTextRequest request) {
        String sql = "UPDATE TranslateTextTable SET target_text = ? WHERE digest = ? and shop_name = ?  and target_code = ?";
        Object[] info = {request.getTargetText(), request.getDigest(), request.getShopName(), request.getTargetCode()};
        return CUDInfo(info, sql);
    }

    public int updateTranslateStatusByTranslateRequest(TranslateRequest request) {
        String sql = "UPDATE Translates SET status = 2 WHERE shop_name = ? and source = ? and target = ?";
        Object[] info = {request.getShopName(), request.getSource(), request.getTarget()};
        return CUDInfo(info, sql);
    }

    public int addUser(UserRequest request) {
        String sql = "INSERT INTO Users(shop_name, access_token, email, phone, real_address, ip_address, user_tag) VALUES(?,?,?,?,?,?,?)";
        Object[] info = {request.getShopName(), request.getAccessToken(), request.getEmail(), request.getPhone(), request.getRealAddress(), request.getIpAddress(), request.getUserTag()};
        return CUDInfo(info, sql);
    }

    public int addUserSubscription(UserSubscriptionsRequest request) {
        String sql = "INSERT INTO UserSubscriptions(shop_name, plan_id, status, start_date, end_date) VALUES(?,?,?,?,?)";
        Object[] info = {request.getShopName(), request.getPlanId(), request.getStatus(), request.getStartDate(), request.getEndDate()};
        return CUDInfo(info, sql);
    }

    public String getUserSubscriptionPlan(ShopifyRequest request) {
        String sql = """
                SELECT sp.plan_name
                FROM UserSubscriptions us
                JOIN SubscriptionPlans sp ON us.plan_id = sp.plan_id
                WHERE us.shop_name = ?""";
        Object[] info = {request.getShopName()};
        List<String> strings = readInfo(info, sql, String.class);
        return strings.get(0);
    }

    public List<TranslateTextRequest> readTranslateTextInfo(TranslateTextRequest request) {
        String sql = "SELECT shop_name, resource_id, text_Type, digest, text_key, source_text, target_text, source_code, target_code " +
                "FROM TranslateTextTable WHERE digest = ? and shop_name = ?  and target_code = ? ";
        Object[] info = {request.getDigest(), request.getShopName(), request.getTargetCode()};
        return readInfo(info, sql, TranslateTextRequest.class);
    }

    public List<ItemsRequest> readItemsInfo(ShopifyRequest request) {
        String sql = "SELECT item_name, target, shop_name, translated_number, total_number FROM Items WHERE shop_name = ? and target = ?";
        Object[] info = {request.getShopName(), request.getTarget()};
        return readInfo(info, sql, ItemsRequest.class);
    }

    public int insertItems(ShopifyRequest request, String key, int totalChars, int translatedCounter) {
        String sql = "INSERT INTO Items (item_name, target, shop_name, translated_number, total_number) VALUES (?,?,?,?,?)";
        Object[] info = {key, request.getTarget(), request.getShopName(),  translatedCounter, totalChars};
        return CUDInfo(info, sql);
    }

    public int updateItemsByShopName(ShopifyRequest request, String key, int totalChars, int totalChars1) {
        String sql = "UPDATE Items SET translated_number = ?, total_number = ? WHERE shop_name = ? and target = ? and item_name = ?";
        Object[] info = {totalChars1, totalChars, request.getShopName(), request.getTarget(), key};
        return CUDInfo(info, sql);
    }

    public List<ItemsRequest> readSingleItemInfo(ShopifyRequest request, String key) {
        String sql = "SELECT item_name, target, shop_name, translated_number, total_number FROM Items WHERE shop_name = ? and target = ? and item_name = ?";
        Object[] info = {request.getShopName(), request.getTarget(), key};
        return readInfo(info, sql, ItemsRequest.class);
    }

    public String readStatus(TranslateRequest request) {
        String sql = "SELECT status FROM Translates WHERE shop_name = ? and target = ?";
        Object[] info = {request.getShopName(), request.getTarget()};
        return readInfo(info, sql, String.class).get(0);
    }

    public List<String> readShopNameInUser() {
        String sql = "SELECT shop_name FROM Users";
        return readInfo(null, sql, String.class);
    }
}
