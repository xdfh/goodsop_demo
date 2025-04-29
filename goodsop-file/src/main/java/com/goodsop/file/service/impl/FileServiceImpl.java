package com.goodsop.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.goodsop.file.config.FileProperties;
import com.goodsop.file.constant.FileConstant;
import com.goodsop.file.entity.FileInfo;
import com.goodsop.file.mapper.FileInfoMapper;
import com.goodsop.file.service.FileService;
import com.goodsop.file.util.FileCompressUtil;
import com.goodsop.file.util.FileEncryptUtil;
import com.goodsop.file.util.FileTransferUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * 文件服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements FileService {
    
    private final FileProperties fileProperties;
    private final FileTransferUtil fileTransferUtil;
    private final FileCompressUtil fileCompressUtil;
    private final FileEncryptUtil fileEncryptUtil;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo uploadFile(MultipartFile file, String deviceId, Integer isEncrypted, Integer isCompressed) {
        try {
            // 获取当前日期作为子目录
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern(FileConstant.DATE_FORMAT_YYYYMMDD));
            String storageDir = fileProperties.getStorage().getPath() + dateDir;
            
            // 存储原始文件
            File originalFile = fileTransferUtil.storeFile(file, storageDir);
            String originalFilename = file.getOriginalFilename();
            String fileType = getFileType(originalFilename);
            
            // 处理文件（解压缩、解密）
            File processedFile = processFile(originalFile, isEncrypted, isCompressed);
            
            // 计算MD5
            String md5 = fileEncryptUtil.calculateMD5(processedFile);
            
            // 创建文件信息记录
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(processedFile.getName());
            fileInfo.setOriginalName(originalFilename);
            fileInfo.setFilePath(processedFile.getAbsolutePath());
            fileInfo.setFileSize(processedFile.length());
            fileInfo.setFileType(fileType);
            fileInfo.setMd5(md5);
            fileInfo.setUploadTime(LocalDateTime.now());
            fileInfo.setDeviceId(deviceId);
            fileInfo.setIsEncrypted(isEncrypted);
            fileInfo.setIsCompressed(isCompressed);
            fileInfo.setStatus(FileConstant.FILE_STATUS_NORMAL);
            fileInfo.setCreateTime(LocalDateTime.now());
            fileInfo.setUpdateTime(LocalDateTime.now());
            fileInfo.setIsDeleted(0);
            
            // 保存到数据库
            this.save(fileInfo);
            log.info("文件上传成功: {}", fileInfo);
            
            return fileInfo;
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo uploadFileChunk(MultipartFile file, String fileName, String deviceId, 
                                  Integer chunk, Integer chunks, Integer isEncrypted, Integer isCompressed) {
        try {
            // 获取当前日期作为子目录
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern(FileConstant.DATE_FORMAT_YYYYMMDD));
            String storageDir = fileProperties.getStorage().getPath() + dateDir;
            
            // 存储分块文件
            File targetFile = fileTransferUtil.storeFileChunk(file, storageDir, fileName, chunk, chunks);
            
            // 如果不是最后一块，返回null
            if (targetFile == null) {
                return null;
            }
            
            // 处理文件
            String originalFilename = fileName;
            String fileType = getFileType(originalFilename);
            
            // 如果是最后一块，进行文件处理
            File processedFile = processFile(targetFile, isEncrypted, isCompressed);
            
            // 计算MD5
            String md5 = fileEncryptUtil.calculateMD5(processedFile);
            
            // 创建文件信息记录
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(processedFile.getName());
            fileInfo.setOriginalName(originalFilename);
            fileInfo.setFilePath(processedFile.getAbsolutePath());
            fileInfo.setFileSize(processedFile.length());
            fileInfo.setFileType(fileType);
            fileInfo.setMd5(md5);
            fileInfo.setUploadTime(LocalDateTime.now());
            fileInfo.setDeviceId(deviceId);
            fileInfo.setIsEncrypted(isEncrypted);
            fileInfo.setIsCompressed(isCompressed);
            fileInfo.setStatus(FileConstant.FILE_STATUS_NORMAL);
            fileInfo.setCreateTime(LocalDateTime.now());
            fileInfo.setUpdateTime(LocalDateTime.now());
            fileInfo.setIsDeleted(0);
            
            // 保存到数据库
            this.save(fileInfo);
            log.info("分块文件上传完成: {}", fileInfo);
            
            return fileInfo;
        } catch (Exception e) {
            log.error("分块文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("分块文件上传失败", e);
        }
    }
    
    @Override
    public void downloadFile(Long id, HttpServletRequest request, HttpServletResponse response) {
        try {
            // 获取文件信息
            FileInfo fileInfo = this.getById(id);
            if (fileInfo == null) {
                throw new RuntimeException("文件不存在");
            }
            
            // 检查文件是否存在
            File file = new File(fileInfo.getFilePath());
            if (!file.exists()) {
                throw new RuntimeException("文件不存在");
            }
            
            // 下载文件（支持断点续传）
            fileTransferUtil.downloadWithRange(request, response, file, fileInfo.getOriginalName());
        } catch (Exception e) {
            log.error("文件下载失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件下载失败", e);
        }
    }
    
    @Override
    public FileInfo getFileById(Long id) {
        return this.getById(id);
    }
    
    @Override
    public IPage<FileInfo> listFiles(Page<FileInfo> page, Map<String, Object> params) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        
        // 设置查询条件
        if (params.containsKey("deviceId") && StringUtils.hasText((String) params.get("deviceId"))) {
            wrapper.eq(FileInfo::getDeviceId, params.get("deviceId"));
        }
        
        if (params.containsKey("fileType") && StringUtils.hasText((String) params.get("fileType"))) {
            wrapper.eq(FileInfo::getFileType, params.get("fileType"));
        }
        
        if (params.containsKey("startTime") && params.containsKey("endTime")) {
            wrapper.between(FileInfo::getUploadTime, params.get("startTime"), params.get("endTime"));
        }
        
        // 按上传时间倒序排序
        wrapper.orderByDesc(FileInfo::getUploadTime);
        
        return this.page(page, wrapper);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFile(Long id) {
        // 获取文件信息
        FileInfo fileInfo = this.getById(id);
        if (fileInfo == null) {
            return false;
        }
        
        try {
            // 删除物理文件
            File file = new File(fileInfo.getFilePath());
            if (file.exists()) {
                file.delete();
            }
            
            // 更新数据库记录
            fileInfo.setStatus(FileConstant.FILE_STATUS_DELETED);
            fileInfo.setUpdateTime(LocalDateTime.now());
            return this.updateById(fileInfo);
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件删除失败", e);
        }
    }
    
    /**
     * 根据文件名获取文件类型
     * 
     * @param filename 文件名
     * @return 文件类型
     */
    private String getFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 处理文件（解压缩、解密）
     * 
     * @param originalFile 原始文件
     * @param isEncrypted  是否加密
     * @param isCompressed 是否压缩
     * @return 处理后的文件
     */
    private File processFile(File originalFile, Integer isEncrypted, Integer isCompressed) {
        File processedFile = originalFile;
        
        try {
            // 如果启用解密且文件是加密的
            if (fileProperties.getStorage().getEnableDecrypt() && 
                Objects.equals(isEncrypted, FileConstant.FLAG_TRUE)) {
                
                // 创建解密后的文件
                String decryptedPath = originalFile.getAbsolutePath() + ".decrypted";
                File decryptedFile = new File(decryptedPath);
                
                // 解密文件
                processedFile = fileEncryptUtil.decryptFile(
                    originalFile, 
                    decryptedFile, 
                    fileProperties.getStorage().getAesKey()
                );
            }
            
            // 如果启用解压缩且文件是压缩的
            if (fileProperties.getStorage().getEnableDecompress() && 
                Objects.equals(isCompressed, FileConstant.FLAG_TRUE)) {
                
                // 检查文件是否为GZIP格式
                if (fileCompressUtil.isGzipFile(processedFile)) {
                    // 创建解压后的文件
                    String decompressedPath = processedFile.getAbsolutePath() + ".decompressed";
                    File decompressedFile = new File(decompressedPath);
                    
                    // 解压文件
                    processedFile = fileCompressUtil.decompressFile(processedFile, decompressedFile);
                }
            }
            
            return processedFile;
        } catch (Exception e) {
            log.error("文件处理失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件处理失败", e);
        }
    }
} 