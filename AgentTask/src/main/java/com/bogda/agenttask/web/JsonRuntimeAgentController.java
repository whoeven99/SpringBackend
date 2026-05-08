package com.bogda.agenttask.web;

import com.bogda.common.agent.JsonRuntimeAgentRunner;
import com.bogda.common.controller.response.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent/json-runtime")
public class JsonRuntimeAgentController {

    private static final Logger LOG = LoggerFactory.getLogger(JsonRuntimeAgentController.class);

    @Autowired
    private JsonRuntimeAgentRunner jsonRuntimeAgentRunner;

    @PostMapping("/run")
    public BaseResponse<Object> run(@RequestParam String message) {
        try {
            return BaseResponse.SuccessResponse(jsonRuntimeAgentRunner.run(message));
        } catch (IllegalArgumentException e) {
            return BaseResponse.FailedResponse(e.getMessage());
        } catch (Exception e) {
            LOG.error("agent-controller /run failed, message={}", message, e);
            return BaseResponse.FailedResponse("Agent run failed: " + e.getMessage());
        }
    }
}
