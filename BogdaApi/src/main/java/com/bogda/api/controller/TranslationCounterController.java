package com.bogda.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.service.Service.ITranslationCounterService;
import com.bogda.service.Service.IUsersService;
import com.bogda.common.entity.DO.TranslationCounterDO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.entity.VO.AddCharsVO;
import com.bogda.common.entity.VO.TranslationCharsVO;
import com.bogda.service.logic.TranslationCounterService;
import com.bogda.service.logic.redis.OrdersRedisService;
import com.bogda.common.controller.request.TranslationCounterRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import static com.bogda.common.enums.ErrorEnum.*;

@RestController
@RequestMapping("/translationCounter")
public class TranslationCounterController {
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private TranslationCounterService translationCounterService;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private OrdersRedisService ordersRedisService;

    //给用户添加一个免费额度
    @PostMapping("/insertCharsByShopName")
    public BaseResponse<Object> insertCharsByShopName(@RequestBody TranslationCounterRequest request) {
        TranslationCounterDO translationCounterDO = iTranslationCounterService.readCharsByShopName(request.getShopName());
        if (translationCounterDO == null) {
            int result = iTranslationCounterService.insertCharsByShopName(request);
            //int result = 1;
            if (result > 0) {
                return new BaseResponse().CreateSuccessResponse(result);
            }else {
                return new BaseResponse<>().CreateSuccessResponse(null);
            }
        }else {
            return new BaseResponse<>().CreateSuccessResponse(null);
        }
    }

    /**
     * 获取用户额度信息
     * */
    @GetMapping("/getCharsByShopName")
    public BaseResponse<Object> getCharsByShopName(@RequestParam String shopName) {
       TranslationCounterDO translatesDOS = iTranslationCounterService.readCharsByShopName(shopName);
        if (translatesDOS != null){
            return new BaseResponse().CreateSuccessResponse(translatesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    /**
     * 添加字符额度
     * */
    @PostMapping("/addCharsByShopName")
    public BaseResponse<Object> addCharsByShopName(@RequestParam String shopName, @RequestBody AddCharsVO addCharsVO) {
        UsersDO usersDO = usersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));

        // 判断是否有订单标识 有的话 就直接返回true
        String orderId = ordersRedisService.getOrderId(shopName, addCharsVO.getGid());
        if (!"null".equals(orderId)) {
            AppInsightsUtils.trackTrace("addCharsByShopName 用户 " + shopName + " orderId: " + orderId  + " id: " + addCharsVO.getGid());
            return new BaseResponse<>().CreateErrorResponse(false);
        }

        // 获取用户accessToken
        if (translationCounterService.updateOnceCharsByShopName(shopName, usersDO.getAccessToken(), addCharsVO.getGid(), addCharsVO.getChars())){
            AppInsightsUtils.trackTrace("addCharsByShopName 用户 " + shopName + " id: " + addCharsVO.getGid());
            return new BaseResponse<>().CreateSuccessResponse(SERVER_SUCCESS);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_UPDATE_ERROR);
    }

    /**
     * 订阅付费计划后，后端判断是否是免费计划，是的话，不添加额度；不是的话，添加额度
     * */
    @PostMapping("/addCharsByShopNameAfterSubscribe")
    public BaseResponse<Object> addCharsByShopNameAfterSubscribe(@RequestParam String shopName, @RequestBody TranslationCharsVO translationCharsVO) {
        Boolean flag = translationCounterService.addCharsByShopNameAfterSubscribe(shopName, translationCharsVO);
        if (flag == null) {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateSuccessResponse(false);
        }
    }
}
