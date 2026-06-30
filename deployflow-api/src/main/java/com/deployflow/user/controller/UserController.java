package com.deployflow.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("/")
    public String home() {
        return "DeployFlow API is running!";
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello DeployFlow!";
    }
}