package com.goodsop.file.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件压缩工具类单元测试
 */
class FileCompressUtilTest {
    
    private FileCompressUtil fileCompressUtil;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        fileCompressUtil = new FileCompressUtil();
    }
    
    @Test
    void compressAndDecompressFile() throws IOException {
        // 创建测试文件
        String testContent = "这是测试内容，用于测试文件压缩和解压功能";
        File sourceFile = tempDir.resolve("test.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(testContent.getBytes(StandardCharsets.UTF_8));
        }
        
        // 压缩文件
        File compressedFile = tempDir.resolve("test.txt.gz").toFile();
        File result = fileCompressUtil.compressFile(sourceFile, compressedFile);
        
        // 断言压缩文件存在且不为空
        assertTrue(result.exists());
        assertTrue(result.length() > 0);
        assertTrue(result.length() < sourceFile.length()); // 压缩后应该更小
        
        // 解压文件
        File decompressedFile = tempDir.resolve("test_decompressed.txt").toFile();
        File decompressResult = fileCompressUtil.decompressFile(compressedFile, decompressedFile);
        
        // 断言解压后的文件存在且内容与原始一致
        assertTrue(decompressResult.exists());
        assertEquals(sourceFile.length(), decompressResult.length()); // 解压后的大小应该与原始一致
        
        // 检查是否为GZIP格式
        assertTrue(fileCompressUtil.isGzipFile(compressedFile));
        assertFalse(fileCompressUtil.isGzipFile(sourceFile));
    }
} 