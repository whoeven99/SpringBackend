package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SingleReturnVO {
    private String targetText;

    private Map<String, String> translateVariables;
}
