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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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
    public FileInfo uploadFile(MultipartFile file, String deviceId, Integer isEncrypted, Integer isCompressed, String originalExtension) {
        try {
            // 获取当前日期作为子目录
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern(FileConstant.DATE_FORMAT_YYYYMMDD));
            // 确保路径分隔符正确
            String basePath = fileProperties.getStorage().getPath();
            if (!basePath.endsWith(File.separator)) {
                basePath = basePath + File.separator;
            }
            String storageDir = basePath + dateDir;
            
            // 获取原始文件名并检查格式
            String originalFilename = file.getOriginalFilename();
            String fileType = getFileType(originalFilename);
            
            // 保存原始扩展名，用于解压缩后恢复
            if (originalExtension != null && !originalExtension.isEmpty()) {
                // 保存原始扩展名到线程局部变量或缓存中，供解压缩使用
                EXTENSION_CACHE.put(originalFilename, originalExtension);
                log.info("保存原始文件扩展名: {} -> {}", originalFilename, originalExtension);
            }
            
            // 如果文件名不符合标准格式，则重命名
            if (originalFilename != null && !isStandardFilename(originalFilename)) {
                log.info("原始文件名不符合标准格式，将进行重命名: {}", originalFilename);
                // 解析或使用默认值生成标准格式文件名
                originalFilename = generateStandardFilename(originalFilename, deviceId, fileType);
                log.info("生成标准格式文件名: {}", originalFilename);
            }
            
            // 存储原始文件 - 注意这里传入重命名后的文件名
            File originalFile = storeFileWithName(file, storageDir, originalFilename);
            
            // 处理文件（解压缩、解密）- 如果提供了原始扩展名，会在解压缩时使用
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
                                  Integer chunk, Integer chunks, Integer isEncrypted, Integer isCompressed, String originalExtension) {
        try {
            // 获取当前日期作为子目录
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern(FileConstant.DATE_FORMAT_YYYYMMDD));
            // 确保路径分隔符正确
            String basePath = fileProperties.getStorage().getPath();
            if (!basePath.endsWith(File.separator)) {
                basePath = basePath + File.separator;
            }
            // 修复路径连接，使用File.separator确保分隔符一致性
            String storageDir = basePath + dateDir;
            
            // 添加日志，输出存储路径信息
            log.info("文件存储配置路径: {}", fileProperties.getStorage().getPath());
            log.info("修正的基础路径: {}", basePath);
            log.info("今日日期格式化: {}", dateDir);
            log.info("实际存储目录: {}", storageDir);
            
            // 确保加密文件有正确的后缀
            String processedFileName = ensureCorrectFileSuffix(fileName, isEncrypted);
            log.info("处理后的文件名: {} -> {}", fileName, processedFileName);
            
            // 保存原始扩展名，用于解压缩后恢复
            if (originalExtension != null && !originalExtension.isEmpty()) {
                // 保存原始扩展名到缓存中，供解压缩使用
                EXTENSION_CACHE.put(processedFileName, originalExtension);
                log.info("保存原始文件扩展名: {} -> {}", processedFileName, originalExtension);
            }
            
            // 检查目录是否存在，不存在则创建
            File storageDirFile = new File(storageDir);
            if (!storageDirFile.exists()) {
                boolean created = storageDirFile.mkdirs();
                log.info("创建存储目录: {} 结果: {}", storageDir, created ? "成功" : "失败");
            } else {
                log.info("存储目录已存在: {}", storageDir);
            }
            
            // 将原始文件名保存到线程本地变量，用于后续分块使用同一文件名
            String originalFileName = processedFileName;
            
            // 检查文件名是否符合标准格式，如果不符合则重命名
            // 重要修复：使用原始文件名作为key，获取已经生成的标准文件名
            String finalFileName = getOrCreateStandardFileName(originalFileName, deviceId);
            
            log.info("处理分块上传: fileChunk={}/{}, fileName={}", chunk + 1, chunks, finalFileName);
            
            // 存储分块文件 - 注意fileTransferUtil.storeFileChunk只有在所有分块上传完毕后才会返回合并后的文件
            File targetFile = fileTransferUtil.storeFileChunk(file, storageDir, finalFileName, chunk, chunks);
            
            // 如果不是所有分块都上传完成，返回null
            if (targetFile == null) {
                log.info("分块{}上传成功，等待其他分块上传", chunk + 1);
                return null;
            }
            
            // 所有分块上传完成，清理文件名缓存
            cleanupFilenameCache(originalFileName);
            
            log.info("所有分块上传完成，开始处理合并后的文件: {}", targetFile.getAbsolutePath());
            if (!targetFile.exists()) {
                log.error("合并后的文件不存在: {}", targetFile.getAbsolutePath());
                throw new RuntimeException("合并后的文件不存在");
            }
            
            // 处理文件
            String fileType = getFileType(finalFileName);
            
            // 所有分块已上传完成，进行文件处理
            File processedFile = processFile(targetFile, isEncrypted, isCompressed);
            
            // 处理完成后清理原始扩展名缓存
            EXTENSION_CACHE.remove(fileName);
            
            log.info("文件处理完成: {}", processedFile.getAbsolutePath());
            if (!processedFile.exists()) {
                log.error("处理后的文件不存在: {}", processedFile.getAbsolutePath());
                throw new RuntimeException("处理后的文件不存在");
            }
            
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
    
    // 新增一个线程安全的文件名缓存Map，用于确保同一文件的所有分块使用相同的文件名
    private static final Map<String, String> FILENAME_CACHE = new ConcurrentHashMap<>();
    
    // 扩展名缓存，用于保存原始文件扩展名
    private static final Map<String, String> EXTENSION_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 生成标准格式文件名
     * 确保相同原始文件名的所有分块使用相同的标准文件名
     * 
     * @param originalFilename 原始文件名
     * @param deviceId 设备ID
     * @return 标准格式文件名
     */
    private String getOrCreateStandardFileName(String originalFilename, String deviceId) {
        if (originalFilename == null) {
            return UUID.randomUUID().toString();
        }
        
        // 使用原始文件名作为缓存key
        return FILENAME_CACHE.computeIfAbsent(originalFilename, key -> {
            log.info("分块上传的文件名不符合标准格式，将进行重命名: {}", originalFilename);
            String fileType = getFileType(originalFilename);
            
            // 尝试从原始文件名中提取信息
            String userId = "default";
            Long timestamp = System.currentTimeMillis();
            Long duration = 0L;
            
            // 尝试从原始文件名中解析信息，如果可能的话
            if (originalFilename.contains("_")) {
                String[] parts = originalFilename.split("_");
                // 如果包含足够的部分，尝试提取
                if (parts.length >= 3) {
                    // 尝试提取用户ID (通常是第3个部分)
                    if (parts.length > 2) {
                        userId = parts[2];
                    }
                    
                    // 尝试提取时间戳 (通常是第4个部分)
                    if (parts.length > 3) {
                        try {
                            timestamp = Long.parseLong(parts[3]);
                        } catch (NumberFormatException e) {
                            log.warn("无法从文件名解析时间戳，使用当前时间戳");
                        }
                    }
                    
                    // 尝试提取时长 (通常是第5个部分)
                    if (parts.length > 4) {
                        try {
                            duration = Long.parseLong(parts[4]);
                        } catch (NumberFormatException e) {
                            log.warn("无法从文件名解析时长，使用默认值0");
                        }
                    }
                }
            }
            
            // 计算或使用文件MD5值
            String md5;
            if (originalFilename.contains("_") && originalFilename.split("_").length > 5) {
                // 尝试从原始文件名中提取MD5值
                String[] parts = originalFilename.split("_");
                String lastPart = parts[5];
                if (lastPart.contains(".")) {
                    lastPart = lastPart.substring(0, lastPart.indexOf("."));
                }
                
                if (lastPart.matches("[a-fA-F0-9]{32}")) {
                    md5 = lastPart;
                } else {
                    md5 = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
                }
            } else {
                md5 = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            }
            
            // 生成标准格式的文件名
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            
            String extension = "";
            if (originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            } else if (fileType != null && !fileType.equals("unknown")) {
                extension = "." + fileType;
            }
            
            String standardName = String.format("%s_%s_%s_%d_%d_%s%s", 
                    deviceId, dateStr, userId, timestamp, duration, md5, extension);
            
            log.info("生成标准格式文件名: {}", standardName);
            return standardName;
        });
    }
    
    private static final Pattern STANDARD_FILENAME_PATTERN = 
            Pattern.compile("^.+_\\d{8}_.+_\\d+_\\d+_[a-fA-F0-9]{32}.*$");
    
    /**
     * 检查文件名是否符合标准格式：设备ID_YYYYMMDD_用户ID_时间戳_时长_MD5值.扩展名
     * 
     * @param filename 文件名
     * @return 是否符合标准格式
     */
    private boolean isStandardFilename(String filename) {
        if (filename == null) {
            return false;
        }
        
        // 使用正则表达式验证文件名格式
        return STANDARD_FILENAME_PATTERN.matcher(filename).matches();
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
        File intermediateFile = null;
        
        try {
            log.info("开始处理文件: {}, 加密状态: {}, 压缩状态: {}", 
                    originalFile.getAbsolutePath(), isEncrypted, isCompressed);
            
            // 检测文件是否实际为加密文件
            boolean detectedAsEncrypted = fileEncryptUtil.isEncryptedFile(originalFile);
            if (detectedAsEncrypted && !Objects.equals(isEncrypted, FileConstant.FLAG_TRUE)) {
                log.warn("文件被检测为加密文件，但isEncrypted参数为0，自动调整为1: {}", originalFile.getName());
                isEncrypted = FileConstant.FLAG_TRUE;
            } else if (Objects.equals(isEncrypted, FileConstant.FLAG_TRUE) && !detectedAsEncrypted) {
                log.warn("文件标记为加密，但未检测到加密特征，请确认加密状态: {}", originalFile.getName());
            }
            
            // 如果启用解密且文件是加密的
            if (fileProperties.getStorage().getEnableDecrypt() && 
                Objects.equals(isEncrypted, FileConstant.FLAG_TRUE)) {
                
                log.info("准备解密文件，AES密钥长度: {}", fileProperties.getStorage().getAesKey().length());
                
                // 创建解密后的文件，保持原文件命名格式
                String decryptedPath = getProcessedFilePath(originalFile, "decrypted");
                File decryptedFile = new File(decryptedPath);
                
                // 解密文件
                try {
                    log.info("开始解密文件: {} -> {}", originalFile.getAbsolutePath(), decryptedFile.getAbsolutePath());
                    processedFile = fileEncryptUtil.decryptFile(
                        originalFile, 
                        decryptedFile, 
                        fileProperties.getStorage().getAesKey()
                    );
                    log.info("文件解密完成: {}, 文件大小: {}", processedFile.getAbsolutePath(), processedFile.length());
                } catch (Exception e) {
                    log.error("文件解密失败，详细错误: {}", e.getMessage(), e);
                    // 如果解密失败，直接使用原始文件
                    log.warn("解密失败，将使用原始文件: {}", originalFile.getAbsolutePath());
                    processedFile = originalFile;
                }
                
                // 记录中间文件，稍后删除
                intermediateFile = decryptedFile;
            } else {
                log.info("跳过解密，原因: {}启用解密={}, 文件是否加密={}",
                        fileProperties.getStorage().getEnableDecrypt() ? "" : "未", 
                        fileProperties.getStorage().getEnableDecrypt(),
                        isEncrypted);
            }
            
            // 如果启用解压缩且文件是压缩的
            if (fileProperties.getStorage().getEnableDecompress() && 
                Objects.equals(isCompressed, FileConstant.FLAG_TRUE)) {
                
                // 检查文件是否为GZIP格式
                boolean isGzip = fileCompressUtil.isGzipFile(processedFile);
                log.info("文件是否为GZIP格式: {}", isGzip);
                
                if (isGzip) {
                    // 获取原始文件的真实扩展名（从缓存中或从名称猜测）
                    String originalExtension = EXTENSION_CACHE.getOrDefault(
                        originalFile.getName(), 
                        getOriginalExtension(originalFile.getName())
                    );
                    
                    log.info("解压缩使用的原始扩展名: {}", originalExtension);
                    
                    // 创建解压后的文件，确保保留原始扩展名
                    String decompressedPath = getProcessedFilePathWithExt(processedFile, originalExtension);
                    File decompressedFile = new File(decompressedPath);
                    
                    // 解压文件
                    try {
                        log.info("开始解压文件: {} -> {}", processedFile.getAbsolutePath(), decompressedFile.getAbsolutePath());
                        File resultFile = fileCompressUtil.decompressFile(processedFile, decompressedFile);
                        log.info("文件解压完成: {}, 文件大小: {}", resultFile.getAbsolutePath(), resultFile.length());
                        
                        // 如果当前处理的文件不是原始文件，则可以删除中间文件
                        if (processedFile != originalFile && intermediateFile == null) {
                            intermediateFile = processedFile;
                        }
                        
                        processedFile = resultFile;
                    } catch (Exception e) {
                        log.error("文件解压失败，详细错误: {}", e.getMessage(), e);
                        // 如果解压失败但已解密成功，仍使用解密后的文件
                        log.warn("解压失败，将使用解密后的文件: {}", processedFile.getAbsolutePath());
                    }
                } else {
                    log.info("文件不是GZIP格式，跳过解压");
                }
            } else {
                log.info("跳过解压缩，原因: {}启用解压缩={}, 文件是否压缩={}",
                        fileProperties.getStorage().getEnableDecompress() ? "" : "未", 
                        fileProperties.getStorage().getEnableDecompress(), 
                        isCompressed);
            }
            
            // 删除中间处理文件
            if (intermediateFile != null && intermediateFile.exists() && intermediateFile != processedFile) {
                boolean deleted = intermediateFile.delete();
                if (deleted) {
                    log.info("已删除中间处理文件: {}", intermediateFile.getAbsolutePath());
                } else {
                    log.warn("无法删除中间处理文件: {}", intermediateFile.getAbsolutePath());
                    intermediateFile.deleteOnExit(); // 注册JVM退出时删除
                }
            }
            
            log.info("文件处理完成: {}", processedFile.getAbsolutePath());
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
     * 获取处理后的文件路径，但使用指定的扩展名
     * 
     * @param originalFile 原始文件
     * @param newExtension 新扩展名(不含".")
     * @return 处理后的文件路径
     */
    private String getProcessedFilePathWithExt(File originalFile, String newExtension) {
        String originalPath = originalFile.getAbsolutePath();
        String parentDir = originalFile.getParent();
        String fileName = originalFile.getName();
        
        // 移除所有处理标记和原扩展名
        String nameWithoutExt = fileName;
        if (fileName.contains(".")) {
            nameWithoutExt = fileName.substring(0, fileName.indexOf("."));
        }
        
        // 如果是标准文件名，提取最后一个下划线之前的部分作为基础名称
        if (isStandardFilename(nameWithoutExt)) {
            // 保留标准格式的文件名，即使它包含下划线
            // 不需要额外处理
        }
        
        // 添加新扩展名
        String newFileName = nameWithoutExt;
        if (newExtension != null && !newExtension.isEmpty()) {
            if (!newExtension.startsWith(".")) {
                newExtension = "." + newExtension;
            }
            newFileName += newExtension;
        }
        
        return parentDir + File.separator + newFileName;
    }
    
    /**
     * 尝试获取压缩文件的原始扩展名
     * 
     * @param compressedFileName 压缩文件名
     * @return 原始扩展名(带.)或空字符串
     */
    private String getOriginalExtension(String compressedFileName) {
        if (compressedFileName == null) {
            return "";
        }
        
        // 获取文件名中的原始扩展名部分
        // 1. 如果是.tar.gz格式，返回.tar
        if (compressedFileName.toLowerCase().endsWith(".tar.gz")) {
            return ".tar";
        }
        
        // 2. 从客户端传递的originalFilename中获取
        // (这部分需要客户端在上传时提供原始文件扩展名，我们这里只能猜测)
        
        // 3. 检查文件内容的前几个字节，识别常见文件类型
        // 这需要额外的文件类型检测库
        
        // 4. 默认情况返回空扩展名
        if (compressedFileName.toLowerCase().endsWith(".gz")) {
            // 从文件名中猜测可能的扩展名
            String nameWithoutGz = compressedFileName.substring(0, compressedFileName.length() - 3);
            
            // 检查常见的音频文件扩展名
            String[] commonAudioExts = {".mp3", ".wav", ".aac", ".flac", ".ogg", ".m4a"};
            for (String ext : commonAudioExts) {
                if (nameWithoutGz.toLowerCase().endsWith(ext)) {
                    return ext;
                }
            }
            
            // 也可能是.gz压缩前没有扩展名
            return ".mp3"; // 默认音频格式为mp3
        }
        
        return "";
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
    
    /**
     * 清理文件名缓存
     * 
     * @param originalFilename 原始文件名
     */
    private void cleanupFilenameCache(String originalFilename) {
        if (originalFilename != null && FILENAME_CACHE.containsKey(originalFilename)) {
            String removedName = FILENAME_CACHE.remove(originalFilename);
            log.info("从缓存中清理文件名映射: {} -> {}", originalFilename, removedName);
        }
    }
    
    /**
     * 确保文件名后缀正确，用于加密/解密处理
     * 
     * @param filename 原始文件名
     * @param isEncrypted 是否加密
     * @return 处理后的文件名
     */
    private String ensureCorrectFileSuffix(String filename, Integer isEncrypted) {
        if (filename == null) {
            return null;
        }
        
        // 如果文件已加密，但文件名不以.enc结尾，则添加后缀
        if (Objects.equals(isEncrypted, FileConstant.FLAG_TRUE)) {
            if (!filename.toLowerCase().endsWith(".enc")) {
                log.info("添加加密标识后缀(.enc)到文件名: {}", filename);
                return filename + ".enc";
            }
        }
        
        return filename;
    }
} 