<template>
  <view class="emotion-chip-list" :class="compact ? 'compact' : ''">
    <text v-for="item in normalizedItems" :key="item" :class="['emotion-chip', chipClass(item)]">{{ item }}</text>
  </view>
</template>

<script>
export default {
  props: {
    items: {
      type: Array,
      default: () => [],
    },
    compact: {
      type: Boolean,
      default: false,
    },
    max: {
      type: Number,
      default: 4,
    },
  },
  computed: {
    normalizedItems() {
      return (Array.isArray(this.items) ? this.items : []).filter(Boolean).slice(0, this.max)
    },
  },
  methods: {
    chipClass(emotion) {
      if (emotion && /委屈|难过|焦虑|疲惫/.test(emotion)) return 'warm'
      if (emotion && /平静|释怀|轻松|安心/.test(emotion)) return 'mint'
      return 'neutral'
    },
  },
}
</script>

<style scoped>
.emotion-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.emotion-chip-list.compact {
  gap: 10rpx;
}

.emotion-chip {
  padding: 12rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
}

.compact .emotion-chip {
  padding: 8rpx 16rpx;
  font-size: 20rpx;
}

.emotion-chip.warm {
  background: rgba(255, 225, 214, 0.88);
  color: #b85f3f;
}

.emotion-chip.mint {
  background: rgba(214, 234, 223, 0.82);
  color: #2f6756;
}

.emotion-chip.neutral {
  background: rgba(238, 235, 230, 0.9);
  color: #5b615f;
}
</style>
