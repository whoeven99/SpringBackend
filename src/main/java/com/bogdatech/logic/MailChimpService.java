package com.bogdatech.logic;

import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.SendEmailRequest;
import com.bogdatech.model.controller.request.TranslateSuccessRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.integration.EmailIntegration.sendEmail;
import static com.bogdatech.utils.JsonUtils.objectToJson;

@Component
public class MailChimpService {

    @Autowired
    private EmailIntegration emailIntegration;

    //发送首次安装的邮件
    public static String sendFirstInstallMail(SendEmailRequest sendEmailRequest) {
        sendEmailRequest.setSubject(FIRST_INSTALL_SUBJECT);
        sendEmailRequest.setTemplateName(FIRST_INSTALL);
        sendEmailRequest.setFromEmail(FROM_EMAIL);
        Map<String, String> userName = Map.of("name", "EMAIL",
                "content", sendEmailRequest.getUser()); //TODO: 暂时为邮件后面传用户名（从数据库中获取）
        List<Map<String, String>> userNameList = List.of(userName);

        String jsonString = objectToJson(userNameList);
        sendEmailRequest.setTemplateContent(jsonString);

        return sendEmail(sendEmailRequest);
    }

    //翻译成功后发送邮件
    public static String sendTranslateSuccessMail(SendEmailRequest sendEmailRequest, TranslateSuccessRequest translateSuccessRequest) {
        sendEmailRequest.setSubject(SUCCESSFUL_TRANSLATION_SUBJECT);
        sendEmailRequest.setTemplateName(SUCCESSFUL_TRANSLATION);
        sendEmailRequest.setFromEmail(FROM_EMAIL);
        //TODO： 同上，下面USER也先用email暂时使用一下
        //存放USER变量
        Map<String, String> userName = Map.of("name", "USER", "content", sendEmailRequest.getUser());
        //存放目标语言
        Map<String, String> target = Map.of("name", "TARGET", "content", translateSuccessRequest.getTarget());
        //存放消耗字符数
        Map<String, String> chars = Map.of("name", "CHARS", "content", translateSuccessRequest.getChars());
        //存放消耗时间
        Map<String, String> time = Map.of("name", "TIME", "content", translateSuccessRequest.getTime());
        //剩余字符数
        Map<String, String> remaining = Map.of("name", "ALLCHARS", "content", translateSuccessRequest.getAllChars());

        //将变量存入list
        List<Map<String, String>> variableList = List.of(userName, target, chars, time, remaining);
        String jsonString = objectToJson(variableList);
        sendEmailRequest.setTemplateContent(jsonString);

        return sendEmail(sendEmailRequest);
    }

    //翻译失败后发送邮件
    public static String sendTranslateFailMail(SendEmailRequest sendEmailRequest, String failureMessage) {
        sendEmailRequest.setSubject(TRANSLATION_FAILED_SUBJECT);
        sendEmailRequest.setTemplateName(TRANSLATION_FAILED);
        sendEmailRequest.setFromEmail(FROM_EMAIL);
        //存放USER变量
        Map<String, String> userName = Map.of("name", "USER", "content", sendEmailRequest.getUser());
        //存放FAILURE变量
        Map<String, String> failure = Map.of("name", "FAILURE", "content", failureMessage);
        //将变量存入list
        List<Map<String, String>> variableList = List.of(userName, failure);
        String jsonString = objectToJson(variableList);
        sendEmailRequest.setTemplateContent(jsonString);

        return sendEmail(sendEmailRequest);
    }

    //字符购买成功后发送邮件
    public static String sendCharacterPurchaseSuccessMail(SendEmailRequest sendEmailRequest, String purchaseAmount, String remainingChars) {
        sendEmailRequest.setSubject(CHARACTER_PURCHASE_SUCCESSFUL_SUBJECT);
        sendEmailRequest.setTemplateName(CHARACTER_PURCHASE_SUCCESSFUL);
        sendEmailRequest.setFromEmail(FROM_EMAIL);

        //存放USER变量
        Map<String, String> userName = Map.of("name", "USER", "content", sendEmailRequest.getUser());
        //存放购买金额AMOUNT
        Map<String, String> amount = Map.of("name", "AMOUNT", "content", purchaseAmount);
        //存放获得字符数CHARS
        Map<String, String> chars = Map.of("name", "CHARS", "content", remainingChars);
        //将变量存入list
        List<Map<String, String>> variableList = List.of(userName, amount, chars);
        String jsonString = objectToJson(variableList);
        sendEmailRequest.setTemplateContent(jsonString);

        return sendEmail(sendEmailRequest);
    }

    //字符超限后发送邮件
    public static String sendCharacterQuotaUsedUpMail(SendEmailRequest sendEmailRequest) {
        sendEmailRequest.setSubject(CHARACTER_QUOTA_USED_UP_SUBJECT);
        sendEmailRequest.setTemplateName(CHARACTER_QUOTA_USED_UP);
        sendEmailRequest.setFromEmail(FROM_EMAIL);
        //存放USER变量
        Map<String, String> userName = Map.of("name", "USER", "content", sendEmailRequest.getUser());
        //将变量存入list
        List<Map<String, String>> variableList = List.of(userName);
        String jsonString = objectToJson(variableList);
        sendEmailRequest.setTemplateContent(jsonString);

        return sendEmail(sendEmailRequest);
    }
}

