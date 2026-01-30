package com.bogda.common.entity.VO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslationCharsVO {
    @NotBlank(message = "订阅ID不能为空")
    private String subGid;
    private String accessToken;
    @NotNull(message = "费用类型不能为空")
    @Min(value = 0, message = "费用类型必须为0或1")
    @Max(value = 1, message = "费用类型必须为0或1")
    private Integer feeType; // 0是月费； 1是年费
}
