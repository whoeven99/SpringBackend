package com.bogdatech.repository;

import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.model.controller.request.TranslateRequest;
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

import static com.bogdatech.enums.ErrorEnum.SQL_INSERT_ERROR;
import static com.bogdatech.enums.ErrorEnum.SQL_SELECT_ERROR;

@Component
public class JdbcRepository {

    @Autowired
    private Connection connection;

    //对sql增删改的操作
    public int CUDInfo(Object[] info, String sql){
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
    public <T> List<T> readInfo(Object[] info,String sql,Class<T> clazz){

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

    public BaseResponse insertShopTranslateInfo(TranslateRequest request) {
        String sql = "INSERT INTO Translates (shop_name, access_token, source, target) VALUES (?, ?, ?, ?)";//TODO token应该加密存入sql
        Object[] info = {request.getShopName(), request.getAccessToken(), request.getSource(), request.getTarget()};
        int result = CUDInfo(info, sql);
        if (result > 0) {
            return new BaseResponse().CreateSuccessResponse(result);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_INSERT_ERROR);
    }

    public List<TranslatesDO> readTranslateInfo(int status){
        String sql = "SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE status = ?";
        Object[] info = {status};
        List<TranslatesDO> list =  readInfo(info, sql, TranslatesDO.class);
        return list;
    }

    public int updateTranslateStatus(int id, int status){
        String sql = "UPDATE Translates SET status = ? WHERE id = ?";
        Object[] info = {status, id};
        int result = CUDInfo(info, sql);
        return result;
    }

    public BaseResponse readInfoByShopName(TranslateRequest request) {
        String sql = "SELECT id,source,target,shop_name,status,create_at,update_at FROM Translates WHERE shop_name = ?";
        Object[] info = {request.getShopName()};
        List<TranslatesDO> translatesDOS = readInfo(info, sql, TranslatesDO.class);
        if (translatesDOS.size() > 0){
            return new BaseResponse().CreateSuccessResponse(translatesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }
}
