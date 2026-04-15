<template>
  <view class="np-page conversation-history-page">
    <view class="conversation-history-shell">
      <view class="history-topbar">
        <button class="back-button" @click="goBack">‹</button>
        <view class="topbar-copy">
          <text class="topbar-title">历史对话</text>
          <text class="topbar-subtitle">半年以前的对话已冷冻保存</text>
        </view>
        <view class="topbar-spacer"></view>
      </view>

      <view class="intro-block">
        <text class="intro-title">按日收好</text>
        <text class="intro-copy">这里只看过往对话。今晚的内容仍在首页继续。</text>
      </view>

      <view v-for="group in visibleGroups" :key="group.key" class="history-group">
        <view class="group-head">
          <text class="group-title">{{ group.title }}</text>
          <text class="group-count">{{ group.items.length }} 次</text>
        </view>
        <view class="conversation-list">
          <view
            v-for="item in group.items"
            :key="item.sessionId"
            class="conversation-row"
            @click="openConversation(item.sessionId)"
          >
            <view class="conversation-main">
              <text class="conversation-date">{{ formatDate(item.businessDate) }}</text>
              <text class="conversation-preview">{{ item.preview }}</text>
              <text class="conversation-meta">{{ item.userMessageCount }} 句 · {{ formatTime(item.startedAt) }}</text>
            </view>
            <text class="conversation-arrow">›</text>
          </view>
        </view>
      </view>

      <view v-if="!hasVisibleItems" class="empty-block">
        <text class="empty-title">还没有历史对话</text>
        <text class="empty-copy">今晚说过的话，会按天留在这里。</text>
      </view>

      <view v-if="frozenCount > 0" class="frozen-note">
        <text>{{ frozenCount }} 次更早的对话已冷冻保存，暂不在列表里显示。</text>
      </view>
    </view>
    <phone-login-modal
      :visible="loginVisible"
      :loading="loginLoading"
      @phone-login="handlePhoneLogin"
      @cancel="goBack"
    />
  </view>
</template>

<script>
import PhoneLoginModal from "../../components/PhoneLoginModal.vue"
import { getConversationHistory } from "../../services/conversation"
import { isAuthenticated } from "../../services/session"
import { loginFromPhoneDetail, restoreAuthState } from "../../utils/auth-flow"
import { showError } from "../../utils/ui"

export default {
  components: { PhoneLoginModal },
  data() {
    return {
      groups: [],
      frozenCount: 0,
      loginVisible: false,
      loginLoading: false,
    }
  },
  computed: {
    visibleGroups() {
      return this.groups.filter((group) => Array.isArray(group.items) && group.items.length)
    },
    hasVisibleItems() {
      return this.visibleGroups.length > 0
    },
  },
  onShow() {
    restoreAuthState()
    if (!isAuthenticated()) {
      this.loginVisible = true
      return
    }
    this.loadHistory()
  },
  methods: {
    async loadHistory() {
      try {
        const response = await getConversationHistory()
        this.groups = response && Array.isArray(response.groups) ? response.groups : []
        this.frozenCount = response && typeof response.frozenCount === "number" ? response.frozenCount : 0
      } catch (error) {
        showError(error && error.message ? error.message : "加载历史对话失败")
      }
    },
    formatDate(value) {
      if (!value) return "未知日期"
      const [year, month, day] = String(value).split("-")
      return `${year}.${month}.${day}`
    },
    formatTime(value) {
      if (!value) return "--:--"
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return "--:--"
      const hh = `${date.getHours()}`.padStart(2, "0")
      const mm = `${date.getMinutes()}`.padStart(2, "0")
      return `${hh}:${mm}`
    },
    openConversation(sessionId) {
      if (!sessionId) return
      uni.navigateTo({ url: `/pages/chat/index?sessionId=${sessionId}&readonly=1&source=conversation-history` })
    },
    async handlePhoneLogin(event) {
      if (this.loginLoading) return
      this.loginLoading = true
      try {
        await loginFromPhoneDetail(event && event.detail)
        this.loginVisible = false
        await this.loadHistory()
      } catch (error) {
        showError(error && error.message ? error.message : "登录失败")
      } finally {
        this.loginLoading = false
      }
    },
    goBack() {
      uni.reLaunch({ url: "/pages/home/index" })
    },
  },
}
</script>

<style scoped>
.conversation-history-page {
  background: #fcf9f0;
}

.conversation-history-shell {
  min-height: 100vh;
  padding: var(--np-page-top-space) 32rpx 56rpx;
}

.history-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: var(--np-capsule-avoid-space);
}

.back-button {
  width: 56rpx;
  min-height: auto;
  padding: 0;
  background: transparent;
  color: #233830;
  font-size: 34rpx;
}

.topbar-copy {
  flex: 1;
  text-align: center;
}

.topbar-title {
  display: block;
  color: #233830;
  font-size: 30rpx;
  font-weight: 700;
}

.topbar-subtitle {
  display: block;
  margin-top: 6rpx;
  color: rgba(31, 56, 48, 0.44);
  font-size: 20rpx;
}

.topbar-spacer {
  width: 56rpx;
}

.intro-block {
  margin-top: 52rpx;
}

.intro-title {
  display: block;
  color: #1f3830;
  font-size: 58rpx;
  line-height: 1.1;
  font-weight: 700;
}

.intro-copy {
  display: block;
  margin-top: 14rpx;
  color: rgba(31, 56, 48, 0.58);
  font-size: 26rpx;
  line-height: 1.55;
}

.history-group {
  margin-top: 46rpx;
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.group-title {
  color: #1f3830;
  font-size: 32rpx;
  font-weight: 700;
}

.group-count {
  color: rgba(31, 56, 48, 0.42);
  font-size: 22rpx;
}

.conversation-list {
  margin-top: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.conversation-row {
  min-height: 138rpx;
  padding: 22rpx 24rpx;
  border: 1rpx solid rgba(31, 56, 48, 0.08);
  border-radius: 8rpx;
  background: rgba(255, 255, 255, 0.62);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.conversation-main {
  min-width: 0;
  flex: 1;
}

.conversation-date {
  display: block;
  color: rgba(31, 56, 48, 0.48);
  font-size: 22rpx;
  font-weight: 700;
}

.conversation-preview {
  display: block;
  margin-top: 10rpx;
  color: #1f3830;
  font-size: 28rpx;
  line-height: 1.38;
}

.conversation-meta {
  display: block;
  margin-top: 10rpx;
  color: rgba(31, 56, 48, 0.38);
  font-size: 20rpx;
}

.conversation-arrow {
  margin-left: 18rpx;
  color: rgba(31, 56, 48, 0.26);
  font-size: 32rpx;
}

.empty-block {
  margin-top: 96rpx;
  text-align: center;
}

.empty-title,
.empty-copy,
.frozen-note {
  display: block;
  color: rgba(31, 56, 48, 0.52);
  font-size: 26rpx;
}

.empty-copy {
  margin-top: 12rpx;
  font-size: 22rpx;
}

.frozen-note {
  margin-top: 42rpx;
  padding: 22rpx 24rpx;
  border-radius: 8rpx;
  background: rgba(31, 56, 48, 0.05);
  line-height: 1.5;
}
</style>
