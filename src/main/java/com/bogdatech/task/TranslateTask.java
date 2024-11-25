package com.bogdatech.task;

import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@EnableScheduling
@EnableAsync
public class TranslateTask {

//    @Autowired
    private TranslatesServiceImpl translatesServiceImpl;

    @Autowired
    private TranslateService translateService;

//    @PostConstruct
//    @Scheduled(cron = "0 5 * * * ?")
    public void translate() {
        System.out.println("translate");
        //查询Translates表status为0的数据
        List<TranslatesDO> list = translatesServiceImpl.readTranslateInfo(0);
        //遍历list，调用translateService.translate()方法
        for (TranslatesDO request : list) {
            //调用翻译
            System.out.println("正在进行翻译");
            //翻译功能目前注释中
            translateService.test(request);
            //修改status状态
//            int i = translatesServiceImpl.updateTranslateStatus(request.getShopName(), 2, request.getTarget());
            //判断是否修改成功
//            if (i > 0) {
//                System.out.println("修改成功");
//            }
        }
    }
}
