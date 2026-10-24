package com.transcaribe.transcaribe.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminSistemaInterceptor adminSistemaInterceptor;

    public WebMvcConfig(AdminSistemaInterceptor adminSistemaInterceptor) {
        this.adminSistemaInterceptor = adminSistemaInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminSistemaInterceptor).addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/seleccionar-entidad", "/admin/contexto", "/admin/contexto/confirmar");
    }
}
