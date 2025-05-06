package com.goodsop.file.constant;

/**
 * 文件模块常量
 */
public class FileConstant {
    
    /**
     * 文件状态：上传中
     */
    public static final int FILE_STATUS_UPLOADING = 0;
    
    /**
     * 文件状态：已完成
     */
    public static final int FILE_STATUS_NORMAL = 1;
    
    /**
     * 文件状态：已失效
     */
    public static final int FILE_STATUS_EXPIRED = 2;
    
    /**
     * 是否删除：否
     */
    public static final int DELETED_FALSE = 0;
    
    /**
     * 是否删除：是
     */
    public static final int DELETED_TRUE = 1;
    
    /**
     * 是否加密/压缩：否
     */
    public static final int FLAG_FALSE = 0;
    
    /**
     * 是否加密/压缩：是
     */
    public static final int FLAG_TRUE = 1;
    
    /**
     * 日期格式：年月日
     */
    public static final String DATE_FORMAT_YYYYMMDD = "yyyyMMdd";
    
    /**
     * 文件分隔符
     */
    public static final String FILE_SEPARATOR = "/";
    
    /**
     * 文件名分隔符
     */
    public static final String FILE_NAME_SEPARATOR = "_";
    
    /**
     * 默认分块大小：1MB
     */
    public static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
    
    /**
     * 加密算法：AES
     */
    public static final String ALGORITHM_AES = "AES";
    
    /**
     * 加密模式：AES/CTR/NoPadding
     */
    public static final String TRANSFORMATION_AES = "AES/CTR/NoPadding";
    
    /**
     * 初始向量大小（字节）
     */
    public static final int IV_SIZE = 16;
    
    /**
     * 默认文件上传路径
     */
    public static final String DEFAULT_UPLOAD_PATH = "D:/file/";
    
    /**
     * 加密标志：是
     */
    public static final int ENCRYPTED_YES = 1;
    
    /**
     * 加密标志：否
     */
    public static final int ENCRYPTED_NO = 0;
    
    /**
     * 压缩标志：是
     */
    public static final int COMPRESSED_YES = 1;
    
    /**
     * 压缩标志：否
     */
    public static final int COMPRESSED_NO = 0;
    
    /**
     * AES加密类型
     */
    public static final String ENCRYPTION_TYPE_AES = "AES-256";
    
    /**
     * GZIP压缩类型
     */
    public static final String COMPRESSION_TYPE_GZIP = "GZIP";
    
    /**
     * ZIP压缩类型
     */
    public static final String COMPRESSION_TYPE_ZIP = "ZIP";
    
    /**
     * 加密文件后缀
     */
    public static final String ENCRYPTED_FILE_SUFFIX = ".enc";
    
    /**
     * GZIP压缩文件后缀
     */
    public static final String GZIP_FILE_SUFFIX = ".gz";
    
    /**
     * ZIP压缩文件后缀
     */
    public static final String ZIP_FILE_SUFFIX = ".zip";
    
    /**
     * 处理过的文件后缀
     */
    public static final String PROCESSED_FILE_SUFFIX = ".processed";
    
    /**
     * 临时文件目录
     */
    public static final String TEMP_DIR = "temp";
    
    /**
     * 默认缓冲区大小
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192;
    
    /**
     * 最大文件大小（默认100MB）
     */
    public static final long MAX_FILE_SIZE = 100 * 1024 * 1024;
    
    /**
     * AES加密密钥（长度需要是16或32字节）
     * 注意：生产环境中应该从配置或安全存储中获取，而不是硬编码
     */
    public static final String AES_KEY = "1234567890abcdef1234567890abcdef";
} 