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
            // 确保路径中使用一致的分隔符
            directory = directory.replace('\\', '/');
            
            // 确保目录存在
            Path dirPath = Paths.get(directory);
            Path tempDirPath = Paths.get(directory, "temp", fileName);
            
            log.info("目录检查 - 主目录: {}", dirPath.toAbsolutePath());
            log.info("目录检查 - 临时目录: {}", tempDirPath.toAbsolutePath());
            
            // 先创建主目录
            if (!Files.exists(dirPath)) {
                try {
                    Files.createDirectories(dirPath);
                    log.info("主目录不存在，已创建: {}", dirPath.toAbsolutePath());
                } catch (Exception e) {
                    log.error("创建主目录失败: {}, 错误: {}", dirPath.toAbsolutePath(), e.getMessage());
                    throw e;
                }
            } else {
                log.info("主目录已存在: {}", dirPath.toAbsolutePath());
            }
            
            // 然后创建临时目录
            if (!Files.exists(tempDirPath)) {
                try {
                    Files.createDirectories(tempDirPath);
                    log.info("临时目录不存在，已创建: {}", tempDirPath.toAbsolutePath());
                } catch (Exception e) {
                    log.error("创建临时目录失败: {}, 错误: {}", tempDirPath.toAbsolutePath(), e.getMessage());
                    throw e;
                }
            } else {
                log.info("临时目录已存在: {}", tempDirPath.toAbsolutePath());
            }
            
            // 存储分块文件
            String chunkFileName = chunk + ".part";
            Path chunkPath = tempDirPath.resolve(chunkFileName);
            File chunkFile = chunkPath.toFile();
            
            try {
                file.transferTo(chunkFile);
                log.info("分块文件已保存: {}", chunkPath.toAbsolutePath());
            } catch (Exception e) {
                log.error("保存分块文件失败: {}, 错误: {}", chunkPath.toAbsolutePath(), e.getMessage());
                throw e;
            }
            
            log.info("分块文件上传: chunk {}/{}, 大小: {}", chunk + 1, chunks, file.getSize());
            
            // 检查是否已上传所有分块
            int uploadedCount = getUploadedChunkCount(tempDirPath.toFile());
            boolean allUploaded = uploadedCount == chunks;
            log.info("分块上传状态 - 已上传: {}/{}, 是否全部上传: {}", uploadedCount, chunks, allUploaded);
            
            // 只有当所有分块都上传完成时才合并文件
            if (allUploaded) {
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
                log.info("准备合并文件到: {}", targetPath.toAbsolutePath());
                
                try {
                    mergeChunks(tempDirPath.toFile(), targetFile, chunks);
                    log.info("合并文件成功: {}, 大小: {}", targetPath.toAbsolutePath(), targetFile.length());
                } catch (Exception e) {
                    log.error("合并文件失败: {}, 错误: {}", targetPath.toAbsolutePath(), e.getMessage());
                    throw e;
                }
                
                // 删除临时分块文件和目录
                boolean deleted = deleteDirectory(tempDirPath.toFile());
                if (!deleted) {
                    log.warn("临时目录删除不完全，将在JVM退出时尝试再次删除: {}", tempDirPath.toAbsolutePath());
                    // 注册JVM退出时删除
                    tempDirPath.toFile().deleteOnExit();
                    
                    // 尝试使用File类的delete方法再删除一次
                    File parentTempDir = new File(directory, "temp");
                    if (parentTempDir.exists()) {
                        boolean parentDeleted = parentTempDir.delete();
                        if (parentDeleted) {
                            log.info("成功删除父临时目录: {}", parentTempDir.getAbsolutePath());
                        }
                    }
                } else {
                    log.info("临时目录删除成功: {}", tempDirPath.toAbsolutePath());
                }
                
                // 从文件名缓存中移除当前文件(在FileServiceImpl中实现)
                
                log.info("分块文件上传完成并合并: {}", targetFile.getAbsolutePath());
                
                if (!targetFile.exists()) {
                    log.error("合并后的文件不存在: {}", targetFile.getAbsolutePath());
                    throw new RuntimeException("合并后的文件不存在: " + targetFile.getAbsolutePath());
                }
                
                return targetFile;
            } else {
                log.info("分块文件上传进度: {}/{}", uploadedCount, chunks);
                return null;
            }
        } catch (IOException e) {
            log.error("分块文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("分块文件上传失败", e);
        }
    }
    
    /**
     * 获取已上传的分块数量
     * 
     * @param tempDir 临时目录
     * @return 已上传的分块数量
     */
    private int getUploadedChunkCount(File tempDir) {
        File[] files = tempDir.listFiles((dir, name) -> name.endsWith(".part"));
        return files != null ? files.length : 0;
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
            
            byte[] buffer = new byte[8192]; // 增大缓冲区，提高性能
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
                } else {
                    log.warn("缺少分块文件: {}", chunkFile.getAbsolutePath());
                    throw new IOException("缺少分块文件: " + chunkFile.getAbsolutePath());
                }
            }
            
            // 确保所有数据都写入磁盘
            bos.flush();
        }
    }
    
    /**
     * 递归删除目录
     * 
     * @param directory 要删除的目录
     * @return 是否成功删除
     */
    private boolean deleteDirectory(File directory) {
        boolean success = true;
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        success = deleteDirectory(file) && success;
                    } else {
                        boolean deleted = file.delete();
                        if (!deleted) {
                            log.warn("无法删除文件: {}", file.getAbsolutePath());
                            success = false;
                        } else {
                            log.info("成功删除文件: {}", file.getAbsolutePath());
                        }
                    }
                }
            }
            boolean deleted = directory.delete();
            if (!deleted) {
                log.warn("无法删除目录: {}", directory.getAbsolutePath());
                success = false;
            } else {
                log.info("成功删除目录: {}", directory.getAbsolutePath());
            }
        }
        return success;
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