package com.goodsop.file.controller;

import com.goodsop.common.core.model.Result;
import com.goodsop.file.config.FileProperties;
import com.goodsop.file.entity.FileInfo;
import com.goodsop.file.service.FileService;
import com.goodsop.file.vo.FileUploadResponseVO;
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
    private final FileProperties fileProperties;
    
    /**
     * 上传文件
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public Result<Map<String, Object>> uploadFile(
            @Parameter(description = "文件", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "设备ID") @RequestParam(required = false, defaultValue = "unknown") String deviceId,
            @Parameter(description = "是否加密(0-否，1-是)") @RequestParam(required = false, defaultValue = "0") Integer isEncrypted,
            @Parameter(description = "是否压缩(0-否，1-是)") @RequestParam(required = false, defaultValue = "0") Integer isCompressed,
            @Parameter(description = "原始文件扩展名") @RequestParam(required = false) String originalExtension) {
        
        log.info("接收到文件上传请求: fileName={}, fileSize={}, deviceId={}, isEncrypted={}, isCompressed={}, originalExtension={}",
                file.getOriginalFilename(), file.getSize(), deviceId, isEncrypted, isCompressed, originalExtension);
        
        try {
            FileInfo fileInfo = fileService.uploadFile(file, deviceId, isEncrypted, isCompressed, originalExtension);
            
            Map<String, Object> result = new HashMap<>();
            result.put("fileId", fileInfo.getId());
            result.put("fileName", fileInfo.getFileName());
            result.put("fileSize", fileInfo.getFileSize());
            result.put("fileType", fileInfo.getFileType());
            result.put("uploadTime", fileInfo.getUploadTime());
            result.put("filePath", fileInfo.getFilePath());
            result.put("accessUrl", fileInfo.getAccessUrl());
            result.put("success", true);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 分块上传文件（断点续传）
     */
    @PostMapping("/chunk/upload")
    @Operation(summary = "分块上传文件(断点续传)")
    public Result<FileUploadResponseVO> uploadFileChunk(
            @Parameter(description = "文件块", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件名", required = true) @RequestParam("fileName") String fileName,
            @Parameter(description = "设备ID") @RequestParam(required = false, defaultValue = "unknown") String deviceId,
            @Parameter(description = "当前块索引", required = true) @RequestParam("chunk") Integer chunk,
            @Parameter(description = "总块数", required = true) @RequestParam("chunks") Integer chunks,
            @Parameter(description = "是否加密(0-否，1-是)") @RequestParam(required = false, defaultValue = "0") Integer isEncrypted,
            @Parameter(description = "是否压缩(0-否，1-是)") @RequestParam(required = false, defaultValue = "0") Integer isCompressed,
            @Parameter(description = "原始文件扩展名") @RequestParam(required = false) String originalExtension,
            @Parameter(description = "关键词") @RequestParam(required = false) String keywords) {
        
        log.info("接收到分块上传请求: fileName={}, chunk={}/{}, fileSize={}, deviceId={}, isEncrypted={}, isCompressed={}, originalExtension={}, keywords={}",
                fileName, chunk + 1, chunks, file.getSize(), deviceId, isEncrypted, isCompressed, originalExtension, keywords);
        
        // 参数校验
        if (file.isEmpty()) {
            log.error("分块上传失败: 文件块为空");
            return Result.error("文件块不能为空");
        }
        
        if (fileName == null || fileName.trim().isEmpty()) {
            log.error("分块上传失败: 文件名为空");
            return Result.error("文件名不能为空");
        }
        
        if (chunk < 0 || chunks <= 0 || chunk >= chunks) {
            log.error("分块上传失败: 分块参数无效 chunk={}, chunks={}", chunk, chunks);
            return Result.error("分块参数无效");
        }
        
        // 确保isEncrypted和isCompressed是有效值
        if (isEncrypted != null && (isEncrypted != 0 && isEncrypted != 1)) {
            log.warn("接收到无效的isEncrypted值: {}, 默认使用0(未加密)", isEncrypted);
            isEncrypted = 0;
        }
        
        if (isCompressed != null && (isCompressed != 0 && isCompressed != 1)) {
            log.warn("接收到无效的isCompressed值: {}, 默认使用0(未压缩)", isCompressed);
            isCompressed = 0;
        }
        
        FileUploadResponseVO responseVO = new FileUploadResponseVO();
        
        try {
            // 上传文件块
            FileInfo fileInfo = fileService.uploadFileChunk(file, fileName, deviceId, chunk, chunks,
                    isEncrypted, isCompressed, originalExtension, keywords);
            
            // 设置共同的返回信息
            responseVO.setFileName(fileName)
                      .setChunks(chunks)
                      .setChunk(chunk)
                      .setSuccess(true);
            
            if (fileInfo != null) {
                // 全部分块上传完成，合并成功
                FileProperties.Storage storage = fileProperties.getStorage();
                String accessUrl = String.format("http://%s:%d%s", storage.getInternetHost(), storage.getInternetPort(), fileInfo.getAccessUrl());
                String lanAccessUrl = String.format("http://%s:%d%s", storage.getLanHost(), storage.getLanPort(), fileInfo.getAccessUrl());
                
                responseVO.setFileId(fileInfo.getId())
                          .setFileName(fileInfo.getFileName())
                          .setFileSize(fileInfo.getFileSize())
                          .setFileType(fileInfo.getFileType())
                          .setUploadTime(fileInfo.getUploadTime())
//                          .setFilePath(fileInfo.getFilePath())
                          .setAccessUrl(accessUrl)
                          .setLanAccessUrl(lanAccessUrl)
                          .setIsEncrypted(isEncrypted)
                          .setIsCompressed(isCompressed)
                          .setCompleted(true)
                          .setMessage("文件上传完成");
                          
                log.info("分块上传完成: fileId={}, filePath={}, accessUrl={}, lanAccessUrl={}",
                        fileInfo.getId(), fileInfo.getFilePath(), accessUrl, lanAccessUrl);
            } else {
                // 部分分块上传完成，返回当前进度
                responseVO.setCompleted(false)
                          .setMessage("分块" + (chunk + 1) + "上传成功，请继续上传");
                log.info("分块{}上传成功，等待继续上传", chunk + 1);
            }
            
            return Result.success(responseVO);
        } catch (Exception e) {
            log.error("分块上传失败: {}", e.getMessage(), e);
            responseVO.setSuccess(false)
                      .setMessage("分块上传失败: " + e.getMessage())
                      .setChunk(chunk)
                      .setFileName(fileName);
            return Result.error(500, "分块上传失败: " + e.getMessage());
        }
    }
} 