//package com.bogdatech.integration;
//
//import com.bogdatech.model.JdbcTestModel;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.scheduling.annotation.EnableAsync;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Component
//@EnableScheduling
//@EnableAsync
//public class TestTaskIntegration {
//
//    @Autowired
//    private BaseHttpIntegration baseHttpIntegration;
//
//    private String url = "http://localhost:8080/test";
//    @Scheduled(cron = "0/5 * * * * ? ")
//    @Async
//    public void testTask1() throws Exception {
//        System.out.println(LocalDateTime.now() + " Test task1 " + Thread.currentThread().getName());
//        System.out.println(baseHttpIntegration.sendHttpGet(url));
//    }
//
//    @Scheduled(cron = "0/10 * * * * ? ")
//    @Async
//    public void testTask2(){
//        System.out.println(LocalDateTime.now() + " Test task2 " + Thread.currentThread().getName());
//    }
//}
