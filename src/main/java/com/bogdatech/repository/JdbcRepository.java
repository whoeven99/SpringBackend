package com.bogdatech.repository;

import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.model.controller.request.CurrencyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.request.TranslateTextRequest;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CamelToUnderscore;
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
        try (PreparedStatement cudStatement = connection.prepareStatement(sql);) {
            for (int i = 0; i < info.length; i++) {
                cudStatement.setObject(i + 1, info[i]);
            }
            // 执行插入
            int rowsAffected = cudStatement.executeUpdate();
            return rowsAffected;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //对sql查询的操作
    public <T> List<T> readInfo(Object[] info, String sql, Class<T> clazz) {

        try (PreparedStatement selectStatement = connection.prepareStatement(sql);) {
            for (int i = 0; i < info.length; i++) {
                selectStatement.setObject(i + 1, info[i]);
            }
            // 执行查询
            ResultSet resultSet = selectStatement.executeQuery();
            // 处理结果集
            List<T> list = new ArrayList<T>();
            while (resultSet.next()) {
                T t = clazz.newInstance();
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object name = CamelToUnderscore.camelToUnderscore(field.getName());
                    Object value = resultSet.getObject((String) name);
                    field.set(t, value);
                }
                list.add(t);
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public int insertShopTranslateInfo(TranslateRequest request) {
        String sql = "INSERT INTO Translates (shop_name, access_token, source, target, status) VALUES (?, ?, ?, ?, ?)";//TODO token应该加密存入sql
        Object[] info = {request.getShopName(), request.getAccessToken(), request.getSource(), request.getTarget(), 2};
        int result = CUDInfo(info, sql);
        return result;
    }

    public List<TranslatesDO> readTranslateInfo(int status) {
        String sql = "SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE status = ?";
        Object[] info = {status};
        List<TranslatesDO> list = readInfo(info, sql, TranslatesDO.class);
        return list;
    }

    public int updateTranslateStatus(String shopName, int status) {
        String sql = "UPDATE Translates SET status = ? WHERE shop_name = ?";
        Object[] info = {status, shopName};
        int result = CUDInfo(info, sql);
        return result;
    }

    public List<TranslatesDO> readInfoByShopName(TranslateRequest request) {
        String sql = "SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<TranslatesDO> translatesDOS = readInfo(info, sql, TranslatesDO.class);
        return translatesDOS;
    }

    public List<TranslationCounterRequest> readCharsByShopName(TranslationCounterRequest request) {
        String sql = "SELECT id, shop_name, chars, used_chars, google_chars, open_ai_chars, total_chars FROM TranslationCounter WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<TranslationCounterRequest> translatesDOS = readInfo(info, sql, TranslationCounterRequest.class);
        return translatesDOS;
    }

    public int insertCharsByShopName(TranslationCounterRequest translationCounterRequest) {
        String sql = "INSERT INTO TranslationCounter (shop_name, chars) VALUES (?, ?)";
        Object[] info = {translationCounterRequest.getShopName(), translationCounterRequest.getChars()};
        int result = CUDInfo(info, sql);
        return result;
    }

    public int updateCharsByShopName(TranslationCounterRequest translationCounterRequest) {
        String sql = "UPDATE TranslationCounter SET used_chars = ? WHERE shop_name = ?";
        Object[] info = {translationCounterRequest.getChars(), translationCounterRequest.getShopName()};
        int result = CUDInfo(info, sql);
        return result;
    }

    public BaseResponse insertCurrency(CurrencyRequest request) {
        // 准备SQL插入语句
        String sql = "INSERT INTO Currencies (shop_name, country_name, currency_code, rounding, exchange_rate) VALUES (?, ?, ?, ?, ?)";
        Object[] info = {request.getShopName(), request.getCountryName(), request.getCurrencyCode(), request.getRounding(), request.getExchangeRate()};
        int result = CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    public BaseResponse updateCurrency(CurrencyRequest request) {
        String sql = "UPDATE Currencies SET rounding = ?, exchange_rate = ? WHERE id = ?";
        Object[] info = {request.getRounding(), request.getExchangeRate(), request.getId()};
        int result = CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);

    }

    public BaseResponse deleteCurrency(CurrencyRequest request) {
        String sql = "DELETE FROM Currencies  WHERE id = ?";
        Object[] info = {request.getId()};
        int result = CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);

    }

    public BaseResponse getCurrencyByShopName(CurrencyRequest request) {
        String sql = "SELECT id, shop_name, country_name, currency_code, rounding, exchange_rate FROM Currencies  WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<CurrencyRequest> list = readInfo(info, sql, CurrencyRequest.class);
        return new BaseResponse<>().CreateSuccessResponse(list);
    }

    public BaseResponse test(CurrencyRequest request) {
        String sql = "SELECT id, shop_name, country_name, currency_code, rounding, exchange_rate FROM Currencies  WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<CurrencyRequest> list = readInfo(info, sql, CurrencyRequest.class);
        return new BaseResponse<>().CreateSuccessResponse(list);
    }

    public int insertTranslateText(TranslateTextRequest request){
        String sql = "INSERT INTO TranslateTextTable (shop_name, resource_id, text_Type, digest, text_key, source_text, target_text, source_code, " +
                "target_code) VALUES (?,?,?,?,?,?,?,?,?)";
        Object[] info = {request.getShopName(), request.getResourceId(), request.getTextType(), request.getDigest(), request.getTextKey()
                , request.getSourceText(), request.getTargetText(), request.getSourceCode(), request.getTargetCode()};
        int result = CUDInfo(info, sql);
        return result;
    }

    public List<TranslateTextRequest> getTranslateText(String request){
        String sql = "SELECT shop_name, resource_id, text_Type, digest, text_key, source_text, target_text, source_code, target_code FROM TranslateText WHERE digest = ?";
        Object[] info = {request};
        List<TranslateTextRequest> list = readInfo(info, sql, TranslateTextRequest.class);
        return list;
    }

    public int updateTranslateText(TranslateTextRequest request){
        String sql = "UPDATE TranslateText SET target_text = ? WHERE digest = ?";
        Object[] info = {request.getTargetText(), request.getDigest()};
        int result = CUDInfo(info, sql);
        return result;
    }

    public int updateTranslateStatusByTranslateRequest(TranslateRequest request) {
        String sql = "UPDATE Translates SET status = 2 WHERE shop_name = ? and source = ? and target = ?";
        Object[] info = {request.getShopName(), request.getSource(), request.getTarget()};
        int result = CUDInfo(info, sql);
        return result;
    }

}
