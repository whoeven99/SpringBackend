package com.bogda.api.entity.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiscountBasicDTO {
    private String shopName;
    private String discountGid;
    private String status;
    private Object metafields;
    @JsonProperty("basic_information")
    private Object basicInformation;
}
