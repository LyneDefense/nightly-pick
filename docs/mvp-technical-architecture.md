# 夜拾 MVP 技术文档

## 1. 文档目标

本文档基于 [MVP PRD](./mvp-prd.md) 定义 `夜拾 / End of Day` 微信小程序 MVP 阶段的技术架构，覆盖以下内容：

- 前端技术架构
- 后端技术架构
- agent 服务架构
- 数据模型与核心数据流
- 语音、记忆、记录生成的实现边界
- MVP 阶段推荐的技术拆分方式

本文档目标不是一次性定死所有技术细节，而是先建立一个适合 `先做小程序、后续可扩展 App` 的工程骨架。

## 2. 架构目标

MVP 技术架构需要满足以下目标：

- 支持微信小程序优先上线
- 支持语音输入与文字输入
- 支持多轮对话和记录生成
- 支持基础长期记忆能力
- 支持历史记录查看与删除
- 保持核心业务逻辑可迁移，便于后续扩展 App
- 在开发复杂度、成本和可维护性之间取得平衡

## 3. 总体架构

系统分为三层：

1. `前端层`
   微信小程序客户端，负责交互、录音、播放、展示记录和触发对话。
2. `后端应用层`
   负责用户、会话、记录、记忆、鉴权、存储和 API 编排。
3. `Agent 层`
   负责对话生成、摘要提炼、结构化记录生成、历史引用和记忆抽取。

推荐采用如下逻辑结构：

```text
微信小程序（uni-app）
    |
    v
API Gateway / BFF
    |
    +--> 用户与鉴权服务
    +--> 对话会话服务
    +--> 记录服务
    +--> 记忆服务
    +--> 文件服务（音频上传）
    |
    v
Agent Orchestrator
    |
    +--> 语音转写服务
    +--> 对话模型
    +--> 记录生成链路
    +--> 记忆提取链路
    |
    v
数据库 / 对象存储 / 缓存
```

## 4. 技术选型建议

## 4.1 前端

- 框架：`uni-app`
- 语言：`TypeScript`
- UI：`uni-ui` + 自定义业务组件
- 状态管理：轻量状态管理，优先 `Pinia`
- 网络请求：统一 API client 封装
- 录音与播放：微信小程序原生录音能力，通过 `uni` 能力封装调用

选择理由：

- 适合微信小程序优先开发
- 后续可保留一定多端扩展能力
- 对 MVP 阶段开发速度更友好

## 4.2 后端

推荐优先采用 `Java + Spring Boot`。

- 语言：`Java 17+`
- Web 框架：`Spring Boot`
- Web 层：`Spring Web`
- 鉴权：`Spring Security` 或轻量 JWT 方案
- ORM：`MyBatis-Plus` 或 `Spring Data JPA`
- 数据库：`PostgreSQL`
- 缓存：`Redis`
- 对象存储：`S3 兼容存储` 或云厂商对象存储
- 队列：MVP 可先不用独立 MQ，必要时用数据库任务表、Redis 队列或 `Spring Scheduler`

选择理由：

- 你对 Java 更熟，MVP 阶段开发效率和可控性更高
- Spring Boot 对模块化单体、鉴权、数据访问和异步处理都很成熟
- 后端作为对话编排和数据沉淀中枢，用 Java 完全可以稳定承载

## 4.3 Agent

Agent 层建议独立为后端中的一个逻辑模块，而不是一开始就做成单独微服务。

MVP 阶段推荐采用 `single-agent harness` 设计，而不是多 agent 架构。这里的 agent 不是“一个大 prompt”，而是一套明确的运行结构：

```text
agent = model + loop + tools + state + prompt modules
```

实现方式：

- `Agent Orchestrator` 作为后端中的独立模块
- 通过统一接口调用 LLM、语音转写和后处理逻辑
- 把 prompt、工具调用、结构化输出解析、记忆提取拆成清晰步骤
- 把业务状态和 agent 状态明确分离

设计原则：

- 业务逻辑负责用户、会话、记录、权限和存储
- agent 负责理解输入、组织上下文、调用工具和生成输出
- prompt 不直接散落在 controller 或 service 业务代码中
- 优先使用结构化 state 和明确 tools，不用大 prompt 硬扛所有复杂度

MVP 不建议：

- 过早引入复杂多 agent 架构
- 过早引入工作流平台
- 过早做实时流式语音对话

## 4.4 AI 能力选型

基于当前产品方向，MVP 阶段采用 `语音优先` 策略，而不是“语音只是文字输入的附属能力”。

推荐分工如下：

- `ASR`
  采用 `MiniMax`
- `TTS`
  采用 `MiniMax` 预置音色
- `LLM`
  单独选择文本模型，通过统一 provider 接口接入
- `声音克隆`
  MVP 阶段暂不做

这样拆分的原因是：

- 语音链路是产品核心体验，应优先选更适合中文语音场景的方案
- 文本模型和语音模型解耦，后续更容易替换和对比
- MVP 阶段先验证“语音复盘是否成立”，不增加声音克隆带来的额外复杂度

### 4.4.1 MiniMax 在本项目中的职责

MiniMax 在 `夜拾` MVP 中主要承担两类能力：

- `ASR`
  用户语音转文字
- `TTS`
  agent 文本转语音

TTS 首版建议使用 `MiniMax` 预置音色，不引入自定义声音。

理由：

- 上线更快
- 中文语音表现更值得优先验证
- 不需要额外处理声音克隆的授权、审核和产品说明

### 4.4.2 LLM 接入策略

文本模型不与语音能力绑死。

建议在 Java 后端中抽象：

- `SpeechProvider`
  负责 ASR / TTS
- `LlmProvider`
  负责文本生成和结构化输出

这样后续如果更换文本模型，不影响 MiniMax 语音链路；如果更换语音提供方，也不影响 agent 主体逻辑。

### 4.4.3 成本判断

MVP 阶段由于坚持 `语音优先`，需要接受一个事实：

- 文本 LLM 成本通常不是大头
- `TTS` 大概率会成为更主要的成本项
- `ASR` 是第二个主要成本项

因此在产品实现上建议：

- 保持 agent 回复简短
- 不做过长语音播报
- 控制单次会话轮数
- 优先优化“高质量短回复”，而不是长段语音陪聊

语音优先是产品方向，不建议为了节省成本把产品改回文字优先。但成本意识需要在回复长度和回合设计上提前体现。

## 5. 模块划分

## 5.1 前端模块

建议前端按以下模块拆分：

- `pages/home`
  首页，展示欢迎语、开始复盘入口、最近记录卡片
- `pages/chat`
  对话页，支持语音输入、文字输入、消息流展示
- `pages/record`
  今夜记录生成结果页
- `pages/history`
  历史记录列表页
- `pages/record-detail`
  单条记录详情页
- `pages/settings`
  设置页、隐私说明、清空历史等

公共层：

- `components`
  通用组件，如消息气泡、录音按钮、记录卡片
- `stores`
  用户状态、会话状态、设置状态
- `services`
  API 请求封装
- `utils`
  时间格式化、上传封装、错误处理

### 5.1.1 小程序调试方式

`uni-app` 可以通过微信开发者工具调试，但通常不是直接拿源码目录打开，而是以下链路：

1. 使用 `HBuilderX` 或 `uni-app CLI` 编译为微信小程序
2. 生成微信小程序产物目录
3. 使用微信开发者工具打开该产物目录进行调试、预览和上传

这意味着：

- 日常开发代码仍然写在 `uni-app` 工程里
- 真正运行和调试的载体是编译后的微信小程序项目
- 这条路径是 `uni-app -> 微信小程序` 的标准开发方式

## 5.2 后端模块

- `auth`
  微信登录、用户鉴权、会话 token
- `user`
  用户基础信息、偏好设置
- `conversation`
  对话会话管理、消息存储、结束条件
- `record`
  今夜记录生成、编辑、查询、删除
- `memory`
  近期记忆摘要、引用策略、记忆清空
- `audio`
  语音文件上传、转写任务触发
- `agent`
  prompt 编排、模型调用、结构化输出解析

## 5.3 Agent 模块

推荐按 `harness + pipelines` 的方式拆分，而不是按多个独立 agent 进程拆分。

- `agent-orchestrator`
  agent 总入口，负责 loop、上下文构建、工具调用和阶段切换
- `conversation-pipeline`
  负责睡前对话生成和追问
- `record-pipeline`
  负责将对话整理为结构化记录
- `memory-pipeline`
  负责抽取近期可复用记忆
- `safety-guard`
  负责基础回复边界控制和风险内容兜底

MVP 阶段这几个模块可以在代码上实现为同一个 agent 模块内的不同链路，不需要物理拆分成多个服务。

### 5.3.1 Agent Harness 组成

推荐把 `夜拾` 的 agent 设计成以下几个组成部分：

- `model`
  底层 LLM 和语音能力提供方
- `loop`
  一次睡前复盘会话内的处理循环
- `tools`
  agent 可调用的受控动作
- `session state`
  当前会话的短期状态
- `external state`
  存在数据库中的持久化状态
- `prompt modules`
  可维护、可替换的提示词模块

在 provider 层建议继续细分：

- `speech-provider`
  对接 MiniMax ASR / TTS
- `llm-provider`
  对接文本模型

### 5.3.2 Agent Loop

MVP 阶段建议采用最小可用 loop：

```text
接收输入
-> 预处理输入
-> 构建上下文
-> 调用 conversation pipeline
-> 返回回复
-> 判断是否继续
-> 若结束则生成记录
-> 抽取记忆
```

这个 loop 的目标是稳定，而不是复杂。它只需要支持：

- 一次输入一次回复
- 少量历史引用
- 可控结束
- 结束后进入记录生成和记忆提取

### 5.3.3 Agent Tools

MVP 建议只开放少量高价值工具，避免 agent 变成无边界执行器。

建议工具集合：

- `transcribeAudio`
  把用户语音转成文本
- `synthesizeSpeech`
  把 agent 回复转成语音
- `loadRecentMemory`
  读取最近 7-14 天的轻量记忆摘要
- `loadRecentMessages`
  读取当前会话最近几轮消息
- `saveUserMessage`
  保存用户输入
- `saveAssistantMessage`
  保存 agent 回复
- `generateDailyRecord`
  生成结构化今夜记录
- `saveDailyRecord`
  保存今夜记录
- `extractMemory`
  从记录中提取轻量记忆
- `saveMemoryItems`
  保存记忆项

原则：

- tool 名称清晰
- tool 职责原子化
- tool 返回结构明确
- agent 不直接操作数据库细节

### 5.3.4 Agent State

`夜拾` 的 agent 状态应分为两层。

第一层是 `session state`，用于一次会话内的短期控制：

- 当前轮数
- 当前阶段：`opening | exploring | closing`
- 是否已经问过“今天最想记住什么”
- 是否已经问过“还有什么挂心的事”
- 是否满足结束条件
- 本轮是否允许引用历史

第二层是 `external state`，由业务系统持久化：

- 用户信息
- 会话信息
- 消息记录
- 今夜记录
- 轻量记忆
- 用户设置

规则：

- 短期流程控制放 `session state`
- 持久业务事实放 `external state`
- 不用 prompt 文本去“记住状态”

### 5.3.5 Prompt Modules

prompt 应该模块化，而不是堆成一个大 system prompt。

建议拆成：

- `base-system-prompt`
  定义角色、语气、边界
- `conversation-strategy-prompt`
  定义复盘提问方式和对话节奏
- `record-generation-prompt`
  定义今夜记录的结构化输出要求
- `memory-extraction-prompt`
  定义可抽取哪些短期记忆
- `safety-prompt`
  定义高风险内容时的回复边界

这样做的好处是：

- 更容易调试
- 更容易替换和迭代
- 不会把业务逻辑和提示词绑死在一起

## 6. 数据流设计

## 6.1 首次使用

```text
用户打开小程序
-> 微信登录
-> 前端获取 token
-> 后端创建 user
-> 返回首页
```

## 6.2 一次睡前复盘

```text
用户点击开始今晚复盘
-> 前端创建会话
-> 后端创建 conversation session
-> agent 生成开场问题
-> 用户输入文字或语音
-> 若为语音则上传音频并转写
-> 后端写入消息
-> 后端取最近记忆摘要
-> agent 生成下一轮回复
-> 达到结束条件后触发记录生成
-> record-agent 输出结构化记录
-> record 服务保存记录
-> memory-agent 生成近期记忆摘要
-> 返回今夜记录页
```

## 6.3 历史回看

```text
用户进入历史列表
-> 前端请求记录列表
-> 后端按用户和日期倒序返回
-> 用户点击详情
-> 返回完整记录内容
```

## 7. Agent 工作流设计

MVP 阶段建议把 agent 设计成 `single-agent harness + 多阶段 pipeline` 的工作流。

### 7.0 Agent 与业务逻辑的边界

推荐边界如下：

- `业务层负责`
  用户、鉴权、会话创建、消息落库、记录查询、设置修改、权限控制
- `agent 层负责`
  对话理解、上下文拼装、追问策略、记录生成、记忆提取、安全回复

不要让业务 controller 直接拼 prompt，也不要让 agent 直接拥有数据库读写细节。

推荐调用关系：

```text
Controller
-> Application Service
-> Agent Orchestrator
-> Tools / Providers
-> Pipeline Output
-> Application Service 落库
```

### 7.0.1 Java 模块建议

如果按 Spring Boot 实现，建议在 `agent` 模块中继续分层：

```text
agent/
  orchestrator/
  pipeline/
  prompt/
  tool/
  provider/
  context/
  model/
```

建议职责：

- `orchestrator`
  负责总流程编排
- `pipeline`
  负责 conversation、record、memory 三条链路
- `prompt`
  存放各类 prompt 模板
- `tool`
  定义 agent 可调用工具接口
- `provider`
  封装 LLM、转写、TTS 等外部 AI 能力
- `context`
  构建本轮上下文
- `model`
  定义 agent 入参、出参和结构化 schema

如果按供应商适配实现，`provider` 层建议类似：

```text
provider/
  llm/
    LlmProvider.java
    XxxLlmProvider.java
  speech/
    SpeechProvider.java
    MiniMaxSpeechProvider.java
```

### 7.1 对话阶段

输入：

- 当前用户输入
- 最近几轮会话消息
- 最近若干条记录摘要
- 用户设置项，例如是否允许历史引用

输出：

- agent 回复文本
- 是否继续追问
- 是否建议结束本次复盘

原则：

- 回复简短、自然、温和
- 避免一次问多个问题
- 避免过度分析和说教
- 历史引用频率要低

### 7.2 记录生成阶段

触发时机：

- 用户主动结束
- 或达到最小轮次并确认生成记录

输入：

- 本轮完整对话
- 必要的上下文信息

输出固定 JSON 结构：

```json
{
  "title": "string",
  "summary": "string",
  "events": ["string"],
  "emotions": ["string"],
  "open_loops": ["string"],
  "highlight": "string"
}
```

生成要求：

- 尽量忠实于用户表达
- 不擅自虚构事件
- 总结语言比用户原话更清晰，但不过度“包装”

### 7.3 记忆抽取阶段

MVP 只抽取轻量记忆，不做复杂画像系统。

抽取内容示例：

- 最近持续关注的事情
- 最近多次提到的人或主题
- 最近情绪波动相关的事项

输出结构建议：

```json
{
  "short_term_memory": [
    {
      "type": "topic",
      "content": "最近持续提到工作中的某个项目推进压力",
      "source_record_id": "xxx"
    }
  ]
}
```

### 7.4 安全边界

agent 需要遵守以下边界：

- 不宣称自己是心理医生或治疗工具
- 对高风险内容给出克制、稳定的安全回复
- 不制造虚假的长期记忆
- 不对用户做强结论式人格判断

## 8. 数据模型

## 8.1 User

```ts
type User = {
  id: string
  wechatOpenId: string
  nickname?: string
  avatarUrl?: string
  allowMemoryReference: boolean
  createdAt: string
  updatedAt: string
}
```

## 8.2 ConversationSession

```ts
type ConversationSession = {
  id: string
  userId: string
  status: "active" | "completed" | "abandoned"
  startedAt: string
  endedAt?: string
}
```

## 8.3 Message

```ts
type Message = {
  id: string
  sessionId: string
  role: "user" | "assistant"
  inputType: "text" | "audio" | "system"
  text: string
  audioUrl?: string
  transcriptStatus?: "pending" | "done" | "failed"
  createdAt: string
}
```

## 8.4 DailyRecord

```ts
type DailyRecord = {
  id: string
  userId: string
  sessionId: string
  date: string
  title: string
  summary: string
  events: string[]
  emotions: string[]
  openLoops: string[]
  highlight: string
  rawConversationRef?: string
  createdAt: string
  updatedAt: string
}
```

## 8.5 MemoryItem

```ts
type MemoryItem = {
  id: string
  userId: string
  type: "topic" | "person" | "emotion" | "ongoing"
  content: string
  sourceRecordId?: string
  createdAt: string
  updatedAt: string
}
```

## 9. API 设计草案

## 9.1 鉴权

- `POST /auth/wechat-login`
  使用微信登录凭证换取业务 token

## 9.2 会话

- `POST /conversations`
  创建一次新的复盘会话
- `GET /conversations/:id`
  获取会话详情
- `POST /conversations/:id/messages`
  发送一条用户消息
- `POST /conversations/:id/complete`
  主动结束会话并生成记录

## 9.3 音频

- `POST /audio/upload`
  上传语音文件
- `POST /audio/transcribe`
  触发转写

MVP 也可以把上传和转写合并成后端内部流程，对前端只暴露一个消息发送接口。

## 9.4 记录

- `GET /records`
  获取历史记录列表
- `GET /records/:id`
  获取记录详情
- `PATCH /records/:id`
  编辑标题或摘要
- `DELETE /records/:id`
  删除记录

## 9.5 设置

- `GET /me/settings`
- `PATCH /me/settings`
- `POST /me/clear-memories`

## 10. 前端架构建议

前端从第一天开始按 `平台层` 和 `业务层` 分离。

建议目录思路：

```text
src/
  pages/
  components/
  stores/
  services/
    api/
    conversation/
    record/
  adapters/
    audio/
    storage/
    auth/
  types/
  utils/
```

设计原则：

- 页面层只负责展示和交互
- 业务逻辑尽量放到 `services`
- 微信平台特有能力放到 `adapters`
- 不把录音、上传、登录等平台代码散落到页面中

这样后续如果扩到 App，可以优先替换 `adapters` 层，而不是重写整个业务层。

## 11. 后端架构建议

后端建议采用 `Java 模块化单体`，而不是微服务。

推荐结构：

```text
server/
  src/main/java/com/nightlypick/
    auth/
    user/
    conversation/
    record/
    memory/
    audio/
    agent/
    common/
    infra/
  src/main/resources/
```

如果你偏向更清晰的分层，也可以采用：

```text
server/
  src/main/java/com/nightlypick/
    controller/
    service/
    repository/
    domain/
    integration/
    config/
```

但更推荐按业务领域拆包，再在包内分层。这样后续更容易演进。

设计原则：

- 对外统一 REST API
- 内部按领域模块拆分
- Agent 编排能力先作为内部模块存在
- 模型调用、语音转写、对象存储都通过 `integration/provider` 封装
- 避免把 prompt、模型请求和业务控制器耦合在一起

这样做的好处是：

- MVP 快
- 结构符合 Java 常见工程习惯
- 不会过早引入部署复杂度
- 后续如果 agent 压力变大，再独立拆服务也容易

## 12. 存储设计

至少需要三类存储：

1. `关系型数据库`
   存用户、会话、消息、记录、记忆元数据。
2. `对象存储`
   存用户音频文件。
3. `缓存`
   存临时会话状态、短时结果缓存、限流信息。

推荐：

- PostgreSQL：主业务数据
- Redis：缓存和短时状态
- S3 兼容对象存储：音频文件

## 13. 语音方案

MVP 阶段建议采用 `非实时语音`，但保持 `语音优先` 体验。

即：

- 用户按住录音
- 录完上传
- 后端转写
- 转写文本进入对话流程
- agent 回复默认生成语音并返回给前端播放

推荐能力组合：

- `ASR`：MiniMax
- `TTS`：MiniMax 预置音色
- `声音克隆`：MVP 暂不启用

为什么仍然采用非实时方案：

- 微信小程序下实时语音交互链路更复杂
- MVP 核心是验证睡前语音复盘体验，而不是验证通话式实时 agent
- 录音上传 + 转写 + 语音回复，已经足够体现“语音为主”的产品核心

不要一开始做：

- 实时打断式语音对话
- 边说边回的低延迟语音 agent
- 复杂 VAD 和通话状态机

原因：

- 微信小程序下实时语音交互链路更复杂
- MVP 核心是验证复盘价值，不是验证实时语音技术
- 成本、稳定性和调试复杂度都更低

## 14. 记忆系统设计

MVP 记忆系统分两层：

1. `记录层`
   每天生成的结构化今夜记录。
2. `轻量记忆层`
   从最近若干条记录中抽取少量摘要，用于下次复盘引用。

不做：

- 复杂向量检索系统
- 全量消息 embedding
- 大规模用户画像图谱

MVP 实现建议：

- 只保留最近 `7-14` 天的轻量记忆摘要供 agent 使用
- 按规则筛选高价值记忆
- 后续再决定是否引入向量数据库

## 15. 安全与隐私

需要明确处理以下问题：

- 用户内容默认仅用于产品服务本身
- 删除记录后，对应引用链路也应失效
- 用户可关闭历史引用
- 敏感内容不应在日志中输出原文
- prompt、模型响应和用户内容需要基本脱敏与访问控制

最少需要具备：

- 鉴权
- 数据按用户隔离
- 管理端不可随意查看用户私密内容
- 基础审计日志

## 16. 部署建议

MVP 阶段推荐最小可用部署：

- `前端`
  uni-app 构建微信小程序包
- `后端`
  单个 API 服务实例
- `数据库`
  托管 PostgreSQL
- `缓存`
  托管 Redis
- `对象存储`
  云对象存储

如果早期用户量不大，可以先不拆：

- 独立任务服务
- 独立 agent 服务
- 独立消息队列

## 17. 演进路线

### 阶段一：MVP

- 微信小程序
- 文字 + 非实时语音输入
- 多轮对话
- 结构化今夜记录
- 轻量历史引用

### 阶段二：体验优化

- 流式回复
- 更自然的对话节奏
- 周回顾和月回顾
- 更稳定的记忆引用策略

### 阶段三：多端扩展

- App 端接入
- 替换平台适配层
- 复用核心 API、记录模型和 agent 编排逻辑

## 18. 当前建议结论

基于 `夜拾` 的产品目标，MVP 阶段最合适的技术方案是：

- 前端采用 `uni-app + TypeScript`
- 小程序通过 `uni-app` 编译后使用微信开发者工具调试
- 后端采用 `Java 17 + Spring Boot + PostgreSQL`
- Agent 作为 Java 后端中的独立编排模块实现
- 语音采用 `录音上传 + 转写 + 语音回复` 的非实时方案
- `ASR/TTS` 采用 `MiniMax`
- 文本模型通过独立 `LlmProvider` 接入
- 记忆系统先做 `轻量摘要记忆`，不做复杂检索架构

这个方案的重点不是技术炫技，而是优先验证以下闭环：

- 用户是否愿意睡前使用
- 对话是否足够自然
- 记录沉淀是否有价值
- 用户是否感受到“它越来越懂我”
