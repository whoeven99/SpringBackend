package com.bogdatech.entity.DO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRightsAndInterestsDO {
    private Integer userId;
    private Integer rightsAndInterestsId;
}
