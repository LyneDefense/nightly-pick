<template>
  <view v-if="visible" class="login-mask">
    <view class="login-panel">
      <button class="login-close" @click="$emit('cancel')">×</button>
      <text class="login-title">手机号快捷登录</text>
      <button
        class="login-button"
        open-type="getPhoneNumber"
        :loading="loading"
        :disabled="loading"
        @getphonenumber="$emit('phone-login', $event)"
      >
        微信手机号登录
      </button>
    </view>
  </view>
</template>

<script>
export default {
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
}
</script>

<style scoped>
.login-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  background: rgba(20, 27, 24, 0.36);
}

.login-panel {
  position: relative;
  width: 100%;
  max-width: 640rpx;
  padding: 54rpx 36rpx 36rpx;
  border-radius: 8rpx;
  background: #fcf9f0;
  box-shadow: 0 20rpx 64rpx rgba(20, 27, 24, 0.18);
}

.login-close {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  width: 64rpx;
  min-height: 64rpx;
  line-height: 64rpx;
  border-radius: 8rpx;
  background: transparent;
  color: rgba(31, 56, 48, 0.62);
  font-size: 38rpx;
}

.login-title {
  display: block;
  color: #1f3830;
  font-size: 34rpx;
  font-weight: 700;
  line-height: 1.3;
  text-align: center;
}

.login-button {
  margin-top: 36rpx;
  min-height: 92rpx;
  border-radius: 8rpx;
  background: #21473d;
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
}
</style>
