package com.org.codeerrorexplainerdemo.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技术栈自动识别：扫描堆栈中的类名前缀，映射到框架标签。
 * 无命中返回 ["通用/未知"]。
 */
public final class TechStackDetector {

    /** 最多返回的标签数 */
    private static final int MAX_TAGS = 5;

    /** 类名前缀 → 框架标签（插入顺序即优先级，Spring Boot 需先于 Spring） */
    private static final Map<String, String> RULES = new LinkedHashMap<>();

    static {
        RULES.put("org.springframework.boot", "Spring Boot");
        RULES.put("org.springframework", "Spring");
        RULES.put("org.apache.ibatis", "MyBatis");
        RULES.put("com.baomidou.mybatisplus", "MyBatis");
        RULES.put("org.hibernate", "JPA/Hibernate");
        RULES.put("jakarta.persistence", "JPA");
        RULES.put("com.fasterxml.jackson", "Jackson");
        RULES.put("tools.jackson", "Jackson");
        RULES.put("org.apache.catalina", "Tomcat");
        RULES.put("org.apache.tomcat", "Tomcat");
        RULES.put("io.netty", "Netty");
        RULES.put("com.mysql.cj", "MySQL/JDBC");
        RULES.put("java.sql", "JDBC");
        RULES.put("okhttp3", "OkHttp");
        RULES.put("org.apache.http", "Apache HttpClient");
    }

    private TechStackDetector() {
    }

    public static List<String> detect(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return List.of("通用/未知");
        }
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> rule : RULES.entrySet()) {
            if (!stackTrace.contains(rule.getKey())) {
                continue;
            }
            String label = rule.getValue();
            // Spring Boot 已命中时不再重复标记 Spring
            if ("Spring".equals(label) && found.contains("Spring Boot")) {
                continue;
            }
            if (!found.contains(label)) {
                found.add(label);
            }
        }
        if (found.isEmpty()) {
            return List.of("通用/未知");
        }
        return found.size() > MAX_TAGS ? new ArrayList<>(found.subList(0, MAX_TAGS)) : found;
    }
}
