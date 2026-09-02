package com.jjx.framework.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Web配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 跨域配置
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /**
     * 配置Jackson
     * <p>DEV-306(2026-08-30)：必须移除默认 Jackson 后追加，不能 add(0) 插到最前——
     * 否则 springdoc 的 api-docs 返回 byte[] 时会被 Jackson Base64 化
     * （官方 issue springdoc/springdoc-openapi#2289），ByteArray/String 转换器必须保持优先。
     * <p>2026-09-02 修复：classpath 含 jackson-dataformat-yaml（springdoc 间接引入），
     * Spring 自动注册 YAML HttpMessageConverter → 当 Accept 为通配符（如 el-upload 原生 XHR
     * 默认请求头）时内容协商返回 YAML，前端解析失败（1284 附件不进流水根因）。
     * 统一清除非 JSON 转换器，所有接口只出 JSON。
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 清除自动注册的非 JSON 转换器（YAML 等），避免内容协商出非 JSON 响应
        converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter
                || c instanceof org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();

        // 注册JavaTimeModule以支持Java 8时间API
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

        converter.setObjectMapper(objectMapper);
        converters.add(converter);
    }

    /**
     * 静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Swagger UI
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        // 上传文件访问映射
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");
    }
}
