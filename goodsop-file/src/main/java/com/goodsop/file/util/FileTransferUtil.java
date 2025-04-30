package com.goodsop.file.util;

import com.goodsop.file.constant.FileConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件传输工具类
 */
@Slf4j
@Component
public class FileTransferUtil {

    /**
     * 存储上传的文件
     * 
     * @param file      上传的文件
     * @param directory 存储目录
     * @return 存储后的文件对象
     */
    public File storeFile(MultipartFile file, String directory) {
        try {
            // 确保目录存在
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            
            // 获取原始文件名
            String originalFilename = file.getOriginalFilename();
            String filename;
            String extension = "";
            
            // 检查是否符合规范的文件名格式（设备ID_日期_用户ID_时间戳_时长_MD5值）
            if (originalFilename != null && isStandardFilename(originalFilename)) {
                // 直接使用原始文件名，因为它已经符合格式要求
                filename = originalFilename;
                log.info("使用原始格式化文件名: {}", filename);
            } else {
                // 如果不符合格式，使用UUID作为文件名
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                filename = UUID.randomUUID().toString() + extension;
                log.info("使用随机文件名: {}", filename);
            }
            
            // 保存文件
            Path targetPath = dirPath.resolve(filename);
            File targetFile = targetPath.toFile();
            file.transferTo(targetFile);
            
            log.info("文件存储成功: {}", targetFile.getAbsolutePath());
            return targetFile;
        } catch (IOException e) {
            log.error("文件存储失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件存储失败", e);
        }
    }
    
    /**
     * 检查文件名是否符合标准格式：设备ID_YYYYMMDD_用户ID_时间戳_时长_MD5值.扩展名
     * 
     * @param filename 文件名
     * @return 是否符合标准格式
     */
    private boolean isStandardFilename(String filename) {
        if (filename == null || !filename.contains("_")) {
            return false;
        }
        
        String nameWithoutExt = filename;
        if (filename.contains(".")) {
            nameWithoutExt = filename.substring(0, filename.lastIndexOf("."));
        }
        
        String[] parts = nameWithoutExt.split("_");
        
        // 必须至少有6个部分
        if (parts.length < 6) {
            return false;
        }
        
        // 检查第二部分是否为8位日期格式
        if (parts[1].length() != 8) {
            return false;
        }
        
        try {
            // 检查日期格式
            Integer.parseInt(parts[1]);
            
            // 检查时间戳是否为数字
            Long.parseLong(parts[3]);
            
            // 检查时长是否为数字
            Long.parseLong(parts[4]);
            
            // 通过所有检查
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 按照日期存储文件
     * 
     * @param file       上传的文件
     * @param baseDir    基础目录
     * @param dateFormat 日期格式
     * @return 存储后的文件对象
     */
    public File storeFileByDate(MultipartFile file, String baseDir, String dateFormat) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern(dateFormat));
        String directory = baseDir + FileConstant.FILE_SEPARATOR + datePath;
        return storeFile(file, directory);
    }
    
    /**
     * 分块上传文件（断点续传）
     * 
     * @param file      文件块
     * @param directory 存储目录
     * @param fileName  文件名
     * @param chunk     当前块索引
     * @param chunks    总块数
     * @return 如果是最后一块，返回合并后的文件；否则返回null
     */
    public File storeFileChunk(MultipartFile file, String directory, String fileName, 
                               Integer chunk, Integer chunks) {
        try {
            // 确保目录存在
            Path dirPath = Paths.get(directory);
            Path tempDirPath = Paths.get(directory, "temp", fileName);
            if (!Files.exists(tempDirPath)) {
                Files.createDirectories(tempDirPath);
            }
            
            // 存储分块文件
            String chunkFileName = chunk + ".part";
            Path chunkPath = tempDirPath.resolve(chunkFileName);
            File chunkFile = chunkPath.toFile();
            file.transferTo(chunkFile);
            
            // 检查是否为最后一块，如果是则合并文件
            if (chunk == chunks - 1) {
                // 检查文件名是否符合规范，否则重命名
                String finalFileName = fileName;
                if (!isStandardFilename(fileName)) {
                    // 如果不符合规范，生成随机名称
                    String extension = "";
                    if (fileName.contains(".")) {
                        extension = fileName.substring(fileName.lastIndexOf("."));
                    }
                    finalFileName = UUID.randomUUID().toString() + extension;
                    log.info("分块上传使用随机文件名: {}", finalFileName);
                } else {
                    log.info("分块上传使用标准文件名: {}", finalFileName);
                }
                
                Path targetPath = dirPath.resolve(finalFileName);
                File targetFile = targetPath.toFile();
                mergeChunks(tempDirPath.toFile(), targetFile, chunks);
                
                // 删除临时分块文件
                deleteDirectory(tempDirPath.toFile());
                
                log.info("分块文件上传完成并合并: {}", targetFile.getAbsolutePath());
                return targetFile;
            } else {
                log.info("分块文件上传: chunk {}/{}", chunk + 1, chunks);
                return null;
            }
        } catch (IOException e) {
            log.error("分块文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("分块文件上传失败", e);
        }
    }
    
    /**
     * 合并分块文件
     * 
     * @param tempDir    临时目录
     * @param targetFile 目标文件
     * @param chunks     分块总数
     */
    private void mergeChunks(File tempDir, File targetFile, int chunks) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            byte[] buffer = new byte[1024];
            for (int i = 0; i < chunks; i++) {
                File chunkFile = new File(tempDir, i + ".part");
                if (chunkFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(chunkFile);
                         BufferedInputStream bis = new BufferedInputStream(fis)) {
                        int len;
                        while ((len = bis.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 递归删除目录
     * 
     * @param directory 要删除的目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * 设置文件下载响应头
     * 
     * @param response      HTTP响应对象
     * @param filename      文件名
     * @param contentLength 内容长度
     */
    public void setDownloadResponseHeaders(HttpServletResponse response, String filename, long contentLength) {
        try {
            String encodedFileName = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
            
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setHeader("Content-Length", String.valueOf(contentLength));
        } catch (UnsupportedEncodingException e) {
            log.error("设置下载响应头失败: {}", e.getMessage(), e);
            throw new RuntimeException("设置下载响应头失败", e);
        }
    }
    
    /**
     * 支持断点续传的文件下载
     * 
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param file     要下载的文件
     * @param filename 下载后的文件名
     */
    public void downloadWithRange(HttpServletRequest request, HttpServletResponse response, 
                                 File file, String filename) {
        try {
            long fileLength = file.length();
            String range = request.getHeader("Range");
            
            // 设置响应头
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Type", "application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + 
                    URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20") + "\"");
            
            // 分块下载
            if (range != null && range.startsWith("bytes=")) {
                range = range.substring("bytes=".length());
                long start = 0, end = fileLength - 1;
                
                if (range.startsWith("-")) {
                    // 如果范围是 -100，表示最后100个字节
                    start = fileLength - Long.parseLong(range.substring(1));
                } else {
                    String[] parts = range.split("-");
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                }
                
                // 检查范围有效性
                if (start < 0 || end >= fileLength || start > end) {
                    response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    response.setHeader("Content-Range", "bytes */" + fileLength);
                    return;
                }
                
                // 设置分块下载响应头
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                response.setHeader("Content-Length", String.valueOf(end - start + 1));
                
                // 写入文件数据
                try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
                     OutputStream os = response.getOutputStream()) {
                    
                    randomAccessFile.seek(start);
                    long remaining = end - start + 1;
                    byte[] buffer = new byte[4096];
                    
                    while (remaining > 0) {
                        int read = randomAccessFile.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (read == -1) {
                            break;
                        }
                        os.write(buffer, 0, read);
                        remaining -= read;
                    }
                }
            } else {
                // 普通下载
                response.setHeader("Content-Length", String.valueOf(fileLength));
                
                // 写入文件数据
                try (FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream bis = new BufferedInputStream(fis);
                     OutputStream os = response.getOutputStream()) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }
        } catch (IOException e) {
            log.error("文件下载失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件下载失败", e);
        }
    }
} 