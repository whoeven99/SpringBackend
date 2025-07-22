package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.IAPGUserGeneratedTaskService;
import com.bogdatech.Service.IAPGUserPlanService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateDescriptionsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class APGUserGeneratedTaskService {
    private final IAPGUsersService iapgUsersService;
    private final IAPGUserGeneratedTaskService iapgUserGeneratedTaskService;
    private final IAPGUserPlanService iapgUserPlanService;

    @Autowired
    public APGUserGeneratedTaskService(IAPGUsersService iapgUsersService, IAPGUserGeneratedTaskService iapgUserGeneratedTaskService, IAPGUserPlanService iapgUserPlanService) {
        this.iapgUsersService = iapgUsersService;
        this.iapgUserGeneratedTaskService = iapgUserGeneratedTaskService;
        this.iapgUserPlanService = iapgUserPlanService;
    }

    /**
     * 初始化或更新相关数据
     * */
    public Boolean initOrUpdateData(String shopName, int status, String taskModel){
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return false;
        }

        //获取用户任务状态
        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, userDO.getId()));
        APGUserGeneratedTaskDO apgUserGeneratedTaskDO = new APGUserGeneratedTaskDO(null, userDO.getId(), status,taskModel);
        if (taskDO == null){
            //插入对应数据
            return iapgUserGeneratedTaskService.save(apgUserGeneratedTaskDO);
        }

        //更新对应数据
        return iapgUserGeneratedTaskService.update(apgUserGeneratedTaskDO, new LambdaUpdateWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, userDO.getId()));
    }

    public APGUserGeneratedTaskDO getUserData(String shopName) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return null;
        }

        return iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, userDO.getId()));
    }

    public void batchGenerateDescription(String shopName, GenerateDescriptionsVO generateDescriptionsVO) {
        // 根据shopName获取用户数据
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        // 获取用户最大额度限制
        Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());

        //按productIds将其分为一个个小任务及传入的数据存到APG_User_Generated_SubTask中


    }
}
