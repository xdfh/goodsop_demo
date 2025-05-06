package com.goodsop.file.task;

import com.goodsop.file.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 文件清理定时任务
 * 用于清理过期的临时文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupTask {

    private final FileProperties fileProperties;
    
    /**
     * 清理长时间未更新的临时文件
     * 使用配置的cron表达式执行
     */
    @Scheduled(cron = "${goodsop.file.cleanup.cron:0 0 1 * * ?}")
    public void cleanupTempFiles() {
        // 如果未启用清理功能，则直接返回
        if (!fileProperties.getCleanup().getEnabled()) {
            log.info("文件清理任务未启用，跳过执行");
            return;
        }
        
        log.info("开始执行临时文件清理任务...");
        
        try {
            // 获取文件存储根目录
            String basePath = fileProperties.getStorage().getPath();
            if (!basePath.endsWith(File.separator)) {
                basePath = basePath + File.separator;
            }
            
            // 获取当前时间和过期时间点
            long currentTimeMillis = System.currentTimeMillis();
            long maxAgeMillis = fileProperties.getCleanup().getTempFileMaxAge() * 60 * 60 * 1000L; // 转换为毫秒
            long expirationTimeMillis = currentTimeMillis - maxAgeMillis;
            
            LocalDateTime expirationDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(expirationTimeMillis), 
                    ZoneId.systemDefault());
                    
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            log.info("清理{}之前未更新的临时文件", expirationDateTime.format(formatter));
            
            // 查找所有日期目录
            File baseDir = new File(basePath);
            if (!baseDir.exists() || !baseDir.isDirectory()) {
                log.warn("文件存储根目录不存在: {}", basePath);
                return;
            }
            
            File[] dateDirs = baseDir.listFiles(File::isDirectory);
            if (dateDirs == null || dateDirs.length == 0) {
                log.info("未找到日期目录，无需清理");
                return;
            }
            
            // 统计删除的文件和目录数量
            AtomicInteger deletedFileCount = new AtomicInteger(0);
            AtomicInteger deletedDirCount = new AtomicInteger(0);
            
            // 处理每个日期目录
            for (File dateDir : dateDirs) {
                // 查找temp目录
                File tempDir = new File(dateDir, "temp");
                if (!tempDir.exists() || !tempDir.isDirectory()) {
                    continue;
                }
                
                log.info("处理临时目录: {}", tempDir.getAbsolutePath());
                
                // 获取临时目录中的所有文件夹（每个代表一个文件的分块目录）
                File[] uploadDirs = tempDir.listFiles(File::isDirectory);
                if (uploadDirs == null || uploadDirs.length == 0) {
                    continue;
                }
                
                // 检查每个上传目录的最后修改时间
                for (File uploadDir : uploadDirs) {
                    long lastModified = getLatestModificationTime(uploadDir);
                    
                    // 如果目录的最后修改时间早于过期时间，则删除该目录
                    if (lastModified < expirationTimeMillis) {
                        LocalDateTime modifiedTime = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(lastModified), 
                                ZoneId.systemDefault());
                                
                        log.info("删除过期目录: {}, 最后修改时间: {}", 
                                uploadDir.getAbsolutePath(), 
                                modifiedTime.format(formatter));
                        
                        // 删除目录及其内容
                        int filesDeleted = deleteDirectory(uploadDir);
                        deletedFileCount.addAndGet(filesDeleted);
                        deletedDirCount.incrementAndGet();
                    }
                }
                
                // 如果temp目录为空，也可以删除
                if (tempDir.list() != null && tempDir.list().length == 0) {
                    log.info("删除空的临时目录: {}", tempDir.getAbsolutePath());
                    tempDir.delete();
                    deletedDirCount.incrementAndGet();
                }
            }
            
            log.info("临时文件清理完成 - 删除了{}个文件和{}个目录", 
                    deletedFileCount.get(), deletedDirCount.get());
            
        } catch (Exception e) {
            log.error("执行临时文件清理任务时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取目录中最新的修改时间
     * 
     * @param directory 目录
     * @return 最新的修改时间（毫秒）
     */
    private long getLatestModificationTime(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return directory.lastModified();
        }
        
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            return directory.lastModified();
        }
        
        // 查找目录中所有文件和子目录的最后修改时间
        long latestTime = directory.lastModified();
        
        for (File file : files) {
            long fileTime;
            if (file.isDirectory()) {
                fileTime = getLatestModificationTime(file);
            } else {
                fileTime = file.lastModified();
            }
            
            if (fileTime > latestTime) {
                latestTime = fileTime;
            }
        }
        
        return latestTime;
    }
    
    /**
     * 递归删除目录及其内容
     * 
     * @param directory 要删除的目录
     * @return 删除的文件数量
     */
    private int deleteDirectory(File directory) {
        AtomicInteger fileCount = new AtomicInteger(0);
        
        if (!directory.exists()) {
            return 0;
        }
        
        try {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        fileCount.addAndGet(deleteDirectory(file));
                    } else {
                        if (file.delete()) {
                            fileCount.incrementAndGet();
                            log.debug("删除文件: {}", file.getAbsolutePath());
                        } else {
                            log.warn("无法删除文件: {}", file.getAbsolutePath());
                        }
                    }
                }
            }
            
            // 最后删除目录本身
            if (directory.delete()) {
                log.debug("删除目录: {}", directory.getAbsolutePath());
            } else {
                log.warn("无法删除目录: {}", directory.getAbsolutePath());
            }
            
        } catch (Exception e) {
            log.error("删除目录时发生错误: {}, 错误: {}", directory.getAbsolutePath(), e.getMessage());
        }
        
        return fileCount.get();
    }
} 