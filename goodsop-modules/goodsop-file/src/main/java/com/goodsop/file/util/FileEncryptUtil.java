package com.goodsop.file.util;

import com.goodsop.file.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 文件加解密工具类
 */
@Slf4j
@Component
public class FileEncryptUtil {

    /**
     * AES加密文件
     * 
     * @param sourceFile 源文件
     * @param targetFile 目标文件
     * @param key        加密密钥
     * @return 加密后的文件
     */
    public File encryptFile(File sourceFile, File targetFile, String key) {
        try {
            SecretKey secretKey = generateKey(key);
            Cipher cipher = Cipher.getInstance(FileConstant.TRANSFORMATION_AES);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            try (FileInputStream fis = new FileInputStream(sourceFile);
                 FileOutputStream fos = new FileOutputStream(targetFile);
                 CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    cos.write(buffer, 0, len);
                }
                log.info("文件加密成功: {} -> {}", sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
                return targetFile;
            }
        } catch (Exception e) {
            log.error("文件加密失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件加密失败", e);
        }
    }
    
    /**
     * AES解密文件
     * 
     * @param sourceFile 源文件
     * @param targetFile 目标文件
     * @param key        解密密钥
     * @return 解密后的文件
     */
    public File decryptFile(File sourceFile, File targetFile, String key) {
        try {
            SecretKey secretKey = generateKey(key);
            Cipher cipher = Cipher.getInstance(FileConstant.TRANSFORMATION_AES);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            try (FileInputStream fis = new FileInputStream(sourceFile);
                 CipherInputStream cis = new CipherInputStream(fis, cipher);
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                
                byte[] buffer = new byte[1024];
                int len;
                while ((len = cis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                log.info("文件解密成功: {} -> {}", sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
                return targetFile;
            }
        } catch (Exception e) {
            log.error("文件解密失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件解密失败", e);
        }
    }
    
    /**
     * 生成AES密钥
     * 
     * @param key 密钥字符串
     * @return SecretKey
     */
    private SecretKey generateKey(String key) throws NoSuchAlgorithmException {
        // 确保密钥长度为16字节(128位)或32字节(256位)
        byte[] keyBytes;
        
        if (key.length() == 16 || key.length() == 32) {
            keyBytes = key.getBytes(StandardCharsets.UTF_8);
        } else {
            // 如果密钥不是16或32字节，使用MD5或SHA-256进行处理
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
            // 截取为16字节(128位)
            byte[] truncatedBytes = new byte[16];
            System.arraycopy(keyBytes, 0, truncatedBytes, 0, truncatedBytes.length);
            keyBytes = truncatedBytes;
        }
        
        return new SecretKeySpec(keyBytes, FileConstant.ALGORITHM_AES);
    }
    
    /**
     * 计算文件的MD5值
     * 
     * @param file 文件
     * @return MD5值
     */
    public String calculateMD5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) != -1) {
                md.update(buffer, 0, length);
            }
            
            byte[] mdBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte mdByte : mdBytes) {
                sb.append(String.format("%02x", mdByte));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("计算文件MD5失败: {}", e.getMessage(), e);
            throw new RuntimeException("计算文件MD5失败", e);
        }
    }
    
    /**
     * Base64编码
     * 
     * @param data 原始数据
     * @return 编码后的字符串
     */
    public String encodeBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
    
    /**
     * Base64解码
     * 
     * @param base64String 编码字符串
     * @return 解码后的数据
     */
    public byte[] decodeBase64(String base64String) {
        return Base64.getDecoder().decode(base64String);
    }
} 