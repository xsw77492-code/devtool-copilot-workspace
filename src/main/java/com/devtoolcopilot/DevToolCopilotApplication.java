package com.devtoolcopilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.devtoolcopilot.**.mapper")
@EnableScheduling
public class DevToolCopilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevToolCopilotApplication.class, args);
    }
}
