package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.IUserPicturesService;
import com.bogdatech.entity.DO.UserPicturesDO;
import com.bogdatech.logic.UserPicturesService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.bogdatech.integration.HunYuanBucketIntegration.uploadFile;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/picture")
public class UserPicturesController {

    private final IUserPicturesService iUserPicturesService;
    private final UserPicturesService userPicturesService;

    @Autowired
    public UserPicturesController(IUserPicturesService iUserPicturesService, UserPicturesService userPicturesService) {
        this.iUserPicturesService = iUserPicturesService;
        this.userPicturesService = userPicturesService;
    }

    /**
     * 存储图片到数据库和腾讯云服务器上面
     */
    @PostMapping("/insertPictureToDbAndCloud")
    public BaseResponse<Object> insertPictureToDbAndCloud(@RequestParam("file") MultipartFile file, @RequestParam("shopName") String shopName, @RequestParam("userPicturesDoJson") String userPicturesDoJson) {
        //解析userPicturesDO
        UserPicturesDO userPicturesDO = null;
        try {
            userPicturesDO = OBJECT_MAPPER.readValue(userPicturesDoJson, UserPicturesDO.class);
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("userPicturesDoJson 解析失败 errors " + e);
        }
        //先将图片接受到后,存储到腾讯云服务器上面
        if (userPicturesDO == null || userPicturesDO.getImageId() == null) {
            return new BaseResponse<>().CreateErrorResponse("data is null");
        }
        String afterUrl = uploadFile(file, shopName, userPicturesDO);
        userPicturesDO.setImageAfterUrl(afterUrl);
        //再将图片相关数据存到数据库中
        userPicturesDO.setShopName(shopName);
        boolean b = iUserPicturesService.insertPictureData(userPicturesDO);
        //返回请求是否成功
        if (afterUrl != null && b) {
            return new BaseResponse<>().CreateSuccessResponse(userPicturesDO);
        } else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

    /**
     * 根据shopName, resourceId,pictureId获取数据库对应用户图片信息
     */
    @PostMapping("/getPictureDataByShopNameAndResourceIdAndPictureId")
    public BaseResponse<Object> getPictureDataByShopNameAndResourceIdAndPictureId(@RequestParam("shopName") String shopName, @RequestBody UserPicturesDO userPicturesDO) {
        List<UserPicturesDO> list = iUserPicturesService.list(new QueryWrapper<UserPicturesDO>().eq("shop_name", shopName).eq("image_id", userPicturesDO.getImageId()).eq("language_code", userPicturesDO.getLanguageCode()).eq("is_delete", false));
        if (list != null) {
            return new BaseResponse<>().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 软删除用户图片数据
     * */
    @PostMapping("/deletePictureData")
    public BaseResponse<Object> deletePictureData(@RequestParam("shopName") String shopName, @RequestBody UserPicturesDO userPicturesDO) {
        boolean b = iUserPicturesService.deletePictureData(shopName, userPicturesDO.getImageId(), userPicturesDO.getImageBeforeUrl(), userPicturesDO.getLanguageCode());
        if (b){
            userPicturesDO.setIsDelete(true);
            return new BaseResponse<>().CreateSuccessResponse(userPicturesDO);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
