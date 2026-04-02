<template>
  <view :class="['chat-page', inputMode === 'voice' ? 'voice-mode' : 'text-mode']">
    <view class="chat-topbar">
      <button class="back-button" @click="goBack">‹</button>
      <view class="chat-topbar-copy">
        <text class="chat-status">正在记录...</text>
        <text v-if="inputMode === 'text'" class="chat-brand">夜拾</text>
      </view>
      <button class="finish-button" :disabled="loading || !userHasSpoken" @click="handleComplete">完成</button>
    </view>

    <scroll-view class="chat-scroll" scroll-y :scroll-into-view="scrollTarget" scroll-with-animation>
      <view class="chat-scroll-inner">
        <view
          v-for="(message, index) in messages"
          :id="'message-' + index"
          :key="index"
          :class="['message-wrap', message.role]"
        >
          <view :class="['message-bubble', bubbleClass(message)]">
            <view v-if="message.role === 'user' && message.inputType === 'voice'" class="transcript-label">语音已转录</view>
            <text class="message-copy">{{ message.text }}</text>
          </view>
          <text class="message-time">{{ message.timeLabel || defaultTime }}</text>
        </view>

        <view v-if="loading" class="typing-row">
          <view class="typing-dot"></view>
          <view class="typing-dot"></view>
          <view class="typing-dot"></view>
        </view>

        <view id="message-anchor"></view>
      </view>
    </scroll-view>

    <view v-if="inputMode === 'voice'" class="voice-composer">
      <button class="mode-switch voice-switch" @click="toggleInputMode">
        <view class="keyboard-icon">
          <view class="keyboard-top">
            <text class="keyboard-dot"></text>
            <text class="keyboard-dot"></text>
            <text class="keyboard-dot"></text>
          </view>
          <view class="keyboard-bottom"></view>
        </view>
      </button>
      <view class="voice-center">
        <view class="voice-record-stack">
          <view class="recording-hint">{{ recordingHint }}</view>
          <view class="mic-progress-ring" :style="recordingRingStyle">
            <button class="mic-button" @longpress.prevent="startRecording" @touchend.prevent="stopRecording" @touchcancel.prevent="stopRecording">
              <text class="mic-icon">{{ isRecording ? recordingCountdownLabel : "●" }}</text>
            </button>
          </view>
        </view>
      </view>
      <view class="voice-right-placeholder"></view>
    </view>

    <view v-else class="text-composer">
      <button class="text-back-mode" @click="toggleInputMode">◉</button>
      <view class="text-input-shell">
        <textarea
          v-model="inputValue"
          class="text-input"
          placeholder="输入今晚的想法..."
          confirm-type="send"
          auto-height
          @confirm="handleSend"
        />
      </view>
      <button class="send-button" :disabled="loading || !inputValue.trim()" @click="handleSend">➤</button>
      <text class="text-footnote">RECORDING THE WHISPERS OF THE NIGHT</text>
    </view>
  </view>
</template>

<script>
import { autosaveConversation, completeConversation, createConversation, getActiveConversation, getConversation, sendMessage } from "../../services/conversation"
import { transcribeAudio, uploadAudio } from "../../services/audio"
import { getSettings } from "../../services/settings"
import {
  appState,
  appendChatMessage,
  clearActiveConversation,
  hydrateConversation,
  setActiveSessionId,
  setChatInput,
  setUser,
  updateConversationDraft,
} from "../../stores/app-state"
import { isCurrentBusinessDate } from "../../utils/business-day"
import { showError, showSuccess } from "../../utils/ui"

let recorderManager = null
let autosaveTimer = null
let recordingProgressTimer = null
let assistantAudioContext = null

export default {
  data() {
    return {
      loading: false,
      isRecording: false,
      recordingHint: "长按说话，最多 20 秒",
      recordingElapsedMs: 0,
      recordingMaxDurationMs: 20000,
      recorderReady: false,
      autoSavePending: false,
      defaultTime: "22:14",
      scrollTarget: "message-anchor",
      inputMode: "voice",
      state: appState,
    }
  },
  computed: {
    businessDayResetHour() {
      return this.state.user ? this.state.user.businessDayResetHour : null
    },
    sessionId: {
      get() {
        return this.state.activeSessionId
      },
      set(value) {
        setActiveSessionId(value)
      },
    },
    inputValue: {
      get() {
        return this.state.chatState.inputValue
      },
      set(value) {
        setChatInput(value)
      },
    },
    messages() {
      return this.state.chatState.messages
    },
    recordingProgressPercent() {
      return Math.min(100, Math.round((this.recordingElapsedMs / this.recordingMaxDurationMs) * 100))
    },
    recordingRingStyle() {
      const percent = this.recordingProgressPercent
      return {
        background: `conic-gradient(#21473d 0 ${percent}%, rgba(33, 71, 61, 0.12) ${percent}% 100%)`,
      }
    },
    recordingCountdownLabel() {
      const remainingMs = Math.max(0, this.recordingMaxDurationMs - this.recordingElapsedMs)
      return `${Math.max(1, Math.ceil(remainingMs / 1000))}`
    },
    userHasSpoken: {
      get() {
        return this.state.conversationDraft.userHasSpoken
      },
      set(value) {
        updateConversationDraft({ userHasSpoken: Boolean(value) })
      },
    },
    lastAutoSavedMessageCount: {
      get() {
        return this.state.conversationDraft.lastAutoSavedMessageCount || 0
      },
      set(value) {
        updateConversationDraft({ lastAutoSavedMessageCount: value || 0 })
      },
    },
  },
  onShow() {
    this.initializePage()
  },
  async onUnload() {
    this.stopRecording({ force: true })
    this.stopAssistantAudio()
    await this.tryAutoSaveConversation({ force: true })
    if (autosaveTimer) {
      clearTimeout(autosaveTimer)
      autosaveTimer = null
    }
  },
  methods: {
    bubbleClass(message) {
      return message.role === "assistant" ? "bubble-mint" : "bubble-paper"
    },
    async initializePage() {
      try {
        await this.ensureUserSettings()
        if (this.sessionId && !isCurrentBusinessDate(this.state.activeSessionStartedAt, this.businessDayResetHour)) {
          clearActiveConversation()
        }
        await this.restoreConversationIfNeeded()
        await this.ensureSession()
        this.setupRecorder()
        this.bumpScroll()
      } catch (error) {
        showError(error && error.message ? error.message : "初始化会话失败")
      }
    },
    setupRecorder() {
      if (this.recorderReady) return
      recorderManager = uni.getRecorderManager()
      recorderManager.onStop(async (result) => {
        this.finishRecordingSession()
        try {
          if (!result || !result.tempFilePath) {
            throw new Error("没有拿到录音文件")
          }
          await this.ensureSession()
          this.recordingHint = "正在整理这段语音..."
          const uploaded = await uploadAudio(this.sessionId, result.tempFilePath)
          const transcript = await transcribeAudio(this.sessionId, uploaded.audioUrl)
          const transcriptText = (transcript && transcript.transcriptText ? transcript.transcriptText : "").trim()
          if (transcriptText) {
            await this.handleSend("voice", transcriptText)
          } else {
            showError("没有识别到有效内容，请再说一次")
          }
        } catch (error) {
          showError(error && error.message ? error.message : "语音处理失败")
        } finally {
          if (!this.isRecording) {
            this.recordingHint = "长按说话，最多 20 秒"
          }
        }
      })
      recorderManager.onError((error) => {
        this.finishRecordingSession()
        console.error("[nightly-pick][recorder] error", error)
        const errorMessage = error && error.errMsg ? `录音失败：${error.errMsg}` : "录音失败，请稍后重试"
        showError(errorMessage)
      })
      this.recorderReady = true
    },
    async ensureUserSettings() {
      if (this.state.user && this.state.user.businessDayResetHour !== undefined && this.state.user.businessDayResetHour !== null) return
      setUser(await getSettings())
    },
    async restoreConversationIfNeeded() {
      const sessionIdFromRoute = this.getRouteSessionId()
      if (sessionIdFromRoute && sessionIdFromRoute !== this.sessionId) {
        await this.loadConversation(sessionIdFromRoute)
        return
      }
      if (this.sessionId && this.messages.length > 1) return
      if (this.sessionId) {
        await this.loadConversation(this.sessionId)
        return
      }
      const activeConversation = await getActiveConversation()
      if (activeConversation && activeConversation.session) {
        hydrateConversation(activeConversation.session, this.normalizeConversationMessages(activeConversation.messages))
      }
    },
    getRouteSessionId() {
      const pages = getCurrentPages()
      const currentPage = pages[pages.length - 1]
      const options = currentPage && currentPage.options ? currentPage.options : {}
      return options.sessionId ? String(options.sessionId) : ""
    },
    async loadConversation(sessionId) {
      const response = await getConversation(sessionId)
      if (!response || !response.session) return
      hydrateConversation(response.session, this.normalizeConversationMessages(response.messages))
    },
    async ensureSession() {
      if (this.sessionId) return
      const response = await createConversation()
      this.sessionId = response.sessionId
    },
    async handleSend(inputTypeOverride, explicitText) {
      const draftText = typeof explicitText === "string" ? explicitText : this.inputValue
      if (!draftText.trim() || this.loading) return
      await this.ensureSession()
      this.loading = true
      const text = draftText.trim()
      const inputType = inputTypeOverride || this.inputMode
      this.userHasSpoken = true
      appendChatMessage({ role: "user", text, inputType, timeLabel: this.currentTimeLabel() })
      if (typeof explicitText !== "string") {
        this.inputValue = ""
      }
      this.bumpScroll()
      try {
        const response = await sendMessage(this.sessionId, text, inputType === "voice" ? "voice" : "text")
        appendChatMessage({
          role: "assistant",
          text: response.assistantReply,
          assistantAudioUrl: response.assistantAudioUrl,
          inputType: "text",
          timeLabel: this.currentTimeLabel(),
        })
        if (inputType === "voice" && response.assistantAudioUrl) {
          this.playAssistantAudio(response.assistantAudioUrl)
        } else {
          this.stopAssistantAudio()
        }
        this.inputMode = inputType === "voice" ? "voice" : "text"
        this.bumpScroll()
        if (response.shouldEnd || response.stage === "closing") {
          this.scheduleAutoSaveConversation()
        }
      } catch (error) {
        showError(error && error.message ? error.message : "发送失败")
      } finally {
        this.loading = false
      }
    },
    async startRecording() {
      if (!recorderManager || this.isRecording || this.loading) return
      const permissionGranted = await this.ensureRecordPermission()
      if (!permissionGranted) return
      this.stopAssistantAudio()
      this.isRecording = true
      this.recordingHint = "松开发送，20 秒后自动发出"
      this.recordingElapsedMs = 0
      this.startRecordingProgress()
      recorderManager.start({
        duration: this.recordingMaxDurationMs,
        format: "aac",
        sampleRate: 16000,
        numberOfChannels: 1,
        encodeBitRate: 96000,
      })
    },
    async stopRecording({ force = false } = {}) {
      if (!recorderManager || !this.isRecording) return
      this.isRecording = false
      if (force) {
        this.finishRecordingSession()
      }
      recorderManager.stop()
    },
    startRecordingProgress() {
      if (recordingProgressTimer) clearInterval(recordingProgressTimer)
      recordingProgressTimer = setInterval(() => {
        const nextElapsed = Math.min(this.recordingElapsedMs + 100, this.recordingMaxDurationMs)
        this.recordingElapsedMs = nextElapsed
        if (nextElapsed >= this.recordingMaxDurationMs) {
          this.stopRecording()
        }
      }, 100)
    },
    finishRecordingSession() {
      this.isRecording = false
      if (recordingProgressTimer) {
        clearInterval(recordingProgressTimer)
        recordingProgressTimer = null
      }
      this.recordingElapsedMs = 0
    },
    async handleComplete() {
      try {
        await this.ensureSession()
        if (autosaveTimer) {
          clearTimeout(autosaveTimer)
          autosaveTimer = null
        }
        const response = await completeConversation(this.sessionId)
        this.lastAutoSavedMessageCount = this.messages.length
        clearActiveConversation()
        showSuccess(response.notice || "已生成今夜整理")
        uni.redirectTo({ url: `/pages/record-detail/index?recordId=${response.recordId}&source=chat` })
      } catch (error) {
        showError(error && error.message ? error.message : "生成记录失败")
      }
    },
    ensureRecordPermission() {
      return new Promise((resolve) => {
        uni.getSetting({
          success: (settingResult) => {
            const currentSetting = settingResult && settingResult.authSetting ? settingResult.authSetting["scope.record"] : undefined
            if (currentSetting === false) {
              uni.showModal({
                title: "需要录音权限",
                content: "语音输入需要麦克风权限，请在设置里打开录音权限。",
                confirmText: "去设置",
                success: (modalResult) => {
                  if (!modalResult.confirm) {
                    resolve(false)
                    return
                  }
                  uni.openSetting({
                    success: (openResult) => resolve(Boolean(openResult.authSetting && openResult.authSetting["scope.record"])),
                    fail: () => resolve(false),
                  })
                },
              })
              return
            }
            uni.authorize({
              scope: "scope.record",
              success: () => resolve(true),
              fail: () => {
                showError("请先允许录音权限")
                resolve(false)
              },
            })
          },
          fail: () => {
            showError("无法获取录音权限状态")
            resolve(false)
          },
        })
      })
    },
    currentTimeLabel() {
      const now = new Date()
      const hh = `${now.getHours()}`.padStart(2, "0")
      const mm = `${now.getMinutes()}`.padStart(2, "0")
      return `${hh}:${mm}`
    },
    bumpScroll() {
      this.scrollTarget = ""
      this.$nextTick(() => {
        this.scrollTarget = "message-anchor"
      })
    },
    toggleInputMode() {
      if (this.isRecording) return
      this.inputMode = this.inputMode === "voice" ? "text" : "voice"
    },
    playAssistantAudio(audioUrl) {
      if (!audioUrl) return
      this.stopAssistantAudio()
      assistantAudioContext = uni.createInnerAudioContext()
      assistantAudioContext.autoplay = true
      assistantAudioContext.src = audioUrl
      assistantAudioContext.obeyMuteSwitch = false
      assistantAudioContext.onError((error) => {
        console.error("[nightly-pick][assistant-audio] error", error)
      })
    },
    stopAssistantAudio() {
      if (!assistantAudioContext) return
      try {
        assistantAudioContext.stop()
        assistantAudioContext.destroy()
      } catch (error) {
        console.error("[nightly-pick][assistant-audio] cleanup error", error)
      }
      assistantAudioContext = null
    },
    scheduleAutoSaveConversation() {
      if (!this.shouldAutosaveQuietly()) return
      if (autosaveTimer) clearTimeout(autosaveTimer)
      autosaveTimer = setTimeout(() => {
        this.tryAutoSaveConversation()
      }, 1200)
    },
    shouldAutosaveQuietly() {
      const delta = this.messages.length - this.lastAutoSavedMessageCount
      const userMessages = this.messages.filter((message) => message.role === "user")
      const totalUserChars = userMessages.reduce((sum, message) => sum + (message.text || "").trim().length, 0)
      return delta >= 2 && (userMessages.length >= 2 || totalUserChars >= 30)
    },
    hasRecoverableContent() {
      const userMessages = this.messages.filter((message) => message.role === "user")
      const totalUserChars = userMessages.reduce((sum, message) => sum + (message.text || "").trim().length, 0)
      return userMessages.length > 0 && totalUserChars >= 8
    },
    async tryAutoSaveConversation({ force = false } = {}) {
      if (!this.sessionId || !this.userHasSpoken || this.autoSavePending) return
      if (this.messages.length <= this.lastAutoSavedMessageCount) return
      if (force) {
        if (!this.hasRecoverableContent()) return
      } else if (!this.shouldAutosaveQuietly()) {
        return
      }
      this.autoSavePending = true
      try {
        await autosaveConversation(this.sessionId)
        this.lastAutoSavedMessageCount = this.messages.length
      } finally {
        this.autoSavePending = false
      }
    },
    async goBack() {
      this.stopRecording({ force: true })
      this.stopAssistantAudio()
      if (autosaveTimer) {
        clearTimeout(autosaveTimer)
        autosaveTimer = null
      }
      await this.tryAutoSaveConversation({ force: true })
      uni.reLaunch({ url: "/pages/home/index" })
    },
    normalizeConversationMessages(messages) {
      if (!Array.isArray(messages) || !messages.length) return this.messages
      return messages.map((message) => ({
        role: message.role,
        text: message.text,
        inputType: message.inputType,
        timeLabel: this.formatTimeLabel(message.createdAt),
      }))
    },
    formatTimeLabel(value) {
      if (!value) return this.defaultTime
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return this.defaultTime
      const hh = `${date.getHours()}`.padStart(2, "0")
      const mm = `${date.getMinutes()}`.padStart(2, "0")
      return `${hh}:${mm}`
    },
  },
}
</script>

<style scoped>
.chat-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

.voice-mode,
.text-mode {
  background:
    radial-gradient(circle at top right, rgba(173, 206, 192, 0.12), transparent 28%),
    radial-gradient(circle at bottom left, rgba(255, 181, 151, 0.12), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.38), transparent 28%),
    #fcf9f0;
}

.chat-topbar {
  position: fixed;
  top: calc(var(--status-bar-height, 0px) + 4rpx);
  left: 0;
  right: 0;
  z-index: 20;
  padding: 0 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.back-button,
.finish-button {
  min-height: auto;
  background: transparent;
}

.back-button {
  width: 56rpx;
  color: #233830;
  font-size: 34rpx;
  padding: 0;
}

.chat-topbar-copy {
  flex: 1;
  text-align: center;
}

.chat-status {
  display: block;
  font-size: 28rpx;
  color: rgba(31, 56, 48, 0.82);
  font-weight: 600;
}

.chat-brand {
  display: block;
  margin-top: 4rpx;
  font-size: 24rpx;
  color: #233830;
  font-weight: 700;
}

.finish-button {
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: #21473d;
  color: #fff;
  font-size: 22rpx;
}

.topbar-spacer {
  width: 56rpx;
}

.chat-scroll {
  min-height: 100vh;
  padding-top: calc(var(--status-bar-height, 0px) + 88rpx);
}

.chat-scroll-inner {
  padding: 20rpx 24rpx 320rpx;
}

.message-wrap {
  display: flex;
  flex-direction: column;
  margin-top: 26rpx;
}

.message-wrap.user {
  align-items: flex-end;
}

.message-bubble {
  max-width: 84%;
  padding: 28rpx 28rpx;
  border-radius: 30rpx;
  box-shadow: 0 12rpx 32rpx rgba(31, 56, 48, 0.06);
}

.bubble-mint {
  background: #d7eadc;
  color: rgba(31, 56, 48, 0.82);
  border-top-left-radius: 10rpx;
}

.bubble-paper {
  background: #e7e2da;
  color: rgba(31, 56, 48, 0.82);
  border-top-right-radius: 10rpx;
}

.transcript-label {
  margin-bottom: 12rpx;
  font-size: 18rpx;
  color: rgba(31, 56, 48, 0.38);
}

.message-copy {
  font-size: 28rpx;
  line-height: 1.68;
}

.message-time {
  margin-top: 10rpx;
  font-size: 18rpx;
  color: rgba(31, 56, 48, 0.26);
  padding: 0 8rpx;
}

.typing-row {
  margin-top: 34rpx;
  display: flex;
  justify-content: flex-end;
  gap: 10rpx;
  padding-right: 20rpx;
}

.typing-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: rgba(31, 56, 48, 0.4);
}

.voice-composer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 34rpx);
  padding: 0 34rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mode-switch,
.text-back-mode {
  width: 72rpx;
  height: 72rpx;
  min-height: auto;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.34);
  color: rgba(31, 56, 48, 0.52);
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 20rpx rgba(31, 56, 48, 0.04);
}

.voice-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.voice-record-stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18rpx;
}

.recording-hint {
  min-height: 34rpx;
  font-size: 22rpx;
  color: rgba(31, 56, 48, 0.48);
}

.mic-progress-ring {
  width: 156rpx;
  height: 156rpx;
  border-radius: 50%;
  padding: 12rpx;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.18s linear;
}

.mic-button {
  width: 128rpx;
  height: 128rpx;
  min-height: auto;
  border-radius: 50%;
  background: #16392f;
  box-shadow: 0 18rpx 42rpx rgba(31, 56, 48, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mic-icon {
  color: #fff;
  font-size: 36rpx;
  font-weight: 600;
}

.voice-right-placeholder {
  width: 72rpx;
}

.text-composer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18rpx 24rpx calc(env(safe-area-inset-bottom, 0px) + 18rpx);
  background: rgba(252, 249, 240, 0.9);
  backdrop-filter: blur(14rpx);
}

.text-composer {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
}

.text-input-shell {
  flex: 1;
  min-width: 0;
  padding: 0 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.72);
  display: flex;
  align-items: center;
}

.text-input {
  width: 100%;
  min-height: 52rpx;
  max-height: 160rpx;
  padding: 14rpx 0;
  font-size: 26rpx;
  line-height: 1.5;
  display: block;
}

.send-button {
  width: 76rpx;
  height: 76rpx;
  min-height: auto;
  border-radius: 50%;
  background: #16392f;
  color: #fff;
  font-size: 28rpx;
}

.text-footnote {
  width: 100%;
  text-align: center;
  font-size: 14rpx;
  letter-spacing: 4rpx;
  color: rgba(31, 56, 48, 0.2);
}

.keyboard-icon {
  width: 28rpx;
  height: 22rpx;
  border: 2rpx solid rgba(31, 56, 48, 0.42);
  border-radius: 6rpx;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 3rpx 4rpx;
  box-sizing: border-box;
}

.keyboard-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.keyboard-dot {
  width: 4rpx;
  height: 4rpx;
  border-radius: 50%;
  background: rgba(31, 56, 48, 0.42);
}

.keyboard-bottom {
  margin-top: 4rpx;
  height: 3rpx;
  border-radius: 999rpx;
  background: rgba(31, 56, 48, 0.28);
}
</style>
