package com.bogdatech.integration;

import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import com.tencentcloudapi.ses.v20201002.models.Template;
import org.springframework.stereotype.Component;
import java.util.Map;
import static com.bogdatech.constants.MailChimpConstants.CC_EMAIL;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TimeOutUtils.*;
import static com.bogdatech.utils.TimeOutUtils.DEFAULT_MAX_RETRIES;

@Component
public class EmailIntegration {

    /**
     * 腾讯邮件发送
     */
    public Boolean sendEmailByTencent(TencentSendEmailRequest tencentSendEmailRequest) {
        Map<String, String> templateData = tencentSendEmailRequest.getTemplateData();

        String jsonString = null;
        try {
            Credential cred = new Credential(System.getenv("Tencent_Cloud_KEY_ID"), System.getenv("Tencent_Cloud_KEY"));
            String templateDataJson = OBJECT_MAPPER.writeValueAsString(templateData);
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
//            httpProfile.setEndpoint("ses.ap-guangzhou-open.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            SesClient client = new SesClient(cred, "ap-hongkong", clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            SendEmailRequest req = new SendEmailRequest();
            req.setFromEmailAddress(tencentSendEmailRequest.getFromEmail());

            String[] destination1 = {tencentSendEmailRequest.getToEmail()};
            req.setDestination(destination1);

            req.setSubject(tencentSendEmailRequest.getSubject());
            Template template1 = new Template();
            template1.setTemplateID(tencentSendEmailRequest.getTemplateId());
            template1.setTemplateData(templateDataJson);
            req.setTemplate(template1);
            String [] cc1 = {CC_EMAIL};
            req.setCc(cc1);
            // 返回的resp是一个SendEmailResponse的实例，与请求对象对应
            SendEmailResponse resp = callWithTimeoutAndRetry(() -> {
                        try {
                            return client.SendEmail(req);
                        } catch (Exception e) {
                            appInsights.trackTrace("每日须看 sendEmailByTencent 腾讯邮件报错信息 errors ： " + e.getMessage() + " templateId : " + tencentSendEmailRequest.getTemplateId() + " data" + templateData.toString());
                            appInsights.trackException(e);
                            return null;
                        }
                    },
                    DEFAULT_TIMEOUT, DEFAULT_UNIT,    // 超时时间
                    DEFAULT_MAX_RETRIES                // 最多重试3次
            );
            if (resp == null) {
                appInsights.trackTrace("sendEmailByTencent 腾讯邮件报错信息 errors ： templateId : " + tencentSendEmailRequest.getTemplateId() + " data" + templateData.toString());
                return false;
            }

            // 输出json格式的字符串回包
            jsonString = AbstractModel.toJsonString(resp);
            appInsights.trackTrace("sendEmailByTencent 腾讯邮件信息 jsonString : " + jsonString + " data: " + tencentSendEmailRequest.getTemplateData());
        } catch (Exception e1){
            appInsights.trackException(e1);
            appInsights.trackTrace("sendEmailByTencent 腾讯邮件报错信息 errors ： " + e1.getMessage() + " templateId : " + tencentSendEmailRequest.getTemplateId() + " data" + templateData.toString());
        }
        //判断服务的返回值是否含有RequestId
        if (jsonString == null) {
            return false;
        }
        return jsonString.contains("RequestId");
        }
    }
