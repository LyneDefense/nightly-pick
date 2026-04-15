<template>
  <view class="np-page home-page">
    <view class="home-shell">
      <view class="home-topbar">
        <view class="brand-block">
          <text class="brand-name">夜拾</text>
          <text class="brand-sub">深 夜 记 录</text>
        </view>
        <view class="date-block">
          <text class="date-text">{{ todayFullLabel }}</text>
          <text class="greet-text">晚上好，今天过得怎么样？</text>
        </view>
      </view>

      <view class="home-main">
        <view class="hero-column">
          <view class="hero-orbit"></view>
          <view class="hero-core">
            <text class="hero-core-copy">子夜之梦</text>
          </view>
          <text class="hero-title">{{ hasActiveConversation ? "接着说下去..." : "聊聊今晚..." }}</text>
          <view class="hero-subline">
            <view class="hero-line"></view>
            <text class="hero-subcopy">{{ hasActiveConversation ? "继续刚才的话" : "开启今夜的诉说" }}</text>
            <view class="hero-line"></view>
          </view>

          <button class="hero-cta" @click="goToChat">
            <text class="hero-cta-label">{{ hasActiveConversation ? "继续刚才的倾诉" : "开始今夜对话" }}</text>
          </button>
        </view>

        <button class="conversation-history-link" @click="goToConversationHistory">
          <view class="conversation-history-copy">
            <text class="conversation-history-title">历史对话</text>
            <text class="conversation-history-desc">回看以前聊过的话</text>
          </view>
          <text class="conversation-history-arrow">›</text>
        </button>

        <view class="memory-card" @click="openLatestRecord">
          <view class="memory-head">
            <text class="memory-head-icon">✦</text>
            <text class="memory-head-copy">心 路 回 顾</text>
          </view>
          <text class="memory-quote">{{ latestPreview }}</text>
          <view class="memory-foot">
            <text class="memory-foot-icon">◌</text>
            <text class="memory-foot-copy">{{ latestFootnote }}</text>
          </view>
        </view>
      </view>
    </view>

    <np-bottom-nav active="home" />
  </view>
</template>

<script>
import NpBottomNav from "../../components/NpBottomNav.vue"
import { login } from "../../services/auth"
import { getRecords } from "../../services/records"
import { getActiveConversation } from "../../services/conversation"
import { appState, clearActiveConversation, hydrateConversation, setRecords, setUser } from "../../stores/app-state"
import { isCurrentBusinessDate } from "../../utils/business-day"
import { showError } from "../../utils/ui"

const WEEKDAYS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"]

export default {
  components: { NpBottomNav },
  data() {
    return { state: appState }
  },
  computed: {
    businessDayResetHour() {
      return this.state.user ? this.state.user.businessDayResetHour : null
    },
    hasActiveConversation() {
      return Boolean(this.state.activeSessionId) && isCurrentBusinessDate(this.state.activeSessionStartedAt, this.businessDayResetHour)
    },
    latestRecord() {
      return this.state.records[0] || null
    },
    latestPreview() {
      if (this.latestRecord && this.latestRecord.highlight) {
        return `“${this.latestRecord.highlight}”`
      }
      if (this.latestRecord && this.latestRecord.summary) {
        return `“${this.truncate(this.latestRecord.summary, 26)}”`
      }
      return "“那天你说：那一刻，我觉得生活慢了下来...”"
    },
    latestFootnote() {
      return this.latestRecord ? `上次记录：${this.formatRelativeLabel(this.latestRecord.date)}` : "上次记录：3小时前"
    },
    todayFullLabel() {
      const date = new Date()
      return `${date.getMonth() + 1}月${date.getDate()}日 · ${WEEKDAYS[date.getDay()]}`
    },
  },
  onShow() {
    this.loadHome()
  },
  methods: {
    async loadHome() {
      try {
        if (!this.state.user) {
          const response = await login()
          setUser(response.user)
        }
        if (this.state.activeSessionId && !isCurrentBusinessDate(this.state.activeSessionStartedAt, this.businessDayResetHour)) {
          clearActiveConversation()
        }
        const activeConversation = await getActiveConversation()
        if (activeConversation && activeConversation.session) {
          hydrateConversation(activeConversation.session, this.normalizeConversationMessages(activeConversation.messages))
        } else {
          clearActiveConversation()
        }
        setRecords(await getRecords())
      } catch (error) {
        showError(error && error.message ? error.message : "连接夜拾失败")
      }
    },
    normalizeConversationMessages(messages) {
      if (!Array.isArray(messages) || !messages.length) return []
      return messages.map((message) => ({
        role: message.role,
        text: message.text,
        timeLabel: this.formatTimeLabel(message.createdAt),
      }))
    },
    formatTimeLabel(value) {
      if (!value) return "21:30"
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return "21:30"
      const hh = `${date.getHours()}`.padStart(2, "0")
      const mm = `${date.getMinutes()}`.padStart(2, "0")
      return `${hh}:${mm}`
    },
    truncate(text, length) {
      if (!text) return ""
      return text.length > length ? `${text.slice(0, length)}...` : text
    },
    formatRelativeLabel(dateValue) {
      if (!dateValue) return "3小时前"
      return String(dateValue).replace(/-/g, ".")
    },
    goToChat() {
      const url = this.hasActiveConversation ? `/pages/chat/index?sessionId=${this.state.activeSessionId}` : "/pages/chat/index"
      uni.navigateTo({ url })
    },
    goToConversationHistory() {
      uni.navigateTo({ url: "/pages/conversation-history/index" })
    },
    openLatestRecord() {
      if (!this.latestRecord) {
        uni.reLaunch({ url: "/pages/history/index" })
        return
      }
      uni.navigateTo({ url: `/pages/record-detail/index?recordId=${this.latestRecord.id}&source=home` })
    },
  },
}
</script>

<style scoped>
.home-page {
  background: linear-gradient(180deg, #fbf7ee 0%, #f8f4ea 100%);
}

.home-shell {
  min-height: 100vh;
  padding: var(--np-page-top-space) 0 var(--np-page-bottom-space);
}

.home-topbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 8rpx calc(32rpx + var(--np-capsule-avoid-space)) 24rpx 32rpx;
  border-bottom: 1rpx solid rgba(31, 56, 48, 0.06);
}

.brand-name {
  display: block;
  font-size: 56rpx;
  line-height: 1;
  color: #233830;
  font-weight: 700;
}

.brand-sub {
  display: block;
  margin-top: 14rpx;
  font-size: 18rpx;
  letter-spacing: 6rpx;
  color: rgba(31, 56, 48, 0.42);
}

.date-block {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  padding-top: 8rpx;
  max-width: 240rpx;
}

.date-text {
  font-size: 24rpx;
  color: rgba(31, 56, 48, 0.7);
  font-weight: 600;
}

.greet-text {
  margin-top: 12rpx;
  font-size: 20rpx;
  color: rgba(31, 56, 48, 0.28);
  font-style: italic;
}

.home-main {
  padding: 36rpx 32rpx 0;
}

.hero-column {
  min-height: 720rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.hero-column::before {
  content: "";
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 1rpx;
  height: 140rpx;
  background: linear-gradient(180deg, rgba(31, 56, 48, 0.08), rgba(31, 56, 48, 0));
}

.hero-orbit {
  position: absolute;
  width: 460rpx;
  height: 460rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(31, 56, 48, 0.08) 0%, rgba(252, 249, 240, 0) 70%);
}

.hero-core {
  position: relative;
  z-index: 1;
  width: 176rpx;
  height: 176rpx;
  border-radius: 50%;
  border: 1rpx solid rgba(31, 56, 48, 0.06);
  background: rgba(252, 249, 240, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-core-copy {
  font-size: 26rpx;
  color: rgba(31, 56, 48, 0.42);
}

.hero-title {
  position: relative;
  z-index: 1;
  margin-top: 110rpx;
  font-size: 76rpx;
  letter-spacing: 12rpx;
  color: rgba(31, 56, 48, 0.8);
  font-weight: 300;
}

.hero-subline {
  position: relative;
  z-index: 1;
  margin-top: 34rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.hero-line {
  width: 90rpx;
  height: 1rpx;
  background: rgba(31, 56, 48, 0.08);
}

.hero-subcopy {
  font-size: 18rpx;
  letter-spacing: 4rpx;
  color: rgba(31, 56, 48, 0.3);
}

.hero-cta {
  position: relative;
  z-index: 1;
  min-width: 360rpx;
  min-height: 94rpx;
  margin-top: 46rpx;
  padding: 0 38rpx;
  border-radius: 999rpx;
  background: linear-gradient(145deg, #16392f, #295146);
  color: #fff;
  box-shadow: 0 18rpx 40rpx rgba(31, 56, 48, 0.14);
}

.hero-cta-label {
  font-size: 28rpx;
  letter-spacing: 2rpx;
}

.conversation-history-link {
  min-height: 96rpx;
  margin: 0 32rpx 18rpx;
  padding: 18rpx 2rpx 20rpx;
  border-radius: 0;
  background: transparent;
  border-top: 1rpx solid rgba(31, 56, 48, 0.08);
  border-bottom: 1rpx solid rgba(31, 56, 48, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  text-align: left;
}

.conversation-history-copy {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.conversation-history-title {
  color: #1f3830;
  font-size: 28rpx;
  font-weight: 700;
}

.conversation-history-desc {
  color: rgba(31, 56, 48, 0.46);
  font-size: 22rpx;
}

.conversation-history-arrow {
  color: rgba(31, 56, 48, 0.32);
  font-size: 34rpx;
}

.memory-card {
  margin: 0 32rpx;
  padding: 34rpx 30rpx 32rpx;
  border-radius: 28rpx;
  background: rgba(252, 249, 240, 0.44);
  border: 1rpx solid rgba(31, 56, 48, 0.08);
  box-shadow: 0 10rpx 40rpx rgba(31, 56, 48, 0.04);
}

.memory-head {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10rpx;
}

.memory-head-icon,
.memory-foot-icon {
  font-size: 18rpx;
  color: rgba(31, 56, 48, 0.24);
}

.memory-head-copy {
  font-size: 18rpx;
  letter-spacing: 6rpx;
  color: rgba(31, 56, 48, 0.26);
}

.memory-quote {
  display: block;
  margin-top: 34rpx;
  text-align: center;
  font-size: 32rpx;
  line-height: 1.8;
  color: rgba(31, 56, 48, 0.56);
  font-style: italic;
}

.memory-foot {
  margin-top: 34rpx;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10rpx;
}

.memory-foot-copy {
  font-size: 16rpx;
  color: rgba(31, 56, 48, 0.22);
}
</style>
