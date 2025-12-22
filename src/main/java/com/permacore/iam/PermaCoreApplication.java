package com.permacore.iam;

import org.mybatis.spring.annotation.MapperScan;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PermaCore权限管理系统启动类cd
 */
@SpringBootApplication(exclude = { RedissonAutoConfigurationV2.class })
public class PermaCoreApplication {

    private static final Logger log = LoggerFactory.getLogger(PermaCoreApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PermaCoreApplication.class, args);
        log.info("========================================");
        log.info("PermaCore IAM 启动成功！");
        log.info("访问地址：http://localhost:54321/doc.html");
        log.info("========================================");
    }
}