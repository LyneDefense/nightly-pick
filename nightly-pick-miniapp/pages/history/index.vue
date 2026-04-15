<template>
  <view class="np-page history-page">
    <view class="history-shell">
      <view class="history-topbar">
        <view class="history-topbar-left" @click="goToHome">
          <text class="topbar-back">‹</text>
          <text class="topbar-brand">Nocturne Whisper</text>
        </view>
        <text class="topbar-search">⌕</text>
      </view>

      <view class="history-head">
        <text class="history-title">流年似水</text>
        <text class="history-subtitle">A catalog of your night whispers and lunar dreams.</text>
      </view>

      <button class="conversation-history-entry" @click="goToConversationHistory">
        <view class="conversation-history-entry-copy">
          <text class="conversation-history-entry-title">历史对话</text>
          <text class="conversation-history-entry-desc">回看以前聊过的话</text>
        </view>
        <text class="conversation-history-entry-arrow">›</text>
      </button>

      <view class="month-block">
        <view class="month-head">
          <text class="month-number">{{ visibleMonthLabel }}</text>
          <text class="month-name">{{ visibleMonthEnglish }}</text>
        </view>

        <view class="entry-list">
          <view v-for="item in visibleRecords" :key="item.id" class="entry-row" @click="openDetail(item.id)">
            <view class="entry-left">
              <text class="entry-day">{{ formatDay(item.date) }}</text>
              <view class="entry-tags">
                <text v-for="tag in emotionList(item)" :key="tag" class="entry-tag">{{ formatTag(tag) }}</text>
              </view>
            </view>
            <text class="entry-arrow">›</text>
          </view>
        </view>
      </view>

      <view class="highlight-block" v-if="records.length">
        <view class="highlight-head">
          <text class="highlight-title">近期回顾</text>
          <view class="highlight-line"></view>
        </view>

        <view class="highlight-card" v-for="item in highlightRecords" :key="item.id" @click="openDetail(item.id)">
          <view class="highlight-meta">
            <text class="highlight-date">{{ formatLongDate(item.date) }}</text>
            <text class="highlight-icon">✦</text>
          </view>
          <text class="highlight-quote">{{ pickHighlight(item) }}</text>
          <view class="highlight-tags">
            <text v-for="tag in emotionList(item)" :key="tag" class="highlight-tag">{{ formatTag(tag) }}</text>
          </view>
        </view>
      </view>
    </view>

    <np-bottom-nav active="history" />
  </view>
</template>

<script>
import NpBottomNav from "../../components/NpBottomNav.vue"
import { getRecords } from "../../services/records"
import { appState, setRecords } from "../../stores/app-state"
import { showError } from "../../utils/ui"

const MONTHS_EN = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]

export default {
  components: { NpBottomNav },
  data() {
    const now = new Date()
    return {
      state: appState,
      visibleMonth: now.getMonth(),
      visibleYear: now.getFullYear(),
    }
  },
  computed: {
    records() {
      return this.state.records
    },
    visibleRecords() {
      return this.records.filter((item) => {
        if (!item || !item.date) return false
        const date = new Date(item.date)
        return date.getFullYear() === this.visibleYear && date.getMonth() === this.visibleMonth
      })
    },
    highlightRecords() {
      return this.records.slice(0, 2)
    },
    visibleMonthLabel() {
      return `${this.visibleMonth + 1}`.padStart(2, "0")
    },
    visibleMonthEnglish() {
      return `${MONTHS_EN[this.visibleMonth]} ${this.visibleYear}`
    },
  },
  onShow() {
    this.loadRecords()
  },
  methods: {
    async loadRecords() {
      try {
        setRecords(await getRecords())
      } catch (error) {
        showError(error && error.message ? error.message : "加载历史失败")
      }
    },
    formatDay(value) {
      if (!value) return "--"
      return String(value).split("-")[2]
    },
    formatLongDate(value) {
      if (!value) return "MAY 05, 2024"
      const [year, month, day] = String(value).split("-")
      return `${MONTHS_EN[Number(month) - 1]} ${day}, ${year}`
    },
    emotionList(item) {
      if (!item || !Array.isArray(item.emotions) || !item.emotions.length) return ["CALM"]
      return item.emotions.slice(0, 2)
    },
    formatTag(tag) {
      return String(tag || "").toUpperCase()
    },
    pickHighlight(item) {
      if (item && item.highlight) return `“${item.highlight}”`
      if (item && item.summary) return `“${item.summary.slice(0, 34)}...”`
      return "“深夜的两声让思绪变得异常清晰，仿佛在这片宁静中找回了久违的自我。”"
    },
    openDetail(recordId) {
      uni.navigateTo({ url: `/pages/record-detail/index?recordId=${recordId}&source=history` })
    },
    goToConversationHistory() {
      uni.navigateTo({ url: "/pages/conversation-history/index" })
    },
    goToHome() {
      uni.reLaunch({ url: "/pages/home/index" })
    },
  },
}
</script>

<style scoped>
.history-page {
  background: #fcf9f0;
}

.history-shell {
  min-height: 100vh;
  padding: var(--np-page-top-space) var(--np-page-side-space) var(--np-page-bottom-space);
}

.history-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8rpx;
  padding-right: var(--np-capsule-avoid-space);
}

.history-topbar-left {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.topbar-back,
.topbar-search {
  font-size: 34rpx;
  color: #1f3830;
}

.topbar-brand {
  font-size: 26rpx;
  color: rgba(31, 56, 48, 0.82);
  font-style: italic;
}

.history-head {
  margin-top: 42rpx;
}

.history-title {
  display: block;
  font-size: 68rpx;
  line-height: 1.08;
  color: #1f3830;
  font-weight: 700;
}

.history-subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.45;
  color: rgba(31, 56, 48, 0.56);
}

.conversation-history-entry {
  min-height: 96rpx;
  margin-top: 34rpx;
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

.conversation-history-entry-copy {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.conversation-history-entry-title {
  color: #1f3830;
  font-size: 28rpx;
  font-weight: 700;
}

.conversation-history-entry-desc {
  color: rgba(31, 56, 48, 0.48);
  font-size: 22rpx;
}

.conversation-history-entry-arrow {
  color: rgba(31, 56, 48, 0.32);
  font-size: 34rpx;
}

.month-block {
  margin-top: 54rpx;
}

.month-head {
  display: flex;
  align-items: baseline;
  gap: 14rpx;
}

.month-number {
  font-size: 56rpx;
  color: rgba(31, 56, 48, 0.22);
  font-style: italic;
}

.month-name {
  font-size: 18rpx;
  letter-spacing: 2rpx;
  color: #1f3830;
  font-weight: 700;
}

.entry-list {
  margin-top: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.entry-row {
  padding: 24rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.56);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.entry-left {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.entry-day {
  width: 50rpx;
  font-size: 42rpx;
  color: #1f3830;
}

.entry-tags {
  display: flex;
  gap: 10rpx;
  flex-wrap: wrap;
}

.entry-tag,
.highlight-tag {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #eef0ec;
  color: #55605a;
  font-size: 16rpx;
  font-weight: 700;
}

.entry-arrow {
  font-size: 24rpx;
  color: rgba(31, 56, 48, 0.24);
}

.highlight-block {
  margin-top: 58rpx;
}

.highlight-head {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.highlight-title {
  font-size: 34rpx;
  color: #1f3830;
}

.highlight-line {
  flex: 1;
  height: 1rpx;
  background: rgba(31, 56, 48, 0.08);
}

.highlight-card {
  margin-top: 20rpx;
  padding: 26rpx 24rpx;
  border-radius: 28rpx;
  background: rgba(248, 244, 234, 0.78);
}

.highlight-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.highlight-date {
  font-size: 16rpx;
  letter-spacing: 2rpx;
  color: rgba(31, 56, 48, 0.42);
  font-weight: 700;
}

.highlight-icon {
  font-size: 24rpx;
  color: rgba(31, 56, 48, 0.58);
}

.highlight-quote {
  display: block;
  margin-top: 20rpx;
  font-size: 30rpx;
  line-height: 1.7;
  color: rgba(31, 56, 48, 0.78);
  font-style: italic;
}

.highlight-tags {
  margin-top: 22rpx;
  display: flex;
  gap: 10rpx;
  flex-wrap: wrap;
}
</style>
