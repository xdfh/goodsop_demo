package com.goodsop.auth.detail;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Spring Security 用户详情封装类
 * <p>
 * 用于存放认证和授权所需的用户核心信息，例如用户名、密码、权限、以及自定义业务字段。
 * 这个类的实例将作为 Authentication 对象的 principal 存在于 SecurityContextHolder 中。
 */
@Data
@Accessors(chain = true)
public class AuthUserDetails implements UserDetails {

    // =====================================================================================
    // =                            ↓↓↓ 在下方添加您需要的自定义字段 ↓↓↓
    // =====================================================================================

    /**
     * 用户唯一ID (例如：数据库主键)
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String nickname;
    
    /**
     * 用户所属部门ID
     */
    private Long departmentId;

    private String deviceId;

    // =====================================================================================
    // =                            ↑↑↑ 在上方添加您需要的自定义字段 ↑↑↑
    // =====================================================================================


    // ============== UserDetails 接口要求的基本字段 ==============

    /**
     * 用户名 (唯一，用于登录)
     */
    private String username;

    /**
     * 用户密码 (加密后的)
     */
    private String password;

    /**
     * 用户拥有的权限集合
     */
    private Collection<? extends GrantedAuthority> authorities;
    
    /**
     * 账户是否未过期
     */
    private boolean isAccountNonExpired = true;

    /**
     * 账户是否未被锁定
     */
    private boolean isAccountNonLocked = true;

    /**
     * 凭证(密码)是否未过期
     */
    private boolean isCredentialsNonExpired = true;

    /**
     * 账户是否可用
     */
    private boolean isEnabled = true;

    // ============== UserDetails 接口方法的实现 ==============

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return this.isAccountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.isAccountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return this.isCredentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return this.isEnabled;
    }
} 