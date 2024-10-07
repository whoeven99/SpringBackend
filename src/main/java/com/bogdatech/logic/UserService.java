package com.bogdatech.logic;

import com.bogdatech.repository.JdbcTestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserService {

    @Autowired
    private JdbcTestRepository jdbcTestRepository;

    public void addUser() {

    }

    public void getUser() {

    }
}
