package com.bogdatech.integration;

import com.bogdatech.utils.CamelToUnderscore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Component
public class AzureSQLIntegration {

    @Autowired
    private Connection connection;


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
}
