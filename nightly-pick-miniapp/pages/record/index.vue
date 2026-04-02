<template>
  <view class="np-page">
    <view v-if="record" class="np-shell record-shell">
      <view class="np-topbar">
        <view class="np-brand">
          <view class="np-brand-mark">✦</view>
          <view class="np-brand-copy">
            <text class="np-brand-name">夜拾</text>
          </view>
        </view>
        <view class="topbar-actions">
          <text class="action-icon">⤴</text>
          <text class="action-icon">⋮</text>
        </view>
      </view>

      <view class="record-card">
        <view class="record-meta">
          <text class="record-date">◷ {{ formatDate(record.date) }}</text>
        </view>
        <text class="record-title">{{ record.title }}</text>

        <view class="section-block">
          <view class="label-row">
            <view class="label-dot"></view>
            <text class="label-text">心路摘要</text>
          </view>
          <text class="summary-text">{{ record.summary }}</text>
        </view>

        <view class="event-panel">
          <view class="label-row">
            <text class="section-icon">≋</text>
            <text class="label-text normal">今日纪事</text>
          </view>
          <view class="event-list">
            <view v-for="(event, index) in displayEvents" :key="index" class="event-item">
              <text class="event-bullet">•</text>
              <text class="event-text">{{ event }}</text>
            </view>
          </view>
        </view>

        <emotion-chips :items="displayEmotions" :max="3" />

        <view class="quote-block">
          <text class="quote-mark">❝</text>
          <text class="quote-text">{{ record.highlight }}</text>
        </view>

        <view class="image-block"></view>
      </view>

      <view class="record-actions">
        <button class="save-button" @click="openDetail">⌑ 保存并收藏</button>
        <button class="secondary-button" @click="revisitRecord">⟲ 重新回顾</button>
      </view>
    </view>

    <np-bottom-nav active="history" />
  </view>
</template>

<script>
import EmotionChips from "../../components/EmotionChips.vue"
import NpBottomNav from "../../components/NpBottomNav.vue"
import { getRecord } from "../../services/records"
import { appState, upsertRecord } from "../../stores/app-state"
import { showError } from "../../utils/ui"

export default {
  components: {
    EmotionChips,
    NpBottomNav,
  },
  data() {
    return {
      record: null,
      currentRecordId: "",
      state: appState,
    }
  },
  computed: {
    displayEvents() {
      if (!this.record || !Array.isArray(this.record.events) || !this.record.events.length) {
        return ["完成了一次睡前复盘对话", "把白天的情绪安静地整理下来"]
      }
      return this.record.events.slice(0, 3)
    },
    displayEmotions() {
      if (!this.record || !Array.isArray(this.record.emotions) || !this.record.emotions.length) {
        return ["平静", "释怀", "整理"]
      }
      return this.record.emotions.slice(0, 3)
    },
  },
  onShow() {
    const pages = getCurrentPages()
    const currentPage = pages[pages.length - 1]
    const recordId = currentPage && currentPage.options ? currentPage.options.recordId : ""
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
        }
        this.record = await getRecord(String(recordId))
        upsertRecord(this.record)
      } catch (error) {
        showError(error && error.message ? error.message : "加载记录失败")
      }
    },
    formatDate(value) {
      if (!value) return "今夜"
      const [year, month, day] = String(value).split("-")
      return `${year}年${month}月${day}日`
    },
    openDetail() {
      if (!this.record) return
      uni.navigateTo({ url: "/pages/record-detail/index?recordId=" + this.record.id })
    },
    revisitRecord() {
      uni.navigateTo({ url: "/pages/chat/index" })
    },
  },
}
</script>

<style scoped>
.record-shell {
  padding-bottom: 260rpx;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 28rpx;
}

.action-icon {
  font-size: 34rpx;
  color: var(--ink-deep);
}

.record-card {
  margin-top: 24rpx;
  padding: 36rpx 32rpx;
  border-radius: 28rpx;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.72), transparent 30%),
    linear-gradient(150deg, rgba(247, 180, 145, 0.84), rgba(240, 97, 110, 0.86) 58%, rgba(255, 238, 199, 0.88));
  box-shadow: var(--shadow-card);
}

.record-meta {
  display: flex;
  align-items: center;
}

.record-date {
  font-size: 24rpx;
  color: rgba(65, 72, 69, 0.82);
}

.record-title {
  display: block;
  margin-top: 16rpx;
  font-size: 50rpx;
  line-height: 1.3;
  color: var(--ink-deep);
  font-weight: 700;
}

.section-block {
  margin-top: 28rpx;
}

.label-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.label-dot {
  width: 8rpx;
  height: 32rpx;
  border-radius: 999rpx;
  background: var(--accent-warm);
}

.label-text {
  font-size: 24rpx;
  color: #4b4a47;
  letter-spacing: 2rpx;
}

.label-text.normal {
  letter-spacing: 1rpx;
}

.summary-text {
  display: block;
  margin-top: 16rpx;
  font-size: 32rpx;
  line-height: 1.8;
  color: #2f2a27;
}

.event-panel {
  margin-top: 30rpx;
  padding: 28rpx 24rpx;
  border-radius: 24rpx;
  background: rgba(255, 248, 242, 0.58);
}

.section-icon {
  font-size: 26rpx;
  color: var(--ink-deep);
}

.event-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 22rpx;
}

.event-item {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.event-bullet {
  font-size: 28rpx;
  color: var(--accent-warm);
}

.event-text {
  flex: 1;
  font-size: 30rpx;
  line-height: 1.65;
  color: #262320;
}

.big-gap {
  margin-top: 26rpx;
}

.tag-row {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.emotion-chip {
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.emotion-chip.warm {
  background: rgba(255, 237, 231, 0.9);
  color: #70361c;
}

.emotion-chip.mint {
  background: rgba(232, 247, 240, 0.92);
  color: #2f4d42;
}

.emotion-chip.neutral {
  background: rgba(255, 247, 240, 0.8);
  color: #4b4a47;
}

.quote-block {
  position: relative;
  margin-top: 34rpx;
  padding-left: 28rpx;
  border-left: 4rpx solid rgba(22, 52, 43, 0.12);
}

.quote-mark {
  position: absolute;
  left: -14rpx;
  top: -14rpx;
  font-size: 40rpx;
  color: rgba(22, 52, 43, 0.24);
}

.quote-text {
  font-size: 34rpx;
  line-height: 1.7;
  color: var(--ink-deep);
}

.image-block {
  margin-top: 34rpx;
  height: 240rpx;
  border-radius: 26rpx;
  background:
    linear-gradient(180deg, rgba(17, 32, 38, 0.82), rgba(54, 62, 69, 0.52)),
    linear-gradient(90deg, rgba(251, 179, 76, 0.9), rgba(247, 124, 58, 0.2) 45%, rgba(14, 17, 21, 0.2) 46%, rgba(255, 183, 81, 0.88) 54%, rgba(18, 20, 25, 0.3) 55%),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.15) 0, rgba(255, 255, 255, 0.15) 2rpx, transparent 2rpx, transparent 26rpx);
}

.record-actions {
  margin-top: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.save-button {
  background: linear-gradient(145deg, #16342b, #2d4b41);
  color: #fff;
}

.secondary-button {
  background: #e7e2db;
  color: var(--ink-strong);
}
</style>
