package com.nightlypick.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@TableName("app_user")
public class UserEntity {
    @TableId
    private String id;
    private String nickname;
    private String phone;
    private String openid;
    private String provider;
    private String avatarUrl;
    private Boolean allowMemoryReference;
    private LocalDate createdDate;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastLoginAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Boolean getAllowMemoryReference() { return allowMemoryReference; }
    public void setAllowMemoryReference(Boolean allowMemoryReference) { this.allowMemoryReference = allowMemoryReference; }
    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
