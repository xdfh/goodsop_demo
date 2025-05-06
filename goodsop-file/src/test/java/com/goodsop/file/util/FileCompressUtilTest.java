package com.goodsop.file.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        // 创建一个较大的测试文件
        StringBuilder testContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            testContent.append("这是测试内容，用于测试文件压缩和解压功能。这段文字会重复很多次以确保文件足够大，可以被有效压缩。");
        }
        
        File sourceFile = tempDir.resolve("test.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(testContent.toString().getBytes(StandardCharsets.UTF_8));
        }
        
        // 压缩文件
        File compressedFile = tempDir.resolve("test.txt.gz").toFile();
        File result = fileCompressUtil.compressFile(sourceFile, compressedFile);
        
        // 断言压缩文件存在且不为空
        assertTrue(result.exists(), "压缩文件应该存在");
        assertTrue(result.length() > 0, "压缩文件不应该为空");
        assertTrue(result.length() < sourceFile.length(), "压缩后的文件应该比原文件小");
        
        // 检查是否为GZIP格式
        assertTrue(fileCompressUtil.isGzipFile(compressedFile), "压缩文件应该是GZIP格式");
        assertFalse(fileCompressUtil.isGzipFile(sourceFile), "源文件不应该是GZIP格式");
        
        // 解压文件
        File decompressedFile = tempDir.resolve("test_decompressed.txt").toFile();
        File decompressResult = fileCompressUtil.decompressFile(compressedFile, decompressedFile);
        
        // 断言解压后的文件存在且内容与原始一致
        assertNotNull(decompressResult, "解压文件结果不应为null");
        assertTrue(decompressResult.exists(), "解压后的文件应该存在");
        assertEquals(sourceFile.length(), decompressResult.length(), "解压后的文件大小应该与原文件相同");
        
        // 检查解压后的内容是否与原始内容一致
        String decompressedContent = new String(Files.readAllBytes(decompressResult.toPath()), StandardCharsets.UTF_8);
        assertEquals(testContent.toString(), decompressedContent, "解压后的内容应该与原始内容一致");
    }
    
    @Test
    void decompressNonCompressedFile() throws IOException {
        // 创建一个普通文本文件
        String testContent = "这是一个非压缩的普通文本文件";
        File plainFile = tempDir.resolve("plain.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(plainFile)) {
            fos.write(testContent.getBytes(StandardCharsets.UTF_8));
        }
        
        // 尝试解压非压缩文件
        File targetFile = tempDir.resolve("plain_decompressed.txt").toFile();
        File result = fileCompressUtil.decompressFile(plainFile, targetFile);
        
        // 断言返回结果不为null，并且文件内容与原始内容相同
        assertNotNull(result, "解压非压缩文件的结果不应为null");
        assertTrue(result.exists(), "解压结果文件应该存在");
        assertEquals(
            testContent,
            new String(Files.readAllBytes(result.toPath()), StandardCharsets.UTF_8),
            "非压缩文件'解压'后内容应与原始内容相同"
        );
    }
} 