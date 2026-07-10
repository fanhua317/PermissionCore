package com.permacore.codegen;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

/**
 * 独立代码生成器。只连接真实 MySQL 元数据，并默认输出到 target/codegen-preview，
 * 不直接覆盖业务源码。
 */
public final class CodeGeneratorMain {

    private static final List<String> TABLES = List.of(
            "sys_user", "sys_role", "sys_dept", "sys_permission",
            "sys_role_permission", "sys_user_role", "sys_role_inheritance",
            "sys_sod_constraint", "sys_oper_log", "sys_login_log");

    private CodeGeneratorMain() {
    }

    public static void main(String[] args) throws Exception {
        String dbUrl = requireEnv("CODEGEN_DB_URL");
        String dbUsername = requireEnv("CODEGEN_DB_USERNAME");
        String dbPassword = requireEnv("CODEGEN_DB_PASSWORD");
        Path projectRoot = findProjectRoot(Path.of(System.getProperty("user.dir")).toAbsolutePath());
        String configuredOutput = System.getenv().getOrDefault("CODEGEN_OUTPUT_DIR", "target/codegen-preview");
        Path outputRoot = Path.of(configuredOutput);
        if (!outputRoot.isAbsolute()) {
            outputRoot = projectRoot.resolve(outputRoot);
        }
        outputRoot = outputRoot.normalize();
        Path javaOutput = outputRoot.resolve("java");
        Path xmlOutput = outputRoot.resolve("resources/mapper");
        Files.createDirectories(javaOutput);
        Files.createDirectories(xmlOutput);

        try (Connection ignored = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
            System.out.println("Database metadata connection verified.");
        }

        FastAutoGenerator.create(dbUrl, dbUsername, dbPassword)
                .globalConfig(builder -> builder
                        .author("PermaCore Team")
                        .disableOpenDir()
                        .outputDir(javaOutput.toString()))
                .packageConfig(builder -> builder
                        .parent("com.permacore.iam")
                        .entity("domain.entity")
                        .pathInfo(Map.of(OutputFile.xml, xmlOutput.toString())))
                .strategyConfig(builder -> builder
                        .addInclude(TABLES)
                        .entityBuilder().enableLombok().formatFileName("%sEntity")
                        .mapperBuilder().formatMapperFileName("%sMapper")
                        .serviceBuilder().formatServiceFileName("%sService").formatServiceImplFileName("%sServiceImpl")
                        .controllerBuilder().formatFileName("%sController"))
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();

        System.out.println("Generated preview: " + outputRoot);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private static Path findProjectRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isRegularFile(current.resolve("src/main/resources/db/schema.sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate PermissionCore project root from " + start);
    }
}
