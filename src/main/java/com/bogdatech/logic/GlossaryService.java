package com.bogdatech.logic;

import com.bogdatech.Service.IGlossaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GlossaryService {

    @Autowired
    private IGlossaryService glossaryService;

}
