package com.bogdatech.logic;

import com.bogdatech.Service.IPCUserService;
import com.bogdatech.entity.DO.PCUsersDO;
import com.bogdatech.entity.VO.PCUserPointsVO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static com.bogdatech.enums.ErrorEnum.SQL_UPDATE_ERROR;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

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
            pcUsersDO.setPurchasePoints(50000);
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

    public BaseResponse<Object> addPurchasePoints(String shopName, Integer chars) {
        PCUsersDO userByShopName = ipcUserService.getUserByShopName(shopName);
        // 先简单的添加额度， 订单表后面再做
        if (userByShopName == null){
            return new BaseResponse<>().CreateErrorResponse("用户不存在");
        }
        boolean flag = ipcUserService.updatePurchasePointsByShopName(shopName, chars);
        if (flag){
            appInsights.trackTrace("addPurchasePoints 添加额度成功 " + shopName + " chars: " + chars);
            return new BaseResponse<>().CreateSuccessResponse("添加成功");
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }

    public BaseResponse<Object> getPurchasePoints(String shopName) {
        PCUsersDO userByShopName = ipcUserService.getUserByShopName(shopName);
        if (userByShopName == null){
            return new BaseResponse<>().CreateErrorResponse("用户不存在");
        }
        PCUserPointsVO purchasePoints = new PCUserPointsVO();
        purchasePoints.setPurchasePoints(userByShopName.getPurchasePoints());
        purchasePoints.setUsedPoints(userByShopName.getUsedPoints());
        purchasePoints.setShopName(shopName);
        return new BaseResponse<>().CreateSuccessResponse(purchasePoints);
    }

    public BaseResponse<Object> uninstall(String shopName) {
        boolean flag = ipcUserService.updateUninstallByShopName(shopName);
        if (flag){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
