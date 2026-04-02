package com.nightlypick.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

@TableName("app_user")
public class UserEntity {
    @TableId
    private String id;
    private String nickname;
    private Boolean allowMemoryReference;
    private LocalDate createdDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Boolean getAllowMemoryReference() { return allowMemoryReference; }
    public void setAllowMemoryReference(Boolean allowMemoryReference) { this.allowMemoryReference = allowMemoryReference; }
    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }
}
