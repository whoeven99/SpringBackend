package com.bogdatech.logic;

import com.bogdatech.Service.IUserPageFlyService;
import com.bogdatech.entity.DO.UserPageFlyDO;
import com.bogdatech.entity.VO.PageFlyVO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.bogdatech.task.TranslateTask.appInsights;

@Service
public class UserPageFlyService {
    @Autowired
    private IUserPageFlyService iUserPageFlyService;

    public BaseResponse<Object> editTranslatedData(String shopName, List<UserPageFlyDO> userPageFlys) {
        if (shopName == null || shopName.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse("shopName cannot be null or empty");
        }

        if (userPageFlys == null || userPageFlys.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(Collections.emptyList());
        }

        List<UserPageFlyDO> resultList = new ArrayList<>();

        for (UserPageFlyDO item : userPageFlys) {
            if (item == null) {
                continue;
            }

            item.setShopName(shopName);

            // 判断新增还是更新
            if (item.getId() == null) {

                boolean success = false;
                try {
                    success = iUserPageFlyService.insertUserPageFlysData(item);
                    System.out.println("success: " + success);
                } catch (Exception e) {
                    appInsights.trackTrace("FatalException editTranslatedData " + e.getMessage());
                }

                if (success) {
                    // 避免再次用多个字段查，可以考虑 insert 返回 id
                    UserPageFlyDO inserted = iUserPageFlyService.getUserPageFlysData(item.getLanguageCode(), shopName
                            , item.getTargetText(), item.getSourceText());
                    System.out.println("inserted: " + inserted);
                    resultList.add(inserted != null ? inserted : item);
                } else {
                    resultList.add(item);
                }

            } else {

                if (item.getTargetText() != null && item.getTargetText().isEmpty()) {
                    item.setDeleted(true);
                }

                Timestamp nowUtc = Timestamp.from(Instant.now());
                item.setUpdatedAt(nowUtc);
                iUserPageFlyService.updateUserPageFlysData(item);
                resultList.add(item);
            }
        }

        return new BaseResponse<>().CreateSuccessResponse(resultList);
    }

    public BaseResponse<Object> readTranslatedText(String shopName, String languageCode) {
        // 获取对应的所有数据，转化为Map格式
        List<UserPageFlyDO> userPageFlyDOS = iUserPageFlyService.selectUserPageFlysData(shopName, languageCode);
        List<PageFlyVO> voList = userPageFlyDOS.stream()
                .filter(Objects::nonNull)
                .map(doObj -> {
                    PageFlyVO vo = new PageFlyVO();
                    vo.setId(doObj.getId());
                    vo.setSourceText(doObj.getSourceText());
                    vo.setTargetText(doObj.getTargetText());
                    return vo;
                })
                .toList();
        return new BaseResponse<>().CreateSuccessResponse(voList);
    }
}
