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
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
            
            // 获取原始文件名并检查格式
            String originalFilename = file.getOriginalFilename();
            String fileType = getFileType(originalFilename);
            
            // 如果文件名不符合标准格式，则重命名
            if (originalFilename != null && !isStandardFilename(originalFilename)) {
                log.info("原始文件名不符合标准格式，将进行重命名: {}", originalFilename);
                // 解析或使用默认值生成标准格式文件名
                originalFilename = generateStandardFilename(originalFilename, deviceId, fileType);
                log.info("生成标准格式文件名: {}", originalFilename);
            }
            
            // 存储原始文件 - 注意这里传入重命名后的文件名
            File originalFile = storeFileWithName(file, storageDir, originalFilename);
            
            // 处理文件（解压缩、解密）
            File processedFile = processFile(originalFile, isEncrypted, isCompressed);
            
            // 计算MD5
            String md5 = fileEncryptUtil.calculateMD5(processedFile);
            
            // 解析文件名，例如: {设备ID}_{YYYYMMDD}_{用户ID}_{音频开始时间戳13位}_{音频时长毫秒级}_{文件md5值}
            // 示例: ASD111_20250429_333_1745921269000_10000_a98b56f513cc95932141567aa4c0524d.tgz
            String userId = "default";
            LocalDate recordDate = LocalDate.now();
            LocalDateTime recordStartTime = null;
            Long recordDuration = null;
            
            if (originalFilename != null && originalFilename.contains("_")) {
                String[] parts = originalFilename.split("_");
                // 至少有5个部分才进行解析
                if (parts.length >= 5) {
                    // 尝试解析日期
                    try {
                        if (parts[1].length() == 8) {
                            String dateStr = parts[1];
                            recordDate = LocalDate.parse(dateStr, 
                                DateTimeFormatter.ofPattern("yyyyMMdd"));
                        }
                    } catch (Exception e) {
                        log.warn("解析录音日期失败: {}", e.getMessage());
                    }
                    
                    // 尝试解析用户ID
                    if (parts.length > 2) {
                        userId = parts[2];
                    }
                    
                    // 尝试解析录音开始时间
                    if (parts.length > 3) {
                        try {
                            long timestamp = Long.parseLong(parts[3]);
                            recordStartTime = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(timestamp), 
                                ZoneId.systemDefault());
                        } catch (Exception e) {
                            log.warn("解析录音开始时间失败: {}", e.getMessage());
                        }
                    }
                    
                    // 尝试解析录音时长
                    if (parts.length > 4) {
                        try {
                            recordDuration = Long.parseLong(parts[4]);
                        } catch (Exception e) {
                            log.warn("解析录音时长失败: {}", e.getMessage());
                        }
                    }
                }
            }
            
            // 创建文件信息记录
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(originalFilename);
            fileInfo.setFilePath(processedFile.getAbsolutePath());
            fileInfo.setFileSize(processedFile.length());
            fileInfo.setFileType(fileType);
            fileInfo.setUploadTime(LocalDateTime.now());
            fileInfo.setDeviceId(deviceId);
            fileInfo.setUserId(userId);
            fileInfo.setFileMd5(md5);
            fileInfo.setRecordDate(recordDate);
            fileInfo.setRecordStartTime(recordStartTime);
            fileInfo.setRecordDuration(recordDuration);
            
            // 设置访问URL和域名前缀
            String baseUrl = fileProperties.getStorage().getBaseUrl();
            if (baseUrl != null && !baseUrl.isEmpty()) {
                String relativePath = processedFile.getAbsolutePath().replace(fileProperties.getStorage().getPath(), "").replace("\\", "/");
                // 确保路径分隔符是前斜杠
                if (!relativePath.startsWith("/")) {
                    relativePath = "/" + relativePath;
                }
                // 使用配置的baseUrl和相对路径生成访问URL
                fileInfo.setAccessUrl(baseUrl + relativePath);
                
                try {
                    URL url = new URL(baseUrl);
                    fileInfo.setDomainPrefix(url.getHost());
                } catch (Exception e) {
                    log.warn("解析域名前缀失败: {}", e.getMessage());
                }
            } else {
                // 如果没有配置baseUrl，则使用服务器配置生成
                String relativePath = processedFile.getAbsolutePath().replace(fileProperties.getStorage().getPath(), "").replace("\\", "/");
                if (!relativePath.startsWith("/")) {
                    relativePath = "/" + relativePath;
                }
                // 生成完整URL，包含服务器地址和端口
                String host = fileProperties.getStorage().getServerHost();
                Integer port = fileProperties.getStorage().getServerPort();
                String contextPath = fileProperties.getStorage().getContextPath();
                
                // 确保contextPath以/开头且不以/结尾
                if (contextPath != null && !contextPath.isEmpty() && !contextPath.startsWith("/")) {
                    contextPath = "/" + contextPath;
                }
                if (contextPath != null && contextPath.endsWith("/")) {
                    contextPath = contextPath.substring(0, contextPath.length() - 1);
                }
                
                String serverUrl = "http://" + host + ":" + port + contextPath;
                fileInfo.setAccessUrl(serverUrl + "/files" + relativePath);
                fileInfo.setDomainPrefix(host);
            }
            
            fileInfo.setStatus(FileConstant.FILE_STATUS_NORMAL);
            fileInfo.setCreateTime(LocalDateTime.now());
            fileInfo.setUpdateTime(LocalDateTime.now());
            fileInfo.setDeleted(0);
            
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
            
            // 检查文件名是否符合标准格式，如果不符合则重命名
            String finalFileName = fileName;
            if (!isStandardFilename(fileName)) {
                log.info("分块上传的文件名不符合标准格式，将进行重命名: {}", fileName);
                String fileType = getFileType(fileName);
                finalFileName = generateStandardFilename(fileName, deviceId, fileType);
                log.info("生成标准格式文件名: {}", finalFileName);
            }
            
            // 存储分块文件
            File targetFile = fileTransferUtil.storeFileChunk(file, storageDir, finalFileName, chunk, chunks);
            
            // 如果不是最后一块，返回null
            if (targetFile == null) {
                return null;
            }
            
            // 处理文件
            String fileType = getFileType(finalFileName);
            
            // 如果是最后一块，进行文件处理
            File processedFile = processFile(targetFile, isEncrypted, isCompressed);
            
            // 计算MD5
            String md5 = fileEncryptUtil.calculateMD5(processedFile);

            // 解析文件名，例如: {设备ID}_{YYYYMMDD}_{用户ID}_{音频开始时间戳13位}_{音频时长毫秒级}_{文件hash值}
            String userId = "default";
            LocalDate recordDate = LocalDate.now();
            LocalDateTime recordStartTime = null;
            Long recordDuration = null;
            
            if (finalFileName != null && finalFileName.contains("_")) {
                String[] parts = finalFileName.split("_");
                // 至少有5个部分才进行解析
                if (parts.length >= 5) {
                    // 尝试解析日期
                    try {
                        if (parts[1].length() == 8) {
                            String dateStr = parts[1];
                            recordDate = LocalDate.parse(dateStr, 
                                DateTimeFormatter.ofPattern("yyyyMMdd"));
                        }
                    } catch (Exception e) {
                        log.warn("解析录音日期失败: {}", e.getMessage());
                    }
                    
                    // 尝试解析用户ID
                    if (parts.length > 2) {
                        userId = parts[2];
                    }
                    
                    // 尝试解析录音开始时间
                    if (parts.length > 3) {
                        try {
                            long timestamp = Long.parseLong(parts[3]);
                            recordStartTime = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(timestamp), 
                                ZoneId.systemDefault());
                        } catch (Exception e) {
                            log.warn("解析录音开始时间失败: {}", e.getMessage());
                        }
                    }
                    
                    // 尝试解析录音时长
                    if (parts.length > 4) {
                        try {
                            recordDuration = Long.parseLong(parts[4]);
                        } catch (Exception e) {
                            log.warn("解析录音时长失败: {}", e.getMessage());
                        }
                    }
                }
            }
            
            // 创建文件信息记录，合并所有分块文件
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(finalFileName);
            fileInfo.setFilePath(processedFile.getAbsolutePath());
            fileInfo.setFileSize(processedFile.length());
            fileInfo.setFileType(fileType);
            fileInfo.setUploadTime(LocalDateTime.now());
            fileInfo.setDeviceId(deviceId);
            fileInfo.setUserId(userId);
            fileInfo.setFileMd5(md5);
            fileInfo.setRecordDate(recordDate);
            fileInfo.setRecordStartTime(recordStartTime);
            fileInfo.setRecordDuration(recordDuration);
            
            // 设置访问URL和域名前缀
            String baseUrl = fileProperties.getStorage().getBaseUrl();
            if (baseUrl != null && !baseUrl.isEmpty()) {
                String relativePath = processedFile.getAbsolutePath().replace(fileProperties.getStorage().getPath(), "").replace("\\", "/");
                // 确保路径分隔符是前斜杠
                if (!relativePath.startsWith("/")) {
                    relativePath = "/" + relativePath;
                }
                // 使用配置的baseUrl和相对路径生成访问URL
                fileInfo.setAccessUrl(baseUrl + relativePath);
                
                try {
                    URL url = new URL(baseUrl);
                    fileInfo.setDomainPrefix(url.getHost());
                } catch (Exception e) {
                    log.warn("解析域名前缀失败: {}", e.getMessage());
                }
            } else {
                // 如果没有配置baseUrl，则使用服务器配置生成
                String relativePath = processedFile.getAbsolutePath().replace(fileProperties.getStorage().getPath(), "").replace("\\", "/");
                if (!relativePath.startsWith("/")) {
                    relativePath = "/" + relativePath;
                }
                // 生成完整URL，包含服务器地址和端口
                String host = fileProperties.getStorage().getServerHost();
                Integer port = fileProperties.getStorage().getServerPort();
                String contextPath = fileProperties.getStorage().getContextPath();
                
                // 确保contextPath以/开头且不以/结尾
                if (contextPath != null && !contextPath.isEmpty() && !contextPath.startsWith("/")) {
                    contextPath = "/" + contextPath;
                }
                if (contextPath != null && contextPath.endsWith("/")) {
                    contextPath = contextPath.substring(0, contextPath.length() - 1);
                }
                
                String serverUrl = "http://" + host + ":" + port + contextPath;
                fileInfo.setAccessUrl(serverUrl + "/files" + relativePath);
                fileInfo.setDomainPrefix(host);
            }
            
            fileInfo.setStatus(FileConstant.FILE_STATUS_NORMAL);
            fileInfo.setCreateTime(LocalDateTime.now());
            fileInfo.setUpdateTime(LocalDateTime.now());
            fileInfo.setDeleted(0);
            
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
            fileTransferUtil.downloadWithRange(request, response, file, fileInfo.getFileName());
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
            fileInfo.setStatus(FileConstant.FILE_STATUS_EXPIRED);
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
                
                // 创建解密后的文件，保持原文件命名格式
                String decryptedPath = getProcessedFilePath(originalFile, "decrypted");
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
                    // 创建解压后的文件，保持原文件命名格式
                    String decompressedPath = getProcessedFilePath(originalFile, "decompressed");
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
    
    /**
     * 获取处理后的文件路径，保持文件命名标准格式
     * 
     * @param originalFile 原始文件
     * @param suffix 后缀标识（处理类型）
     * @return 处理后的文件路径
     */
    private String getProcessedFilePath(File originalFile, String suffix) {
        String originalPath = originalFile.getAbsolutePath();
        String parentDir = originalFile.getParent();
        String fileName = originalFile.getName();
        
        // 检查是否是标准文件名格式
        if (isStandardFilename(fileName)) {
            // 保持标准格式，添加处理标识
            if (fileName.contains(".")) {
                // 有扩展名的情况
                int dotIndex = fileName.lastIndexOf(".");
                String nameWithoutExt = fileName.substring(0, dotIndex);
                String extension = fileName.substring(dotIndex);
                return parentDir + File.separator + nameWithoutExt + "." + suffix + extension;
            } else {
                // 无扩展名的情况
                return originalPath + "." + suffix;
            }
        } else {
            // 非标准格式，简单添加后缀
            return originalPath + "." + suffix;
        }
    }
    
    /**
     * 检查文件名是否符合标准格式：设备ID_YYYYMMDD_用户ID_时间戳_时长_MD5值.扩展名
     * 
     * @param filename 文件名
     * @return 是否符合标准格式
     */
    private boolean isStandardFilename(String filename) {
        if (filename == null || !filename.contains("_")) {
            return false;
        }
        
        String nameWithoutExt = filename;
        if (filename.contains(".")) {
            nameWithoutExt = filename.substring(0, filename.lastIndexOf("."));
        }
        
        String[] parts = nameWithoutExt.split("_");
        
        // 必须至少有6个部分
        if (parts.length < 6) {
            return false;
        }
        
        // 检查第二部分是否为8位日期格式
        if (parts[1].length() != 8) {
            return false;
        }
        
        try {
            // 检查日期格式
            Integer.parseInt(parts[1]);
            
            // 检查时间戳是否为数字
            Long.parseLong(parts[3]);
            
            // 检查时长是否为数字
            Long.parseLong(parts[4]);
            
            // 通过所有检查
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 解析或使用默认值生成标准格式文件名
     * 
     * @param originalFilename 原始文件名
     * @param deviceId 设备ID
     * @param fileType 文件类型
     * @return 标准格式文件名
     */
    private String generateStandardFilename(String originalFilename, String deviceId, String fileType) {
        // 使用当前日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 用户ID默认为"default"
        String userId = "default";
        
        // 当前时间戳
        long timestamp = System.currentTimeMillis();
        
        // 音频时长默认为0
        long duration = 0;
        
        // 如果原始文件名可以解析，尝试提取信息
        if (originalFilename != null && originalFilename.contains("_")) {
            String[] parts = originalFilename.split("_");
            if (parts.length > 2) {
                userId = parts[parts.length - 2]; // 尝试从倒数第二个部分获取用户ID
            }
        }
        
        // 计算MD5文件哈希值
        String md5;
        if (StringUtils.hasText(originalFilename)) {
            md5 = fileEncryptUtil.calculateMD5(originalFilename);
        } else {
            md5 = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        }
        
        // 构建标准格式文件名：{设备ID}_{YYYYMMDD}_{用户ID}_{音频开始时间戳13位}_{音频时长毫秒级}_{文件md5值}
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        } else {
            extension = "." + fileType;
        }
        
        String standardFilename = String.format("%s_%s_%s_%d_%d_%s%s", 
                deviceId, date, userId, timestamp, duration, md5, extension);
        
        return standardFilename;
    }
    
    /**
     * 存储文件，并返回存储后的文件对象
     * 
     * @param file 要存储的文件
     * @param storageDir 存储目录
     * @param originalFilename 原始文件名
     * @return 存储后的文件对象
     */
    private File storeFileWithName(MultipartFile file, String storageDir, String originalFilename) {
        try {
            // 创建存储目录
            File dir = new File(storageDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // 存储文件
            File targetFile = new File(storageDir + File.separator + originalFilename);
            file.transferTo(targetFile);
            
            return targetFile;
        } catch (Exception e) {
            log.error("文件存储失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件存储失败", e);
        }
    }
} 