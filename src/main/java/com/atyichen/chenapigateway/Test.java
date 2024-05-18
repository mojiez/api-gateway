package com.atyichen.chenapigateway;

import com.yichen.project.service.TestNacosDubboService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class Test implements CommandLineRunner {
    @DubboReference
    private TestNacosDubboService testNacosDubboService;
    @Override
    public void run(String... args) throws Exception {
        System.out.println(testNacosDubboService.sayCao() + "world");
    }
}
