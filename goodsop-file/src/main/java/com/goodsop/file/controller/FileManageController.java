package com.goodsop.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.goodsop.file.entity.FileInfo;
import com.goodsop.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "文件管理接口")
public class FileManageController {
    
    private final FileService fileService;
    
    /**
     * 分页查询文件列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询文件列表")
    public Map<String, Object> listFiles(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备ID") @RequestParam(required = false) String deviceId,
            @Parameter(description = "文件类型") @RequestParam(required = false) String fileType,
            @Parameter(description = "开始时间") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) String endTime) {
        
        log.info("分页查询文件列表: current={}, size={}, deviceId={}, fileType={}, startTime={}, endTime={}",
                current, size, deviceId, fileType, startTime, endTime);
        
        Page<FileInfo> page = new Page<>(current, size);
        
        Map<String, Object> params = new HashMap<>();
        if (deviceId != null && !deviceId.isEmpty()) {
            params.put("deviceId", deviceId);
        }
        if (fileType != null && !fileType.isEmpty()) {
            params.put("fileType", fileType);
        }
        if (startTime != null && !startTime.isEmpty() && endTime != null && !endTime.isEmpty()) {
            params.put("startTime", startTime);
            params.put("endTime", endTime);
        }
        
        IPage<FileInfo> result = fileService.listFiles(page, params);
        
        Map<String, Object> map = new HashMap<>();
        map.put("records", result.getRecords());
        map.put("total", result.getTotal());
        map.put("size", result.getSize());
        map.put("current", result.getCurrent());
        map.put("pages", result.getPages());
        
        return map;
    }
    
    /**
     * 获取文件详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取文件详情")
    public FileInfo getFileDetail(@Parameter(description = "文件ID") @PathVariable Long id) {
        log.info("获取文件详情: id={}", id);
        return fileService.getFileById(id);
    }
    
    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除文件")
    public Map<String, Object> deleteFile(@Parameter(description = "文件ID") @PathVariable Long id) {
        log.info("删除文件: id={}", id);
        
        boolean success = fileService.deleteFile(id);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "文件删除成功" : "文件删除失败");
        
        return result;
    }
} 