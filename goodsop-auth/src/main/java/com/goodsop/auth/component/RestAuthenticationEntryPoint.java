package com.goodsop.auth.component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        // =====================================================================================
        // = 在这里实现您的认证失败处理逻辑
        // =====================================================================================
        //
        // 当用户尝试访问受保护的资源但未提供身份验证凭证时（例如，未登录或token无效），此方法将被调用
        //
        // 示例步骤:
        // 1. 设置 response 的 Content-Type 为 "application/json;charset=UTF-8"
        // response.setContentType("application/json;charset=UTF-8");
        //
        // 2. 设置 HTTP 状态码，通常是 401 Unauthorized
        // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        //
        // 3. 构建一个统一的错误响应体（例如，使用您在 goodsop-common 中定义的通用结果类）
        // CommonResult<String> result = CommonResult.failed("认证失败：" + authException.getMessage());
        //
        // 4. 使用 ObjectMapper (e.g., from Jackson) 将结果对象序列化为 JSON 字符串
        // String json = new ObjectMapper().writeValueAsString(result);
        //
        // 5. 将 JSON 字符串写入 response 的输出流
        // response.getWriter().println(json);
        // response.getWriter().flush();
        
        // 简单的默认实现
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
} 