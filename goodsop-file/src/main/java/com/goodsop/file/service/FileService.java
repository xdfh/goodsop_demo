package com.goodsop.file.service;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.goodsop.file.entity.FileInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件服务接口
 */
public interface FileService {
    
    /**
     * 上传文件
     * 
     * @param file       文件
     * @param deviceId   设备ID
     * @param isEncrypted 是否加密
     * @param isCompressed 是否压缩
     * @param originalExtension 原始文件扩展名（解压后使用）
     * @return 文件信息
     */
    FileInfo uploadFile(MultipartFile file, String deviceId, Integer isEncrypted, Integer isCompressed, String originalExtension);
    
    /**
     * 分块上传文件（断点续传）
     * 
     * @param file       文件块
     * @param fileName   文件名
     * @param deviceId   设备ID
     * @param chunk      当前块索引
     * @param chunks     总块数
     * @param isEncrypted 是否加密
     * @param isCompressed 是否压缩
     * @param originalExtension 原始文件扩展名（解压后使用）
     * @return 如果是最后一块，返回文件信息；否则返回null
     */
    FileInfo uploadFileChunk(MultipartFile file, String fileName, String deviceId, 
                            Integer chunk, Integer chunks, Integer isEncrypted, Integer isCompressed, String originalExtension);
    
    /**
     * 下载文件
     * 
     * @param id       文件ID
     * @param request  HTTP请求
     * @param response HTTP响应
     */
    void downloadFile(Long id, HttpServletRequest request, HttpServletResponse response);
    
    /**
     * 根据ID获取文件信息
     * 
     * @param id 文件ID
     * @return 文件信息
     */
    FileInfo getFileById(Long id);
    
    /**
     * 分页查询文件列表
     * 
     * @param page      分页参数
     * @param params    查询参数
     * @return 分页结果
     */
    IPage<FileInfo> listFiles(Page<FileInfo> page, Map<String, Object> params);
    
    /**
     * 删除文件
     * 
     * @param id 文件ID
     * @return 是否删除成功
     */
    boolean deleteFile(Long id);
} 