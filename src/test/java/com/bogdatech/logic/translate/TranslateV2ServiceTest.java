package com.bogdatech.logic.translate;

import com.bogdatech.context.TranslateContext;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.logic.token.UserTokenService;
import com.bogdatech.logic.translate.stragety.ITranslateStrategyService;
import com.bogdatech.logic.translate.stragety.TranslateStrategyFactory;
import kotlin.Pair;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class TranslateV2ServiceTest {
    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private TranslateStrategyFactory translateStrategyFactory;

    @MockBean
    private UserTokenService userTokenService;
    @MockBean
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;

//    @Test
//    public void testBatchTranslate() {
//        Mockito.when(userTokenService.addUsedToken(Mockito.anyString(), Mockito.anyInt()))
//                .thenReturn(100);
//        Mockito.when(aLiYunTranslateIntegration.userTranslate(Mockito.anyString(), Mockito.anyString()))
//                .thenReturn(new Pair<>("{\"0\":\"Content\",\"1\":\"Title\",\"2\":\"Description\"}", 100));
//
//        // 调用方法
//        Map<Integer, String> contentMap = new HashMap<>();
//        contentMap.put(0, "内容");
//        contentMap.put(1, "标题");
//        contentMap.put(2, "描述");
//        TranslateContext context = TranslateContext.startBatchTranslate(contentMap, "en");
//
//        ITranslateStrategyService service = translateStrategyFactory.getServiceByContext(context);
//        service.translate(context);
//
//        Assert.assertEquals("Content", context.getTranslatedTextMap().get(0));
//        Assert.assertEquals("Title", context.getTranslatedTextMap().get(1));
//        Assert.assertEquals("Description", context.getTranslatedTextMap().get(2));
//    }

    @Test
    public void testHtmlTranslate() {
        Mockito.when(userTokenService.addUsedToken(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(100);
        Mockito.when(aLiYunTranslateIntegration.userTranslate(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new Pair<>("{\"0\":\"\uD83D\uDE04 contenido\",\"1\":\"título\",\"2\":\"ciwi\"}", 100));

        // 调用方法
        String content = "\uD83D\uDE04内容 <strong>ciwi </strong> 标题";
        TranslateContext context = translateV2Service.singleTranslate(
                "shopname", content, "es",
                "type", "key", new HashMap<>());
        Assert.assertEquals("\uD83D\uDE04 contenido <strong>ciwi </strong> título", context.getTranslatedContent());
    }

    @Test
    public void testSingleTranslate() {
        Mockito.when(userTokenService.addUsedToken(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(100);
        Mockito.when(aLiYunTranslateIntegration.userTranslate(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new Pair<>("Contenido traducido de prueba", 100));

        String content = "测试翻译内容";
        TranslateContext context = translateV2Service.singleTranslate(
                "shopname", content, "es",
                "type", "key", new HashMap<>());

        Assert.assertEquals("Contenido traducido de prueba", context.getTranslatedContent());
    }
}
