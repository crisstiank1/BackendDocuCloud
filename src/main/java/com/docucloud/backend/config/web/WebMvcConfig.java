package com.docucloud.backend.config.web;

import com.docucloud.backend.audit.interceptor.HttpAuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final HttpAuditInterceptor httpAuditInterceptor;

    public WebMvcConfig(HttpAuditInterceptor httpAuditInterceptor) {
        this.httpAuditInterceptor = httpAuditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpAuditInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/error");
    }
}