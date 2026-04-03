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
      <view v-if="template && draft" class="share-card-live-card">
        <image class="share-card-preview-bg" :src="template.background" mode="widthFix" />
        <view class="share-card-overlay">
          <text class="share-card-date-text" :style="previewDateStyle">{{ draft.dateLabel || "" }}</text>
          <text class="share-card-headline-text" :style="previewHeadlineStyle">{{ draft.headline || "" }}</text>
          <text class="share-card-subline-text" :style="previewSublineStyle">{{ draft.subline || "" }}</text>
          <text class="share-card-brand-text" :style="previewBrandStyle">夜拾 · 今夜片段</text>
        </view>
      </view>
      <view v-if="!renderReady" class="share-card-preview loading">正在生成卡片...</view>
    </view>

    <button class="share-card-save" :disabled="saving || !renderReady" @click="saveCard">
      {{ saving ? "保存中..." : "保存这张卡片" }}
    </button>

    <canvas
      id="share-card-canvas"
      type="2d"
      class="share-card-hidden-canvas"
      :style="hiddenCanvasStyle"
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
      templateBackgroundPath: "",
      canvasNode: null,
      canvasContext: null,
      assetsReady: false,
      canvasReady: false,
      renderReady: false,
      saving: false,
      exportWidth: SHARE_CARD_CANVAS.width,
      exportHeight: SHARE_CARD_CANVAS.height,
      hiddenCanvasStyle: "width: 360px; height: 640px;",
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
    await this.prepareTemplateAssets()
    this.assetsReady = true
    await this.tryRenderCard()
  },
  async onReady() {
    await this.initializeCanvas()
    this.canvasReady = true
    await this.tryRenderCard()
  },
  methods: {
    toPreviewStyle(box = {}) {
      const style = []
      if (typeof box.x === "number") style.push(`left:${(box.x / this.exportWidth) * 100}%`)
      if (typeof box.y === "number") style.push(`top:${(box.y / this.exportHeight) * 100}%`)
      if (typeof box.width === "number") style.push(`width:${(box.width / this.exportWidth) * 100}%`)
      if (typeof box.fontSize === "number") style.push(`font-size:${box.fontSize}rpx`)
      if (typeof box.lineHeight === "number") style.push(`line-height:${box.lineHeight}rpx`)
      return style.join(";")
    },
    initializeCanvas() {
      if (this.canvasNode && this.canvasContext) {
        return Promise.resolve()
      }
      return new Promise((resolve, reject) => {
        uni
          .createSelectorQuery()
          .in(this)
          .select("#share-card-canvas")
          .fields({ node: true, size: true }, (result) => {
            if (!result || !result.node) {
              reject(new Error("画布初始化失败"))
              return
            }
            const canvas = result.node
            const context = canvas.getContext("2d")
            canvas.width = this.exportWidth
            canvas.height = this.exportHeight
            this.canvasNode = canvas
            this.canvasContext = context
            resolve()
          })
          .exec()
      })
    },
    async renderCard() {
      try {
        await this.renderExportCanvas()
        this.renderReady = true
        if (this.draft && this.draft.type === "today") {
          markTodayCardGenerated(this.draft.date, this.draft)
        }
      } catch (error) {
        console.error("[nightly-pick][share-card] render failed", error)
        this.renderReady = false
        showError("卡片生成失败")
      }
    },
    async tryRenderCard() {
      if (!this.assetsReady || !this.canvasReady || !this.template || !this.draft) {
        return
      }
      await this.renderCard()
    },
    async prepareTemplateAssets() {
      if (!this.template) {
        throw new Error("卡片模板不存在")
      }
      this.templateBackgroundPath = await this.resolveCanvasImagePath(this.template.background)
    },
    resolveCanvasImagePath(src) {
      return new Promise((resolve, reject) => {
        if (typeof wx === "undefined" || !wx.getFileSystemManager || !wx.env || !wx.env.USER_DATA_PATH) {
          reject(new Error("卡片背景加载失败：文件系统不可用"))
          return
        }
        const sourcePath = String(src || "")
        const normalizedSourcePath = sourcePath.startsWith("/") ? sourcePath : `/${sourcePath}`
        const fileName = normalizedSourcePath.split("/").pop() || `share-card-bg-${Date.now()}.png`
        const targetPath = `${wx.env.USER_DATA_PATH}/${Date.now()}-${fileName}`
        wx.getFileSystemManager().copyFile({
          srcPath: normalizedSourcePath,
          destPath: targetPath,
          success: () => {
            console.log("[nightly-pick][share-card] background copied", {
              sourcePath: normalizedSourcePath,
              targetPath,
            })
            resolve(targetPath)
          },
          fail: (copyError) => {
            console.error("[nightly-pick][share-card] background copy failed", {
              sourcePath: normalizedSourcePath,
              targetPath,
              copyError,
            })
            reject(new Error("卡片背景加载失败：复制背景图失败"))
          },
        })
      })
    },
    async renderExportCanvas() {
      const template = this.template
      if (!template) throw new Error("卡片模板不存在")
      const backgroundPath = this.templateBackgroundPath
      if (!backgroundPath) throw new Error("卡片背景加载失败")
      if (!this.canvasNode || !this.canvasContext) throw new Error("画布初始化失败")
      const backgroundImage = await this.loadCanvasImage(backgroundPath)
      const context = this.canvasContext
      context.clearRect(0, 0, this.exportWidth, this.exportHeight)
      context.fillStyle = "#0d1418"
      context.fillRect(0, 0, this.exportWidth, this.exportHeight)
      context.drawImage(backgroundImage, 0, 0, this.exportWidth, this.exportHeight)
      this.drawDate(context, template)
      this.drawHeadline(context, template)
      this.drawSubline(context, template)
      this.drawBrand(context, template)
      await this.waitForCanvasDraw(context)
    },
    loadCanvasImage(src) {
      return new Promise((resolve, reject) => {
        if (!this.canvasNode || !this.canvasNode.createImage) {
          reject(new Error("画布图片加载失败：createImage 不可用"))
          return
        }
        const image = this.canvasNode.createImage()
        image.onload = () => resolve(image)
        image.onerror = (error) => {
          console.error("[nightly-pick][share-card] canvas image load failed", {
            src,
            error,
          })
          reject(new Error("画布图片加载失败：image.onerror"))
        }
        image.src = src
      })
    },
    waitForCanvasDraw() {
      return new Promise((resolve) => {
        setTimeout(resolve, 120)
      })
    },
    drawDate(context, template) {
      context.fillStyle = template.dateColor
      context.font = this.buildCanvasFont(34)
      context.fillText(this.draft.dateLabel || "", template.datePosition.x, template.datePosition.y)
    },
    drawHeadline(context, template) {
      context.fillStyle = template.headlineColor
      context.font = this.buildCanvasFont(template.headlineBox.fontSize, 500)
      this.drawWrappedText(context, this.draft.headline || "", template.headlineBox)
    },
    drawSubline(context, template) {
      context.fillStyle = template.sublineColor
      context.font = this.buildCanvasFont(template.sublineBox.fontSize)
      this.drawWrappedText(context, this.draft.subline || "", template.sublineBox)
    },
    drawBrand(context, template) {
      context.fillStyle = template.brandColor
      context.font = this.buildCanvasFont(24)
      context.fillText("夜拾 · 今夜片段", template.brandPosition.x, template.brandPosition.y)
    },
    buildCanvasFont(fontSize, weight = 400) {
      return `${weight} ${fontSize}px sans-serif`
    },
    drawWrappedText(context, text, box) {
      if (!text) return
      const chars = String(text).split("")
      const lines = []
      let line = ""
      const maxWidth = box.width

      chars.forEach((char) => {
        const nextLine = line + char
        const metrics = context.measureText(nextLine)
        if (metrics.width > maxWidth && line) {
          lines.push(line)
          line = char
          return
        }
        line = nextLine
      })

      if (line) {
        lines.push(line)
      }

      const maxLines = box.maxLines || lines.length
      const visibleLines = lines.slice(0, maxLines)

      if (lines.length > maxLines && visibleLines.length) {
        const lastIndex = visibleLines.length - 1
        let truncated = `${visibleLines[lastIndex]}...`
        while (context.measureText(truncated).width > maxWidth && truncated.length > 1) {
          truncated = `${truncated.slice(0, -4)}...`
        }
        visibleLines[lastIndex] = truncated
      }

      visibleLines.forEach((content, index) => {
        context.fillText(content, box.x, box.y + index * box.lineHeight)
      })
    },
    exportCanvas() {
      return new Promise((resolve, reject) => {
        wx.canvasToTempFilePath({
          canvas: this.canvasNode,
          width: this.exportWidth,
          height: this.exportHeight,
          destWidth: this.exportWidth,
          destHeight: this.exportHeight,
          fileType: "png",
          success: (result) => resolve(result.tempFilePath),
          fail: reject,
        })
      })
    },
    async saveCard() {
      if (!this.renderReady || this.saving) return
      this.saving = true
      try {
        await this.renderExportCanvas()
        await new Promise((resolve) => setTimeout(resolve, 120))
        const previewImagePath = await this.exportCanvas()
        await this.ensureAlbumPermission()
        await this.saveImageToPhotosAlbum(previewImagePath)
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
  computed: {
    previewDateStyle() {
      return this.toPreviewStyle({
        x: this.template?.datePosition?.x || 0,
        y: this.template?.datePosition?.y || 0,
      })
    },
    previewHeadlineStyle() {
      return this.toPreviewStyle(this.template?.headlineBox || {})
    },
    previewSublineStyle() {
      return this.toPreviewStyle(this.template?.sublineBox || {})
    },
    previewBrandStyle() {
      return this.toPreviewStyle({
        x: this.template?.brandPosition?.x || 0,
        y: this.template?.brandPosition?.y || 0,
      })
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
  position: relative;
  background: rgba(31, 56, 48, 0.08);
}

.share-card-live-card {
  position: relative;
}

.share-card-preview-bg {
  width: 100%;
  display: block;
}

.share-card-overlay {
  position: absolute;
  inset: 0;
}

.share-card-date-text,
.share-card-headline-text,
.share-card-subline-text,
.share-card-brand-text {
  position: absolute;
  display: block;
}

.share-card-date-text {
  color: rgba(244, 243, 238, 0.84);
  font-size: 34rpx;
}

.share-card-headline-text {
  color: #f5f3ec;
  font-weight: 500;
}

.share-card-subline-text {
  color: rgba(245, 243, 236, 0.72);
}

.share-card-brand-text {
  color: rgba(245, 243, 236, 0.54);
  font-size: 24rpx;
}

.share-card-preview.loading {
  position: absolute;
  inset: 0;
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

.share-card-hidden-canvas {
  position: fixed;
  left: -9999px;
  top: -9999px;
  opacity: 0;
  pointer-events: none;
}
</style>
