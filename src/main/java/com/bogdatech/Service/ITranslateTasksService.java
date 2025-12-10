package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.TranslateTasksDO;

import java.util.List;

public interface ITranslateTasksService extends IService<TranslateTasksDO> {
    int updateStatusAllTo5ByShopName(String shopName);
    List<TranslateTasksDO> listTranslateStatus2And0TasksByShopName(String shopName);
}
