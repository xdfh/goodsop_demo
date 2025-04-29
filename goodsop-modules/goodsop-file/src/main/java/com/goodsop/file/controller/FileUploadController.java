package com.goodsop.file.controller;

import com.goodsop.file.constant.FileConstant;
import com.goodsop.file.entity.FileInfo;
import com.goodsop.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "文件上传接口")
public class FileUploadController {
    
    private final FileService fileService;
    
    /**
     * 上传文件
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public Map<String, Object> uploadFile(
            @Parameter(description = "文件", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "设备ID") @RequestParam(value = "deviceId", required = false) String deviceId,
            @Parameter(description = "是否加密(0-否，1-是)") @RequestParam(value = "isEncrypted", required = false, defaultValue = "0") Integer isEncrypted,
            @Parameter(description = "是否压缩(0-否，1-是)") @RequestParam(value = "isCompressed", required = false, defaultValue = "0") Integer isCompressed) {
        
        log.info("接收到文件上传请求: fileName={}, deviceId={}, isEncrypted={}, isCompressed={}",
                file.getOriginalFilename(), deviceId, isEncrypted, isCompressed);
        
        FileInfo fileInfo = fileService.uploadFile(file, deviceId, isEncrypted, isCompressed);
        
        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileInfo.getId());
        result.put("fileName", fileInfo.getFileName());
        result.put("originalName", fileInfo.getOriginalName());
        result.put("fileSize", fileInfo.getFileSize());
        result.put("fileType", fileInfo.getFileType());
        result.put("uploadTime", fileInfo.getUploadTime());
        result.put("url", "/api/file/download/" + fileInfo.getId());
        
        return result;
    }
    
    /**
     * 分块上传文件（断点续传）
     */
    @PostMapping("/chunk")
    @Operation(summary = "分块上传文件（断点续传）")
    public Map<String, Object> uploadFileChunk(
            @Parameter(description = "文件块", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件名", required = true) @RequestParam("fileName") String fileName,
            @Parameter(description = "设备ID") @RequestParam(value = "deviceId", required = false) String deviceId,
            @Parameter(description = "当前块索引", required = true) @RequestParam("chunk") Integer chunk,
            @Parameter(description = "总块数", required = true) @RequestParam("chunks") Integer chunks,
            @Parameter(description = "是否加密(0-否，1-是)") @RequestParam(value = "isEncrypted", required = false, defaultValue = "0") Integer isEncrypted,
            @Parameter(description = "是否压缩(0-否，1-是)") @RequestParam(value = "isCompressed", required = false, defaultValue = "0") Integer isCompressed) {
        
        log.info("接收到分块文件上传请求: fileName={}, chunk={}/{}, deviceId={}, isEncrypted={}, isCompressed={}",
                fileName, chunk + 1, chunks, deviceId, isEncrypted, isCompressed);
        
        FileInfo fileInfo = fileService.uploadFileChunk(file, fileName, deviceId, chunk, chunks, isEncrypted, isCompressed);
        
        Map<String, Object> result = new HashMap<>();
        result.put("chunk", chunk);
        result.put("chunks", chunks);
        
        // 如果是最后一块，返回完整文件信息
        if (fileInfo != null) {
            result.put("fileId", fileInfo.getId());
            result.put("fileName", fileInfo.getFileName());
            result.put("originalName", fileInfo.getOriginalName());
            result.put("fileSize", fileInfo.getFileSize());
            result.put("fileType", fileInfo.getFileType());
            result.put("uploadTime", fileInfo.getUploadTime());
            result.put("url", "/api/file/download/" + fileInfo.getId());
            result.put("finished", true);
        } else {
            result.put("finished", false);
        }
        
        return result;
    }
} 