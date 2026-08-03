package com.org.codeerrorexplainerdemo;

import com.org.codeerrorexplainerdemo.service.TechStackDetector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TechStackDetectorTest {

    @Test
    void detectsSpringFromSpringFrames() {
        String stack = "java.lang.NullPointerException\n"
                + "\tat org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor.postProcessProperties(...)\n"
                + "\tat com.app.UserService.login(UserService.java:47)";
        assertThat(TechStackDetector.detect(stack)).contains("Spring");
    }

    @Test
    void detectsSpringBootWithoutDuplicatingSpring() {
        String stack = "java.lang.IllegalStateException\n"
                + "\tat org.springframework.boot.web.embedded.tomcat.TomcatWebServer.start(TomcatWebServer.java:220)\n"
                + "\tat org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1190)";
        List<String> tags = TechStackDetector.detect(stack);
        assertThat(tags).contains("Spring Boot", "Tomcat");
        assertThat(tags).doesNotContain("Spring"); // Spring Boot 已覆盖，不重复标 Spring
    }

    @Test
    void detectsMybatisAndJackson() {
        String stack = "org.apache.ibatis.exceptions.PersistenceException\n"
                + "\tat com.baomidou.mybatisplus.core.MybatisMapperMethod...\n"
                + "\tat com.fasterxml.jackson.databind.ObjectMapper.readValue(...)";
        List<String> tags = TechStackDetector.detect(stack);
        assertThat(tags).contains("MyBatis", "Jackson");
    }

    @Test
    void unknownStackReturnsGenericTag() {
        String stack = "java.net.SocketTimeoutException: Read timed out\n"
                + "\tat java.net.SocketInputStream.socketRead0(Native Method)";
        assertThat(TechStackDetector.detect(stack)).containsExactly("通用/未知");
    }

    @Test
    void blankOrNullReturnsGenericTag() {
        assertThat(TechStackDetector.detect("")).containsExactly("通用/未知");
        assertThat(TechStackDetector.detect(null)).containsExactly("通用/未知");
    }
}
