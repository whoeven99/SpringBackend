package com.bogda.common.entity.VO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddCharsVO {
    private String shopName;
    @NotNull(message = "字符数不能为空")
    @Min(value = 0, message = "字符数不能为负数")
    private Integer chars;
    @NotBlank(message = "计划ID不能为空")
    private String gid; // 计划id
    private String accessToken;
}
