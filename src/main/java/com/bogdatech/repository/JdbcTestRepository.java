package com.bogdatech.repository;

import com.bogdatech.model.JdbcTestModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Component
public class JdbcTestRepository {
    @Autowired
    private Connection connection;

    public List<JdbcTestModel> sqlTest() {
        try {
            PreparedStatement readStatement = connection.prepareStatement("SELECT TOP (20) * FROM [dbo].[TestTable]");
            ResultSet resultSet = readStatement.executeQuery();

            List<JdbcTestModel> list = new ArrayList<>();
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");

                list.add(new JdbcTestModel(id, name));
            }

            resultSet.close();
            readStatement.close();
            return list;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
