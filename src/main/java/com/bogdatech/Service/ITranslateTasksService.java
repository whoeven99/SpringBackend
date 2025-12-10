package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.TranslateTasksDO;

import java.util.List;

public interface ITranslateTasksService extends IService<TranslateTasksDO> {
    boolean updateByTaskId(String taskId, Integer status);

    int updateStatusAllTo5ByShopName(String shopName);

    int updateByShopName(String shopName, int i);

    List<String> listStatus2ShopName();

    List<String> listStatus0ShopName();

    List<TranslateTasksDO> listTranslateStatus2And0TasksByShopName(String shopName);

    List<TranslateTasksDO> getTranslateTasksByShopNameAndSourceAndTarget(String shopName, String source, String target);
}
