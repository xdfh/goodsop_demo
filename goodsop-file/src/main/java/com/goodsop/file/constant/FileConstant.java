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
     * 默认分块大小：2MB
     */
    public static final int DEFAULT_CHUNK_SIZE = 2 * 1024 * 1024;
    
    /**
     * 加密算法：AES
     */
    public static final String ALGORITHM_AES = "AES";
    
    /**
     * 加密模式：AES/ECB/PKCS5Padding
     */
    public static final String TRANSFORMATION_AES = "AES/ECB/PKCS5Padding";
} 