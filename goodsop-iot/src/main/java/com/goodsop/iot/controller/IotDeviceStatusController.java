package com.goodsop.iot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.goodsop.iot.entity.IotDeviceStatus;
import com.goodsop.iot.service.IIotDeviceStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * IoT设备状态Controller
 */
@Slf4j
@RestController
@RequestMapping("/iot/device/status")
@RequiredArgsConstructor
@Tag(name = "IoT设备状态管理", description = "提供设备状态的增删改查接口")
public class IotDeviceStatusController {

    private final IIotDeviceStatusService deviceStatusService;

    @Operation(summary = "分页查询设备状态", description = "根据设备ID和设备名称分页查询设备状态信息")
    @Parameters({
        @Parameter(name = "current", description = "当前页码", required = true, schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(name = "size", description = "每页数量", required = true, schema = @Schema(type = "integer", defaultValue = "10")),
        @Parameter(name = "deviceId", description = "设备ID", required = false, schema = @Schema(type = "string")),
        @Parameter(name = "deviceName", description = "设备名称", required = false, schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "500", description = "服务器错误")
    })
    @GetMapping("/page")
    public Page<IotDeviceStatus> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String deviceName) {
        
        Page<IotDeviceStatus> page = new Page<>(current, size);
        LambdaQueryWrapper<IotDeviceStatus> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.like(deviceId != null, IotDeviceStatus::getDeviceId, deviceId)
               .like(deviceName != null, IotDeviceStatus::getDeviceName, deviceName)
               .orderByDesc(IotDeviceStatus::getUpdateTime);
        
        return deviceStatusService.page(page, wrapper);
    }

    @Operation(summary = "获取设备状态详情", description = "根据ID获取设备状态详细信息")
    @Parameter(name = "id", description = "设备状态ID", required = true, schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "设备状态不存在"),
        @ApiResponse(responseCode = "500", description = "服务器错误")
    })
    @GetMapping("/{id}")
    public IotDeviceStatus getById(@PathVariable Long id) {
        return deviceStatusService.getById(id);
    }

    @Operation(summary = "新增设备状态", description = "新增一个设备状态记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "500", description = "服务器错误")
    })
    @PostMapping
    public boolean save(@RequestBody IotDeviceStatus deviceStatus) {
        return deviceStatusService.save(deviceStatus);
    }

    @Operation(summary = "更新设备状态", description = "根据ID更新设备状态信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "404", description = "设备状态不存在"),
        @ApiResponse(responseCode = "500", description = "服务器错误")
    })
    @PutMapping
    public boolean update(@RequestBody IotDeviceStatus deviceStatus) {
        return deviceStatusService.updateById(deviceStatus);
    }

    @Operation(summary = "删除设备状态", description = "根据ID删除设备状态记录")
    @Parameter(name = "id", description = "设备状态ID", required = true, schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "设备状态不存在"),
        @ApiResponse(responseCode = "500", description = "服务器错误")
    })
    @DeleteMapping("/{id}")
    public boolean remove(@PathVariable Long id) {
        return deviceStatusService.removeById(id);
    }
} 