package com.nightlypick.server.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@TableName("daily_record")
public class DailyRecordEntity {
    @TableId
    private String id;
    private String userId;
    private String sessionId;
    private LocalDate recordDate;
    private String title;
    private String summary;
    private String eventsJson;
    private String emotionsJson;
    private String openLoopsJson;
    private String highlight;
    private OffsetDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getEventsJson() { return eventsJson; }
    public void setEventsJson(String eventsJson) { this.eventsJson = eventsJson; }
    public String getEmotionsJson() { return emotionsJson; }
    public void setEmotionsJson(String emotionsJson) { this.emotionsJson = emotionsJson; }
    public String getOpenLoopsJson() { return openLoopsJson; }
    public void setOpenLoopsJson(String openLoopsJson) { this.openLoopsJson = openLoopsJson; }
    public String getHighlight() { return highlight; }
    public void setHighlight(String highlight) { this.highlight = highlight; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
