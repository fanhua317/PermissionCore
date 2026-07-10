package com.permacore.iam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * PermaCore权限管理系统启动类
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PermaCoreApplication {

    private static final Logger log = LoggerFactory.getLogger(PermaCoreApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PermaCoreApplication.class, args);
        log.info("========================================");
        log.info("PermaCore IAM 启动成功！");
        log.info("========================================");
    }
}
