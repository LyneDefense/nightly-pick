# 夜拾 MVP 实施步骤

## 1. 当前仓库规划

当前工作目录下已创建 3 个独立仓库：

- `nightly-pick-miniapp`
  微信小程序前端，技术栈为 `uni-app`
- `nightly-pick-server`
  业务后端，技术栈为 `Java + Spring Boot`
- `nightly-pick-agent`
  AI 能力服务，技术栈为 `Python`

调用链路固定为：

```text
miniapp -> server -> agent
```

边界约束：

- 前端不直连 agent
- agent 不直接承担主业务系统职责
- 主数据库由 server 管理
- agent 默认返回结构化结果，由 server 负责落库

## 2. 总体实施顺序

建议按以下顺序推进：

1. 先搭建 `server` 和 `agent` 的最小联通链路
2. 再搭建 `miniapp` 基础页面与接口接入
3. 先跑通 `文字输入 -> agent 回复 -> 记录生成`
4. 再补 `语音输入 -> ASR -> agent 回复 -> TTS 播放`
5. 最后补历史记录、记忆引用、设置和删除能力

这样做的原因：

- 核心闭环在服务端，不先打通服务端链路，前端很容易空转
- 语音优先是产品方向，但实现上仍然应该先验证最小 AI 闭环
- 先把结构化记录跑通，再上语音，整体风险更低

## 3. 分阶段实施

## 阶段 0：项目初始化

目标：

- 三个仓库都具备最小可开发结构
- 本地能同时启动
- 仓库职责边界明确

### 3.1 miniapp 需要完成的工作

- 初始化 `uni-app` 项目
- 配置 TypeScript
- 建立基础目录结构
- 配置 API base URL
- 建立全局请求封装
- 建立页面路由骨架

完成标准：

- 能在微信开发者工具中打开并运行
- 至少有首页空页面

### 3.2 server 需要完成的工作

- 初始化 `Spring Boot` 项目
- 配置基础 Web 能力
- 配置 PostgreSQL 连接
- 配置 Redis 连接
- 配置统一返回结构
- 建立基础模块目录
- 建立健康检查接口

完成标准：

- 服务可启动
- `GET /health` 可返回成功

### 3.3 agent 需要完成的工作

- 初始化 `Python` 项目
- 建立依赖管理
- 建立基础 Web 服务
- 建立 provider 目录结构
- 建立 prompt 目录结构
- 建立健康检查接口

完成标准：

- 服务可启动
- `GET /health` 可返回成功

## 阶段 1：打通 server 和 agent 的最小链路

目标：

- server 能调用 agent
- agent 能接收请求并返回固定 mock 结果
- 先不接模型，先把服务边界跑通

### 3.4 server 需要完成的工作

- 定义调用 agent 的 HTTP client
- 定义 agent 请求与响应 DTO
- 增加 `conversation` 应用服务
- 增加一个测试接口，例如 `POST /internal/test-agent`
- 处理超时、异常和日志

完成标准：

- server 调 agent 成功
- server 能拿到 agent 的 mock 对话回复

### 3.5 agent 需要完成的工作

- 提供 `POST /chat/reply`
- 固定返回 mock 回复
- 提供 `POST /record/generate`
- 固定返回 mock 记录 JSON
- 提供 `POST /memory/extract`
- 固定返回 mock 记忆 JSON

完成标准：

- 三个接口都能被 server 调通
- 返回结构稳定

## 阶段 2：文字闭环 MVP

目标：

- 先跑通不带语音的完整业务闭环
- 用户能完成一次文字复盘并生成记录

### 3.6 server 需要完成的工作

- 建立用户、会话、消息、记录基础表
- 实现微信登录占位方案或开发期 mock 登录
- 实现以下接口：
  - `POST /auth/login`
  - `POST /conversations`
  - `POST /conversations/{id}/messages`
  - `POST /conversations/{id}/complete`
  - `GET /records`
  - `GET /records/{id}`
- 在 `complete` 时调用 agent 生成记录
- 完成记录落库

完成标准：

- 一次文字会话可以创建、继续、结束
- 结束后能生成并保存记录

### 3.7 agent 需要完成的工作

- 实现 `conversation pipeline`
- 实现 `record pipeline`
- 接入实际文本模型
- 建立 `base-system-prompt`
- 建立 `conversation-strategy-prompt`
- 建立 `record-generation-prompt`
- 输出稳定结构化 JSON

完成标准：

- agent 可根据输入生成自然回复
- agent 可根据完整会话生成结构化今夜记录

### 3.8 miniapp 需要完成的工作

- 首页
- 对话页文字输入模式
- 记录结果页
- 历史列表页
- 记录详情页
- 与 server 对接会话接口和记录接口

完成标准：

- 用户能在小程序里完成一次文字复盘并看到生成记录

## 阶段 3：语音闭环 MVP

目标：

- 实现你这个产品最核心的语音体验
- 用户可以用语音说，agent 以语音回

### 3.9 agent 需要完成的工作

- 接入 `MiniMax ASR`
- 接入 `MiniMax TTS`
- 封装 `SpeechProvider`
- 提供语音相关接口能力：
  - 语音转写
  - 语音合成
- 先使用 MiniMax 预置音色

完成标准：

- 能把一段用户语音转成文本
- 能把 agent 回复转成可播放音频

### 3.10 server 需要完成的工作

- 增加音频上传能力
- 增加对象存储接入
- 增加音频消息处理流程
- 在用户发送语音时调用 agent 转写
- 在 agent 回复完成后调用 TTS
- 把音频地址返回给前端

完成标准：

- server 能接住音频上传
- 能拿到转写结果
- 能返回 agent 语音回复地址

### 3.11 miniapp 需要完成的工作

- 录音按钮
- 录音状态管理
- 音频上传
- 消息气泡中的音频播放
- 语音回复自动播放或点击播放机制

完成标准：

- 用户能录音发送
- agent 返回语音并可播放

## 阶段 4：记忆与历史引用

目标：

- 让用户开始感受到“它记得我”

### 3.12 agent 需要完成的工作

- 实现 `memory pipeline`
- 定义短期记忆抽取规则
- 定义记忆引用规则
- 控制历史引用频率

完成标准：

- 每次生成记录后能抽取轻量记忆
- 新会话中能有限引用历史

### 3.13 server 需要完成的工作

- 增加记忆表
- 保存记忆项
- 提供查询近期记忆的内部能力
- 支持用户关闭历史引用
- 支持清空历史与清空记忆

完成标准：

- 记忆可保存、可读取、可关闭

### 3.14 miniapp 需要完成的工作

- 设置页增加历史引用开关
- 设置页增加删除/清空入口

完成标准：

- 用户能感知和控制历史引用

## 阶段 5：上线前收尾

目标：

- 让 MVP 达到可测试、可演示、可小范围上线的状态

### 3.15 miniapp 需要完成的工作

- 补齐空状态和错误提示
- 处理录音授权失败
- 优化夜间场景 UI
- 基础埋点

### 3.16 server 需要完成的工作

- 完成鉴权收口
- 增加限流与超时保护
- 增加基础审计日志
- 完成配置管理
- 增加部署配置

### 3.17 agent 需要完成的工作

- 优化 prompt
- 处理高风险内容兜底
- 优化 JSON 结构稳定性
- 增加最小评测样本

完成标准：

- 小程序可稳定演示
- 一次完整语音复盘成功率可接受
- 记录生成质量基本稳定

## 4. 推荐的近期执行顺序

如果马上开始干，推荐按这个顺序逐项推进：

1. 初始化 `nightly-pick-server`
2. 初始化 `nightly-pick-agent`
3. 打通 `server -> agent` mock 调用
4. 初始化 `nightly-pick-miniapp`
5. 跑通文字闭环
6. 接入 MiniMax ASR/TTS
7. 跑通语音闭环
8. 补记忆和设置

## 5. 当前最先要做的具体任务

现在最优先的不是 UI，而是以下 6 件事：

1. 在 `nightly-pick-server` 建 Spring Boot 基础工程
2. 在 `nightly-pick-agent` 建 Python 基础工程
3. 定义 server 到 agent 的接口协议
4. 在 `nightly-pick-agent` 返回 mock 对话和 mock 记录
5. 在 `nightly-pick-server` 建会话和记录的最小接口
6. 在 `nightly-pick-miniapp` 建首页和对话页骨架

## 6. 交付原则

整个实施过程需要遵守以下原则：

- 先打通链路，再做体验优化
- 先文字闭环，再语音闭环
- 先最小可用，再做记忆增强
- server 是业务事实中心
- agent 是 AI 能力中心
- 前端不承载复杂业务逻辑
