<template>
  <view class="np-page settings-page">
    <view class="settings-shell">
      <view class="settings-topbar">
        <view class="settings-topbar-left" @click="goToHome">
          <text class="topbar-back">‹</text>
          <text class="topbar-brand">夜拾</text>
        </view>
      </view>

      <view class="settings-head">
        <text class="settings-title">设置</text>
        <text class="settings-subtitle">调整您的夜晚对话体验与数据偏好</text>
      </view>

      <view class="profile-card">
        <view class="avatar">{{ avatarText }}</view>
        <view class="profile-copy">
          <text class="profile-name">{{ user ? user.nickname : "林舒" }}</text>
          <text class="profile-mail">lin.shu@example.com</text>
          <button class="profile-link" @click="showProfileTip">编辑个人资料</button>
        </view>
      </view>

      <view class="group-block">
        <text class="group-label">对话偏好</text>
        <view class="group-card">
          <view class="setting-row">
            <view class="setting-left">
              <text class="setting-icon">◉</text>
              <view>
                <text class="setting-name">语音反馈</text>
                <text class="setting-desc">对话时开启 AI 语音实时响应</text>
              </view>
            </view>
            <switch checked color="#21473d" />
          </view>
          <view class="setting-row">
            <view class="setting-left">
              <text class="setting-icon">◌</text>
              <view>
                <text class="setting-name">AI 对话语气</text>
                <text class="setting-desc">当前：温柔疗愈</text>
              </view>
            </view>
            <text class="setting-arrow">›</text>
          </view>
        </view>
      </view>

      <view class="group-block">
        <text class="group-label">记忆与智能</text>
        <view class="group-card">
          <view class="setting-row">
            <view class="setting-left">
              <text class="setting-icon">✦</text>
              <view>
                <text class="setting-name">长期记忆功能</text>
                <text class="setting-desc">允许 AI 引用过去数周的对话背景以提供更深度的陪伴感</text>
              </view>
            </view>
            <switch :checked="user && user.allowMemoryReference" color="#21473d" @change="toggleMemory" />
          </view>
        </view>
      </view>

      <view class="group-block">
        <text class="group-label">数据安全</text>
        <button class="danger-card logout">退出登录</button>
        <button class="danger-card" @click="handleClearHistory">清空所有对话记录</button>
      </view>

      <view class="version-block">
        <text class="version-brand">夜拾</text>
        <text class="version-copy">VERSION 2.4.0 (BUILD 82)</text>
      </view>
    </view>

    <np-bottom-nav active="settings" />
  </view>
</template>

<script>
import NpBottomNav from "../../components/NpBottomNav.vue"
import { clearHistory, getSettings, updateSettings } from "../../services/settings"
import { appState, setMemories, setRecords, setUser } from "../../stores/app-state"
import { showError, showSuccess } from "../../utils/ui"

export default {
  components: { NpBottomNav },
  data() {
    return { state: appState }
  },
  computed: {
    user() {
      return this.state.user
    },
    avatarText() {
      const name = this.user && this.user.nickname ? this.user.nickname : "林舒"
      return name.slice(0, 1)
    },
  },
  onShow() {
    this.loadData()
  },
  methods: {
    async loadData() {
      try {
        setUser(await getSettings())
      } catch (error) {
        showError(error && error.message ? error.message : "加载设置失败")
      }
    },
    async toggleMemory(event) {
      try {
        setUser(await updateSettings(Boolean(event.detail.value)))
      } catch (error) {
        showError(error && error.message ? error.message : "更新设置失败")
      }
    },
    async handleClearHistory() {
      try {
        await clearHistory()
        setMemories([])
        setRecords([])
        showSuccess("历史已清空")
      } catch (error) {
        showError(error && error.message ? error.message : "清空历史失败")
      }
    },
    showProfileTip() {
      showSuccess("个人资料编辑后续开放")
    },
    goToHome() {
      uni.reLaunch({ url: "/pages/home/index" })
    },
  },
}
</script>

<style scoped>
.settings-page {
  background: #fcf9f0;
}

.settings-shell {
  min-height: 100vh;
  padding: var(--np-page-top-space) var(--np-page-side-space) var(--np-page-bottom-space);
}

.settings-topbar {
  padding-right: var(--np-capsule-avoid-space);
}

.settings-topbar-left {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding-top: 8rpx;
}

.topbar-back {
  font-size: 34rpx;
  color: #1f3830;
}

.topbar-brand {
  font-size: 28rpx;
  color: #1f3830;
  font-weight: 700;
}

.settings-head {
  margin-top: 42rpx;
}

.settings-title {
  display: block;
  font-size: 68rpx;
  color: #1f3830;
  font-weight: 700;
}

.settings-subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgba(31, 56, 48, 0.54);
}

.profile-card {
  margin-top: 30rpx;
  padding: 24rpx;
  border-radius: 28rpx;
  background: rgba(244, 239, 229, 0.78);
  display: flex;
  align-items: center;
  gap: 22rpx;
}

.avatar {
  width: 92rpx;
  height: 92rpx;
  border-radius: 50%;
  background: linear-gradient(145deg, #5ca7a0, #2a6f6b);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 700;
}

.profile-name {
  display: block;
  font-size: 36rpx;
  color: #1f3830;
  font-weight: 700;
}

.profile-mail {
  display: block;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: rgba(31, 56, 48, 0.52);
}

.profile-link {
  min-height: auto;
  margin-top: 10rpx;
  background: transparent;
  padding: 0;
  color: rgba(31, 56, 48, 0.6);
  font-size: 18rpx;
}

.group-block {
  margin-top: 38rpx;
}

.group-label {
  display: block;
  margin-bottom: 14rpx;
  font-size: 18rpx;
  color: rgba(31, 56, 48, 0.26);
  letter-spacing: 3rpx;
}

.group-card {
  border-radius: 28rpx;
  background: rgba(235, 232, 223, 0.74);
  overflow: hidden;
}

.setting-row {
  padding: 26rpx 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.setting-row + .setting-row {
  border-top: 1rpx solid rgba(31, 56, 48, 0.06);
}

.setting-left {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.setting-icon {
  width: 48rpx;
  text-align: center;
  font-size: 26rpx;
  color: #1f3830;
}

.setting-name {
  display: block;
  font-size: 28rpx;
  color: #1f3830;
  font-weight: 600;
}

.setting-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 18rpx;
  line-height: 1.5;
  color: rgba(31, 56, 48, 0.52);
}

.setting-arrow {
  font-size: 24rpx;
  color: rgba(31, 56, 48, 0.24);
}

.danger-card {
  width: 100%;
  margin-top: 14rpx;
  min-height: 110rpx;
  border-radius: 28rpx;
  background: rgba(244, 239, 229, 0.78);
  color: rgba(31, 56, 48, 0.78);
  text-align: left;
  padding: 0 28rpx;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  line-height: 1.25;
}

.danger-card.logout {
  color: #d7554f;
}

.version-block {
  margin-top: 84rpx;
  text-align: center;
}

.version-brand {
  display: block;
  font-size: 30rpx;
  color: rgba(31, 56, 48, 0.28);
  font-weight: 700;
}

.version-copy {
  display: block;
  margin-top: 8rpx;
  font-size: 14rpx;
  color: rgba(31, 56, 48, 0.18);
  letter-spacing: 1rpx;
}
</style>
