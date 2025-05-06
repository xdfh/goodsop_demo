package com.goodsop.file.util;

import com.goodsop.file.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
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
            log.info("准备解密文件 - 源文件: {}, 目标文件: {}, 密钥长度: {}", 
                    sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), key.length());
            
            // 确保源文件存在且可读
            if (!sourceFile.exists()) {
                throw new RuntimeException("源文件不存在: " + sourceFile.getAbsolutePath());
            }
            if (!sourceFile.canRead()) {
                throw new RuntimeException("源文件不可读: " + sourceFile.getAbsolutePath());
            }
            
            // 确保目标文件目录存在
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                log.info("创建目标文件目录: {} 结果: {}", parentDir.getAbsolutePath(), created ? "成功" : "失败");
            }
            
            // 生成AES密钥
            try {
                SecretKey secretKey = generateKey(key);
                log.info("成功生成AES密钥，算法: {}", secretKey.getAlgorithm());
                
                Cipher cipher = Cipher.getInstance(FileConstant.TRANSFORMATION_AES);
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                log.info("初始化Cipher成功，模式: {}", FileConstant.TRANSFORMATION_AES);
                
                try (FileInputStream fis = new FileInputStream(sourceFile);
                     CipherInputStream cis = new CipherInputStream(fis, cipher);
                     FileOutputStream fos = new FileOutputStream(targetFile)) {
                    
                    byte[] buffer = new byte[1024];
                    int len;
                    long totalBytes = 0;
                    
                    log.info("开始解密文件流...");
                    while ((len = cis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                        totalBytes += len;
                    }
                    
                    fos.flush();
                    log.info("文件解密成功: {} -> {}, 解密后文件大小: {}", 
                            sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), totalBytes);
                    
                    // 验证解密结果
                    if (targetFile.exists() && targetFile.length() > 0) {
                        log.info("解密文件验证成功: 文件大小 = {}", targetFile.length());
                    } else {
                        log.warn("解密文件异常: 文件大小为0或文件不存在");
                    }
                    
                    return targetFile;
                } catch (IOException e) {
                    log.error("解密文件IO异常: {}", e.getMessage(), e);
                    throw new RuntimeException("解密文件IO异常", e);
                }
            } catch (NoSuchAlgorithmException e) {
                log.error("找不到加密算法: {}", e.getMessage(), e);
                throw new RuntimeException("找不到加密算法", e);
            } catch (NoSuchPaddingException e) {
                log.error("找不到填充方式: {}", e.getMessage(), e);
                throw new RuntimeException("找不到填充方式", e);
            } catch (InvalidKeyException e) {
                log.error("AES密钥无效: {}", e.getMessage(), e);
                throw new RuntimeException("AES密钥无效", e);
            }
        } catch (Exception e) {
            log.error("文件解密失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件解密失败: " + e.getMessage(), e);
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
            // 如果密钥不是16或32字节，使用MD5或SHA-256进行处理
            log.info("密钥长度不标准，将使用哈希处理: {} -> 16字节", key.length());
            
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
            // 截取为16字节(128位)
            byte[] truncatedBytes = new byte[16];
            System.arraycopy(keyBytes, 0, truncatedBytes, 0, truncatedBytes.length);
            keyBytes = truncatedBytes;
            
            log.info("生成的AES密钥(截取后): {} 字节", keyBytes.length);
            
            // 打印密钥的前几个字节，用于调试
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(4, keyBytes.length); i++) {
                String hex = Integer.toHexString(0xff & keyBytes[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            log.info("密钥前4字节(Hex): {}", hexString.toString());
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
     * 检查文件是否为AES加密格式
     * 
     * @param file 要检查的文件
     * @return 是否为加密文件
     */
    public boolean isEncryptedFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        
        // 检查文件名是否以.enc结尾
        if (file.getName().toLowerCase().endsWith(".enc")) {
            log.info("文件名后缀表明这是一个加密文件: {}", file.getName());
            return true;
        }
        
        // 尝试通过文件头进行简单检测 (这不是100%准确，但可作为辅助判断)
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[16]; // 读取前16字节进行分析
            int read = fis.read(header);
            
            if (read < 16) {
                log.info("文件太小，可能不是加密文件: {}", file.getName());
                return false;
            }
            
            // 分析文件头 - 加密文件通常有较高的熵值和随机性
            int randomnessScore = calculateRandomnessScore(header);
            log.debug("文件头随机性评分: {} (得分>7可能是加密文件)", randomnessScore);
            
            return randomnessScore > 7; // 阈值可根据实际情况调整
        } catch (Exception e) {
            log.error("检查文件是否加密时出错: {}", e.getMessage());
            return false;
        }
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