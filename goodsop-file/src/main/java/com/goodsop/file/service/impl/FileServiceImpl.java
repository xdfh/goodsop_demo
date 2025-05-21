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
import com.goodsop.file.util.FileProcessingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    
    /**
     * 文件名缓存，用于存储标准化后的文件名
     */
    private static final Map<String, String> FILENAME_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 扩展名缓存，用于存储原始文件扩展名
     */
    private static final Map<String, String> EXTENSION_CACHE = new ConcurrentHashMap<>();

    /**
     * 原始文件大小缓存，用于存储解压/解密前的文件大小
     */
    private static final Map<String, String> ORIGINAL_SIZE_CACHE = new ConcurrentHashMap<>();

    private final FileProperties fileProperties;
    private final FileTransferUtil fileTransferUtil;
    private final FileCompressUtil fileCompressUtil;
    private final FileEncryptUtil fileEncryptUtil;
    private final FileProcessingUtil fileProcessingUtil;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo uploadFile(MultipartFile file, String deviceId, Integer isEncrypted, Integer isCompressed, String originalExtension) {
        try {
            // 获取当前日期作为子目录
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern(FileConstant.DATE_FORMAT_YYYYMMDD));
            String basePath = fileProperties.getStorage().getPath();
            if (!basePath.endsWith(File.separator)) {
                basePath = basePath + File.separator;
            }
            String storageDir = basePath + dateDir;
            
            // 确保存储目录存在
            File storageDirFile = new File(storageDir);
            if (!storageDirFile.exists()) {
                boolean created = storageDirFile.mkdirs();
                log.info("创建存储目录: {} 结果: {}", storageDir, created ? "成功" : "失败");
            }
            
            // 获取原始文件名并检查格式
            String originalFilename = file.getOriginalFilename();
            String fileType = getFileType(originalFilename);
            
            // 保存原始文件大小
            long originalSize = file.getSize();
            
            // 首先保存原始文件到临时目录
            File tempDir = Files.createTempDirectory("upload_").toFile();
            File tempFile = new File(tempDir, originalFilename);
            file.transferTo(tempFile);
            log.info("已保存原始文件: {}, 大小: {} 字节", tempFile.getAbsolutePath(), tempFile.length());
            
            // 处理文件（解密和解压缩）
            File processedFile = tempFile;
            
            // 如果需要解密和解压缩
            if ((isEncrypted != null && isEncrypted == 1 && fileProperties.getStorage().getEnableDecrypt()) ||
                (isCompressed != null && isCompressed == 1 && fileProperties.getStorage().getEnableDecompress())) {
                
                log.info("文件需要处理: isEncrypted={}, isCompressed={}", isEncrypted, isCompressed);
                
                // 解密文件
                if (isEncrypted != null && isEncrypted == 1 && fileProperties.getStorage().getEnableDecrypt()) {
                    log.info("开始解密文件: {}", tempFile.getAbsolutePath());
                    String decryptedPath = tempFile.getAbsolutePath() + ".decrypted";
                    File decryptedFile = new File(decryptedPath);
                    processedFile = fileEncryptUtil.decryptFile(tempFile, decryptedFile, fileProperties.getStorage().getAesKey());
                    log.info("文件解密完成: {}, 大小: {}", processedFile.getAbsolutePath(), processedFile.length());
                }
                
                // 解压文件
                if (isCompressed != null && isCompressed == 1 && fileProperties.getStorage().getEnableDecompress()) {
                    log.info("开始解压文件: {}", processedFile.getAbsolutePath());
                    String decompressedPath = processedFile.getAbsolutePath() + ".decompressed";
                    File decompressedFile = new File(decompressedPath);
                    File tempProcessedFile = processedFile;
                    processedFile = fileCompressUtil.decompressFile(tempProcessedFile, decompressedFile);
                    log.info("文件解压完成: {}, 大小: {}", processedFile.getAbsolutePath(), processedFile.length());
                    
                    // 如果解压后文件与解密后文件不同，可以删除中间文件
                    if (!tempProcessedFile.equals(tempFile) && !tempProcessedFile.equals(processedFile)) {
                        tempProcessedFile.delete();
                        log.info("删除中间处理文件: {}", tempProcessedFile.getAbsolutePath());
                    }
                }
            }
            
            // 确定最终文件名（如果已经解密，原始文件名可能包含.enc后缀，需要移除）
            String finalFileName = originalFilename;
            
            // 移除加密后缀
            if (isEncrypted != null && isEncrypted == 1 && fileProperties.getStorage().getEnableDecrypt()
                && finalFileName.toLowerCase().endsWith(".enc") && !processedFile.equals(tempFile)) {
                // 只有解密成功的情况下才移除.enc后缀
                finalFileName = finalFileName.substring(0, finalFileName.length() - 4);
                log.info("移除加密后缀，最终文件名: {}", finalFileName);
            }
            
            // 移除压缩后缀
            if (isCompressed != null && isCompressed == 1 && fileProperties.getStorage().getEnableDecompress()) {
                // 检查是否是压缩格式的后缀，如果是则移除
                String lcFilename = finalFileName.toLowerCase();
                if (lcFilename.endsWith(".gz") || lcFilename.endsWith(".gzip")) {
                    int suffixLen = lcFilename.endsWith(".gz") ? 3 : 5;
                    finalFileName = finalFileName.substring(0, finalFileName.length() - suffixLen);
                    log.info("移除GZIP压缩后缀，最终文件名: {}", finalFileName);
                } else if (lcFilename.endsWith(".zip")) {
                    finalFileName = finalFileName.substring(0, finalFileName.length() - 4);
                    log.info("移除ZIP压缩后缀，最终文件名: {}", finalFileName);
                }
            }
            
            // 从缓存中检查是否有原始扩展名，如果有，则添加
            String cachedExtension = EXTENSION_CACHE.get(originalFilename);
            if (cachedExtension != null && !cachedExtension.isEmpty() && 
                !finalFileName.toLowerCase().endsWith(cachedExtension.toLowerCase())) {
                // 确保扩展名以.开头
                if (!cachedExtension.startsWith(".")) {
                    cachedExtension = "." + cachedExtension;
                }
                finalFileName = finalFileName + cachedExtension;
                log.info("从缓存获取文件类型: {} -> {}, 添加原始扩展名后: {}", 
                         originalFilename, cachedExtension, finalFileName);
            }
            
            // 将处理后的文件复制到最终存储位置
            File targetFile = new File(storageDir, finalFileName);
            Files.copy(processedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("处理后的文件已保存到最终位置: {}", targetFile.getAbsolutePath());
            
            // 计算MD5
            String md5 = calculateFileMd5(targetFile);
            
            // 创建文件信息对象
            FileInfo fileInfo = new FileInfo();
            fileInfo.setDeviceId(deviceId);
            fileInfo.setUserId(getCurrentUserId());
            fileInfo.setFileName(finalFileName);
            fileInfo.setFilePath(targetFile.getAbsolutePath());
            fileInfo.setFileSize(targetFile.length());
            
            // 获取解压缩/解密后的文件类型，不是原始压缩文件的类型
            String fileType2 = getFileType(finalFileName);
            fileInfo.setFileType(fileType2);
            
            fileInfo.setFileMd5(md5);
            fileInfo.setUploadTime(LocalDateTime.now());
            
            // 设置加密和压缩状态字段
            // 存储文件是否仍然处于加密状态 - 如果客户端标记为加密但未解密，或解密失败
            boolean stillEncrypted = (isEncrypted != null && isEncrypted == 1) && !processedFile.equals(tempFile);
            // 存储文件是否仍然处于压缩状态 - 如果客户端标记为压缩但未解压，或解压失败
            boolean stillCompressed = (isCompressed != null && isCompressed == 1) && !processedFile.equals(tempFile);
            
            fileInfo.setIsEncrypted(stillEncrypted);
            fileInfo.setIsCompressed(stillCompressed);
            
            log.info("文件最终状态: 是否加密={}, 是否压缩={}", stillEncrypted, stillCompressed);
            
            // 设置加密和压缩类型
            if (fileInfo.getIsEncrypted()) {
                fileInfo.setEncryptionType(fileProperties.getStorage().getDefaultEncryptionType());
                log.info("文件保持加密状态，设置加密类型: {}", fileInfo.getEncryptionType());
            }
            if (fileInfo.getIsCompressed()) {
                fileInfo.setCompressionType(fileProperties.getStorage().getDefaultCompressionType());
                log.info("文件保持压缩状态，设置压缩类型: {}", fileInfo.getCompressionType());
            }
            
            // 设置原始文件大小
            fileInfo.setOriginalSize(originalSize);
            
            // 解析文件名中的元数据信息
            parseFileMetadata(fileInfo, originalFilename);
            
            // 设置访问URL和域名前缀
            String baseUrl = fileProperties.getStorage().getBaseUrl();
            if (baseUrl != null && !baseUrl.isEmpty()) {
                String serverStoragePath = fileProperties.getStorage().getPath();
                String fileAbsolutePath = targetFile.getAbsolutePath();

                // 统一路径分隔符为 /
                String normalizedServerStoragePath = serverStoragePath.replace("\\", "/");
                String normalizedFileAbsolutePath = fileAbsolutePath.replace("\\", "/");

                //确保 normalizedServerStoragePath 以 / 结尾，除非它是根路径 D:/
                if (!normalizedServerStoragePath.endsWith("/") && normalizedServerStoragePath.contains("/")) {
                    normalizedServerStoragePath += "/";
                }
                
                String relativePath = "";
                if (normalizedFileAbsolutePath.startsWith(normalizedServerStoragePath)) {
                    relativePath = normalizedFileAbsolutePath.substring(normalizedServerStoragePath.length());
                } else {
                    // 如果基础路径不匹配，这可能是一个配置问题或意外情况
                    // 作为备选，尝试从最后一个 dateDir 开始截取，但这不够通用
                    log.warn("文件绝对路径 '{}' 与配置的存储基础路径 '{}' 不匹配。将尝试基于日期目录生成相对路径。", normalizedFileAbsolutePath, normalizedServerStoragePath);
                    // 尝试从日期目录开始获取相对路径
                    int dateDirIndex = normalizedFileAbsolutePath.indexOf(dateDir);
                    if (dateDirIndex != -1) {
                        relativePath = normalizedFileAbsolutePath.substring(dateDirIndex);
                    } else {
                        // 如果连日期目录都找不到，则可能无法正确生成相对路径，这里保留文件名作为最后的手段
                        relativePath = targetFile.getName();
                        log.warn("无法从路径 '{}' 中定位日期目录 '{}'。 accessUrl 可能不正确。", normalizedFileAbsolutePath, dateDir);
                    }
                }
                
                // 确保 relativePath 不以 / 开头，因为 baseUrl 通常以 / 结尾（如 /api/files）
                // 或者 baseUrl 不以 / 结尾而 relativePath 以 / 开头
                // 这里我们假设 baseUrl 类似 http://host:port/context-path/files (没有末尾斜杠)
                // 或者 http://host:port/context-path/files/ (有末尾斜杠)
                // 我们需要确保最终URL路径的正确性

                String finalAccessUrl;
                if (baseUrl.endsWith("/")) {
                    if (relativePath.startsWith("/")) {
                        finalAccessUrl = baseUrl + relativePath.substring(1);
                    } else {
                        finalAccessUrl = baseUrl + relativePath;
                    }
                } else {
                    if (relativePath.startsWith("/")) {
                        finalAccessUrl = baseUrl + relativePath;
                    } else {
                        finalAccessUrl = baseUrl + "/" + relativePath;
                    }
                }
                fileInfo.setAccessUrl(finalAccessUrl);
                
                try {
                    URL url = new URL(baseUrl); // 使用 baseUrl 来解析 host，而不是拼接后的 accessUrl
                    fileInfo.setDomainPrefix(url.getHost());
                } catch (Exception e) {
                    log.warn("解析域名前缀失败: {}", e.getMessage());
                }
            }
            
            fileInfo.setStatus(FileConstant.FILE_STATUS_NORMAL);
            fileInfo.setCreateTime(LocalDateTime.now());
            fileInfo.setUpdateTime(LocalDateTime.now());
            fileInfo.setDeleted(0);
            
            // 保存到数据库
            this.save(fileInfo);
            log.info("文件上传成功: {}", fileInfo);
            
            // 删除临时处理文件
            try {
                if (!processedFile.equals(targetFile) && !processedFile.equals(tempFile)) {
                    processedFile.delete();
                    log.info("删除临时处理文件: {}", processedFile.getAbsolutePath());
                }
                
                if (tempFile.exists()) {
                    tempFile.delete();
                    log.info("删除临时原始文件: {}", tempFile.getAbsolutePath());
                }
                
                // 尝试删除临时目录
                tempDir.delete();
            } catch (Exception e) {
                log.warn("删除临时文件失败: {}", e.getMessage());
            }
            
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
            // 记录配置项值，便于排查问题
            log.info("===== 文件上传配置检查 =====");
            log.info("解密配置: fileProperties.getStorage().getEnableDecrypt()={}", fileProperties.getStorage().getEnableDecrypt());
            log.info("解压配置: fileProperties.getStorage().getEnableDecompress()={}", fileProperties.getStorage().getEnableDecompress());
            log.info("上传参数: isEncrypted={}, isCompressed={}", isEncrypted, isCompressed);
            log.info("===========================");
            
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
            String originalFilename = processedFileName;
            
            // 检查文件名是否符合标准格式，如果不符合则重命名
            // 重要修复：使用原始文件名作为key，获取已经生成的标准文件名
            String finalFileName = getOrCreateStandardFileName(originalFilename, deviceId);
            
            // 保存原始扩展名到FileTransferUtil的缓存中，用于合并分块文件时使用
            if (originalExtension != null && !originalExtension.isEmpty()) {
                fileTransferUtil.saveOriginalExtension(finalFileName, originalExtension);
                log.info("保存原始扩展名到FileTransferUtil: {} -> {}", finalFileName, originalExtension);
            }
            
            log.info("处理分块上传: fileChunk={}/{}, fileName={}", chunk + 1, chunks, finalFileName);
            
            // 存储分块文件 - 注意fileTransferUtil.storeFileChunk只有在所有分块上传完毕后才会返回合并后的文件
            File tempFile = fileTransferUtil.storeFileChunk(file, storageDir, finalFileName, chunk, chunks);
            
            // 如果不是所有分块都上传完成，返回null
            if (tempFile == null) {
                log.info("分块{}上传成功，等待其他分块上传", chunk + 1);
                return null;
            }
            
            // 所有分块上传完成，清理文件名缓存
            cleanupFilenameCache(originalFilename);
            
            log.info("所有分块上传完成，开始处理合并后的文件: {}", tempFile.getAbsolutePath());
            if (!tempFile.exists()) {
                log.error("合并后的文件不存在: {}", tempFile.getAbsolutePath());
                throw new RuntimeException("合并后的文件不存在");
            }
            
            // 处理合并后的文件（解密和解压缩）
            File processedFile = tempFile;
            // 记录是否真的处理了文件
            boolean actuallyDecrypted = false;
            boolean actuallyDecompressed = false;
            
            log.info("检查文件是否需要处理: isEncrypted={}, configEnableDecrypt={}, isCompressed={}, configEnableDecompress={}",
                    isEncrypted, fileProperties.getStorage().getEnableDecrypt(),
                    isCompressed, fileProperties.getStorage().getEnableDecompress());
            
            if ((isEncrypted != null && isEncrypted == 1 && fileProperties.getStorage().getEnableDecrypt()) ||
                (isCompressed != null && isCompressed == 1 && fileProperties.getStorage().getEnableDecompress())) {
                
                // 直接使用已有的工具类处理文件
                log.info("文件需要处理: isEncrypted={}, isCompressed={}", isEncrypted, isCompressed);
                
                if (isEncrypted != null && isEncrypted == 1 && fileProperties.getStorage().getEnableDecrypt()) {
                    try {
                        log.info("开始解密文件: {}", tempFile.getAbsolutePath());
                        String decryptedPath = tempFile.getAbsolutePath() + ".decrypted";
                        File decryptedFile = new File(decryptedPath);
                        File decryptedResult = fileEncryptUtil.decryptFile(tempFile, decryptedFile, fileProperties.getStorage().getAesKey());
                        if (decryptedResult != null && decryptedResult.exists()) {
                            processedFile = decryptedResult;
                            actuallyDecrypted = true;
                            log.info("文件解密完成: {}, 大小: {}", processedFile.getAbsolutePath(), processedFile.length());
                        } else {
                            log.warn("文件解密失败或解密结果文件不存在，将保留原始加密文件: {}", tempFile.getAbsolutePath());
                            // 确保processedFile是tempFile，而不是null
                            processedFile = tempFile;
                        }
                    } catch (Exception e) {
                        log.warn("文件解密过程出现异常，将保留原始加密文件: {}, 异常: {}", tempFile.getAbsolutePath(), e.getMessage());
                        // 确保processedFile是tempFile，而不是null
                        processedFile = tempFile;
                    }
                } else if (isEncrypted != null && isEncrypted == 1) {
                    log.info("文件标记为加密(isEncrypted=1)，但系统配置禁用解密(enable-decrypt=false)，不进行解密处理");
                }
                
                if (isCompressed != null && isCompressed == 1 && fileProperties.getStorage().getEnableDecompress()) {
                    try {
                        log.info("开始解压文件: {}", processedFile.getAbsolutePath());
                        String decompressedPath = processedFile.getAbsolutePath() + ".decompressed";
                        File decompressedFile = new File(decompressedPath);
                        File tempProcessedFile = processedFile;
                        File decompressedResult = fileCompressUtil.decompressFile(tempProcessedFile, decompressedFile);
                        if (decompressedResult != null && decompressedResult.exists()) {
                            processedFile = decompressedResult;
                            actuallyDecompressed = true;
                            log.info("文件解压完成: {}, 大小: {}", processedFile.getAbsolutePath(), processedFile.length());
                            
                            // 如果解压后文件与解密后文件不同，可以删除中间文件
                            if (!tempProcessedFile.equals(tempFile) && !tempProcessedFile.equals(processedFile)) {
                                tempProcessedFile.delete();
                                log.info("删除中间处理文件: {}", tempProcessedFile.getAbsolutePath());
                            }
                        } else {
                            log.warn("文件解压失败或解压结果文件不存在，将保留原始压缩文件: {}", processedFile.getAbsolutePath());
                            // processedFile保持不变
                        }
                    } catch (Exception e) {
                        log.warn("文件解压过程出现异常，将保留原始压缩文件: {}, 异常: {}", processedFile.getAbsolutePath(), e.getMessage());
                        // processedFile保持不变
                    }
                } else if (isCompressed != null && isCompressed == 1) {
                    log.info("文件标记为压缩(isCompressed=1)，但系统配置禁用解压(enable-decompress=false)，不进行解压处理");
                }
            } else {
                log.info("文件不需要处理，或者系统配置禁用了相应的处理");
            }
            
            // 确定最终文件名（如果已经解密，原始文件名可能包含.enc后缀，需要移除）
            if (isEncrypted != null && isEncrypted == 1 && actuallyDecrypted
                && finalFileName.toLowerCase().endsWith(".enc") && !processedFile.equals(tempFile)) {
                // 只有解密成功的情况下才移除.enc后缀
                finalFileName = finalFileName.substring(0, finalFileName.length() - 4);
                log.info("移除加密后缀，最终文件名: {}", finalFileName);
            }
            
            // 移除压缩后缀
            if (isCompressed != null && isCompressed == 1 && actuallyDecompressed) {
                // 检查是否是压缩格式的后缀，如果是则移除
                String lcFilename = finalFileName.toLowerCase();
                if (lcFilename.endsWith(".gz") || lcFilename.endsWith(".gzip")) {
                    int suffixLen = lcFilename.endsWith(".gz") ? 3 : 5;
                    finalFileName = finalFileName.substring(0, finalFileName.length() - suffixLen);
                    log.info("移除GZIP压缩后缀，最终文件名: {}", finalFileName);
                } else if (lcFilename.endsWith(".zip")) {
                    finalFileName = finalFileName.substring(0, finalFileName.length() - 4);
                    log.info("移除ZIP压缩后缀，最终文件名: {}", finalFileName);
                }
            }
            
            // 从缓存中检查是否有原始扩展名，如果有，则添加
            String cachedExtension = EXTENSION_CACHE.get(originalFilename);
            if (cachedExtension != null && !cachedExtension.isEmpty() && 
                !finalFileName.toLowerCase().endsWith(cachedExtension.toLowerCase())) {
                // 确保扩展名以.开头
                if (!cachedExtension.startsWith(".")) {
                    cachedExtension = "." + cachedExtension;
                }
                finalFileName = finalFileName + cachedExtension;
                log.info("从缓存获取文件类型: {} -> {}, 添加原始扩展名后: {}", 
                         originalFilename, cachedExtension, finalFileName);
            }
            
            // 将处理后的文件复制到最终存储位置
            File targetFile = new File(storageDir, finalFileName);
            Files.copy(processedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("处理后的文件已保存到最终位置: {}", targetFile.getAbsolutePath());
            
            // 处理文件
            String fileType = getFileType(finalFileName);
            
            // 创建文件信息对象
            FileInfo fileInfo = new FileInfo();
            fileInfo.setDeviceId(deviceId);
            fileInfo.setUserId(getCurrentUserId());
            fileInfo.setFileName(finalFileName);
            fileInfo.setFilePath(targetFile.getAbsolutePath());
            fileInfo.setFileSize(targetFile.length());
            fileInfo.setFileType(fileType);
            fileInfo.setFileMd5(calculateFileMd5(targetFile));
            fileInfo.setUploadTime(LocalDateTime.now());
            
            // 设置加密和压缩状态字段
            // 存储文件是否仍然处于加密状态 - 如果客户端标记为加密但未解密，或解密失败
            boolean stillEncrypted = (isEncrypted != null && isEncrypted == 1) && !actuallyDecrypted;
            // 存储文件是否仍然处于压缩状态 - 如果客户端标记为压缩但未解压，或解压失败
            boolean stillCompressed = (isCompressed != null && isCompressed == 1) && !actuallyDecompressed;
            
            fileInfo.setIsEncrypted(stillEncrypted);
            fileInfo.setIsCompressed(stillCompressed);
            
            log.info("文件最终状态: 是否加密={}, 是否压缩={}", stillEncrypted, stillCompressed);
            
            // 设置加密和压缩类型
            if (fileInfo.getIsEncrypted()) {
                fileInfo.setEncryptionType(fileProperties.getStorage().getDefaultEncryptionType());
                log.info("文件保持加密状态，设置加密类型: {}", fileInfo.getEncryptionType());
            }
            if (fileInfo.getIsCompressed()) {
                fileInfo.setCompressionType(fileProperties.getStorage().getDefaultCompressionType());
                log.info("文件保持压缩状态，设置压缩类型: {}", fileInfo.getCompressionType());
            }
            
            // 记录原始文件大小（如果有）
            String cachedOriginalSize = ORIGINAL_SIZE_CACHE.get(finalFileName);
            if (cachedOriginalSize != null) {
                fileInfo.setOriginalSize(Long.parseLong(cachedOriginalSize));
                ORIGINAL_SIZE_CACHE.remove(finalFileName);
            } else {
                // 如果没有缓存，则使用原始文件大小
                fileInfo.setOriginalSize(tempFile.length());
            }
            
            // 解析文件名中的元数据信息 - 使用原始文件名解析，更准确
            parseFileMetadata(fileInfo, originalFilename);
            
            // 设置访问URL和域名前缀 (与 uploadFile 方法中相同的修正逻辑)
            String baseUrl = fileProperties.getStorage().getBaseUrl(); // 统一使用 getBaseUrl()
            if (baseUrl != null && !baseUrl.isEmpty()) {
                String serverStoragePath = fileProperties.getStorage().getPath();
                // targetFile 是最终保存在磁盘上的文件对象
                String fileAbsolutePath = targetFile.getAbsolutePath(); 

                String normalizedServerStoragePath = serverStoragePath.replace("\\", "/");
                String normalizedFileAbsolutePath = fileAbsolutePath.replace("\\", "/");

                //确保 normalizedServerStoragePath 以 / 结尾，除非它是根路径 D:/
                if (!normalizedServerStoragePath.endsWith("/") && normalizedServerStoragePath.contains("/")) {
                    normalizedServerStoragePath += "/";
                }
                
                String relativePath = "";
                if (normalizedFileAbsolutePath.startsWith(normalizedServerStoragePath)) {
                    relativePath = normalizedFileAbsolutePath.substring(normalizedServerStoragePath.length());
                } else {
                    log.warn("文件绝对路径 '{}' 与配置的存储基础路径 '{}' 不匹配。将尝试基于日期目录生成相对路径。", normalizedFileAbsolutePath, normalizedServerStoragePath);
                    int dateDirIndex = normalizedFileAbsolutePath.indexOf(dateDir);
                    if (dateDirIndex != -1) {
                        relativePath = normalizedFileAbsolutePath.substring(dateDirIndex);
                    } else {
                        relativePath = targetFile.getName();
                        log.warn("无法从路径 '{}' 中定位日期目录 '{}'。 accessUrl 可能不正确。", normalizedFileAbsolutePath, dateDir);
                    }
                }
                
                String finalAccessUrl;
                if (baseUrl.endsWith("/")) {
                    if (relativePath.startsWith("/")) {
                        finalAccessUrl = baseUrl + relativePath.substring(1);
                    } else {
                        finalAccessUrl = baseUrl + relativePath;
                    }
                } else {
                    if (relativePath.startsWith("/")) {
                        finalAccessUrl = baseUrl + relativePath;
                    } else {
                        finalAccessUrl = baseUrl + "/" + relativePath;
                    }
                }
                fileInfo.setAccessUrl(finalAccessUrl);
                
                try {
                    URL url = new URL(baseUrl); // 使用 baseUrl 解析 host
                    fileInfo.setDomainPrefix(url.getHost());
                } catch (Exception e) {
                    log.warn("解析域名前缀失败: {}", e.getMessage());
                }
            } else {
                // 如果 baseUrl 未配置，则记录警告，accessUrl 可能不正确或为空
                log.warn("配置项 goodsop.file.storage.base-url 未设置，accessUrl 将不会被正确生成。");
            }
            
            fileInfo.setStatus(FileConstant.FILE_STATUS_NORMAL);
            fileInfo.setCreateTime(LocalDateTime.now());
            fileInfo.setUpdateTime(LocalDateTime.now());
            fileInfo.setDeleted(0);
            
            // 保存到数据库
            this.save(fileInfo);
            log.info("分块文件上传完成: {}", fileInfo);
            
            // 删除临时处理文件
            try {
                if (!processedFile.equals(targetFile) && !processedFile.equals(tempFile)) {
                    processedFile.delete();
                    log.info("删除临时处理文件: {}", processedFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("删除临时处理文件失败: {}", e.getMessage());
            }
            
            return fileInfo;
        } catch (Exception e) {
            log.error("分块文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("分块文件上传失败", e);
        }
    }
    
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
            } else {
                // 尝试从EXTENSION_CACHE获取扩展名
                String cachedExtension = EXTENSION_CACHE.get(originalFilename);
                if (cachedExtension != null && !cachedExtension.isEmpty()) {
                    extension = cachedExtension.startsWith(".") ? cachedExtension : "." + cachedExtension;
                    log.info("使用缓存的扩展名: {}", extension);
                }
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
        if (filename == null) {
            return "unknown";
        }
        
        // 从文件名中获取扩展名
        if (filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        }
        
        // 如果文件名中没有扩展名，尝试从扩展名缓存中获取
        String cachedExtension = EXTENSION_CACHE.get(filename);
        if (cachedExtension != null && !cachedExtension.isEmpty()) {
            // 移除开头的点(如果有)
            if (cachedExtension.startsWith(".")) {
                cachedExtension = cachedExtension.substring(1);
            }
            log.info("从缓存获取文件类型: {} -> {}", filename, cachedExtension);
            return cachedExtension.toLowerCase();
        }
        
        return "unknown";
    }
    
    /**
     * 获取当前用户ID
     * 
     * @return 当前用户ID
     */
    private String getCurrentUserId() {
        // TODO: 从安全上下文或会话中获取当前用户ID
        return "default";
    }

    /**
     * 计算文件的MD5值
     * 
     * @param file 文件
     * @return MD5值
     */
    private String calculateFileMd5(File file) {
        try {
            return fileEncryptUtil.calculateMD5(file);
        } catch (Exception e) {
            log.error("计算文件MD5失败: {}", e.getMessage(), e);
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * 处理文件（加密和压缩）
     * 
     * @param file 原始文件
     * @param isEncrypted 是否加密
     * @param isCompressed 是否压缩
     * @return 处理后的文件
     */
    private File processFile(File file, Integer isEncrypted, Integer isCompressed) {
        File processedFile = file;
        try {
            long originalSize = file.length();
            
            // 记录原始文件大小到缓存
            ORIGINAL_SIZE_CACHE.put(file.getName(), String.valueOf(originalSize));
            
            // 根据配置决定是否需要解密
            if (isEncrypted != null && isEncrypted == 1 && fileProperties.getStorage().getEnableDecrypt()) {
                try {
                    // 创建解密后的文件
                    String decryptedPath = getProcessedFilePath(processedFile, "decrypted");
                    File decryptedFile = new File(decryptedPath);
                    File decryptResult = fileEncryptUtil.decryptFile(processedFile, decryptedFile, fileProperties.getStorage().getAesKey());
                    if (decryptResult != null && decryptResult.exists()) {
                        processedFile = decryptResult;
                        log.info("文件解密完成: {}", processedFile.getAbsolutePath());
                    } else {
                        log.warn("文件解密失败或解密结果为null，将使用原始文件: {}", file.getAbsolutePath());
                        // 保持使用原始文件
                    }
                } catch (Exception e) {
                    log.warn("文件解密过程发生异常，将使用原始文件: {}，异常信息: {}", file.getAbsolutePath(), e.getMessage());
                    // 保持使用原始文件
                }
            }
            
            // 根据配置决定是否需要解压缩
            if (isCompressed != null && isCompressed == 1 && fileProperties.getStorage().getEnableDecompress()) {
                try {
                    // 获取原始扩展名（如果有）
                    String originalExtension = EXTENSION_CACHE.get(file.getName());
                    if (originalExtension != null) {
                        String decompressedPath = getProcessedFilePathWithExt(processedFile, originalExtension);
                        File decompressedFile = new File(decompressedPath);
                        File decompressResult = fileCompressUtil.decompressFile(processedFile, decompressedFile);
                        if (decompressResult != null && decompressResult.exists()) {
                            processedFile = decompressResult;
                            log.info("文件解压完成: {}", processedFile.getAbsolutePath());
                        } else {
                            log.warn("文件解压失败或解压结果为null，将使用原始文件: {}", processedFile.getAbsolutePath());
                            // 保持使用当前处理文件
                        }
                    } else {
                        log.warn("未找到原始文件扩展名，跳过解压缩: {}", file.getName());
                    }
                } catch (Exception e) {
                    log.warn("文件解压过程发生异常，将使用原始文件: {}，异常信息: {}", processedFile.getAbsolutePath(), e.getMessage());
                    // 保持使用当前处理文件
                }
            }
            
            return processedFile;
        } catch (Exception e) {
            log.error("文件处理过程中发生未预期的异常: {}", e.getMessage(), e);
            // 返回原始文件而不是抛出异常
            return file;
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

    /**
     * 从标准格式文件名中解析元数据
     * 格式: {设备ID}_{YYYYMMDD}_{用户ID}_{音频开始时间戳13位}_{音频时长毫秒级}_{文件md5值}
     *
     * @param fileInfo 文件信息对象
     * @param fileName 文件名
     */
    private void parseFileMetadata(FileInfo fileInfo, String fileName) {
        try {
            if (fileName == null || !fileName.contains("_")) {
                log.warn("文件名格式不正确，无法解析元数据: {}", fileName);
                return;
            }
            
            log.info("开始解析文件名元数据: {}", fileName);
            
            // 移除扩展名
            String nameWithoutExt = fileName;
            if (fileName.contains(".")) {
                nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
            }
            
            // 按下划线分割
            String[] parts = nameWithoutExt.split("_");
            
            // 标准格式至少包含5个部分
            if (parts.length >= 5) {
                // 解析录音日期
                if (parts.length > 1 && parts[1].length() == 8) {
                    try {
                        String dateStr = parts[1];
                        LocalDate recordDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                        fileInfo.setRecordDate(recordDate);
                        log.info("解析到录音日期: {}", recordDate);
                    } catch (Exception e) {
                        log.warn("解析录音日期失败: {}", e.getMessage());
                    }
                }
                
                // 解析用户ID (通常是第3个部分)
                if (parts.length > 2) {
                    String userId = parts[2];
                    fileInfo.setUserId(userId);
                    log.info("解析到用户ID: {}", userId);
                }
                
                // 解析录音开始时间 (通常是第4个部分)
                if (parts.length > 3) {
                    try {
                        long timestamp = Long.parseLong(parts[3]);
                        LocalDateTime recordStartTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(timestamp), 
                            ZoneId.systemDefault());
                        fileInfo.setRecordStartTime(recordStartTime);
                        log.info("解析到录音开始时间: {}", recordStartTime);
                    } catch (Exception e) {
                        log.warn("解析录音开始时间失败: {}", e.getMessage());
                    }
                }
                
                // 解析录音时长 (通常是第5个部分)
                if (parts.length > 4) {
                    try {
                        long duration = Long.parseLong(parts[4]);
                        fileInfo.setRecordDuration(duration);
                        log.info("解析到录音时长: {} 毫秒", duration);
                    } catch (Exception e) {
                        log.warn("解析录音时长失败: {}", e.getMessage());
                    }
                }
            } else {
                log.warn("文件名不符合标准格式，无法完全解析元数据: {}", fileName);
            }
        } catch (Exception e) {
            log.error("解析文件元数据失败: {}", e.getMessage());
        }
    }
} 