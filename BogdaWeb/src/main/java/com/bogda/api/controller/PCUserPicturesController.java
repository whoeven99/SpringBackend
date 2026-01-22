package com.bogda.api.controller;


import com.bogda.common.entity.DO.PCUserPicturesDO;
import com.bogda.common.entity.VO.AltTranslateVO;
import com.bogda.common.entity.VO.ImageTranslateVO;
import com.bogda.service.logic.PCApp.PCUserPicturesService;
import com.bogda.common.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/pcUserPic")
public class PCUserPicturesController {
    @Autowired
    private PCUserPicturesService pcUserPicturesService;

    // 存图片到db 和 腾讯云（原先的方法）
    @PostMapping("/insertPicToDbAndCloud")
    public BaseResponse<Object> insertPictureToDbAndCloud(@RequestParam("file") MultipartFile file, @RequestParam("shopName") String shopName, @RequestParam("userPicturesDoJson") String userPicturesDoJson) {
        return pcUserPicturesService.insertPicToDbAndCloud(file, shopName, userPicturesDoJson);
    }

    // 删除图片数据
    @PostMapping("/deletePicByShopNameAndPCUserPictures")
    public BaseResponse<Object> deletePictureData(@RequestParam("shopName") String shopName, @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return pcUserPicturesService.deletePicByShopNameAndPCUserPictures(shopName, pcUserPicturesDO);
    }

    // 翻译图片
    @PostMapping("/translatePic")
    public BaseResponse<Object> translatePic(@RequestParam("shopName") String shopName, @RequestBody ImageTranslateVO imageTranslateVO) {
        return pcUserPicturesService.translatePic(shopName, imageTranslateVO);
    }

    // 根据imageId查询所有的数据
    @PostMapping("/getPicsByImageIdAndShopName")
    public BaseResponse<Object> getPicsByImageIdAndShopName(@RequestParam("shopName") String shopName, @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return pcUserPicturesService.getPicsByImageIdAndShopName(shopName, pcUserPicturesDO);
    }

    // alt翻译
    @PostMapping("/altTranslate")
    public BaseResponse<Object> altTranslate(@RequestParam("shopName") String shopName, @RequestBody AltTranslateVO altTranslateVO) {
        return pcUserPicturesService.altTranslate(shopName, altTranslateVO);
    }

    // 更新userPic 数据
    @PostMapping("/updateUserPic")
    public BaseResponse<Object> updateUserPic(@RequestParam("shopName") String shopName, @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return pcUserPicturesService.updateUserPic(shopName, pcUserPicturesDO);
    }

    // 根据shopName, pictureId, code获取数据库对应用户图片信息
    @PostMapping("/selectPictureDataByShopNameAndProductIdAndLanguageCode")
    public BaseResponse<Object> selectPictureDataByShopNameAndProductIdAndLanguageCode(@RequestParam("shopName") String shopName, @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return pcUserPicturesService.selectPictureDataByShopNameAndProductIdAndLanguageCode(shopName, pcUserPicturesDO.getProductId(), pcUserPicturesDO.getLanguageCode());
    }

    // 根据shopName，languageCode, 获取全部的图片数据
    @PostMapping("/selectPicturesByShopNameAndLanguageCode")
    public BaseResponse<Object> selectPicturesByShopNameAndLanguageCode(@RequestParam("shopName") String shopName, @RequestParam("languageCode") String languageCode) {
        return pcUserPicturesService.selectPicturesByShopNameAndLanguageCode(shopName, languageCode);
    }

    // 单独删除翻译后的图片url
    @PostMapping("/deleteTranslateUrl")
    public BaseResponse<Object> deleteTranslateUrl(@RequestParam("shopName") String shopName, @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return pcUserPicturesService.deleteTranslateUrl(shopName, pcUserPicturesDO);
    }
}
