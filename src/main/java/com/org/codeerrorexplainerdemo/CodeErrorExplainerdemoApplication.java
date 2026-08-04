package com.org.codeerrorexplainerdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CodeErrorExplainerdemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeErrorExplainerdemoApplication.class, args);
    }

}
