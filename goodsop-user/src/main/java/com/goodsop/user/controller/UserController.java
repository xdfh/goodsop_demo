package com.goodsop.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.goodsop.common.core.model.Result;
import com.goodsop.user.entity.User;
import com.goodsop.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户相关接口")
public class UserController {

    private final UserService userService;

    @GetMapping("/page")
    @Operation(summary = "分页查询用户", description = "根据条件分页查询用户信息")
    public Result<Page<User>> pageUsers(
            @Parameter(description = "页码", required = true) @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数", required = true) @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "排序字段，支持create_time/update_time") @RequestParam(required = false) String sortField,
            @Parameter(description = "排序方向，asc升序/desc降序") @RequestParam(defaultValue = "desc") String sortOrder,
            @Parameter(description = "用户查询条件") User user) {
        Page<User> page = new Page<>(current, size);
        Page<User> result = userService.pageUsers(page, user, sortField, sortOrder);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询用户", description = "根据用户ID查询用户信息")
    public Result<User> getUserById(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id) {
        User user = userService.getUserById(id);
        return Result.success(user);
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "根据用户名查询用户", description = "根据用户名查询用户信息")
    public Result<User> getUserByUsername(
            @Parameter(description = "用户名", required = true) @PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return Result.success(user);
    }

    @PostMapping
    @Operation(summary = "新增用户", description = "新增用户信息")
    public Result<Boolean> saveUser(
            @Parameter(description = "用户信息", required = true) @RequestBody User user) {
        boolean result = userService.saveUser(user);
        return Result.success(result);
    }

    @PutMapping
    @Operation(summary = "更新用户", description = "更新用户信息")
    public Result<Boolean> updateUser(
            @Parameter(description = "用户信息", required = true) @RequestBody User user) {
        boolean result = userService.updateUser(user);
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据用户ID删除用户信息")
    public Result<Boolean> deleteUserById(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id) {
        boolean result = userService.deleteUserById(id);
        return Result.success(result);
    }
} 