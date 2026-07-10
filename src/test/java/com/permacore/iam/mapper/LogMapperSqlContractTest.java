package com.permacore.iam.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LogMapperSqlContractTest {

    @Test
    void recentQueriesUseTheSameStableOrderAsPagination() throws IOException {
        String loginMapper = Files.readString(
                Path.of("src/main/resources/mapper/SysLoginLogMapper.xml"),
                StandardCharsets.UTF_8
        );
        String operMapper = Files.readString(
                Path.of("src/main/resources/mapper/SysOperLogMapper.xml"),
                StandardCharsets.UTF_8
        );

        assertThat(loginMapper).contains("ORDER BY login_time DESC, id DESC");
        assertThat(operMapper).contains("ORDER BY oper_time DESC, id DESC");
    }
}
