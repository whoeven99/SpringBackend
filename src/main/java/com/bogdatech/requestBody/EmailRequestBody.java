package com.bogdatech.requestBody;

import com.bogdatech.model.controller.request.SendEmailRequest;

public class EmailRequestBody {
    public String sendEmail(SendEmailRequest sendEmailRequest){
        return "{\n" +
                "    \"key\": \"" + sendEmailRequest.getEmailKey() + "\",\n" +
                "    \"template_name\": \"" + sendEmailRequest.getTemplateName() + "\",\n" +
                "    \"template_content\": " + sendEmailRequest.getTemplateContent() + ",\n" +
                "    \"message\": {\n" +
                "    \"global_merge_vars\": " + sendEmailRequest.getTemplateContent() + ",\n" +
                "        \"subject\": \"" + sendEmailRequest.getSubject() + "\",\n" +
                "        \"from_email\": \"" + sendEmailRequest.getFromEmail() + "\",\n" +
                "        \"to\": [\n" +
                "            {\n" +
                "                \"email\": \"" + sendEmailRequest.getToEmail() + "\",\n" +
                "                \"type\": \"to\"\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "}";
    }
}
