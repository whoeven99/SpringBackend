package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserPicturesDO;

import java.util.List;

public interface IUserPicturesService extends IService<UserPicturesDO> {

    boolean insertPictureData(UserPicturesDO userPicturesDO);

    boolean deletePictureData(String shopName, String imageId, String imageBeforeUrl, String languageCode);

    List<UserPicturesDO> selectPicturesByShopNameAndImageIdAndLanguageCode(String shopName, String imageId, String languageCode);

    List<UserPicturesDO> selectPicturesBySHopNameAndLanguageCode(String shopName, String languageCode);
}
