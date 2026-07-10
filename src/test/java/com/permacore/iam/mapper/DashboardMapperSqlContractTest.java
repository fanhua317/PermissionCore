package com.permacore.iam.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardMapperSqlContractTest {

    @Test
    void aggregateCountsOnlyActiveLogicalDeleteEntities() throws IOException {
        String mapperXml = Files.readString(
                Path.of("src/main/resources/mapper/DashboardMapper.xml"),
                StandardCharsets.UTF_8
        );

        assertThat(mapperXml)
                .contains("SELECT COUNT(*) FROM sys_user WHERE del_flag = 0")
                .contains("SELECT COUNT(*) FROM sys_role WHERE del_flag = 0")
                .contains("SELECT COUNT(*) FROM sys_dept WHERE del_flag = 0");
    }
}
