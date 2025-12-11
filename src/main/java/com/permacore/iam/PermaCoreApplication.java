package com.permacore.iam;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PermaCore权限管理系统启动类
 */
@Slf4j
@SpringBootApplication
@MapperScan("com.permacore.iam.mapper")
public class PermaCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(PermaCoreApplication.class, args);
        log.info("========================================");
        log.info("PermaCore IAM 启动成功！");
        log.info("访问地址：http://localhost:8080/doc.html");
        log.info("========================================");
    }
}