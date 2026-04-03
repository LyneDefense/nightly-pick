<template>
  <view class="np-page detail-page">
    <view v-if="record" class="detail-shell">
      <view class="detail-topbar">
        <text class="detail-back" @click="goBack">‹</text>
        <text class="detail-time">{{ topbarTime }}</text>
        <view class="detail-spacer"></view>
      </view>

      <view class="quote-section">
        <text class="quote-mark">“</text>
        <text class="quote-copy">{{ displayHighlight }}</text>
        <text class="quote-sign">- 夜拾·AI 心语</text>
      </view>

      <view class="content-section">
        <text class="section-label">今 日 摘 要</text>
        <view class="summary-card">
          <textarea v-model="editableSummary" class="summary-input" maxlength="-1" />
        </view>
      </view>

      <view class="content-section">
        <text class="section-label">情 绪 脉 络</text>
        <view class="tags-row">
          <text v-for="item in displayEmotions" :key="item" class="emotion-tag">{{ item }}</text>
        </view>
      </view>

      <view class="content-section" v-if="displayOpenLoops.length">
        <text class="section-label">挂 心 的 事</text>
        <view class="loop-card" v-for="(item, index) in displayOpenLoops" :key="index">
          <text class="loop-icon">◌</text>
          <text class="loop-copy">{{ item }}</text>
        </view>
      </view>
    </view>

    <view v-if="record" class="detail-actions">
      <button class="action-item" @click="handleSave">
        <text class="action-icon">✎</text>
        <text class="action-text">编辑</text>
      </button>
      <button class="action-item">
        <text class="action-icon">↗</text>
        <text class="action-text">分享</text>
      </button>
      <button class="action-item" @click="handleDelete">
        <text class="action-icon">⌦</text>
        <text class="action-text">删除</text>
      </button>
    </view>
  </view>
</template>

<script>
import { deleteRecord, getRecord, updateRecord } from "../../services/records"
import { appState, removeRecord, upsertRecord } from "../../stores/app-state"
import { showError, showSuccess } from "../../utils/ui"

export default {
  data() {
    return {
      record: null,
      currentRecordId: "",
      routeSource: "history",
      editableSummary: "",
      state: appState,
    }
  },
  computed: {
    displayEmotions() {
      if (!this.record || !Array.isArray(this.record.emotions) || !this.record.emotions.length) {
        return ["平静", "略有焦虑", "释然"]
      }
      return this.record.emotions.slice(0, 3)
    },
    displayOpenLoops() {
      if (!this.record || !Array.isArray(this.record.openLoops) || !this.record.openLoops.length) {
        return ["明天10点的项目评审", "给家里打个电话"]
      }
      return this.record.openLoops.slice(0, 3)
    },
    displayHighlight() {
      if (!this.record || !this.record.highlight) {
        return "在琐碎的忙碌中，我们往往忘记了静止也是一种前行。"
      }
      return this.record.highlight
    },
    topbarTime() {
      if (!this.record || !this.record.date) return "8月24日 22:30"
      const [year, month, day] = String(this.record.date).split("-")
      return `${month}月${day}日 22:30`
    },
  },
  onShow() {
    const pages = getCurrentPages()
    const currentPage = pages[pages.length - 1]
    const options = currentPage && currentPage.options ? currentPage.options : {}
    const recordId = options.recordId || ""
    this.routeSource = options.source || "history"
    if (!recordId || recordId === this.currentRecordId) return
    this.currentRecordId = String(recordId)
    this.loadRecord(recordId)
  },
  methods: {
    async loadRecord(recordId) {
      try {
        const cachedRecord = this.state.records.find((item) => item.id === String(recordId))
        if (cachedRecord) {
          this.record = cachedRecord
          this.editableSummary = this.record.summary || ""
        }
        this.record = await getRecord(String(recordId))
        this.editableSummary = this.record.summary || ""
        upsertRecord(this.record)
      } catch (error) {
        showError(error && error.message ? error.message : "加载记录失败")
      }
    },
    async handleSave() {
      if (!this.record) return
      try {
        this.record = await updateRecord(this.record.id, {
          title: this.record.title,
          summary: this.editableSummary,
        })
        upsertRecord(this.record)
        showSuccess("已保存")
      } catch (error) {
        showError(error && error.message ? error.message : "保存失败")
      }
    },
    async handleDelete() {
      if (!this.record) return
      try {
        await deleteRecord(this.record.id)
        removeRecord(this.record.id)
        showSuccess("已删除")
        this.goBack()
      } catch (error) {
        showError(error && error.message ? error.message : "删除失败")
      }
    },
    goBack() {
      if (this.routeSource === "chat" || this.routeSource === "home") {
        uni.reLaunch({ url: "/pages/home/index" })
        return
      }
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
        return
      }
      uni.reLaunch({ url: "/pages/history/index" })
    },
  },
}
</script>

<style scoped>
.detail-page {
  background: #fcf9f0;
}

.detail-shell {
  min-height: 100vh;
  padding: var(--np-page-top-space) 26rpx calc(var(--np-safe-bottom) + 188rpx);
}

.detail-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8rpx;
  padding-right: var(--np-capsule-avoid-space);
}

.detail-back,
.detail-spacer {
  width: 40rpx;
}

.detail-back {
  font-size: 34rpx;
  color: #1f3830;
}

.detail-time {
  font-size: 28rpx;
  color: rgba(31, 56, 48, 0.78);
  font-weight: 600;
}

.quote-section {
  position: relative;
  margin-top: 42rpx;
  padding-left: 18rpx;
}

.quote-mark {
  position: absolute;
  left: 0;
  top: -8rpx;
  font-size: 72rpx;
  color: rgba(31, 56, 48, 0.12);
}

.quote-copy {
  display: block;
  padding-left: 18rpx;
  font-size: 64rpx;
  line-height: 1.32;
  color: rgba(31, 56, 48, 0.88);
}

.quote-sign {
  display: block;
  margin-top: 18rpx;
  text-align: right;
  font-size: 18rpx;
  color: rgba(31, 56, 48, 0.34);
}

.content-section {
  margin-top: 52rpx;
}

.section-label {
  display: block;
  font-size: 18rpx;
  letter-spacing: 4rpx;
  color: rgba(31, 56, 48, 0.24);
}

.summary-card {
  margin-top: 18rpx;
  padding: 28rpx;
  border-radius: 30rpx;
  background: rgba(244, 239, 229, 0.58);
}

.summary-input {
  width: 100%;
  min-height: 300rpx;
  font-size: 28rpx;
  line-height: 1.9;
  color: rgba(31, 56, 48, 0.72);
}

.tags-row {
  margin-top: 18rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.emotion-tag {
  padding: 14rpx 26rpx;
  border-radius: 999rpx;
  background: #eef2eb;
  color: #57605b;
  font-size: 22rpx;
  font-weight: 600;
}

.loop-card {
  margin-top: 16rpx;
  padding: 22rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.76);
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.loop-icon {
  font-size: 22rpx;
  color: rgba(31, 56, 48, 0.34);
}

.loop-copy {
  font-size: 28rpx;
  color: rgba(31, 56, 48, 0.78);
}

.detail-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(env(safe-area-inset-bottom, 0px) + 18rpx);
  background: rgba(252, 249, 240, 0.96);
  display: flex;
  justify-content: space-around;
}

.action-item {
  min-height: auto;
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  color: rgba(31, 56, 48, 0.8);
}

.action-icon {
  font-size: 28rpx;
}

.action-text {
  font-size: 18rpx;
}
</style>
