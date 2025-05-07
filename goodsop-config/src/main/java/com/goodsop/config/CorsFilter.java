package com.goodsop.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 全局跨域过滤器
 * 提供比WebMvcConfig更低层级的跨域支持，确保所有请求都经过跨域处理
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class
CorsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 初始化方法，无需特殊处理
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 允许的来源域名，生产环境应该指定具体域名
        response.setHeader("Access-Control-Allow-Origin", "*");
        // 允许的HTTP方法
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        // 允许的请求头
        response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");
        // 允许暴露的响应头
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        // 允许发送Cookie
        response.setHeader("Access-Control-Allow-Credentials", "true");
        // 预检请求的有效期，单位秒
        response.setHeader("Access-Control-Max-Age", "3600");

        // 对预检请求的处理
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            chain.doFilter(req, res);
        }
    }

    @Override
    public void destroy() {
        // 销毁方法，无需特殊处理
    }
} 