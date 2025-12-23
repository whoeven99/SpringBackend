package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserPicturesDO;

public interface IUserPicturesService extends IService<UserPicturesDO> {

    boolean insertPictureData(UserPicturesDO userPicturesDO);

    boolean deletePictureData(String shopName, String imageId, String imageBeforeUrl, String languageCode);
}
