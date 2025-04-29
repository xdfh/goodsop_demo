package com.goodsop.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文件信息实体类
 */
@Data
@TableName("t_file_info")
public class FileInfo {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 设备ID
     */
    @TableField("device_id")
    private String deviceId;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;
    
    /**
     * 文件名
     */
    @TableField("file_name")
    private String fileName;
    
    /**
     * 文件存储路径
     */
    @TableField("file_path")
    private String filePath;
    
    /**
     * 文件访问URL
     */
    @TableField("access_url")
    private String accessUrl;
    
    /**
     * 域名前缀
     */
    @TableField("domain_prefix")
    private String domainPrefix;
    
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
    @TableField("file_md5")
    private String fileMd5;
    
    /**
     * 录音日期
     */
    @TableField("record_date")
    private LocalDate recordDate;
    
    /**
     * 录音开始时间
     */
    @TableField("record_start_time")
    private LocalDateTime recordStartTime;
    
    /**
     * 录音时长(毫秒)
     */
    @TableField("record_duration")
    private Long recordDuration;
    
    /**
     * 上传时间
     */
    @TableField("upload_time")
    private LocalDateTime uploadTime;
    
    /**
     * 文件状态: 0-上传中，1-已完成，2-已失效
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
     * 是否删除: 0-未删除，1-已删除
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
} 