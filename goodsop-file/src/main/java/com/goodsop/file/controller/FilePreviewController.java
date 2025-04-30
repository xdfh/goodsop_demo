package com.goodsop.file.controller;

import com.goodsop.common.core.model.Result;
import com.goodsop.file.entity.FileInfo;
import com.goodsop.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件预览控制器
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "文件预览接口")
public class FilePreviewController {
    
    private final FileService fileService;
    
    /**
     * 预览文件
     */
    @GetMapping("/preview/{id}")
    @Operation(summary = "预览文件")
    public void previewFile(
            @Parameter(description = "文件ID", required = true) @PathVariable Long id,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.info("接收到文件预览请求: fileId={}", id);
        
        // 获取文件信息，确保文件存在
        FileInfo fileInfo = fileService.getFileById(id);
        if (fileInfo == null) {
            try {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("文件不存在");
            } catch (Exception e) {
                log.error("返回文件不存在错误失败", e);
            }
            return;
        }
        
        File file = new File(fileInfo.getFilePath());
        if (!file.exists()) {
            try {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("文件不存在");
            } catch (Exception e) {
                log.error("返回文件不存在错误失败", e);
            }
            return;
        }
        
        // 设置文件内容类型
        String contentType = getContentType(fileInfo.getFileType());
        response.setContentType(contentType);
        
        // 如果是图片/音频/视频等媒体文件，直接预览
        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        } catch (IOException e) {
            log.error("文件预览失败: {}", e.getMessage(), e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("文件预览失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("返回文件预览失败错误失败", ex);
            }
        }
    }
    
    /**
     * 获取文件URL信息
     */
    @GetMapping("/url/{id}")
    @Operation(summary = "获取文件URL")
    public Result<Map<String, Object>> getFileUrl(
            @Parameter(description = "文件ID", required = true) @PathVariable Long id) {
        
        log.info("获取文件URL: fileId={}", id);
        
        FileInfo fileInfo = fileService.getFileById(id);
        
        if (fileInfo != null) {
            Map<String, Object> result = new HashMap<>();
            result.put("fileId", fileInfo.getId());
            result.put("fileName", fileInfo.getFileName());
            result.put("fileType", fileInfo.getFileType());
            result.put("filePath", fileInfo.getFilePath());
            result.put("accessUrl", fileInfo.getAccessUrl());
            result.put("success", true);
            return Result.success(result);
        } else {
            return Result.error("文件不存在");
        }
    }
    
    /**
     * 根据文件类型获取Content-Type
     */
    private String getContentType(String fileType) {
        if (fileType == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        
        switch (fileType.toLowerCase()) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "pdf":
                return "application/pdf";
            case "mp3":
                return "audio/mpeg";
            case "mp4":
                return "video/mp4";
            case "wav":
                return "audio/wav";
            case "txt":
                return "text/plain";
            case "html":
                return "text/html";
            case "xml":
                return "application/xml";
            case "json":
                return "application/json";
            default:
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
} 