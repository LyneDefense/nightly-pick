<template>
  <view :class="['chat-page', inputMode === 'voice' ? 'voice-mode' : 'text-mode']">
    <view class="chat-topbar">
      <button class="back-button" @click="goBack">‹</button>
      <view class="chat-topbar-copy">
        <text class="chat-status">正在记录...</text>
        <text v-if="inputMode === 'text'" class="chat-brand">夜拾</text>
      </view>
      <view class="topbar-spacer"></view>
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

    <view v-if="shouldShowSummaryCard" class="summary-action-card">
      <view class="summary-action-copy">
        <text class="summary-action-title">{{ summaryCardTitle }}</text>
        <text class="summary-action-desc">{{ summaryCardDescription }}</text>
      </view>
      <button
        v-if="summaryCardButtonLabel"
        class="summary-action-button"
        :disabled="summaryActionLoading"
        @click="handleSummaryAction"
      >
        {{ summaryCardButtonLabel }}
      </button>
      <view v-else class="summary-action-loading">
        <view class="summary-loading-dot"></view>
        <view class="summary-loading-dot"></view>
        <view class="summary-loading-dot"></view>
      </view>
    </view>

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
          <view :class="['mic-progress-ring', { recording: isRecording }]">
            <canvas
              canvas-id="recording-progress-ring"
              class="mic-progress-canvas"
              :style="ringCanvasInlineStyle"
              :width="ringCanvasSizePx"
              :height="ringCanvasSizePx"
            ></canvas>
            <button :class="['mic-button', { recording: isRecording }]" @longpress.prevent="startRecording" @touchend.prevent="stopRecording" @touchcancel.prevent="stopRecording">
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
import { createConversation, getActiveConversation, getConversation, requestConversationSummary, sendMessage } from "../../services/conversation"
import { transcribeAudio, uploadAudio } from "../../services/audio"
import { getRecords } from "../../services/records"
import { getSettings } from "../../services/settings"
import {
  appState,
  appendChatMessage,
  clearActiveConversation,
  hydrateConversation,
  setConversationSummary,
  setActiveSessionId,
  setChatInput,
  setRecords,
  setUser,
  updateConversationDraft,
} from "../../stores/app-state"
import { isCurrentBusinessDate } from "../../utils/business-day"
import { showError, showSuccess } from "../../utils/ui"

let recorderManager = null
let recordingProgressTimer = null
let assistantAudioContext = null
let assistantAudioFilePath = ""
let summaryPollTimer = null
let autoSummaryTimer = null

export default {
  data() {
    return {
      loading: false,
      isRecording: false,
      recordingHint: "长按说话，最多 20 秒",
      recordingElapsedMs: 0,
      recordingMaxDurationMs: 20000,
      ringCanvasSizePx: 156,
      recorderReady: false,
      defaultTime: "22:14",
      scrollTarget: "message-anchor",
      inputMode: "voice",
      summaryActionLoading: false,
      summaryActionTriggered: false,
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
    conversationSummary() {
      return this.state.conversationSummary || {
        status: "noRecordYet",
        recordId: "",
        userMessageCount: 0,
        summarizedUserMessageCount: 0,
      }
    },
    recordingProgressPercent() {
      return Math.min(100, Math.round((this.recordingElapsedMs / this.recordingMaxDurationMs) * 100))
    },
    recordingCountdownLabel() {
      const remainingMs = Math.max(0, this.recordingMaxDurationMs - this.recordingElapsedMs)
      return `${Math.max(1, Math.ceil(remainingMs / 1000))}`
    },
    ringCanvasInlineStyle() {
      return {
        width: `${this.ringCanvasSizePx}px`,
        height: `${this.ringCanvasSizePx}px`,
      }
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
    unsummarizedUserMessages() {
      const userMessages = this.messages.filter((message) => message.role === "user" && message.text)
      return userMessages.slice(this.conversationSummary.summarizedUserMessageCount || 0)
    },
    unsummarizedCharCount() {
      return this.unsummarizedUserMessages.reduce((sum, message) => sum + (message.text || "").trim().length, 0)
    },
    hasMeaningfulUnsummarizedContent() {
      return this.unsummarizedUserMessages.length >= 2 || this.unsummarizedCharCount >= 12
    },
    shouldShowSummaryCard() {
      if (this.conversationSummary.status === "recordGenerating") return true
      if (this.conversationSummary.status === "recordNeedsRefresh") return this.hasMeaningfulUnsummarizedContent
      if (this.conversationSummary.status === "noRecordYet") return this.hasRecoverableContent()
      return false
    },
    summaryCardTitle() {
      if (this.conversationSummary.status === "recordGenerating") {
        return "正在整理刚才的内容"
      }
      if (this.conversationSummary.status === "recordNeedsRefresh") {
        return "把刚才补充的也收进去"
      }
      return "把今晚收一下"
    },
    summaryCardDescription() {
      if (this.conversationSummary.status === "recordGenerating") {
        return "你可以继续聊，整理好了会自动更新。"
      }
      if (this.conversationSummary.status === "recordNeedsRefresh") {
        return "刚才补充的这些，也可以顺手并进今晚那页。"
      }
      return "把刚才说到的，整理成今晚的一页。"
    },
    summaryCardButtonLabel() {
      if (this.conversationSummary.status === "recordGenerating") return ""
      if (this.conversationSummary.status === "recordNeedsRefresh") return "更新一下"
      return "整理今晚"
    },
  },
  onShow() {
    this.initializePage()
  },
  mounted() {
    this.$nextTick(() => {
      this.drawRecordingRing(0)
    })
  },
  async onUnload() {
    this.stopRecording({ force: true })
    this.stopAssistantAudio()
    this.clearSummaryTimers()
  },
  methods: {
    bubbleClass(message) {
      return message.role === "assistant" ? "bubble-mint" : "bubble-paper"
    },
    async initializePage() {
      try {
        this.syncRingCanvasSize()
        await this.ensureUserSettings()
        if (this.sessionId && !isCurrentBusinessDate(this.state.activeSessionStartedAt, this.businessDayResetHour)) {
          clearActiveConversation()
        }
        await this.restoreConversationIfNeeded()
        await this.ensureSession()
        this.setupRecorder()
        if (this.conversationSummary.status === "recordGenerating") {
          this.startSummaryStatusPolling(false)
        }
        this.bumpScroll()
        this.$nextTick(() => {
          this.drawRecordingRing(this.recordingProgressPercent)
        })
      } catch (error) {
        showError(error && error.message ? error.message : "初始化会话失败")
      }
    },
    syncRingCanvasSize() {
      const systemInfo = uni.getSystemInfoSync()
      this.ringCanvasSizePx = Math.round((systemInfo.windowWidth * 156) / 750)
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
          this.recordingHint = "长按说话，最多 20 秒"
          this.drawRecordingRing(0)
        }
      })
      recorderManager.onError((error) => {
        this.finishRecordingSession()
        this.recordingHint = "长按说话，最多 20 秒"
        this.drawRecordingRing(0)
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
        hydrateConversation(
          activeConversation.session,
          this.normalizeConversationMessages(activeConversation.messages),
          activeConversation.summaryStatus
        )
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
      hydrateConversation(
        response.session,
        this.normalizeConversationMessages(response.messages),
        response.summaryStatus
      )
    },
    async ensureSession() {
      if (this.sessionId) return
      const response = await createConversation()
      this.sessionId = response.sessionId
      setConversationSummary(response.summaryStatus)
    },
    async handleSend(inputTypeOverride, explicitText) {
      const draftText = typeof explicitText === "string" ? explicitText : this.inputValue
      if (!draftText.trim() || this.loading) return
      await this.ensureSession()
      this.clearAutoSummaryTimer()
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
        setConversationSummary(response.summaryStatus)
        this.inputMode = inputType === "voice" ? "voice" : "text"
        this.bumpScroll()
        if (response.shouldEnd || response.stage === "closing") {
          this.scheduleAutoSummary()
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
      this.drawRecordingRing(0)
      this.startRecordingProgress()
      recorderManager.start({
        duration: this.recordingMaxDurationMs,
        format: "mp3",
        sampleRate: 16000,
        numberOfChannels: 1,
      })
    },
    async stopRecording({ force = false } = {}) {
      if (!recorderManager || !this.isRecording) return
      this.isRecording = false
      this.recordingHint = "正在整理这段语音..."
      this.drawRecordingRing(0)
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
        this.drawRecordingRing(this.recordingProgressPercent)
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
      this.drawRecordingRing(0)
    },
    drawRecordingRing(percent = 0) {
      const context = uni.createCanvasContext("recording-progress-ring", this)
      const size = this.ringCanvasSizePx
      const lineWidth = Math.max(6, Math.round(size * 0.064))
      const center = size / 2
      const radius = center - lineWidth * 1.2
      const startAngle = -Math.PI / 2
      const endAngle = startAngle + Math.PI * 2 * (Math.max(0, Math.min(100, percent)) / 100)

      context.clearRect(0, 0, size, size)

      context.beginPath()
      context.setLineWidth(lineWidth)
      context.setStrokeStyle("rgba(33, 71, 61, 0.34)")
      context.setLineCap("round")
      context.arc(center, center, radius, 0, Math.PI * 2, false)
      context.stroke()

      if (percent > 0) {
        context.beginPath()
        context.setLineWidth(lineWidth)
        context.setStrokeStyle("#21473d")
        context.setLineCap("round")
        context.arc(center, center, radius, startAngle, endAngle, false)
        context.stroke()
      }

      context.draw()
    },
    async handleSummaryAction() {
      if (this.summaryActionLoading || !this.sessionId) return
      this.clearAutoSummaryTimer()
      await this.requestSummary({ userInitiated: true })
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
      this.prepareAssistantAudio(audioUrl)
        .then((playableUrl) => {
          this.stopAssistantAudio()
          assistantAudioContext = uni.createInnerAudioContext()
          assistantAudioContext.autoplay = true
          assistantAudioContext.src = playableUrl
          assistantAudioContext.obeyMuteSwitch = false
          assistantAudioContext.onError((error) => {
            console.error("[nightly-pick][assistant-audio] error", error)
            showError("语音回复播放失败")
          })
        })
        .catch((error) => {
          console.error("[nightly-pick][assistant-audio] prepare error", error)
          showError("语音回复准备失败")
        })
    },
    prepareAssistantAudio(audioUrl) {
      if (!audioUrl.startsWith("data:audio/")) {
        return Promise.resolve(audioUrl)
      }
      const [, mimeType = "audio/mpeg", base64Content = ""] =
        audioUrl.match(/^data:(audio\/[^;]+);base64,(.+)$/) || []
      if (!base64Content) {
        return Promise.reject(new Error("无效的语音数据"))
      }
      const fileExtension = mimeType.includes("mpeg") ? "mp3" : mimeType.split("/")[1] || "mp3"
      const userDataPath =
        typeof wx !== "undefined" && wx.env && wx.env.USER_DATA_PATH ? wx.env.USER_DATA_PATH : ""
      if (!userDataPath || typeof wx === "undefined" || !wx.getFileSystemManager) {
        return Promise.reject(new Error("当前环境不支持本地语音缓存"))
      }
      const filePath = `${userDataPath}/nightly-reply-${Date.now()}.${fileExtension}`
      const fileSystemManager = wx.getFileSystemManager()
      return new Promise((resolve, reject) => {
        fileSystemManager.writeFile({
          filePath,
          data: base64Content,
          encoding: "base64",
          success: () => {
            assistantAudioFilePath = filePath
            resolve(filePath)
          },
          fail: reject,
        })
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
    hasRecoverableContent() {
      const userMessages = this.messages.filter((message) => message.role === "user")
      const totalUserChars = userMessages.reduce((sum, message) => sum + (message.text || "").trim().length, 0)
      return userMessages.length > 0 && totalUserChars >= 8
    },
    scheduleAutoSummary() {
      if (!this.sessionId || !this.hasMeaningfulUnsummarizedContent) return
      if (!["noRecordYet", "recordNeedsRefresh"].includes(this.conversationSummary.status)) return
      this.clearAutoSummaryTimer()
      autoSummaryTimer = setTimeout(() => {
        this.requestSummary({ userInitiated: false })
      }, 12000)
    },
    clearAutoSummaryTimer() {
      if (!autoSummaryTimer) return
      clearTimeout(autoSummaryTimer)
      autoSummaryTimer = null
    },
    clearSummaryPollTimer() {
      if (!summaryPollTimer) return
      clearInterval(summaryPollTimer)
      summaryPollTimer = null
    },
    clearSummaryTimers() {
      this.clearAutoSummaryTimer()
      this.clearSummaryPollTimer()
    },
    async requestSummary({ userInitiated = false } = {}) {
      if (!this.sessionId || this.summaryActionLoading) return
      this.summaryActionLoading = true
      this.summaryActionTriggered = userInitiated
      try {
        const response = await requestConversationSummary(this.sessionId)
        setConversationSummary(response.summaryStatus)
        if (response.summaryStatus && response.summaryStatus.status === "recordGenerating") {
          this.startSummaryStatusPolling(userInitiated)
        } else if (userInitiated) {
          showSuccess(response.notice || "已为你整理")
        }
      } catch (error) {
        if (userInitiated) {
          showError(error && error.message ? error.message : "整理失败")
        }
      } finally {
        this.summaryActionLoading = false
      }
    },
    startSummaryStatusPolling(userInitiated = false) {
      this.clearSummaryPollTimer()
      summaryPollTimer = setInterval(async () => {
        try {
          const response = await getConversation(this.sessionId)
          if (!response || !response.session) return
          hydrateConversation(
            response.session,
            this.normalizeConversationMessages(response.messages),
            response.summaryStatus
          )
          if (response.summaryStatus && response.summaryStatus.status !== "recordGenerating") {
            this.clearSummaryPollTimer()
            await this.refreshRecords()
            if ((this.summaryActionTriggered || userInitiated) && response.summaryStatus.status === "recordUpToDate") {
              showSuccess("已更新今晚整理")
            }
            this.summaryActionTriggered = false
          }
        } catch (error) {
          this.clearSummaryPollTimer()
          if (this.summaryActionTriggered || userInitiated) {
            showError("整理状态更新失败")
          }
          this.summaryActionTriggered = false
        }
      }, 1500)
    },
    async refreshRecords() {
      try {
        setRecords(await getRecords())
      } catch (error) {
        console.error("[nightly-pick][records] refresh failed", error)
      }
    },
    async goBack() {
      this.stopRecording({ force: true })
      this.stopAssistantAudio()
      this.clearSummaryTimers()
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
  top: calc(var(--np-safe-top) + 4rpx);
  left: 0;
  right: 0;
  z-index: 20;
  padding: 0 calc(24rpx + var(--np-capsule-avoid-space)) 0 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.back-button {
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

.topbar-spacer {
  width: 56rpx;
}

.chat-scroll {
  min-height: 100vh;
  padding-top: calc(var(--np-safe-top) + 88rpx);
}

.chat-scroll-inner {
  padding: 20rpx 24rpx 420rpx;
}

.summary-action-card {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: calc(var(--np-safe-bottom) + 198rpx);
  z-index: 18;
  padding: 24rpx 24rpx 22rpx;
  border-radius: 28rpx;
  background: rgba(255, 252, 246, 0.96);
  box-shadow:
    0 16rpx 36rpx rgba(31, 56, 48, 0.08),
    inset 0 0 0 1rpx rgba(31, 56, 48, 0.04);
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.summary-action-copy {
  flex: 1;
  min-width: 0;
}

.summary-action-title {
  display: block;
  font-size: 28rpx;
  color: rgba(31, 56, 48, 0.9);
  font-weight: 600;
}

.summary-action-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: rgba(31, 56, 48, 0.54);
}

.summary-action-button {
  min-height: 72rpx;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: #21473d;
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
  flex-shrink: 0;
}

.summary-action-loading {
  min-width: 108rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  flex-shrink: 0;
}

.summary-loading-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: rgba(31, 56, 48, 0.4);
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
  bottom: calc(var(--np-safe-bottom) + 22rpx);
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
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 2rpx rgba(33, 71, 61, 0.16);
  position: relative;
}

.mic-progress-ring.recording {
  box-shadow:
    0 0 0 10rpx rgba(33, 71, 61, 0.08),
    0 18rpx 36rpx rgba(31, 56, 48, 0.12);
}

.mic-progress-canvas {
  position: absolute;
  inset: 0;
  width: 156rpx;
  height: 156rpx;
  pointer-events: none;
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
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.mic-button.recording {
  transform: scale(0.96);
  background: #21473d;
  box-shadow: 0 10rpx 24rpx rgba(31, 56, 48, 0.2);
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
  padding: 18rpx 24rpx calc(var(--np-safe-bottom) + 18rpx);
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
