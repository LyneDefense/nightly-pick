<template>
  <view class="share-card-page">
    <view class="share-card-topbar">
      <button class="share-card-back" @click="goBack">‹</button>
      <text class="share-card-title">这一页，已经替你留好了</text>
      <view class="share-card-spacer"></view>
    </view>

    <view class="share-card-copy">
      <text class="share-card-copy-main">你可以把它留给自己。</text>
      <text class="share-card-copy-sub">这张卡已经生成好了，保存下来就行。</text>
    </view>

    <view class="share-card-preview-shell">
      <image v-if="previewImagePath" class="share-card-preview" :src="previewImagePath" mode="aspectFill" />
      <view v-else class="share-card-preview loading">正在生成卡片...</view>
    </view>

    <button class="share-card-save" :disabled="saving || !previewImagePath" @click="saveCard">
      {{ saving ? "保存中..." : "保存这张卡片" }}
    </button>

    <canvas
      canvas-id="share-card-canvas"
      class="share-card-canvas"
      :style="{ width: `${canvasWidth / 2}px`, height: `${canvasHeight / 2}px` }"
      :width="canvasWidth"
      :height="canvasHeight"
    ></canvas>
  </view>
</template>

<script>
import { SHARE_CARD_CANVAS, getShareCardTemplate } from "../../share-card/templates"
import { clearShareCardDraft, markTodayCardGenerated, readShareCardDraft } from "../../utils/share-card"
import { showError, showSuccess } from "../../utils/ui"

export default {
  data() {
    return {
      draft: null,
      template: null,
      previewImagePath: "",
      saving: false,
      canvasWidth: SHARE_CARD_CANVAS.width,
      canvasHeight: SHARE_CARD_CANVAS.height,
      routeSource: "detail",
    }
  },
  async onLoad(options) {
    this.routeSource = options && options.from ? String(options.from) : "detail"
    this.draft = readShareCardDraft()
    if (!this.draft) {
      showError("卡片数据不存在")
      this.goBack()
      return
    }
    this.template = getShareCardTemplate(this.draft.type)
    await this.renderCard()
  },
  methods: {
    async renderCard() {
      try {
        const template = this.template
        if (!template) throw new Error("卡片模板不存在")
        const imageInfo = await this.getImageInfo(template.background)
        const context = uni.createCanvasContext("share-card-canvas", this)
        context.clearRect(0, 0, this.canvasWidth, this.canvasHeight)
        context.drawImage(imageInfo.path, 0, 0, this.canvasWidth, this.canvasHeight)
        this.drawDate(context, template)
        this.drawHeadline(context, template)
        this.drawSubline(context, template)
        this.drawBrand(context, template)
        await new Promise((resolve) => {
          context.draw(false, resolve)
        })
        this.previewImagePath = await this.exportCanvas()
        if (this.draft && this.draft.type === "today") {
          markTodayCardGenerated(this.draft.date, this.draft)
        }
      } catch (error) {
        console.error("[nightly-pick][share-card] render failed", error)
        showError("卡片生成失败")
      }
    },
    drawDate(context, template) {
      context.setFillStyle(template.dateColor)
      context.setFontSize(34)
      context.fillText(this.draft.dateLabel || "", template.datePosition.x, template.datePosition.y)
    },
    drawHeadline(context, template) {
      context.setFillStyle(template.headlineColor)
      context.setFontSize(template.headlineBox.fontSize)
      this.drawWrappedText(context, this.draft.headline || "", template.headlineBox)
    },
    drawSubline(context, template) {
      context.setFillStyle(template.sublineColor)
      context.setFontSize(template.sublineBox.fontSize)
      this.drawWrappedText(context, this.draft.subline || "", template.sublineBox)
    },
    drawBrand(context, template) {
      context.setFillStyle(template.brandColor)
      context.setFontSize(24)
      context.fillText("夜拾 · 今夜片段", template.brandPosition.x, template.brandPosition.y)
    },
    drawWrappedText(context, text, box) {
      if (!text) return
      const chars = String(text).split("")
      let line = ""
      let lineIndex = 0
      const maxY = this.canvasHeight - 160
      chars.forEach((char, index) => {
        const nextLine = line + char
        const metrics = context.measureText(nextLine)
        if (metrics.width > box.width && line) {
          context.fillText(line, box.x, box.y + lineIndex * box.lineHeight)
          line = char
          lineIndex += 1
        } else {
          line = nextLine
        }
        if (index === chars.length - 1 && line && box.y + lineIndex * box.lineHeight <= maxY) {
          context.fillText(line, box.x, box.y + lineIndex * box.lineHeight)
        }
      })
    },
    exportCanvas() {
      return new Promise((resolve, reject) => {
        uni.canvasToTempFilePath(
          {
            canvasId: "share-card-canvas",
            width: this.canvasWidth,
            height: this.canvasHeight,
            destWidth: this.canvasWidth,
            destHeight: this.canvasHeight,
            success: (result) => resolve(result.tempFilePath),
            fail: reject,
          },
          this
        )
      })
    },
    getImageInfo(src) {
      return new Promise((resolve, reject) => {
        uni.getImageInfo({
          src,
          success: resolve,
          fail: reject,
        })
      })
    },
    async saveCard() {
      if (!this.previewImagePath || this.saving) return
      this.saving = true
      try {
        await this.ensureAlbumPermission()
        await this.saveImageToPhotosAlbum(this.previewImagePath)
        showSuccess("已保存到相册")
        clearShareCardDraft()
      } catch (error) {
        showError(error && error.message ? error.message : "保存失败")
      } finally {
        this.saving = false
      }
    },
    ensureAlbumPermission() {
      return new Promise((resolve, reject) => {
        uni.authorize({
          scope: "scope.writePhotosAlbum",
          success: resolve,
          fail: () => reject(new Error("请允许保存到相册")),
        })
      })
    },
    saveImageToPhotosAlbum(filePath) {
      return new Promise((resolve, reject) => {
        uni.saveImageToPhotosAlbum({
          filePath,
          success: resolve,
          fail: reject,
        })
      })
    },
    goBack() {
      clearShareCardDraft()
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
        return
      }
      if (this.routeSource === "chat") {
        uni.reLaunch({ url: "/pages/chat/index" })
        return
      }
      uni.reLaunch({ url: "/pages/home/index" })
    },
  },
}
</script>

<style scoped>
.share-card-page {
  min-height: 100vh;
  background: #fcf9f0;
  padding: var(--np-page-top-space) 28rpx calc(var(--np-safe-bottom) + 44rpx);
}

.share-card-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.share-card-back,
.share-card-spacer {
  width: 52rpx;
}

.share-card-back {
  min-height: auto;
  background: transparent;
  color: #233830;
  font-size: 34rpx;
  padding: 0;
}

.share-card-title {
  font-size: 28rpx;
  color: rgba(31, 56, 48, 0.86);
  font-weight: 600;
}

.share-card-copy {
  margin-top: 34rpx;
}

.share-card-copy-main {
  display: block;
  font-size: 48rpx;
  line-height: 1.3;
  color: rgba(31, 56, 48, 0.92);
}

.share-card-copy-sub {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: rgba(31, 56, 48, 0.5);
}

.share-card-preview-shell {
  margin-top: 36rpx;
  border-radius: 36rpx;
  overflow: hidden;
  box-shadow: 0 24rpx 48rpx rgba(18, 31, 27, 0.14);
}

.share-card-preview {
  width: 100%;
  height: 1120rpx;
  display: block;
  background: rgba(31, 56, 48, 0.08);
}

.share-card-preview.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(31, 56, 48, 0.54);
}

.share-card-save {
  margin-top: 34rpx;
  min-height: 92rpx;
  border-radius: 999rpx;
  background: #21473d;
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

.share-card-canvas {
  position: fixed;
  left: -9999px;
  top: -9999px;
  opacity: 0;
  pointer-events: none;
}
</style>
