package com.permacore.iam;

import com.permacore.iam.domain.entity.UserEntity;
import com.permacore.iam.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Slf4j
@SpringBootTest
public class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void testSelectUser() {
        // 验证MyBatis-Plus是否正常工作
        UserEntity user = userMapper.selectById(1L);
        log.info("查询结果: {}", user);

        // 验证密码加密
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "Admin@123456";
        String encodedPassword = encoder.encode(rawPassword);
        log.info("原始密码: {}", rawPassword);
        log.info("加密后密码: {}", encodedPassword);
        log.info("密码匹配: {}", encoder.matches(rawPassword, encodedPassword));
    }
}