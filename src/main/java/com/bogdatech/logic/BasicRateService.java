package com.bogdatech.logic;

import com.bogdatech.integration.BaseHttpIntegration;
import com.bogdatech.model.controller.request.BasicRateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

import static com.bogdatech.common.enums.BasicEnum.GET_RATE_ERROR;
import static com.bogdatech.model.controller.request.BasicRateRequest.APP_KEY;
import static com.bogdatech.model.controller.request.BasicRateRequest.SIGN;

@Component
public class BasicRateService {
    private static final String BASIC_RATE_BASE_URL = "http://api.k780.com/?app=finance.rate";
    @Autowired
    private BaseHttpIntegration baseHttpIntegration;
//    @Autowired
//    private JdbcTestRepository jdbcTestRepository;
//    @Transactional(rollbackFor = Exception.class)
    public BaseResponse getBasicRate(BasicRateRequest basicRate) throws Exception {
        String URL = BASIC_RATE_BASE_URL + "&scur=" + basicRate.getScur() + "&tcur=" + basicRate.getTcur() + "&appkey=" + APP_KEY + "&sign=" + SIGN;
        String getString = baseHttpIntegration.sendHttpGet(URL);
        ObjectMapper mapper = new ObjectMapper();
        LinkedHashMap<String, Object> dates = (LinkedHashMap<String, Object>) mapper.readValue(getString, LinkedHashMap.class);
        //获取dates的success数据信息
        String success = (String) dates.get("success");
        if (!success.equals("1")) {
            return new BaseResponse().CreateErrorResponse(GET_RATE_ERROR.getErrorMessage(),null);
        } else {

            //获取dates的result数据信息
            LinkedHashMap<String, Object> result = (LinkedHashMap<String, Object>) dates.get("result");
            return new BaseResponse().CreateSuccessResponse(result);
        }
    }
}
