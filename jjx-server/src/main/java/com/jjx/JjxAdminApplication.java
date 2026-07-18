package com.jjx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类
 */
@SpringBootApplication
public class JjxAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(JjxAdminApplication.class, args);
        System.out.println("""
                =========================================
                JJX ERP系统启动成功！
                薄膜开关ERP系统后台管理
                =========================================
                """);
    }
}
