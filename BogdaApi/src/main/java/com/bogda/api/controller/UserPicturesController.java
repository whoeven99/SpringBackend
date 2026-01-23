package com.bogda.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogda.service.Service.IUserPicturesService;
import com.bogda.common.entity.DO.UserPicturesDO;
import com.bogda.common.entity.DTO.SimpleMultipartFileDTO;
import com.bogda.integration.aimodel.HunYuanBucketIntegration;
import com.bogda.service.logic.PCApp.PCUserPicturesService;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.applicationinsights.core.dependencies.apachecommons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

@RestController
@RequestMapping("/picture")
public class UserPicturesController {
    @Autowired
    private IUserPicturesService iUserPicturesService;

    public static List<String> allowedMimeTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/gif",
            "image/jpg"
    );

    /**
     * 存储图片到数据库和腾讯云服务器上面
     */
    @PostMapping("/insertPictureToDbAndCloud")
    public BaseResponse<Object> insertPictureToDbAndCloud(@RequestParam("file") MultipartFile file, @RequestParam("shopName") String shopName, @RequestParam("userPicturesDoJson") String userPicturesDoJson) {
        //解析userPicturesDO
        UserPicturesDO userPicturesDO = null;
        try {
            userPicturesDO = JsonUtils.OBJECT_MAPPER.readValue(userPicturesDoJson, UserPicturesDO.class);
        } catch (JsonProcessingException e) {
            AppInsightsUtils.trackTrace("insertPictureToDbAndCloud " + shopName + " userPicturesDoJson 解析失败 errors " + e);
        }
        //先判断是否有图片,有图片做上传和插入更新数据;没有图片,做插入和更新数据
        if (!file.isEmpty() && userPicturesDO != null && userPicturesDO.getImageId() != null) {
            //做图片的限制
            if (!allowedMimeTypes.contains(file.getContentType())) {
                return new BaseResponse<>().CreateErrorResponse("Image format error");
            }
            //将图片上传到腾讯云
            String afterUrl = HunYuanBucketIntegration.uploadFile(file, shopName, userPicturesDO.getImageId());
            userPicturesDO.setImageAfterUrl(afterUrl);
            //再将图片相关数据存到数据库中
            userPicturesDO.setShopName(shopName);
            boolean b = iUserPicturesService.insertPictureData(userPicturesDO);
            //数据库做上传和插入更新数据
            if (afterUrl != null && b) {
                return new BaseResponse<>().CreateSuccessResponse(userPicturesDO);
            } else {
                return new BaseResponse<>().CreateErrorResponse(false);
            }
        }else if (file.isEmpty() && userPicturesDO != null && userPicturesDO.getImageId() != null) {
            boolean b = iUserPicturesService.insertPictureData(userPicturesDO);
            if (b) {
                return new BaseResponse<>().CreateSuccessResponse(userPicturesDO);
            } else {
                return new BaseResponse<>().CreateErrorResponse(false);
            }
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 根据shopName, resourceId,pictureId获取数据库对应用户图片信息
     */
    @PostMapping("/getPictureDataByShopNameAndResourceIdAndPictureId")
    public BaseResponse<Object> getPictureDataByShopNameAndResourceIdAndPictureId(@RequestParam("shopName") String shopName, @RequestBody UserPicturesDO userPicturesDO) {
        List<UserPicturesDO> list = iUserPicturesService.list(new QueryWrapper<UserPicturesDO>().eq("shop_name", shopName).eq("image_id", userPicturesDO.getImageId()).eq("language_code", userPicturesDO.getLanguageCode()).eq("is_delete", false));
        if (list != null) {
            // 替换图片url
            list.forEach(pic -> {
                if (pic.getImageAfterUrl() != null) {
                    pic.setImageAfterUrl(pic.getImageAfterUrl().replace(PCUserPicturesService.COS_URL, PCUserPicturesService.CDN_URL));
                }
            });
            return new BaseResponse<>().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 根据前端传的shop,languageCode,拿这个语言下的全部图片
     * */
    @PostMapping("/getPictureDataByShopNameAndLanguageCode")
    public BaseResponse<Object> getPictureDataByShopNameAndLanguageCode(@RequestParam("shopName") String shopName, @RequestParam("languageCode") String languageCode) {
        List<UserPicturesDO> list = iUserPicturesService.list(new QueryWrapper<UserPicturesDO>().eq("shop_name", shopName).eq("language_code", languageCode).eq("is_delete", false));
        if (list != null) {
            list.forEach(pic -> {
                if (pic.getImageAfterUrl() != null) {
                    pic.setImageAfterUrl(pic.getImageAfterUrl().replace(PCUserPicturesService.COS_URL, PCUserPicturesService.CDN_URL));
                }
            });
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

    /**
     * 根据图片url的String，存到腾讯云和数据库里面
     * */
    @PostMapping("/saveImageToCloud")
    public BaseResponse<Object> saveImageToCloud(@RequestParam("pic") String pic, @RequestParam("shopName") String shopName, @RequestParam("userPicturesDoJson") String userPicturesDoJson) {
        UserPicturesDO userPicturesDO = null;
        try {
            userPicturesDO = JsonUtils.OBJECT_MAPPER.readValue(userPicturesDoJson, UserPicturesDO.class);
        } catch (JsonProcessingException e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("saveImageToCloud " + shopName + " userPicturesDoJson 解析失败 errors " + e);
        }
        if (userPicturesDO == null) {
            return new BaseResponse<>().CreateSuccessResponse(false);
        }

        // 对返回的图片url做处理
        MultipartFile multipartFile = convertUrlToMultipartFile(pic);
        if (multipartFile == null || multipartFile.isEmpty()){
            return new BaseResponse<>().CreateErrorResponse(false);
        }

        // 存到腾讯云bucket桶里面
        String afterUrl = HunYuanBucketIntegration.uploadFile(multipartFile, shopName, userPicturesDO.getImageId());
        if (afterUrl == null ) {
            return new BaseResponse<>().CreateSuccessResponse(false);
        }
        userPicturesDO.setImageAfterUrl(afterUrl);

        // 再将图片相关数据存到数据库中
        userPicturesDO.setShopName(shopName);
        boolean flag = iUserPicturesService.insertPictureData(userPicturesDO);
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(userPicturesDO);
        }
        return new BaseResponse<>().CreateSuccessResponse(false);
    }

    private MultipartFile convertUrlToMultipartFile(String imageUrl) {
        try {
            // 1. 打开URL连接
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            String contentType = connection.getContentType(); // 尝试获取Content-Type
            String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1); // 取URL最后一段作为文件名
            String fileNameWithoutExt = FilenameUtils.getBaseName(fileName);
            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            // 2. 创建 MultipartFile
            return new SimpleMultipartFileDTO(
                    "file",                // form字段名
                    fileNameWithoutExt,              // 文件名
                    contentType != null ? contentType : "application/octet-stream", // 如果获取不到就用通用类型
                    outputStream.toByteArray()
            );
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            AppInsightsUtils.trackTrace("FatalException convertUrlToMultipartFile error: " + e.getMessage());
        }
        return null;
    }
}
