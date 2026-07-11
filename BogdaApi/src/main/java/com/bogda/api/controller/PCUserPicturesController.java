package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.entity.DO.PCUserPicturesDO;
import com.bogda.common.entity.VO.AltTranslateVO;
import com.bogda.common.entity.VO.ImageTranslateVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/pcUserPic")
public class PCUserPicturesController {

    @PostMapping("/insertPicToDbAndCloud")
    public BaseResponse<Object> insertPictureToDbAndCloud(
            @RequestParam("file") MultipartFile file,
            @RequestParam("shopName") String shopName,
            @RequestParam("userPicturesDoJson") String userPicturesDoJson) {
        return error();
    }

    @PostMapping("/deletePicByShopNameAndPCUserPictures")
    public BaseResponse<Object> deletePictureData(
            @RequestParam("shopName") String shopName,
            @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return error();
    }

    @PostMapping("/translatePic")
    public BaseResponse<Object> translatePic(
            @RequestParam("shopName") String shopName,
            @RequestBody ImageTranslateVO imageTranslateVO) {
        return error();
    }

    @PostMapping("/getPicsByImageIdAndShopName")
    public BaseResponse<Object> getPicsByImageIdAndShopName(
            @RequestParam("shopName") String shopName,
            @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return error();
    }

    @PostMapping("/altTranslate")
    public BaseResponse<Object> altTranslate(
            @RequestParam("shopName") String shopName,
            @RequestBody AltTranslateVO altTranslateVO) {
        return error();
    }

    @PostMapping("/updateUserPic")
    public BaseResponse<Object> updateUserPic(
            @RequestParam("shopName") String shopName,
            @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return error();
    }

    @PostMapping("/selectPictureDataByShopNameAndProductIdAndLanguageCode")
    public BaseResponse<Object> selectPictureDataByShopNameAndProductIdAndLanguageCode(
            @RequestParam("shopName") String shopName,
            @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return error();
    }

    @PostMapping("/selectPicturesByShopNameAndLanguageCode")
    public BaseResponse<Object> selectPicturesByShopNameAndLanguageCode(
            @RequestParam("shopName") String shopName,
            @RequestParam("languageCode") String languageCode) {
        return error();
    }

    @PostMapping("/deleteTranslateUrl")
    public BaseResponse<Object> deleteTranslateUrl(
            @RequestParam("shopName") String shopName,
            @RequestBody PCUserPicturesDO pcUserPicturesDO) {
        return error();
    }
}
