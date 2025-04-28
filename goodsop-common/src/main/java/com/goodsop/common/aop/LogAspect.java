package com.goodsop.common.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * 日志切面
 * 用于记录请求日志、方法执行时间等信息
 */
@Aspect
@Component
@Slf4j
public class LogAspect {
    
    private final ObjectMapper objectMapper;
    private final WebLogProperties logProperties;
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 构造方法，注入配置属性
     */
    public LogAspect(WebLogProperties logProperties, ObjectMapper objectMapper) {
        this.logProperties = logProperties;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 定义切点 - 拦截所有控制器
     */
    @Pointcut("execution(* com.goodsop..*.controller..*.*(..))")
    public void controllerPointcut() {}
    
    /**
     * 定义切点 - 拦截所有服务
     */
    @Pointcut("execution(* com.goodsop..*.service..*.*(..))")
    public void servicePointcut() {}
    
    /**
     * 环绕通知 - 记录请求日志、执行时间等
     */
    @Around("controllerPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 如果日志已禁用，直接执行目标方法
        if (!logProperties.isEnabled()) {
            return joinPoint.proceed();
        }
        
        String requestId = UUID.randomUUID().toString().replace("-", "")
                .substring(0, logProperties.getRequestIdLength());
        
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // 请求开始时间
            long startTime = System.currentTimeMillis();
            
            // 请求信息
            StringBuilder requestLog = new StringBuilder();
            requestLog.append(LINE_SEPARATOR)
                    .append("┌─────────────────────────────────────────────────────┐").append(LINE_SEPARATOR)
                    .append("│ 请求开始 - [").append(requestId).append("] - ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER)).append(" │").append(LINE_SEPARATOR)
                    .append("├─────────────────────────────────────────────────────┤").append(LINE_SEPARATOR)
                    .append("│ 请求URL : ").append(request.getRequestURL()).append(LINE_SEPARATOR)
                    .append("│ 请求方式 : ").append(request.getMethod()).append(LINE_SEPARATOR)
                    .append("│ 请求IP  : ").append(getIpAddress(request)).append(LINE_SEPARATOR)
                    .append("│ 类方法   : ").append(joinPoint.getSignature().getDeclaringTypeName()).append(".")
                    .append(joinPoint.getSignature().getName()).append(LINE_SEPARATOR);
                    
            // 根据配置决定是否记录请求参数
            if (logProperties.isIncludeRequestParams()) {
                requestLog.append("│ 请求参数 : ").append(Arrays.toString(joinPoint.getArgs())).append(LINE_SEPARATOR);
            }
            
            requestLog.append("└─────────────────────────────────────────────────────┘");
            
            log.info(requestLog.toString());
            
            try {
                // 执行目标方法
                Object result = joinPoint.proceed();
                
                // 计算执行时间
                long executionTime = System.currentTimeMillis() - startTime;
                
                // 判断执行时间是否超出阈值
                if (executionTime > logProperties.getControllerSlowThreshold()) {
                    log.warn("控制器方法 [{}] 执行时间过长: {}ms", 
                        joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName(), 
                        executionTime);
                }
                
                // 响应信息
                StringBuilder responseLog = new StringBuilder();
                responseLog.append(LINE_SEPARATOR)
                        .append("┌─────────────────────────────────────────────────────┐").append(LINE_SEPARATOR)
                        .append("│ 请求结束 - [").append(requestId).append("] - ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER)).append(" │").append(LINE_SEPARATOR)
                        .append("├─────────────────────────────────────────────────────┤").append(LINE_SEPARATOR)
                        .append("│ 执行时间 : ").append(executionTime).append("ms").append(LINE_SEPARATOR);
                
                // 根据配置决定是否记录响应内容
                if (logProperties.isIncludeResponseBody() && result != null) {
                    responseLog.append("│ 返回结果 : ").append(objectMapper.writeValueAsString(result)).append(LINE_SEPARATOR);
                }
                
                responseLog.append("└─────────────────────────────────────────────────────┘");
                
                log.info(responseLog.toString());
                
                return result;
            } catch (Exception e) {
                // 异常信息
                StringBuilder errorLog = new StringBuilder();
                errorLog.append(LINE_SEPARATOR)
                        .append("┌─────────────────────────────────────────────────────┐").append(LINE_SEPARATOR)
                        .append("│ 请求异常 - [").append(requestId).append("] - ").append(LocalDateTime.now().format(DATE_TIME_FORMATTER)).append(" │").append(LINE_SEPARATOR)
                        .append("├─────────────────────────────────────────────────────┤").append(LINE_SEPARATOR)
                        .append("│ 异常信息 : ").append(e.getMessage()).append(LINE_SEPARATOR)
                        .append("└─────────────────────────────────────────────────────┘");
                
                log.error(errorLog.toString(), e);
                throw e;
            }
        }
        
        return joinPoint.proceed();
    }
    
    /**
     * 记录服务层方法执行时间
     */
    @Around("servicePointcut()")
    public Object logServiceExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        // 如果日志已禁用，直接执行目标方法
        if (!logProperties.isEnabled()) {
            return joinPoint.proceed();
        }
        
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - startTime;
        
        if (executionTime > logProperties.getServiceSlowThreshold()) {
            log.warn("服务方法 [{}] 执行时间过长: {}ms", 
                joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName(), 
                executionTime);
        }
        
        return result;
    }
    
    /**
     * 获取请求IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
} 