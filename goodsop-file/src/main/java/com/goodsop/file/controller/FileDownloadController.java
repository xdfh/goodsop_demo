package com.goodsop.file.controller;

import com.goodsop.file.entity.FileInfo;
import com.goodsop.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件下载控制器
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "文件下载接口")
public class FileDownloadController {
    
    private final FileService fileService;
    
    /**
     * 下载文件
     */
    @GetMapping("/download/{id}")
    @Operation(summary = "下载文件")
    public void downloadFile(
            @Parameter(description = "文件ID", required = true) @PathVariable Long id,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        log.info("接收到文件下载请求: fileId={}", id);
        
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
        
        try {
            // 下载文件
            fileService.downloadFile(id, request, response);
        } catch (Exception e) {
            log.error("文件下载异常: {}", e.getMessage(), e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("文件下载失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("返回文件下载失败错误失败", ex);
            }
        }
    }
} 