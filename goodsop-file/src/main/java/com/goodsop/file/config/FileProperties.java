package com.goodsop.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件服务配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "goodsop.file")
public class FileProperties {
    
    /**
     * 存储配置
     */
    private Storage storage = new Storage();
    
    /**
     * 上传配置
     */
    private Upload upload = new Upload();
    
    /**
     * 清理配置
     */
    private Cleanup cleanup = new Cleanup();
    
    /**
     * 存储配置
     */
    @Data
    public static class Storage {
        /**
         * 存储路径
         */
        private String path = "D:/file/";
        
        /**
         * 基础URL
         */
        private String baseUrl;
        
        /**
         * 互联网访问主机
         */
        private String internetHost;
        
        /**
         * 互联网访问端口
         */
        private Integer internetPort;
        
        /**
         * 局域网访问主机
         */
        private String lanHost;
        
        /**
         * 局域网访问端口
         */
        private Integer lanPort;
        
        /**
         * 服务器主机名
         */
        private String serverHost = "localhost";
        
        /**
         * 服务器端口
         */
        private Integer serverPort = 8080;
        
        /**
         * 上下文路径
         */
        private String contextPath = "";
        
        /**
         * 默认加密类型 (如果未指定)
         */
        private String defaultEncryptionType = "AES";
        
        /**
         * 默认压缩类型 (如果未指定)
         */
        private String defaultCompressionType = "GZIP";
        
        /**
         * AES密钥 (用于加密/解密文件)
         */
        private String aesKey = "1234567890abcdef1234567890abcdef";
        
        /**
         * 是否启用解密 (将尝试解密带有.enc后缀的文件)
         */
        private Boolean enableDecrypt = true;
        
        /**
         * 是否启用解压缩 (将尝试解压缩带有.gz后缀的文件)
         */
        private Boolean enableDecompress = true;
    }
    
    /**
     * 上传配置
     */
    @Data
    public static class Upload {
        /**
         * 最大文件大小
         */
        private String maxSize;
    }
    
    /**
     * 清理配置
     */
    @Data
    public static class Cleanup {
        /**
         * 是否启用清理
         */
        private Boolean enabled = true;
        
        /**
         * 定时任务表达式
         */
        private String cron = "0 0 1 * * ?";  // 默认每天凌晨1点执行
        
        /**
         * 临时文件最大保留时间（小时）
         */
        private Integer tempFileMaxAge = 24;
    }
} 