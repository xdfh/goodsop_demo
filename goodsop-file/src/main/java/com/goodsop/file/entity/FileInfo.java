package com.goodsop.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件信息实体类
 */
@Data
@TableName("t_file_info")
public class FileInfo {
    
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 文件名
     */
    @TableField("file_name")
    private String fileName;
    
    /**
     * 原始文件名
     */
    @TableField("original_name")
    private String originalName;
    
    /**
     * 文件存储路径
     */
    @TableField("file_path")
    private String filePath;
    
    /**
     * 文件大小(字节)
     */
    @TableField("file_size")
    private Long fileSize;
    
    /**
     * 文件类型
     */
    @TableField("file_type")
    private String fileType;
    
    /**
     * 文件MD5值
     */
    @TableField("md5")
    private String md5;
    
    /**
     * 上传时间
     */
    @TableField("upload_time")
    private LocalDateTime uploadTime;
    
    /**
     * 上传设备ID
     */
    @TableField("device_id")
    private String deviceId;
    
    /**
     * 是否加密(0-否，1-是)
     */
    @TableField("is_encrypted")
    private Integer isEncrypted;
    
    /**
     * 是否压缩(0-否，1-是)
     */
    @TableField("is_compressed")
    private Integer isCompressed;
    
    /**
     * 文件状态(0-临时，1-正常，2-已删除)
     */
    @TableField("status")
    private Integer status;
    
    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
    
    /**
     * 逻辑删除标志(0-未删除，1-已删除)
     */
    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
} 