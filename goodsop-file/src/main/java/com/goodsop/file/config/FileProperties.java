package com.goodsop.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件模块配置属性
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
        private String path;
        
        /**
         * 访问基础URL
         */
        private String baseUrl;
        
        /**
         * 是否启用解密
         */
        private Boolean enableDecrypt = true;
        
        /**
         * 是否启用解压缩
         */
        private Boolean enableDecompress = true;
        
        /**
         * AES加密密钥
         */
        private String aesKey;
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