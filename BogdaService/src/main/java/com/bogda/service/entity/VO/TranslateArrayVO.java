package com.bogda.service.entity.VO;

import com.bogda.service.entity.DO.TranslatesDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateArrayVO {
    private TranslatesDO[] translatesDOResult;
    private Boolean flag;
}
