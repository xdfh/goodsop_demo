package com.goodsop.file.util;

import com.goodsop.file.config.FileProperties;
import com.goodsop.file.constant.FileConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileProcessingUtil {
    
    private final FileProperties fileProperties;
    private final FileEncryptUtil fileEncryptUtil;
    private final FileCompressUtil fileCompressUtil;
    
    /**
     * 处理上传的文件（解密和解压缩）
     *
     * @param file 上传的文件
     * @param isEncrypted 是否加密
     * @param isCompressed 是否压缩
     * @return 处理后的文件
     */
    public File processUploadedFile(MultipartFile file, boolean isEncrypted, boolean isCompressed) throws IOException {
        log.info("开始处理上传的文件: {}, isEncrypted={}, isCompressed={}", 
                 file.getOriginalFilename(), isEncrypted, isCompressed);
                 
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("upload_");
        
        try {
            // 保存原始文件
            File originalFile = new File(tempDir.toFile(), file.getOriginalFilename());
            file.transferTo(originalFile);
            log.info("已保存原始文件: {}, 大小: {} 字节", originalFile.getAbsolutePath(), originalFile.length());
            
            // 记录原始大小
            long originalSize = originalFile.length();
            File processedFile = originalFile;
            
            // 处理文件
            if (isEncrypted && fileProperties.getStorage().getEnableDecrypt()) {
                log.info("正在解密文件...");
                String decryptedPath = getProcessedFilePath(processedFile, "decrypted");
                File decryptedFile = new File(decryptedPath);
                processedFile = fileEncryptUtil.decryptFile(processedFile, decryptedFile, 
                                                           fileProperties.getStorage().getAesKey());
                log.info("文件解密完成: {}, 大小: {} 字节", processedFile.getAbsolutePath(), processedFile.length());
            } else {
                log.info("跳过解密步骤: isEncrypted={}, enableDecrypt={}", 
                         isEncrypted, fileProperties.getStorage().getEnableDecrypt());
            }
            
            if (isCompressed && fileProperties.getStorage().getEnableDecompress()) {
                log.info("正在解压文件...");
                String decompressedPath = getProcessedFilePath(processedFile, "decompressed");
                File decompressedFile = new File(decompressedPath);
                processedFile = fileCompressUtil.decompressFile(processedFile, decompressedFile);
                log.info("文件解压完成: {}, 大小: {} 字节", processedFile.getAbsolutePath(), processedFile.length());
            } else {
                log.info("跳过解压步骤: isCompressed={}, enableDecompress={}", 
                         isCompressed, fileProperties.getStorage().getEnableDecompress());
            }
            
            log.info("文件处理完成: {} -> {}", originalFile.getAbsolutePath(), 
                     processedFile.getAbsolutePath());
            return processedFile;
        } catch (Exception e) {
            log.error("处理上传文件失败", e);
            throw new IOException("处理上传文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解密输入流
     */
    private InputStream decrypt(InputStream inputStream) throws Exception {
        String key = fileProperties.getStorage().getAesKey();
        Key secretKey = new SecretKeySpec(Base64.getDecoder().decode(key), "AES");
        Cipher cipher = Cipher.getInstance(FileConstant.ALGORITHM_AES);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return new CipherInputStream(inputStream, cipher);
    }
    
    /**
     * 解压输入流
     */
    private InputStream decompress(InputStream inputStream) throws IOException {
        return new GZIPInputStream(inputStream);
    }
    
    /**
     * 加密文件
     */
    public File encryptFile(File file) throws Exception {
        File encryptedFile = new File(file.getParent(), file.getName() + ".encrypted");
        String key = fileProperties.getStorage().getAesKey();
        Key secretKey = new SecretKeySpec(Base64.getDecoder().decode(key), "AES");
        Cipher cipher = Cipher.getInstance(FileConstant.ALGORITHM_AES);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        try (FileInputStream inputStream = new FileInputStream(file);
             FileOutputStream outputStream = new FileOutputStream(encryptedFile);
             CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                cipherOutputStream.write(buffer, 0, bytesRead);
            }
        }
        
        return encryptedFile;
    }
    
    /**
     * 压缩文件
     */
    public File compressFile(File file) throws IOException {
        File compressedFile = new File(file.getParent(), file.getName() + ".gz");
        
        try (FileInputStream inputStream = new FileInputStream(file);
             FileOutputStream outputStream = new FileOutputStream(compressedFile);
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                gzipOutputStream.write(buffer, 0, bytesRead);
            }
        }
        
        return compressedFile;
    }
    
    /**
     * 获取处理后的文件路径
     * 
     * @param originalFile 原始文件
     * @param suffix 后缀标识（处理类型）
     * @return 处理后的文件路径
     */
    private String getProcessedFilePath(File originalFile, String suffix) {
        String originalPath = originalFile.getAbsolutePath();
        String parentDir = originalFile.getParent();
        String fileName = originalFile.getName();
        
        if (fileName.contains(".")) {
            // 有扩展名的情况
            int dotIndex = fileName.lastIndexOf(".");
            String nameWithoutExt = fileName.substring(0, dotIndex);
            String extension = fileName.substring(dotIndex);
            
            // 如果是enc扩展名且是解密操作，则去掉这个扩展名
            if (extension.equals(".enc") && suffix.equals("decrypted")) {
                return parentDir + File.separator + nameWithoutExt;
            }
            
            return parentDir + File.separator + nameWithoutExt + "." + suffix + extension;
        } else {
            // 无扩展名的情况
            return originalPath + "." + suffix;
        }
    }
} 