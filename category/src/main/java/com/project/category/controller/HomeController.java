package com.project.category.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.category.ultis.Constants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping(Constants.ENDPOINT.PREFIX_CATEGORY)
public class HomeController {

    @GetMapping("/test")
    public String getMethodName(@RequestParam String param) {
        return param;
    }

}
