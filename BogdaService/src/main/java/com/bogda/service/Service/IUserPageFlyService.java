package com.bogda.service.Service;

import com.bogda.service.entity.DO.UserPageFlyDO;

import java.util.List;

public interface IUserPageFlyService {
    boolean insertUserPageFlysData(UserPageFlyDO userPageFly);

    UserPageFlyDO getUserPageFlysData(String languageCode, String shopName, String targetText, String sourceText);

    boolean updateUserPageFlysData(UserPageFlyDO userPageFly);

    List<UserPageFlyDO> selectUserPageFlysData(String shopName, String languageCode);
}
