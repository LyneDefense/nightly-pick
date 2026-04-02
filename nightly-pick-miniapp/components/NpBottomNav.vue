<template>
  <view class="np-bottom-nav">
    <button :class="['np-bottom-link', active === 'home' ? 'is-active' : '']" @click="go('home')">
      <text class="np-bottom-icon">✦</text>
      <text>今夕</text>
    </button>
    <button :class="['np-bottom-link', active === 'history' ? 'is-active' : '']" @click="go('history')">
      <text class="np-bottom-icon">▤</text>
      <text>往事</text>
    </button>
    <button :class="['np-bottom-link', active === 'settings' ? 'is-active' : '']" @click="go('settings')">
      <text class="np-bottom-icon">◌</text>
      <text>吾心</text>
    </button>
  </view>
</template>

<script>
export default {
  props: {
    active: {
      type: String,
      default: "",
    },
  },
  methods: {
    go(tab) {
      const routeMap = {
        home: "/pages/home/index",
        history: "/pages/history/index",
        settings: "/pages/settings/index",
      }
      const target = routeMap[tab]
      if (!target || this.active === tab) return
      uni.reLaunch({ url: target })
    },
  },
}
</script>
