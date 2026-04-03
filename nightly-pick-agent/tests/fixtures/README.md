# Reflection Fixtures

这组 fixture 用来持续打磨 `reflection planner` 和 `reflection writer`。

## 目标

- 覆盖不同夜间场景，而不是只测一种“标准复盘”
- 先验证结构方向对不对，再继续调 prompt
- 特别盯住 `summary` 是否真的像“用户自己的夜间整理”，而不是聊天纪要

## 当前样本类型

- `companionship_insomnia`
  - 更像睡不着、想有人在
  - 不应该被硬写成重度复盘
- `day_review_with_unfinished`
  - 明确提到了今天发生的事和“想做但没做”
  - 应该能稳定抓到 unfinished
- `light_evening_sorting`
  - 有一点整理感，但没有很重的结论
  - 不应该被强行拔高

## 如何继续加样本

每个 case 保持这几个字段：

- `name`
- `conversation_text`
- `expected_plan`
- `expected_writer`

`expected_writer` 里建议至少覆盖：

- `summary_contains`
- `summary_not_contains`

尤其要盯这些容易滑偏的点：

- 是否默认使用第一人称
- 是否出现“用户说 / 我问 / 我回答 / 后来又问 / 最后又说”这类聊天过程复述词
- 是否把整段对话写成按时间顺序的流水账

建议优先继续补：

- 闲聊里夹一点今天的内容
- 同一天第二次补充
- 有明显情绪，但事实很少
- 事实很多，但结论仍然很轻
