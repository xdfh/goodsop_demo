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
} 