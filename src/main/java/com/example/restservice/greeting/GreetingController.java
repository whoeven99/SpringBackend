package com.example.restservice.greeting;

import java.io.Console;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

	private static final String template = "Hello, %s!";
	private final AtomicLong counter = new AtomicLong();

	@GetMapping("/test")
	public String test() {
		return sqlTest();
	}

	private String sqlTest() {
		try {
			Properties properties = new Properties();
			properties.load(GreetingController.class.getClassLoader().getResourceAsStream("application.properties"));

			//当 encrypt 属性设置为 true 且 trustServerCertificate 属性设置为 true 时，Microsoft JDBC Driver for SQL Server 将不验证SQL Server TLS 证书。 此设置常用于允许在测试环境中建立连接，如 SQL Server 实例只有自签名证书的情况。
			Connection connection = DriverManager.getConnection(properties.getProperty("url"));

			PreparedStatement readStatement = connection.prepareStatement("SELECT TOP (20) * FROM [dbo].[TestTable]");
			ResultSet resultSet = readStatement.executeQuery();

			String ans = "";
			while (resultSet.next()) {
				int id = resultSet.getInt("id");
				String name = resultSet.getString("name");

				ans += "id: " + id + ", name: " + name + "\n";
				System.out.println(ans);
			}

			resultSet.close();
			readStatement.close();
			connection.close();
			return ans;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}
}
