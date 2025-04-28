package com.goodsop.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.goodsop.user.entity.User;
import com.goodsop.user.mapper.UserMapper;
import com.goodsop.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public Page<User> pageUsers(Page<User> page, User user) {
        return pageUsers(page, user, null, null);
    }

    @Override
    public Page<User> pageUsers(Page<User> page, User user, String sortField, String sortOrder) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (user != null) {
            // 根据用户名模糊查询
            if (StringUtils.hasText(user.getUsername())) {
                queryWrapper.like(User::getUsername, user.getUsername());
            }
            // 根据邮箱模糊查询
            if (StringUtils.hasText(user.getEmail())) {
                queryWrapper.like(User::getEmail, user.getEmail());
            }
            // 根据手机号模糊查询
            if (StringUtils.hasText(user.getPhone())) {
                queryWrapper.like(User::getPhone, user.getPhone());
            }
            // 根据状态查询
            if (user.getStatus() != null) {
                queryWrapper.eq(User::getStatus, user.getStatus());
            }
        }
        
        // 添加排序功能
        if (StringUtils.hasText(sortField)) {
            boolean isAsc = !"desc".equalsIgnoreCase(sortOrder);
            
            // 根据创建时间排序
            if ("create_time".equals(sortField)) {
                queryWrapper.orderBy(true, isAsc, User::getCreateTime);
            }
            // 根据更新时间排序
            else if ("update_time".equals(sortField)) {
                queryWrapper.orderBy(true, isAsc, User::getUpdateTime);
            }
            // 默认按照创建时间降序排序
            else {
                queryWrapper.orderByDesc(User::getCreateTime);
            }
        } else {
            // 默认按照创建时间降序排序
            queryWrapper.orderByDesc(User::getCreateTime);
        }
        
        return this.page(page, queryWrapper);
    }

    @Override
    public User getUserById(Long id) {
        return getBaseMapper().selectById(id);
    }

    @Override
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return this.getOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveUser(User user) {
        // 设置默认状态为启用
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        return this.save(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(User user) {
        return this.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUserById(Long id) {
        return this.removeById(id);
    }
} 