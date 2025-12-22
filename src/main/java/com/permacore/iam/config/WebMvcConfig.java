package com.permacore.iam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web MVC 配置
 * 配置静态资源映射等
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.avatar-path:uploads/avatars}")
    private String avatarUploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 头像文件访问映射 - 使用绝对路径
        File uploadDir = new File(avatarUploadPath);
        if (!uploadDir.isAbsolute()) {
            // 如果是相对路径，则基于当前工作目录
            uploadDir = new File(System.getProperty("user.dir"), avatarUploadPath);
        }
        String absolutePath = uploadDir.getAbsolutePath().replace("\\", "/");
        if (!absolutePath.endsWith("/")) {
            absolutePath += "/";
        }
        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations("file:" + absolutePath);
    }
}
