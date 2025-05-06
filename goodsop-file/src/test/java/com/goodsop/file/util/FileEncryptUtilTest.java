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
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件加密工具类单元测试
 */
class FileEncryptUtilTest {
    
    private FileEncryptUtil fileEncryptUtil;
    
    @TempDir
    Path tempDir;
    
    private final String testKey = "1234567890abcdef1234567890abcdef"; // 32字节AES-256密钥
    
    @BeforeEach
    void setUp() {
        fileEncryptUtil = new FileEncryptUtil();
    }
    
    @Test
    void encryptAndDecryptFile() throws IOException {
        // 创建测试文件
        String testContent = "这是测试内容，用于测试文件加密和解密功能";
        File sourceFile = tempDir.resolve("test.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(testContent.getBytes(StandardCharsets.UTF_8));
        }
        
        // 加密文件
        File encryptedFile = tempDir.resolve("test.txt.enc").toFile();
        File encryptResult = fileEncryptUtil.encryptFile(sourceFile, encryptedFile, testKey);
        
        // 断言加密文件存在且内容与原始不同
        assertTrue(encryptResult.exists());
        assertTrue(encryptResult.length() > 0);
        assertNotEquals(
            new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8),
            new String(Files.readAllBytes(encryptedFile.toPath()), StandardCharsets.UTF_8)
        );
        
        // 解密文件
        File decryptedFile = tempDir.resolve("test_decrypted.txt").toFile();
        File decryptResult = fileEncryptUtil.decryptFile(encryptedFile, decryptedFile, testKey);
        
        // 断言解密后的文件存在且内容与原始一致
        assertTrue(decryptResult.exists());
        assertEquals(
            new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8),
            new String(Files.readAllBytes(decryptedFile.toPath()), StandardCharsets.UTF_8)
        );
    }
    
    @Test
    void encryptAndDecryptLargeFile() throws IOException {
        // 创建大文件 (1MB)
        int fileSize = 1024 * 1024;
        byte[] randomData = new byte[fileSize];
        SecureRandom random = new SecureRandom();
        random.nextBytes(randomData);
        
        File sourceFile = tempDir.resolve("large_test.bin").toFile();
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(randomData);
        }
        
        // 加密文件
        File encryptedFile = tempDir.resolve("large_test.bin.enc").toFile();
        File encryptResult = fileEncryptUtil.encryptFile(sourceFile, encryptedFile, testKey);
        
        // 断言加密文件存在且内容与原始不同
        assertTrue(encryptResult.exists());
        assertTrue(encryptResult.length() > 0);
        assertNotEquals(
            Files.readAllBytes(sourceFile.toPath()),
            Files.readAllBytes(encryptedFile.toPath())
        );
        
        // 解密文件
        File decryptedFile = tempDir.resolve("large_test_decrypted.bin").toFile();
        File decryptResult = fileEncryptUtil.decryptFile(encryptedFile, decryptedFile, testKey);
        
        // 断言解密后的文件存在且内容与原始一致
        assertTrue(decryptResult.exists());
        assertArrayEquals(
            Files.readAllBytes(sourceFile.toPath()),
            Files.readAllBytes(decryptedFile.toPath())
        );
    }
    
    @Test
    void encryptWithDifferentIVs() throws IOException {
        // 创建测试文件
        String testContent = "这是测试内容，用于测试不同IV产生不同密文";
        File sourceFile = tempDir.resolve("test_iv.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(testContent.getBytes(StandardCharsets.UTF_8));
        }
        
        // 使用相同密钥进行两次加密
        File encryptedFile1 = tempDir.resolve("test_iv_1.enc").toFile();
        File encryptedFile2 = tempDir.resolve("test_iv_2.enc").toFile();
        
        fileEncryptUtil.encryptFile(sourceFile, encryptedFile1, testKey);
        fileEncryptUtil.encryptFile(sourceFile, encryptedFile2, testKey);
        
        // 断言两次加密结果不同（因为随机IV）
        byte[] encrypted1 = Files.readAllBytes(encryptedFile1.toPath());
        byte[] encrypted2 = Files.readAllBytes(encryptedFile2.toPath());
        
        assertNotEquals(
            new String(encrypted1, StandardCharsets.ISO_8859_1),
            new String(encrypted2, StandardCharsets.ISO_8859_1)
        );
        
        // 但解密结果应该相同
        File decryptedFile1 = tempDir.resolve("test_iv_dec_1.txt").toFile();
        File decryptedFile2 = tempDir.resolve("test_iv_dec_2.txt").toFile();
        
        fileEncryptUtil.decryptFile(encryptedFile1, decryptedFile1, testKey);
        fileEncryptUtil.decryptFile(encryptedFile2, decryptedFile2, testKey);
        
        assertEquals(
            new String(Files.readAllBytes(decryptedFile1.toPath()), StandardCharsets.UTF_8),
            new String(Files.readAllBytes(decryptedFile2.toPath()), StandardCharsets.UTF_8)
        );
    }
    
    @Test
    void calculateMD5() throws IOException {
        // 创建测试文件
        String testContent = "这是测试内容，用于测试MD5计算功能";
        File sourceFile = tempDir.resolve("test_md5.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(sourceFile)) {
            fos.write(testContent.getBytes(StandardCharsets.UTF_8));
        }
        
        // 计算MD5
        String md5 = fileEncryptUtil.calculateMD5(sourceFile);
        
        // 断言MD5不为空且格式正确（32个十六进制字符）
        assertNotNull(md5);
        assertEquals(32, md5.length());
        assertTrue(md5.matches("[0-9a-f]{32}"));
    }
    
    @Test
    void encodeAndDecodeBase64() {
        // 测试数据
        String testString = "Hello, World!";
        byte[] testData = testString.getBytes(StandardCharsets.UTF_8);
        
        // 编码
        String base64String = fileEncryptUtil.encodeBase64(testData);
        
        // 解码
        byte[] decodedData = fileEncryptUtil.decodeBase64(base64String);
        
        // 断言解码后与原始数据一致
        assertEquals(testString, new String(decodedData, StandardCharsets.UTF_8));
    }
} 