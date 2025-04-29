package com.goodsop.file.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件传输工具类单元测试
 */
class FileTransferUtilTest {
    
    private FileTransferUtil fileTransferUtil;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        fileTransferUtil = new FileTransferUtil();
    }
    
    @Test
    void storeFile() throws IOException {
        // 创建测试文件数据
        String testContent = "这是测试内容，用于测试文件存储功能";
        MockMultipartFile mockFile = new MockMultipartFile(
            "testFile", 
            "test.txt", 
            "text/plain", 
            testContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 存储文件
        File storedFile = fileTransferUtil.storeFile(mockFile, tempDir.toString());
        
        // 断言文件存在且内容正确
        assertTrue(storedFile.exists());
        assertEquals(testContent, new String(Files.readAllBytes(storedFile.toPath()), StandardCharsets.UTF_8));
    }
    
    @Test
    void storeFileByDate() throws IOException {
        // 创建测试文件数据
        String testContent = "这是测试内容，用于测试按日期存储文件功能";
        MockMultipartFile mockFile = new MockMultipartFile(
            "testFile", 
            "test.txt", 
            "text/plain", 
            testContent.getBytes(StandardCharsets.UTF_8)
        );
        
        // 存储文件
        File storedFile = fileTransferUtil.storeFileByDate(mockFile, tempDir.toString(), "yyyyMMdd");
        
        // 断言文件存在且内容正确
        assertTrue(storedFile.exists());
        assertEquals(testContent, new String(Files.readAllBytes(storedFile.toPath()), StandardCharsets.UTF_8));
    }
    
    @Test
    void storeFileChunk() throws IOException {
        // 创建测试分块数据
        String chunk1Content = "这是第一块数据";
        String chunk2Content = "这是第二块数据";
        
        MockMultipartFile mockChunk1 = new MockMultipartFile(
            "chunk1", 
            "chunk1.part", 
            "application/octet-stream", 
            chunk1Content.getBytes(StandardCharsets.UTF_8)
        );
        
        MockMultipartFile mockChunk2 = new MockMultipartFile(
            "chunk2", 
            "chunk2.part", 
            "application/octet-stream", 
            chunk2Content.getBytes(StandardCharsets.UTF_8)
        );
        
        // 存储第一块（非最后一块）
        File result1 = fileTransferUtil.storeFileChunk(mockChunk1, tempDir.toString(), "merged.txt", 0, 2);
        assertNull(result1); // 非最后一块应该返回null
        
        // 存储第二块（最后一块）
        File result2 = fileTransferUtil.storeFileChunk(mockChunk2, tempDir.toString(), "merged.txt", 1, 2);
        assertNotNull(result2); // 最后一块应该返回合并后的文件
        
        // 断言文件存在且内容是两块合并的内容
        assertTrue(result2.exists());
        String expectedContent = chunk1Content + chunk2Content;
        assertEquals(expectedContent, new String(Files.readAllBytes(result2.toPath()), StandardCharsets.UTF_8));
    }
    
    @Test
    void setDownloadResponseHeaders() {
        // 创建模拟响应
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // 设置响应头
        fileTransferUtil.setDownloadResponseHeaders(response, "测试文件.txt", 100);
        
        // 断言响应头设置正确
        assertEquals("application/octet-stream", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("attachment"));
        assertEquals("100", response.getHeader("Content-Length"));
    }
    
    @Test
    void downloadWithRange() throws IOException {
        // 创建测试文件
        String testContent = "这是一个测试文件，用于测试断点续传下载功能。";
        File testFile = tempDir.resolve("download_test.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            fos.write(testContent.getBytes(StandardCharsets.UTF_8));
        }
        
        // 创建模拟请求和响应
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // 测试完整下载
        fileTransferUtil.downloadWithRange(request, response, testFile, "download_test.txt");
        
        // 断言响应内容正确
        assertEquals(testContent, response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals("bytes", response.getHeader("Accept-Ranges"));
        
        // 测试范围下载
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.addHeader("Range", "bytes=0-5"); // 只请求前6个字节
        
        fileTransferUtil.downloadWithRange(request, response, testFile, "download_test.txt");
        
        // 断言响应内容是前6个字节
        assertEquals(testContent.substring(0, 6), response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(206, response.getStatus()); // 206 Partial Content
    }
} 