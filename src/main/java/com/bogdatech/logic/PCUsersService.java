package com.bogdatech.logic;

import com.bogdatech.Service.IPCUserService;
import com.bogdatech.entity.DO.PCUsersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
public class PCUsersService {
    @Autowired
    private IPCUserService ipcUserService;

    public void initUser(String shopName, PCUsersDO pcUsersDO) {
        // 获取用户是否存在 ，存在，做更新操作； 不存在，存储用户
        PCUsersDO pcUsers = ipcUserService.getUserByShopName(shopName);
        if (pcUsers == null) {
            pcUsersDO.setPurchasePoints(0);
            pcUsersDO.setUsedPoints(0);
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            pcUsersDO.setCreateAt(now);
            pcUsersDO.setLoginTime(now);
            ipcUserService.saveSingleUser(pcUsersDO);
        } else {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            pcUsers.setEmail(pcUsersDO.getEmail());
            pcUsers.setAccessToken(pcUsersDO.getAccessToken());
            pcUsers.setFirstName(pcUsersDO.getFirstName());
            pcUsers.setUpdateAt(now);
            pcUsers.setLoginTime(now);
            ipcUserService.updateSingleUser(pcUsersDO);
        }
    }
}
