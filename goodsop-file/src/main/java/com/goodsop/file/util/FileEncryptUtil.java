package com.goodsop.file.util;

import com.goodsop.file.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;
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
            log.info("准备加密文件 - 源文件: {}, 目标文件: {}, 密钥长度: {}", 
                    sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), key.length());
            
            if (!sourceFile.exists()) {
                log.error("源文件不存在: {}", sourceFile.getAbsolutePath());
                return null;
            }
            
            // 确保目标文件目录存在
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                log.info("创建目标文件目录: {} 结果: {}", parentDir.getAbsolutePath(), created ? "成功" : "失败");
            }
            
            // 生成随机IV
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[FileConstant.IV_SIZE];
            random.nextBytes(iv);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            log.info("生成随机IV，长度: {} 字节", iv.length);
            
            SecretKey secretKey = generateKey(key);
            Cipher cipher = Cipher.getInstance(FileConstant.TRANSFORMATION_AES);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
            
            try (FileInputStream fis = new FileInputStream(sourceFile);
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                
                // 先写入IV，再写入加密数据
                fos.write(iv);
                
                try (CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        cos.write(buffer, 0, len);
                    }
                }
                
                log.info("文件加密成功: {} -> {}", sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
                return targetFile;
            } catch (IOException e) {
                log.error("加密文件IO异常: {}", e.getMessage(), e);
                // 检查目标文件是否已创建，如果已创建但可能不完整，则删除
                if (targetFile.exists()) {
                    targetFile.delete();
                    log.info("删除加密失败的目标文件: {}", targetFile.getAbsolutePath());
                }
                return null;
            }
        } catch (Exception e) {
            log.error("文件加密失败: {}", e.getMessage(), e);
            // 检查目标文件是否已创建，如果已创建但可能不完整，则删除
            if (targetFile.exists()) {
                targetFile.delete();
                log.info("删除加密失败的目标文件: {}", targetFile.getAbsolutePath());
            }
            return null;
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
            log.info("准备解密文件 - 源文件: {}, 目标文件: {}", 
                    sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
            
            if (!sourceFile.exists()) {
                log.error("源文件不存在: {}", sourceFile.getAbsolutePath());
                return null;
            }
            
            // 确保目标文件目录存在
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                log.info("创建目标文件目录: {} 结果: {}", parentDir.getAbsolutePath(), created ? "成功" : "失败");
            }
            
            // 检查源文件格式
            if (!isEncryptedFile(sourceFile)) {
                log.warn("源文件可能不是加密文件: {}", sourceFile.getAbsolutePath());
                try {
                    FileUtils.copyFile(sourceFile, targetFile);
                    return targetFile;
                } catch (IOException e) {
                    log.error("复制文件失败: {} -> {}, 错误: {}", 
                             sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), e.getMessage());
                    return null;
                }
            }
            
            // 准备密钥
            log.info("AES密钥长度: {} 字符", key.length());
            
            // 确保密钥长度为32字节，用于AES-256
            SecretKey secretKey;
            if (key.length() == 32) {
                // 使用UTF-8编码确保与加密时相同的字节序列
                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                secretKey = new SecretKeySpec(keyBytes, FileConstant.ALGORITHM_AES);
                log.info("使用原始AES-256密钥，长度: {} 字节", keyBytes.length);
            } else {
                log.warn("密钥长度不是32字节，将使用固定的AES密钥");
                // 使用FileConstant中定义的标准密钥
                byte[] keyBytes = FileConstant.AES_KEY.getBytes(StandardCharsets.UTF_8);
                secretKey = new SecretKeySpec(keyBytes, FileConstant.ALGORITHM_AES);
            }
            
            try (FileInputStream fis = new FileInputStream(sourceFile);
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                
                // 读取IV（前16字节）
                byte[] iv = new byte[FileConstant.IV_SIZE];
                int bytesRead = fis.read(iv);
                
                if (bytesRead != FileConstant.IV_SIZE) {
                    log.error("无法读取完整的IV，可能不是加密文件");
                    try {
                        // 如果无法读取IV，尝试直接复制文件
                        fis.close();
                        FileUtils.copyFile(sourceFile, targetFile);
                        log.info("无法读取IV，直接复制文件: {} -> {}", 
                                sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
                        return targetFile;
                    } catch (IOException e) {
                        log.error("复制文件失败: {}", e.getMessage(), e);
                        return null;
                    }
                }
                
                IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
                log.info("读取加密文件IV，长度: {} 字节", iv.length);
                
                // 初始化Cipher
                Cipher cipher = Cipher.getInstance(FileConstant.TRANSFORMATION_AES);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
                log.info("初始化Cipher成功，模式: {}", FileConstant.TRANSFORMATION_AES);
                
                // 开始解密
                log.info("开始解密文件流...");
                
                try (CipherInputStream cis = new CipherInputStream(fis, cipher)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = cis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                
                log.info("文件解密完成: {} -> {}, 大小: {}", 
                        sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), targetFile.length());
                return targetFile;
                
            } catch (IOException e) {
                log.error("解密文件IO异常: {}", e.getMessage(), e);
                // 尝试直接复制文件
                try {
                    log.info("由于解密失败，直接复制原始文件: {} -> {}", 
                            sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
                    FileUtils.copyFile(sourceFile, targetFile);
                    return targetFile;
                } catch (IOException copyEx) {
                    log.error("复制文件失败: {}", copyEx.getMessage(), copyEx);
                    if (targetFile.exists()) {
                        targetFile.delete();
                        log.info("删除解密失败的目标文件: {}", targetFile.getAbsolutePath());
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("文件解密失败: {}", e.getMessage(), e);
            // 检查目标文件是否已创建，如果已创建但可能不完整，则删除
            if (targetFile.exists()) {
                try {
                    targetFile.delete();
                    log.info("删除解密失败的目标文件: {}", targetFile.getAbsolutePath());
                } catch (Exception ex) {
                    log.error("删除目标文件失败: {}", ex.getMessage());
                }
            }
            
            // 尝试直接复制文件
            try {
                log.info("由于异常解密失败，直接复制原始文件: {} -> {}", 
                        sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
                FileUtils.copyFile(sourceFile, targetFile);
                return targetFile;
            } catch (IOException copyEx) {
                log.error("复制文件失败: {}", copyEx.getMessage(), copyEx);
                return null;
            }
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
        
        if (key == null || key.isEmpty()) {
            log.error("AES密钥不能为空");
            throw new IllegalArgumentException("AES密钥不能为空");
        }
        
        log.info("AES密钥长度: {} 字符", key.length());
        
        if (key.length() == 16 || key.length() == 32) {
            keyBytes = key.getBytes(StandardCharsets.UTF_8);
            log.info("使用原始密钥，长度正好: {} 字节", keyBytes.length);
        } else {
            // 如果密钥不是16或32字节，使用SHA-256进行处理
            log.info("密钥长度不标准，将使用哈希处理: {} -> 32字节", key.length());
            
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
            
            // 对于AES-256，使用全部32字节
            if (keyBytes.length > 32) {
                byte[] truncatedBytes = new byte[32];
                System.arraycopy(keyBytes, 0, truncatedBytes, 0, truncatedBytes.length);
                keyBytes = truncatedBytes;
            }
            
            log.info("生成的AES密钥: {} 字节", keyBytes.length);
        }
        
        SecretKey secretKey = new SecretKeySpec(keyBytes, FileConstant.ALGORITHM_AES);
        log.info("AES密钥生成成功，算法: {}", secretKey.getAlgorithm());
        return secretKey;
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
     * 计算字符串的MD5值
     * 
     * @param text 文本字符串
     * @return MD5值
     */
    public String calculateMD5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] mdBytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte mdByte : mdBytes) {
                sb.append(String.format("%02x", mdByte));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("计算字符串MD5失败: {}", e.getMessage(), e);
            throw new RuntimeException("计算字符串MD5失败", e);
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
    
    /**
     * 判断文件是否为加密文件
     * 
     * @param file 要检查的文件
     * @return 是否为加密文件
     */
    private boolean isEncryptedFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        // 检查文件名是否包含加密后缀
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(FileConstant.ENCRYPTED_FILE_SUFFIX);
    }
    
    /**
     * 计算数据的随机性得分 (0-10)，用于评估是否为加密数据
     * 
     * @param data 字节数组
     * @return 随机性得分
     */
    private int calculateRandomnessScore(byte[] data) {
        if (data == null || data.length == 0) {
            return 0;
        }
        
        // 计算字节分布
        int[] byteCounts = new int[256];
        for (byte b : data) {
            byteCounts[b & 0xFF]++;
        }
        
        // 计算出现的不同字节数
        int differentBytes = 0;
        for (int count : byteCounts) {
            if (count > 0) {
                differentBytes++;
            }
        }
        
        // 加密数据通常有更多不同的字节值
        return Math.min(10, (differentBytes * 10) / Math.min(data.length, 128));
    }
} 