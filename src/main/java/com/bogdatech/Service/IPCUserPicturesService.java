package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.PCUserPicturesDO;

import java.util.List;

public interface IPCUserPicturesService extends IService<PCUserPicturesDO> {
    boolean insertPictureData(PCUserPicturesDO pcUserPicturesDO);

    boolean deletePictureData(String shopName, String imageId, String imageBeforeUrl, String languageCode);

    List<PCUserPicturesDO> listPcUserPics(String shopName, String imageId);

    boolean updatePictureData(String shopName, PCUserPicturesDO pcUserPicturesDO);

    List<PCUserPicturesDO> getUserPicByShopNameAndImageIdAndLanguageCode(String shopName, String imageId, String languageCode);
}
