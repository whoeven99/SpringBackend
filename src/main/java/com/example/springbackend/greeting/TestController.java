package com.example.springbackend.greeting;

import java.sql.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

	@Autowired
	private Connection connection;

	@GetMapping("/test")
	public String test() {
		return "test11" + sqlTest();
	}

	private String sqlTest() {
		try {
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
			return ans;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}
}
