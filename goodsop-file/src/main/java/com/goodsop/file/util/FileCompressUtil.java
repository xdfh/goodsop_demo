package com.goodsop.file.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 文件压缩/解压工具类
 */
@Slf4j
@Component
public class FileCompressUtil {

    /**
     * GZIP压缩文件
     * 
     * @param sourceFile 源文件
     * @param targetFile 目标文件
     * @return 压缩后的文件
     */
    public File compressFile(File sourceFile, File targetFile) {
        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(targetFile);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gzos.write(buffer, 0, len);
            }
            log.info("文件压缩成功: {} -> {}", sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
            return targetFile;
        } catch (IOException e) {
            log.error("文件压缩失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件压缩失败", e);
        }
    }
    
    /**
     * GZIP解压文件
     * 
     * @param sourceFile 源文件
     * @param targetFile 目标文件
     * @return 解压后的文件
     */
    public File decompressFile(File sourceFile, File targetFile) {
        try (FileInputStream fis = new FileInputStream(sourceFile);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             FileOutputStream fos = new FileOutputStream(targetFile)) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            log.info("文件解压成功: {} -> {}", sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
            return targetFile;
        } catch (IOException e) {
            log.error("文件解压失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件解压失败", e);
        }
    }
    
    /**
     * 检查文件是否为GZIP格式
     * 
     * @param file 要检查的文件
     * @return 是否为GZIP格式
     */
    public boolean isGzipFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] signature = new byte[2];
            int bytesRead = fis.read(signature);
            return bytesRead == 2 && signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b;
        } catch (IOException e) {
            log.error("检查文件格式失败: {}", e.getMessage(), e);
            return false;
        }
    }
} 