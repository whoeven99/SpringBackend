package com.bogdatech.controller;

import com.bogdatech.logic.TranslateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TranslateController {

    @Autowired
    private TranslateService translateService;

    // TODO should be Post
    @GetMapping("/translate")
    public String translate(@RequestParam(value = "text") String text) {
        return translateService.translate(text);
    }
}
