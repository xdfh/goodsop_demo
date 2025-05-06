package com.goodsop.file.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件压缩工具类
 */
@Slf4j
@Component
public class FileCompressUtil {
    
    /**
     * 压缩文件
     * 
     * @param sourceFile 源文件
     * @param targetFile 目标文件
     * @return 压缩后的文件
     */
    public File compressFile(File sourceFile, File targetFile) {
        try {
            log.info("准备压缩文件: {} -> {}", sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
            
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
            
            try (FileInputStream fis = new FileInputStream(sourceFile);
                 FileOutputStream fos = new FileOutputStream(targetFile);
                 GZIPOutputStream gzos = new GZIPOutputStream(fos, true)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    gzos.write(buffer, 0, bytesRead);
                }
                gzos.finish();
                
                log.info("文件压缩成功: {} -> {}", sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
                return targetFile;
            } catch (IOException e) {
                log.error("压缩文件IO异常: {}", e.getMessage(), e);
                // 检查目标文件是否已创建，如果已创建但可能不完整，则删除
                if (targetFile.exists()) {
                    targetFile.delete();
                    log.info("删除压缩失败的目标文件: {}", targetFile.getAbsolutePath());
                }
                return null;
            }
        } catch (Exception e) {
            log.error("文件压缩失败: {}", e.getMessage(), e);
            // 检查目标文件是否已创建，如果已创建但可能不完整，则删除
            if (targetFile.exists()) {
                targetFile.delete();
                log.info("删除压缩失败的目标文件: {}", targetFile.getAbsolutePath());
            }
            return null;
        }
    }
    
    /**
     * 解压缩文件
     * 
     * @param sourceFile 源文件
     * @param targetFile 目标文件
     * @return 解压缩后的文件
     */
    public File decompressFile(File sourceFile, File targetFile) {
        try {
            log.info("准备解压文件: {} -> {}", sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
            
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
            
            // 检查源文件格式，确定解压方法
            CompressionType compressionType = detectCompressionType(sourceFile);
            log.info("检测到文件压缩类型: {}", compressionType);
            
            if (compressionType == CompressionType.NONE) {
                log.warn("源文件不是压缩文件或无法识别压缩格式: {}", sourceFile.getAbsolutePath());
                // 对于非压缩文件，直接复制原始文件
                FileUtils.copyFile(sourceFile, targetFile);
                return targetFile;
            }
            
            switch (compressionType) {
                case GZIP:
                    return decompressGzip(sourceFile, targetFile);
                case ZIP:
                    return decompressZip(sourceFile, targetFile);
                default:
                    log.warn("不支持的压缩格式: {}", compressionType);
                    FileUtils.copyFile(sourceFile, targetFile);
                    return targetFile;
            }
        } catch (Exception e) {
            log.error("文件解压失败: {}", e.getMessage(), e);
            // 检查目标文件是否已创建，如果已创建但可能不完整，则删除
            if (targetFile.exists()) {
                targetFile.delete();
                log.info("删除解压失败的目标文件: {}", targetFile.getAbsolutePath());
            }
            return null;
        }
    }
    
    /**
     * 解压GZIP文件
     */
    private File decompressGzip(File sourceFile, File targetFile) throws IOException {
        try (GZIPInputStream gzipIn = new GZIPInputStream(new FileInputStream(sourceFile));
             FileOutputStream fos = new FileOutputStream(targetFile)) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            
            log.info("GZIP文件解压完成: {} -> {}, 解压后大小: {}", 
                    sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), targetFile.length());
            
            if (targetFile.exists() && targetFile.length() > 0) {
                return targetFile;
            } else {
                log.error("解压后的文件大小为0或不存在");
                return null;
            }
        } catch (IOException e) {
            log.error("GZIP解压文件IO异常: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 解压ZIP文件
     */
    private File decompressZip(File sourceFile, File targetFile) throws IOException {
        // 创建临时目录，用于解压多文件ZIP
        File tempDir = Files.createTempDirectory("zip_extract_").toFile();
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(sourceFile))) {
            
            ZipEntry entry;
            File extractedFile = null;
            
            while ((entry = zipIn.getNextEntry()) != null) {
                String entryName = entry.getName();
                File entryFile = new File(tempDir, entryName);
                
                // 跳过目录
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                    continue;
                }
                
                // 确保父目录存在
                File entryParent = entryFile.getParentFile();
                if (entryParent != null && !entryParent.exists()) {
                    entryParent.mkdirs();
                }
                
                // 解压文件
                try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zipIn.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                
                // 记录第一个解压出的文件
                if (extractedFile == null) {
                    extractedFile = entryFile;
                }
                
                log.info("解压ZIP文件: {}", entryName);
                zipIn.closeEntry();
            }
            
            // 如果只有一个文件，将它复制到目标位置
            if (extractedFile != null) {
                FileUtils.copyFile(extractedFile, targetFile);
                log.info("ZIP文件解压完成(单文件): {} -> {}, 解压后大小: {}", 
                        sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), targetFile.length());
                return targetFile;
            } else {
                log.warn("ZIP文件中没有找到文件，可能是空ZIP或只包含目录");
                FileUtils.copyFile(sourceFile, targetFile);
                return targetFile;
            }
        } catch (IOException e) {
            log.error("ZIP解压文件IO异常: {}", e.getMessage());
            throw e;
        } finally {
            // 清理临时目录
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException e) {
                log.warn("清理临时目录失败: {}", tempDir);
            }
        }
    }
    
    /**
     * 检测文件的压缩类型
     * 
     * @param file 要检查的文件
     * @return 压缩类型
     */
    public CompressionType detectCompressionType(File file) {
        if (!file.exists() || file.length() < 4) {
            return CompressionType.NONE;
        }
        
        // 先检查文件头标识
        CompressionType headerType = detectByFileHeader(file);
        if (headerType != CompressionType.NONE) {
            return headerType;
        }
        
        // 如果文件头检测失败，尝试通过文件名判断
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".gz") || fileName.endsWith(".gzip")) {
            return CompressionType.GZIP;
        } else if (fileName.endsWith(".zip")) {
            return CompressionType.ZIP;
        }
        
        return CompressionType.NONE;
    }
    
    /**
     * 通过文件头标识检测压缩类型
     */
    private CompressionType detectByFileHeader(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] signature = new byte[4];
            if (fis.read(signature) != 4) {
                return CompressionType.NONE;
            }
            
            // GZIP文件头标识：0x1f 0x8b
            if ((signature[0] & 0xff) == 0x1f && (signature[1] & 0xff) == 0x8b) {
                return CompressionType.GZIP;
            }
            
            // ZIP文件头标识：0x50 0x4b 0x03 0x04 (PK..)
            if ((signature[0] & 0xff) == 0x50 && (signature[1] & 0xff) == 0x4b &&
                (signature[2] & 0xff) == 0x03 && (signature[3] & 0xff) == 0x04) {
                return CompressionType.ZIP;
            }
            
            return CompressionType.NONE;
        } catch (IOException e) {
            log.error("检查文件头失败: {}", e.getMessage(), e);
            return CompressionType.NONE;
        }
    }
    
    /**
     * 支持的压缩文件类型
     */
    public enum CompressionType {
        NONE,   // 不是压缩文件
        GZIP,   // GZIP格式
        ZIP     // ZIP格式
    }
    
    /**
     * 检查文件是否为GZIP格式
     * 
     * @param file 要检查的文件
     * @return 是否为GZIP格式
     */
    public boolean isGzipFile(File file) {
        return detectCompressionType(file) == CompressionType.GZIP;
    }
    
    /**
     * 检查文件是否为ZIP格式
     * 
     * @param file 要检查的文件
     * @return 是否为ZIP格式
     */
    public boolean isZipFile(File file) {
        return detectCompressionType(file) == CompressionType.ZIP;
    }
} 