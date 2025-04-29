package com.goodsop.user.service;

import com.goodsop.user.entity.User;
import com.goodsop.user.service.impl.UserServiceImpl;
import com.goodsop.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用户服务测试类
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Spy
    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testUser");
        user.setPassword("password");
        user.setEmail("test@example.com");
        user.setPhone("12345678901");
        user.setStatus(1);
    }

    @Test
    void testGetUserById() {
        // 准备测试数据
        when(userMapper.selectById(1L)).thenReturn(user);
        
        // 模拟getBaseMapper()方法返回userMapper
        doReturn(userMapper).when(userService).getBaseMapper();
        
        // 执行
        User result = userService.getUserById(1L);

        // 验证
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("testUser", result.getUsername());
        verify(userMapper, times(1)).selectById(1L);
    }
} 