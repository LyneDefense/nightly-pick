package com.nightlypick.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("conversation_session")
public class ConversationSessionEntity {
    @TableId
    private String id;
    private String userId;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Integer userMessageCount;
    private Integer summarizedUserMessageCount;
    private String summaryJobStatus;
    private Integer summaryJobTargetUserMessageCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public Integer getUserMessageCount() { return userMessageCount; }
    public void setUserMessageCount(Integer userMessageCount) { this.userMessageCount = userMessageCount; }
    public Integer getSummarizedUserMessageCount() { return summarizedUserMessageCount; }
    public void setSummarizedUserMessageCount(Integer summarizedUserMessageCount) { this.summarizedUserMessageCount = summarizedUserMessageCount; }
    public String getSummaryJobStatus() { return summaryJobStatus; }
    public void setSummaryJobStatus(String summaryJobStatus) { this.summaryJobStatus = summaryJobStatus; }
    public Integer getSummaryJobTargetUserMessageCount() { return summaryJobTargetUserMessageCount; }
    public void setSummaryJobTargetUserMessageCount(Integer summaryJobTargetUserMessageCount) { this.summaryJobTargetUserMessageCount = summaryJobTargetUserMessageCount; }
}
