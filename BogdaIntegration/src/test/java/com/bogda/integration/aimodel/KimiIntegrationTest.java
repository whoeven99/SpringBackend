
package com.bogda.integration.aimodel;

import com.bogda.integration.feishu.FeiShuRobotIntegration;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KimiIntegrationTest {

    @InjectMocks
    private KimiIntegration kimiIntegration;

    @Mock
    private HttpClient httpClient;

    @Mock
    private FeiShuRobotIntegration feiShuRobotIntegration;

    @Mock
    private HttpResponse<String> httpResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kimiIntegration, "kimiKeyVault", "test-key");
        ReflectionTestUtils.setField(kimiIntegration, "kimiIdVault", "test-id");
        kimiIntegration.init();
        ReflectionTestUtils.setField(kimiIntegration, "httpClient", httpClient);
    }

    @Test
    void chatWithKimi_success() throws IOException, InterruptedException {
        // Given
        String model = "kimi-k2.5";
        String prompt = "Hello";
        String target = "test";
        double magnification = 1.5;
        String responseBody = "{\"choices\":[{\"message\":{\"content\":\"Hi there!\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseBody);
        when(httpClient.send(any(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(httpResponse);

        // When
        Pair<String, Integer> result = kimiIntegration.chatWithKimi(model, prompt, target, magnification);

        // Then
        assertNotNull(result);
        assertEquals("Hi there!", result.getFirst());
        assertEquals(23, result.getSecond()); // (15 * 1.5) rounded up
    }

    @Test
    void chatWithKimi_httpError() throws IOException, InterruptedException {
        // Given
        String model = "kimi-k2.5";
        String prompt = "Hello";
        String target = "test";
        double magnification = 1.5;

        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Internal Server Error");
        when(httpClient.send(any(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(httpResponse);

        // When
        Pair<String, Integer> result = kimiIntegration.chatWithKimi(model, prompt, target, magnification);

        // Then
        assertNull(result);
        verify(feiShuRobotIntegration).sendMessage(contains("FatalException KimiIntegration call error sessionId:"));
    }
}
