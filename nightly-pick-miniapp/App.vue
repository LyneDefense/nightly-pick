<style>
page {
  background: var(--surface-base);
  color: var(--ink-strong);
  font-family: "Avenir Next", "PingFang SC", "Hiragino Sans GB", sans-serif;
  overflow-x: hidden;
}

:root,
page {
  --np-safe-top: env(safe-area-inset-top, 0px);
  --np-safe-bottom: env(safe-area-inset-bottom, 0px);
  --np-page-top-space: calc(var(--np-safe-top) + 44rpx);
  --np-page-side-space: 24rpx;
  --np-capsule-avoid-space: 176rpx;
  --np-nav-height: 132rpx;
  --np-nav-bottom-space: calc(var(--np-safe-bottom) + 12rpx);
  --np-page-bottom-space: calc(var(--np-nav-height) + var(--np-safe-bottom) + 32rpx);
  --surface-base: #fcf9f0;
  --surface-soft: #f8f3ea;
  --surface-card: rgba(255, 255, 255, 0.9);
  --surface-muted: #f1ece2;
  --surface-tint: rgba(255, 255, 255, 0.76);
  --ink-strong: #1d1b17;
  --ink-soft: #727975;
  --ink-deep: #16342b;
  --accent-deep: #21473d;
  --accent-deeper: #16342b;
  --accent-warm: #8d4d31;
  --accent-warm-soft: #ffdbcd;
  --accent-mint: #d6eadf;
  --line-soft: rgba(70, 101, 90, 0.1);
  --line-softer: rgba(29, 27, 23, 0.06);
  --line-strong: rgba(22, 52, 43, 0.14);
  --shadow-soft: 0 18rpx 48rpx rgba(29, 27, 23, 0.08);
  --shadow-card: 0 8rpx 32rpx rgba(29, 27, 23, 0.04);
  --shadow-nav: 0 -10rpx 36rpx rgba(29, 27, 23, 0.05);
  --radius-card: 28rpx;
  --radius-panel: 36rpx;
  --radius-pill: 999rpx;
}

button {
  min-height: 88rpx;
  border: none;
  border-radius: 24rpx;
  font-size: 30rpx;
  padding: 0;
}

input,
textarea {
  color: var(--ink-strong);
}

button::after {
  border: none;
}

.np-page,
.np-page view,
.np-page text,
.np-page button,
.np-page input,
.np-page textarea {
  box-sizing: border-box;
}

.np-page {
  min-height: 100vh;
  position: relative;
  background:
    radial-gradient(circle at top right, rgba(173, 206, 192, 0.12), transparent 28%),
    radial-gradient(circle at bottom left, rgba(255, 181, 151, 0.12), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.35), transparent 24%),
    var(--surface-base);
}

.np-page::before {
  content: "";
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: calc(var(--np-safe-top) + 88rpx);
  background:
    linear-gradient(180deg, rgba(242, 236, 224, 0.96) 0%, rgba(247, 242, 233, 0.84) 68%, rgba(252, 249, 240, 0) 100%);
  pointer-events: none;
  z-index: 0;
}

.np-page > * {
  position: relative;
  z-index: 1;
}

.np-shell {
  padding: var(--np-page-top-space) var(--np-page-side-space) var(--np-page-bottom-space);
  box-sizing: border-box;
}

.np-topbar {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.np-topbar-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.np-brand {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.np-brand-mark {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: linear-gradient(145deg, #17372e, #34584e);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20rpx;
}

.np-brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.np-brand-name {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--ink-deep);
  line-height: 1.1;
}

.np-brand-sub {
  font-size: 18rpx;
  color: rgba(22, 52, 43, 0.52);
  letter-spacing: 4rpx;
  text-transform: uppercase;
}

.np-icon-button {
  min-width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.58);
  color: var(--ink-deep);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
}

.np-section-title {
  font-size: 22rpx;
  letter-spacing: 6rpx;
  text-transform: uppercase;
  color: rgba(22, 52, 43, 0.42);
}

.np-title-xl {
  font-size: 58rpx;
  line-height: 1.18;
  color: var(--ink-deep);
  font-weight: 700;
}

.np-title-lg {
  font-size: 42rpx;
  line-height: 1.28;
  color: var(--ink-deep);
  font-weight: 700;
}

.np-copy {
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--ink-soft);
}

.np-bottom-nav {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 0;
  padding: 18rpx 20rpx var(--np-nav-bottom-space);
  border-radius: 30rpx 30rpx 0 0;
  background: rgba(252, 249, 240, 0.94);
  backdrop-filter: blur(16rpx);
  box-shadow: var(--shadow-nav);
  display: flex;
  align-items: center;
  justify-content: space-between;
  z-index: 20;
}

.np-bottom-link {
  min-height: auto;
  background: transparent;
  color: #46655a;
  width: 31%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
  padding: 12rpx 0;
  font-size: 22rpx;
}

.np-bottom-link.is-active {
  background: linear-gradient(145deg, #16342b, #29453c);
  color: #fff;
  border-radius: 24rpx;
}

.np-bottom-icon {
  font-size: 28rpx;
  line-height: 1;
}

.np-card {
  border-radius: var(--radius-card);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--shadow-card);
}

.np-panel {
  border-radius: var(--radius-panel);
  background: rgba(252, 249, 240, 0.72);
  border: 1rpx solid var(--line-soft);
  box-shadow: var(--shadow-card);
}

.np-ghost-button {
  min-height: auto;
  padding: 20rpx 28rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.56);
  color: var(--ink-deep);
  font-size: 24rpx;
}

.np-primary-button {
  min-height: 96rpx;
  padding: 0 32rpx;
  border-radius: 999rpx;
  background: linear-gradient(145deg, #16342b, #29453c);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  box-shadow: var(--shadow-soft);
}

.np-empty {
  padding: 34rpx 30rpx;
  border-radius: var(--radius-card);
  background: rgba(255, 255, 255, 0.7);
  box-shadow: var(--shadow-card);
}
</style>
