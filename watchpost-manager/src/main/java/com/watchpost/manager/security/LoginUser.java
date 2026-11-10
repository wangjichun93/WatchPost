package com.watchpost.manager.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** 登录用户主体 (JWT 无状态, 从令牌重建)。 */
@Getter
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final List<GrantedAuthority> authorities;

    public LoginUser(Long userId, String username, List<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.authorities = List.copyOf(authorities);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return null; }   // JWT 无状态, 不持有口令
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
