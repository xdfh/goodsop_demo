package com.goodsop.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.goodsop.auth.component.JwtAuthenticationTokenFilter;
import com.goodsop.auth.component.RestAuthenticationEntryPoint;
import com.goodsop.auth.component.RestfulAccessDeniedHandler;
import com.goodsop.auth.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // Knife4j & Swagger UI
                                "/doc.html",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/webjars/**",
                                "/swagger-resources/**",
                                "/favicon.ico",
                                "/files/**",

                                // 业务放行接口
                                "/user/login",
                                "/user/register",
                                "/iot/v1/auth",
                                "/"
                        ).permitAll()
                        .anyRequest().authenticated()
                );
        // 在这里添加您的自定义JWT过滤器
        http.addFilterBefore(jwtAuthenticationTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        
        // 在这里可以添加自定义的认证入口和访问拒绝处理器
        http.exceptionHandling()
                .authenticationEntryPoint(restAuthenticationEntryPoint())
                .accessDeniedHandler(restfulAccessDeniedHandler());

        return http.build();
    }
    
    // =====================================================================================
    // = 下方是为您预留的自定义实现区域
    // = 您需要取消下方代码的注释，并实现具体的逻辑
    // =====================================================================================

    @Bean
    public JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter() {
        return new JwtAuthenticationTokenFilter(jwtTokenUtil);
    }

    @Bean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
        // 自定义处理认证失败的逻辑，例如返回统一的JSON格式错误信息
        return new RestAuthenticationEntryPoint();
    }

    @Bean
    public RestfulAccessDeniedHandler restfulAccessDeniedHandler() {
        // 自定义处理授权失败的逻辑，例如返回统一的JSON格式错误信息
        return new RestfulAccessDeniedHandler();
    }
    
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        // 定义密码加密器，推荐使用BCryptPasswordEncoder
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
} 