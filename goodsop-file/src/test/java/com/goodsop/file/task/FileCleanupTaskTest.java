package com.goodsop.file.task;

import com.goodsop.file.config.FileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 文件清理任务单元测试
 */
class FileCleanupTaskTest {

    @Mock
    private FileProperties fileProperties;
    
    @Mock
    private FileProperties.Storage storage;
    
    @Mock
    private FileProperties.Cleanup cleanup;
    
    @InjectMocks
    private FileCleanupTask fileCleanupTask;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(fileProperties.getStorage()).thenReturn(storage);
        when(fileProperties.getCleanup()).thenReturn(cleanup);
        when(cleanup.getEnabled()).thenReturn(true);
        when(cleanup.getTempFileMaxAge()).thenReturn(24); // 24小时
    }
    
    @Test
    void shouldCleanupExpiredTempFiles() throws IOException {
        // 创建测试目录结构
        Path baseDirPath = tempDir;
        String basePath = baseDirPath.toString();
        when(storage.getPath()).thenReturn(basePath);
        
        // 创建日期目录
        Path dateDirPath = baseDirPath.resolve("20250506");
        Files.createDirectories(dateDirPath);
        
        // 创建temp目录
        Path tempDirPath = dateDirPath.resolve("temp");
        Files.createDirectories(tempDirPath);
        
        // 创建一个过期的上传目录（25小时前）
        Path expiredDirPath = tempDirPath.resolve("expired_file");
        Files.createDirectories(expiredDirPath);
        
        // 创建一些测试文件
        Path expiredFile1 = expiredDirPath.resolve("0.part");
        Path expiredFile2 = expiredDirPath.resolve("1.part");
        
        Files.write(expiredFile1, "test data 1".getBytes(), StandardOpenOption.CREATE);
        Files.write(expiredFile2, "test data 2".getBytes(), StandardOpenOption.CREATE);
        
        // 设置文件的最后修改时间为25小时前
        Instant expired = Instant.now().minus(25, ChronoUnit.HOURS);
        Files.setLastModifiedTime(expiredFile1, FileTime.from(expired));
        Files.setLastModifiedTime(expiredFile2, FileTime.from(expired));
        Files.setLastModifiedTime(expiredDirPath, FileTime.from(expired));
        
        // 创建一个新的上传目录（1小时前）
        Path newDirPath = tempDirPath.resolve("new_file");
        Files.createDirectories(newDirPath);
        
        // 创建一些测试文件
        Path newFile = newDirPath.resolve("0.part");
        Files.write(newFile, "test data".getBytes(), StandardOpenOption.CREATE);
        
        // 设置文件的最后修改时间为1小时前
        Instant recent = Instant.now().minus(1, ChronoUnit.HOURS);
        Files.setLastModifiedTime(newFile, FileTime.from(recent));
        Files.setLastModifiedTime(newDirPath, FileTime.from(recent));
        
        // 执行清理任务
        fileCleanupTask.cleanupTempFiles();
        
        // 验证过期目录被删除
        assertFalse(Files.exists(expiredDirPath), "过期目录应该被删除");
        assertFalse(Files.exists(expiredFile1), "过期文件1应该被删除");
        assertFalse(Files.exists(expiredFile2), "过期文件2应该被删除");
        
        // 验证新目录仍然存在
        assertTrue(Files.exists(newDirPath), "新目录应该保留");
        assertTrue(Files.exists(newFile), "新文件应该保留");
    }
} 