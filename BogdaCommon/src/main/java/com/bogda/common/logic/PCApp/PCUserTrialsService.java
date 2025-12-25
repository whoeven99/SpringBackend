package com.bogda.common.logic.PCApp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.common.model.controller.response.BaseResponse;
import com.bogda.common.repository.entity.PCOrdersDO;
import com.bogda.common.repository.entity.PCUserTrialsDO;
import com.bogda.common.repository.repo.PCOrdersRepo;
import com.bogda.common.repository.repo.PCUserTrialsRepo;
import com.bogda.common.utils.CaseSensitiveUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
public class PCUserTrialsService {
    @Autowired
    private PCUserTrialsRepo pcUserTrialsRepo;
    @Autowired
    private PCOrdersRepo pcOrdersRepo;

    /**
     * 1,向免费订阅表里面插入用户信息
     * 2，修改用户订阅表，改为7 (这个暂时去除，目前没有用)
     *
     * @param shopName 商店名称
     * @return Boolean 是否成功
     */
    public Boolean insertUserTrial(String shopName) {
        return pcUserTrialsRepo.insertUserTrial(shopName);
    }

    /**
     * 1,给前端一个查询接口
     *
     * @param shopName 商店名称
     * @return Boolean 是否免费 true 是已经免费使用，false，没有
     */
    public BaseResponse<Object> queryUserTrialByShopName(String shopName) {
        // 判断是否购买过订阅计划，如果有则返回true
        List<PCOrdersDO> pcOrdersDOList = pcOrdersRepo.selectOrdersByShopName(shopName);

        if (!pcOrdersDOList.isEmpty()) {
            CaseSensitiveUtils.appInsights.trackTrace("queryUserTrialByShopName " + shopName + " 返回的pcOrdersDOList 值为 ： " + pcOrdersDOList);
            return new BaseResponse<>().CreateSuccessResponse(true);
        }

        Boolean flag = pcUserTrialsRepo.queryUserTrialByShopName(shopName);
        if (flag != null) {
            return new BaseResponse<>().CreateSuccessResponse(flag);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 获取免费试用是否弹窗数据
     */
    public BaseResponse<Object> isShowFreePlan(String shopName) {
        //从数据库中获取是否弹出免费试用的弹窗
        PCUserTrialsDO pcUserTrial = pcUserTrialsRepo.getUserTrialByShopName(shopName);

        if (pcUserTrial != null && !pcUserTrial.getIsTrialShow()) {
            //将弹窗状态改为true
            boolean update = pcUserTrialsRepo.updateTrialShowByShopName(shopName);

            if (update) {
                return new BaseResponse<>().CreateSuccessResponse(true);
            }
            return new BaseResponse<>().CreateErrorResponse("update error");

        }
        return new BaseResponse<>().CreateSuccessResponse(false);

    }

    /**
     * 判断是否在免费试用时间
     */
    public BaseResponse<Object> isInFreePlanTime(String shopName) {
        // 获取该用户是否在免费试用期间
        Timestamp now = Timestamp.from(Instant.now());
        PCUserTrialsDO pcUserTrialsDO = pcUserTrialsRepo.getUserTrialByShopName(shopName);

        // 判断now是否在trialStart 和 trialEnd 中间.  是，返回true； 否，返回false
        if (pcUserTrialsDO != null && pcUserTrialsDO.getTrialStart().before(now) && pcUserTrialsDO.getTrialEnd().after(now)
                && !pcUserTrialsDO.getIsTrialExpired()) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateSuccessResponse(false);
    }


    public BaseResponse<Object> insertOrUpdateFreePlan(String shopName) {
        // 获取免费试用表数据， 有，改； 没有，存
        PCUserTrialsDO pcUserTrialsDO = pcUserTrialsRepo.getUserTrialByShopName(shopName);
        if (pcUserTrialsDO != null) {
            pcUserTrialsDO.setIsTrialExpired(true);
            pcUserTrialsRepo.update(pcUserTrialsDO, new LambdaQueryWrapper<PCUserTrialsDO>()
                    .eq(PCUserTrialsDO::getShopName, shopName));
            return new BaseResponse<>().CreateSuccessResponse(true);
        }

        pcUserTrialsRepo.insertUserTrial(shopName);
        return new BaseResponse<>().CreateSuccessResponse(true);
    }
}
