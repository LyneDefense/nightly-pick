package com.nightlypick.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("memory_item")
public class MemoryItemEntity {
    @TableId
    private String id;
    private String userId;
    private String type;
    private String content;
    private String topicKey;
    private Integer mentionCount;
    private Double importanceScore;
    private String sourceRecordId;
    private OffsetDateTime createdAt;
    private OffsetDateTime firstSeenAt;
    private OffsetDateTime lastSeenAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTopicKey() { return topicKey; }
    public void setTopicKey(String topicKey) { this.topicKey = topicKey; }
    public Integer getMentionCount() { return mentionCount; }
    public void setMentionCount(Integer mentionCount) { this.mentionCount = mentionCount; }
    public Double getImportanceScore() { return importanceScore; }
    public void setImportanceScore(Double importanceScore) { this.importanceScore = importanceScore; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(OffsetDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
