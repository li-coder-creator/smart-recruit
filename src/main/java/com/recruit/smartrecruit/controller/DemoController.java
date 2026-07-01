package com.recruit.smartrecruit.controller;
import com.recruit.smartrecruit.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class DemoController {

//    @GetMapping("/other")
    @PostMapping("/other")
    public Result<String> otherWeb() {
            return Result.success("进入内部页面");

    }
}
