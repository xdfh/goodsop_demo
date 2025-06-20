package com.goodsop.auth.util;

import com.goodsop.auth.detail.AuthUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JwtToken生成的工具类
 */
@Component
@Slf4j
public class JwtTokenUtil {
    private static final String CLAIM_KEY_USERNAME = "sub";
    private static final String CLAIM_KEY_CREATED = "created";
    private static final String CLAIM_KEY_DEVICE_ID = "deviceId"; // 自定义声明：设备ID
    private static final String CLAIM_KEY_AUTHORITIES = "authorities"; // 自定义声明：权限

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private Long expiration;
    @Value("${jwt.token-head}")
    private String tokenHead;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 根据负责生成JWT的token
     */
    private String generateToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(generateExpirationDate())
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从token中获取JWT中的负载
     */
    private Claims getClaimsFromToken(String token) {
        Claims claims = null;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.info("JWT格式验证失败:{}", token);
        }
        return claims;
    }

    /**
     * 生成token的过期时间
     */
    private Date generateExpirationDate() {
        return new Date(System.currentTimeMillis() + expiration * 1000);
    }

    /**
     * 从token中获取登录用户名
     */
    public String getUserNameFromToken(String token) {
        String username;
        try {
            Claims claims = getClaimsFromToken(token);
            username = claims.getSubject();
        } catch (Exception e) {
            username = null;
        }
        return username;
    }

    /**
     * 验证token是否还有效
     *
     * @param token       客户端传入的token
     * @param userDetails 从数据库中查询出来的用户信息
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        String username = getUserNameFromToken(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * 判断token是否已经失效
     */
    private boolean isTokenExpired(String token) {
        Date expiredDate = getExpiredDateFromToken(token);
        return expiredDate.before(new Date());
    }

    /**
     * 从token中获取过期时间
     */
    private Date getExpiredDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getExpiration();
    }

    /**
     * 根据用户信息生成token
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_KEY_USERNAME, userDetails.getUsername());
        claims.put(CLAIM_KEY_CREATED, new Date());
        claims.put(CLAIM_KEY_AUTHORITIES, userDetails.getAuthorities()); // 添加权限信息
        
        // 如果是设备，添加deviceId到claims
        if (userDetails instanceof AuthUserDetails) {
            String deviceId = ((AuthUserDetails) userDetails).getDeviceId();
            if (deviceId != null) {
                claims.put(CLAIM_KEY_DEVICE_ID, deviceId);
            }
        }
        return generateToken(claims);
    }

    /**
     * 为设备认证专门生成的Token
     * @param deviceId 设备ID
     * @return a JWT token
     */
    public String generateTokenForDevice(String deviceId) {
        Map<String, Object> claims = new HashMap<>();
        // 对于设备，我们可以用 deviceId 作为 subject
        claims.put(CLAIM_KEY_USERNAME, deviceId);
        claims.put(CLAIM_KEY_CREATED, new Date());
        claims.put(CLAIM_KEY_DEVICE_ID, deviceId);
        claims.put(CLAIM_KEY_AUTHORITIES, AuthorityUtils.createAuthorityList("ROLE_DEVICE")); // 为设备赋予角色
        return generateToken(claims);
    }

    /**
     * 从Token中获取用户详情
     */
    public AuthUserDetails getUserDetailsFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        AuthUserDetails userDetails = new AuthUserDetails();
        userDetails.setUsername(claims.getSubject());
        userDetails.setDeviceId(claims.get(CLAIM_KEY_DEVICE_ID, String.class));
        
        // 从claims中获取权限集合, 并手动转换为GrantedAuthority对象
        List<Map<String, String>> authorityMaps = claims.get(CLAIM_KEY_AUTHORITIES, List.class);
        Collection<GrantedAuthority> authorities = authorityMaps.stream()
                .map(map -> new SimpleGrantedAuthority(map.get("authority")))
                .collect(Collectors.toList());
        userDetails.setAuthorities(authorities);

        return userDetails;
    }

    /**
     * 判断token是否可以被刷新
     */
    public boolean canRefresh(String token) {
        return !isTokenExpired(token);
    }

    /**
     * 刷新token
     */
    public String refreshToken(String token) {
        Claims claims = getClaimsFromToken(token);
        claims.put(CLAIM_KEY_CREATED, new Date());
        return generateToken(claims);
    }
} 