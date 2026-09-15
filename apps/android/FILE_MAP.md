# Android 工程文件说明与 Kotlin → Java 迁移对照

本文档说明 `apps/android` 下每个文件的作用，并逐一交代原 Compose 文件 `QianwenApp.kt` 中的代码搬到了哪里。

- 技术形态：纯 Java + Android View 体系（Activity / RecyclerView / LiveData），已移除 Kotlin、Jetpack Compose、ViewModel + StateFlow、kotlinx.serialization、DataStore。
- 入口 Activity：`com.qianwen.demo.ui.ConversationsActivity`（原为 Compose 的 `MainActivity`）。

---

## 一、构建与工程配置

| 文件 | 说明 |
| --- | --- |
| `settings.gradle` | 工程名 `QianwenDemo`，只包含 `:app` 一个模块；声明 google/mavenCentral 仓库。独立构建 AAR 时用，被宿主 include 时由宿主的 settings 决定。 |
| `build.gradle`（根） | 仅声明 AGP `com.android.application` 与 `com.android.library` 8.13.2，均 `apply false`。Kotlin / Compose / serialization 插件已全部移除。 |
| `app/build.gradle` | **库模块**配置：`id 'com.android.library'`、`namespace com.qianwen.demo`、`compileSdk 35` / `minSdk 26` / `java 17`、`consumerProguardFiles "consumer-rules.pro"`；无 applicationId / targetSdk / versionCode。依赖：appcompat（`api`，宿主需要 AppCompat 主题）、activity、recyclerview、lifecycle-viewmodel/livedata、okhttp、gson（均 `implementation`）、junit（test）。 |
| `app/consumer-rules.pro` | 库自带的消费端 R8 规则：保留 Gson 模型字段名（5 个模型 + 2 个嵌套类型），`-dontwarn okhttp3/okio/javax.annotation`。 |
| `gradle.properties` | AndroidX 开关、非传递 R 类、Gradle JVM 参数（Kotlin 相关配置已删除）。注意：被宿主源码 include 时不生效。 |
| `gradle/wrapper/*`、`gradlew`、`gradlew.bat` | Gradle 8.13 wrapper。 |

---

## 二、`app/src/main/AndroidManifest.xml`

| 项 | 说明 |
| --- | --- |
| 权限 | `INTERNET`（合并进宿主，无副作用）。 |
| application | **空标签**：库不设置 `label` / `theme` / `allowBackup` / `usesCleartextTraffic`，避免覆盖宿主配置或与宿主发生 manifest merger 冲突。 |
| `.ui.ConversationsActivity` | 会话列表页，`exported=false`，自带 `android:label="千问"` 与 `@style/Theme.Qianwen`。 |
| `.ui.ChatActivity` | 聊天页，`exported=false`，自带 `@style/Theme.Qianwen`。 |
| 无 launcher | 库不声明 MAIN/LAUNCHER（否则宿主会多出一个启动图标）；入口由宿主自行 `startActivity`。 |

---

## 三、`app/src/main/java/com/qianwen/demo/data`（数据层，10 个文件）

| 文件 | 说明 |
| --- | --- |
| `Conversation.java` | 会话实体：`id / title / pinned / createdAt / updatedAt`。UI 与接口共用。 |
| `ChatMessage.java` | 消息实体：`id / conversationId / role / content / status / createdAt / updatedAt / error`，并带 `type`（`text` / `news`，默认文本）与 `news`（新闻列表）两个字段，提供 `isNews()` / `hasNews()`；历史缓存里没有 `type` 时按文本处理。 |
| `NewsItem.java` | 新闻列表消息中的一条新闻：`id / title / img / url`。 |
| `ChatStreamEvent.java` | SSE 单帧事件。原本拆成 5 个子类（conversation/message/delta/done/error），现合并为一个实体 + `type` 判别字段，并提供 `isDelta()` / `isError()` / `isTerminal()` / `isKnownType()` / `errorEvent()` 工厂方法；新增 `news` 事件类型与 `news` 列表字段（详见第八节）。 |
| `ApiModels.java` | 服务端传输结构集中处（6 个嵌套 DTO）：`Health`、`Conversations`（`{conversations}`）、`ConversationEnvelope`（`{conversation}`，新建/更新共用）、`Messages`（`{conversation,messages}`）、`ConversationRequest`（title + 可空 pinned，新建与更新共用）、`ChatRequest`。 |
| `LocalSnapshot.java` | 本地缓存快照（`version / savedAt / conversations / messagesByConversation / selectedConversationId`），内含 `Status` 枚举（EMPTY/RESTORED/CORRUPTED）与 `ReadResult`（快照 + 读取状态）两个嵌套类型。 |
| `NativeScreen.java` | 导航目标值对象：`conversationId` 为 null 表示列表页，非 null 即聊天页；提供 `isChat()`。列表↔聊天页跳转的唯一依据。 |
| `ChatSseParserJava.java` | SSE 行解析器：按 `event:` / `data:` 累积帧，遇空行 flush；type 优先取 JSON 的 `type`，否则回落 `event:` 头；`[DONE]` 返回 null；未知类型返回 null；解析异常转成 error 事件（不抛异常）。 |
| `QianwenApiClientJava.java` | OkHttp + Gson 的 HTTP 客户端：`get/post/patch/delete` 泛型请求；`streamChat()` 逐行读 SSE 并回调（终止条件为 `event.isTerminal()`）；`cancelActiveStream()` 取消进行中的流（取消生成用）。 |
| `QianwenRepositoryJava.java` | 仓库层：把响应包拆成 `List<Conversation>` / `List<ChatMessage>` 等业务数据；快照读写用 `SharedPreferences` + Gson（`commit()` 失败会抛异常，供 UI 提示"本地缓存写入失败"）；`cancelStream()` / `shutdown()` 透传取消。 |

---

## 四、`app/src/main/java/com/qianwen/demo/ui`（界面层，7 个文件）

| 文件 | 说明 |
| --- | --- |
| `QianwenUiState.java` | 页面统一状态 + 其配套类型：`ServiceStatus` / `ConversationListStatus` / `SendStatus` / `CacheStatus`（枚举）+ `RetryDraft`（重试草稿）+ 全部状态字段（`screen`、`conversations`、`messagesByConversation`、`selectedConversationId`、`health`、`draft`、`error`、`notice`、`searchQuery`、缓存时间等）与 `isStreaming()`。带拷贝构造，供 VM 做快照式更新。 |
| `QianwenViewModelJava.java` | 视图模型（Java + LiveData，对外暴露 `state`）：导航、健康检查、会话列表刷新/新建/重命名/置顶/删除、消息加载/清空、发送（含 delta 合并缓冲 + 自适应节流）、取消、重试、重新生成、草稿与模板、搜索词、通知与错误、本地快照恢复与持久化；收到 `news` 事件时生成新闻卡片消息（`applyNewsEvent()`）。内含私有 `DeltaAccum`（增量缓冲单元）与 `StreamCallback`（SSE 回调）。 |
| `ConversationsActivity.java` | 会话列表页：列表渲染、搜索过滤、新建对话、重命名/删除弹窗、调试设置弹窗、服务状态弹窗、服务端状态展示；按 `state.screen` 跳转聊天页。 |
| `ChatActivity.java` | 聊天页：消息列表（DiffUtil + 自动滚动防抖）、输入框与草稿同步、发送/取消/重试、清空、快捷模板、编辑与重新生成、新闻卡片点击打开链接、服务状态弹窗；返回时把导航状态同步回列表。 |
| `ConversationsAdapter.java` | 会话列表适配器：绑定标题/更新时间/置顶态，回调点击、置顶切换、重命名、删除。 |
| `MessagesAdapter.java` | 消息列表适配器：DiffUtil + 局部刷新（payload 区分 content/role/status/error）；`hasNews()` 的消息渲染成新闻卡片（正文位让给 `news_container`，按 `news` 逐条 inflate `item_news.xml`），新闻卡片整体重绑、隐藏助手操作按钮，并通过 `onNewsClick` 回调点击。 |
| `ImageLoader.java` | 极简图片加载器：OkHttp 下载 + LruCache 内存缓存 + 按目标尺寸采样解码 + tag 校验防 RecyclerView 复用串图。缩略图场景够用，因此不额外引入图片库依赖。 |

---

## 五、资源

| 文件 | 说明 |
| --- | --- |
| `res/layout/activity_conversations.xml` | 列表页布局：千问标题 + 设置按钮、新建对话、搜索框、加载条、缓存/错误/提示文本、"最近对话"、列表、底部服务端状态 + 服务状态按钮。 |
| `res/layout/activity_chat.xml` | 聊天页布局：顶栏（返回/标题/会话名/状态）、发送状态条、错误与提示、快捷模板按钮行、消息列表、输入框 + 发送/取消、底部免责文案。 |
| `res/layout/item_conversation.xml` | 会话条目：标题、副标题（置顶标记 + 更新时间）、置顶/改名/删除按钮。 |
| `res/layout/item_message.xml` | 消息条目：role、content、status、error、助手操作行（编辑、重新生成），以及新闻卡片容器 `news_container`（默认隐藏，由适配器填充）。 |
| `res/layout/item_news.xml` | 单条新闻卡片：左侧 56dp 正方形缩略图（`centerCrop`）+ 右侧最多两行、超出省略的标题。 |
| `res/values/styles.xml` | 主题 `Theme.Qianwen`，继承 `Theme.AppCompat.Light.NoActionBar`（AppCompatActivity 必需），设置状态栏/导航栏颜色。 |

---

## 六、测试与脚本

| 文件 | 说明 |
| --- | --- |
| `app/src/test/java/com/qianwen/demo/data/ChatSseParserJavaTest.java` | SSE 解析器 JVM 单测（10 例）：delta/message（type 取自 event 头）/done/error/多行 data/畸形帧转 error/注释行忽略/未知帧类型忽略/`[DONE]` 忽略/空 flush。 |
| `scripts/check-android.ps1` | 工程结构自检：校验必需的 Gradle、Manifest、Java 源码与测试文件存在，且 `usesCleartextTraffic=true`。 |
| `scripts/dev-android.ps1` | 一键跑模拟器：修正 SDK 路径、启动或复用 AVD、构建 debug APK、安装并启动 `com.qianwen.demo/.ui.ConversationsActivity`。 |
| `scripts/test-android.ps1` | 执行 `:app:testDebugUnitTest`。 |

---

## 七、`QianwenApp.kt` 的代码去哪了

`QianwenApp.kt`（约 800 行 Compose UI）已随 `MainActivity.kt`、`QianwenViewModel.kt` 一起删除，其中内容按下表拆分到 Java View 体系中。

### 7.1 结构与状态

| `QianwenApp.kt` 中的内容 | 现在的位置 |
| --- | --- |
| 顶部颜色常量（PageBackground / SidebarBackground / TextPrimary / TextSecondary / LineColor / UserBubble / AssistantBubble / InputBackground / ChipBackground / OnlineGreen） | 直接写进各 XML 布局的颜色属性；窗口级颜色在 `styles.xml`。 |
| `rememberQianwenViewModel()`（`viewModel(factory = QianwenViewModel.factory(application))`） | `ConversationsActivity` / `ChatActivity` 中 `new ViewModelProvider(this, QianwenViewModelJava.factory(getApplication())).get(...)`。 |
| `QianwenApp()` 根 Composable + `MaterialTheme` + `when (val screen = state.screen)` 四屏切换 | 拆成两个 Activity：`ConversationsActivity`（列表）与 `ChatActivity`（聊天），由状态里的 `screen`（`NativeScreen`）驱动跳转；Status/Settings 不再占用整屏，改为 AlertDialog。 |
| `val state by actualViewModel.state.collectAsState()`（StateFlow 收集） | `viewModel.state.observe(this, this::render)`（LiveData）。每个 Composable 对应的渲染逻辑集中在该 Activity 的 `render(state)` 里。 |

### 7.2 会话列表页（原 `ConversationListScreen`）

| 原 Composable | 现在的位置 |
| --- | --- |
| 「千问」标题 + 设置 IconButton | `activity_conversations.xml` 的标题 TextView + `settings_button`（点击 `showSettingsDialog()`）。 |
| 「新建对话」Button | `create_button` → `viewModel.createConversation()`。 |
| `SearchField`（BasicTextField） | `search_input`（EditText）+ TextWatcher → `updateSearchQuery()`；标题过滤逻辑在 `filterConversations()`。 |
| `HomeNavItem`「我的空间 / 智能体」两个占位入口 | **未迁移**（原本只是点击弹 notice 的占位）。 |
| `LinearProgressIndicator` | `progress`（ProgressBar），由 `listStatus == LOADING` 控制显隐。 |
| `CacheText` / `ErrorText` / `NoticeText` | `cache_text` / `error_text` / `notice_text` 三个 TextView，`render()` 中统一处理显隐与文案。 |
| 「最近对话」+ `LazyColumn` + `items()` | 「最近对话」TextView + `recycler`（RecyclerView）+ `ConversationsAdapter.setItems()`。 |
| `EmptyText`（暂无会话 / 没有匹配的会话 / 服务端离线） | **未迁移**（当前空列表只显示进度或错误条）。 |
| `ConversationRow`（行内 `OutlinedTextField` 重命名、置顶/改名/删除 IconButton、pinned 图标、updatedAt） | `item_conversation.xml`（标题 + 副标题 + 置顶/改名/删除三个 Button）+ `ConversationsAdapter` 回调；重命名与删除改为 AlertDialog（行内编辑态由 Dialog 自己持有，替代原 `remember { mutableStateOf }`）；pinned 标记并入副标题文案「已置顶 · ...」，按钮文案在「置顶/取消置顶」间切换。 |
| 底部「服务端 xxx」+ CloudQueue 图标 | `service_text` + `status_button`（图标省略）。 |

### 7.3 聊天页（原 `ChatScreen`）

| 原 Composable | 现在的位置 |
| --- | --- |
| `ChatTopBar`（返回 Menu、千问 + 会话名、KeyboardArrowDown、VolumeOff 状态入口） | `activity_chat.xml` 的 `back_button` / `title_text` / `subtitle_text` / `status_button`；下拉箭头图标省略。 |
| `SendStateText`（按 sendStatus 显示文案） | `send_state_text` + `sendStatusText()`（IDLE 隐藏，STREAMING/FAILED/CANCELED 显示对应提示）。 |
| `LazyColumn` + `MobileWelcome` 欢迎语 | `messages_recycler` + `MessagesAdapter` + `autoScrollToBottom()`（仅在最后一条完全可见时滚动，120ms 防抖）；**欢迎语未迁移**。 |
| `MobileMessageBubble`（气泡左右对齐、用户/助手底色、空内容显示"正在生成..."、状态行） | `item_message.xml`：保留 role/content/status/error 文本与显隐逻辑；**气泡对齐与底色样式未迁移**。 |
| `AssistantActionRow`（朗读/分享/复制/编辑/重新生成/赞/踩，含 ClipboardManager 与分享 Intent） | `item_message.xml` 的 `edit_button` / `regenerate_button` → `useMessageAsDraft()` / `regenerateFromMessage()`；**复制、分享、朗读、赞、踩未迁移**（原为占位提示，仅复制/分享有真实行为）。 |
| `MobileComposer` 的 `ToolChip` 行（思考/办事/AI生图/拍题答疑/服务状态/清空/取消生成/重试） | `think_button` / `work_button` / `image_button` / `photo_button` / `clear_button` → `applyComposerTemplate()` / `clearMessages()`；「取消生成」改为 `cancel_button`、「重试」改为 `retry_button`（按 `isStreaming` / `retryDraft` 显隐）；「服务状态」改由顶栏 `status_button` 承担。 |
| 输入框（BasicTextField + 占位文案）+ 相机 IconButton + 发送 IconButton | `input`（EditText，TextWatcher → `updateDraft()`，并用 `updatingInput` 标志避免回填死循环）+ `send_button`（可发送时启用）；**相机按钮未迁移**。 |
| 底部「内容由 AI 生成」 | `activity_chat.xml` 底部 TextView。 |

### 7.4 状态页与设置页

| 原 Composable | 现在的位置 |
| --- | --- |
| `StatusScreen`（status/modelMode/timestamp/lastCheck/cache/cacheSavedAt/api + 「重新检查」+ 模拟器提示文案） | `ConversationsActivity.showStatusDialog()` 与 `ChatActivity.showStatusDialog()`：同样的 7 行字段 + 「重新检查」按钮 → `refreshHealth(true)`；`NativeScreen.STATUS` 常量与整屏入口一并移除。 |
| `SettingsScreen`（当前 API + 三条说明） | `ConversationsActivity.showSettingsDialog()`；其中"本地缓存"一条由 DataStore 改为 SharedPreferences 描述。 |

### 7.5 文案与工具函数

| 原顶层函数 | 现在的位置 |
| --- | --- |
| `serviceStatusText()` / `serviceStatusColor()` | 文案并入 `QianwenUiState.ServiceStatus` 枚举的 `label`（检查中/在线/离线）；颜色写在 XML 布局里。 |
| `cacheStatusText()` | 并入 `QianwenUiState.CacheStatus` 枚举的 `label`（无缓存/已恢复/已保存/缓存异常）。 |
| `listStatusText()` | 仅用于进度条显隐，直接判断 `ConversationListStatus`，不再单独生成文案。 |
| `remember { mutableStateOf(...) }`（重命名行内编辑态） | 交给 AlertDialog 内部状态。 |
| 所有 `viewModel.xxx(...)` 调用 | **调用名完全不变**，仍是同名方法；只是实现从 `QianwenViewModel.kt`（ViewModel + StateFlow + 协程）换成 `QianwenViewModelJava.java`（ViewModel + LiveData + ExecutorService）。 |

### 7.6 未迁移清单（功能落差汇总）

1. 列表页「我的空间 / 智能体」两个占位入口。
2. 列表页空状态文案（暂无会话 / 无匹配 / 离线缓存）。
3. 聊天页空状态欢迎语（"嗨，你好呀！…"）。
4. 消息气泡的左右对齐、底色、空内容"正在生成..."占位。
5. 消息操作中的复制、分享、朗读、点赞、点踩（原实现中仅有复制/分享是真实行为）。
6. 输入框旁相机按钮。
7. 服务状态页与调试设置页从独立整屏改为弹窗（字段与操作已对齐）。
8. 重命名从行内编辑改为弹窗输入。

---

## 八、新闻列表消息（news）

### 8.1 事件契约（SSE）

服务端在 `/chat/stream` 中新增一帧（与 delta 同级，**非终止事件**）：

```
event: news
data: {"type":"news","conversationId":"c-1","messageId":"m-news-1","news":[{"id":"n-1","title":"标题最多两行，超出省略","img":"https://example.com/a.jpg","url":"https://example.com/a"}]}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `type` | 是 | 固定 `news`（也可省略，由 `event: news` 头提供） |
| `conversationId` | 否 | 不传时使用当前会话 |
| `messageId` | 否 | 传入时按该 id 覆盖同一条消息（重发/重试不产生重复卡片）；不传则自动生成 `news-<uuid>` |
| `news[].id` | 否 | 新闻 id，用于内容比较与定位 |
| `news[].title` | 是 | 标题，UI 最多显示两行 |
| `news[].img` | 否 | 缩略图 URL |
| `news[].url` | 否 | 点击打开的原链接 |

### 8.2 渲染规则

- 一条 news 消息 = 一条独立的助手消息（`role=assistant`、`type=news`、`content` 为空），追加在会话消息末尾。
- 复用 `item_message.xml`：隐藏 `content` 文本位，显示 `news_container`，按 `news` 顺序 inflate `item_news.xml`：
  - 左侧 **56dp × 56dp 正方形缩略图**（`centerCrop`，浅灰占位底）；
  - 右侧标题 **最多两行**、超出省略。
- 新闻卡片隐藏「编辑 / 重新生成」操作行（正文为空，无可编辑内容）。
- RecyclerView 复用时先清空 `news_container` 并重置可见性，避免卡片与文本消息互相串视图。

### 8.3 业务规则

| 环节 | 行为 |
| --- | --- |
| 解析 | `ChatSseParserJava` 统一解析为 `ChatStreamEvent`；`isKnownType()` 已包含 `news`，且 `news` 不影响 `isTerminal()`（仅 done/error 终止流）。 |
| 状态 | `QianwenViewModelJava.applyNewsEvent()`：`news` 为空或全是 null 时直接忽略；否则按 `messageId` upsert 进 `messagesByConversation`。 |
| 图片 | `ImageLoader`（OkHttp + LruCache + 按目标尺寸采样 + tag 校验）异步加载，不引入图片库依赖。 |
| 点击 | `ChatActivity.openNews()`：有 `url` 交给系统 `ACTION_VIEW` 打开；没有则 `showNotice("这条新闻没有可打开的链接。")`。 |
| 缓存 | 新闻消息随 `messagesByConversation` 一起写入本地快照（Gson 直接序列化 `type` / `news`），重启后可恢复渲染。 |
| 兼容 | 旧缓存与不带 `type` 的服务端消息按文本消息处理（`type` 默认 `text`）。 |

### 8.4 服务端 / 契约侧待同步（本次未改动）

1. `apps/server/src/app.ts` 的 `/chat/stream` 目前只发 `conversation / message / delta / done / error`，需按 8.1 增加 `news` 帧，App 里才能看到新闻卡片。
2. `packages/shared`、`packages/api-client` 的 `ChatStreamEvent` 类型联合，以及 `apps/ios` 的 `Models.swift`，若要同步支持需一并加 `news`。
3. `scripts/check-contract.mjs` 的 `expectedStreamTypes` 仍写死 5 种类型，且它读取的 `androidModels / androidApi / androidParser` 指向已删除的 Kotlin 文件——该脚本在本次迁移后已失效，需要改造为读取 Java 文件并纳入 `news`。

---

## 九、作为 Android library 接入宿主

库唯一入口是 `QianwenConfig`（`com.qianwen.demo.QianwenConfig`）：宿主注入服务端地址后直接打开会话列表页即可，没有其它初始化步骤。

```java
QianwenConfig.setBaseUrl("http://192.168.1.10:8787"); // 不调用则用默认 http://10.0.2.2:8787
startActivity(new Intent(context, ConversationsActivity.class));
```

原来的 `buildConfigField QWEN_API_BASE_URL` 已删除：库的 BuildConfig 在编译期固化、宿主无法覆盖，因此改为运行时可注入（`QianwenConfig.getBaseUrl()` 被 `QianwenRepositoryJava` 与状态弹窗读取）。

### 9.1 宿主前置条件

| 项 | 要求 |
| --- | --- |
| 插件 / AGP | 宿主 AGP 8.x，能解析 `com.android.library`。 |
| minSdk | **≥ 26**（库 minSdk 为 26；宿主更低会在 manifest 合并时报 `uses-sdk:minSdkVersion ... cannot be smaller`）。 |
| Java 版本 | 库用 Java 17（`compileOptions` 已声明），宿主需一致。 |
| 主题 | 库内 Activity 继承 `AppCompatActivity`，且各自声明 `@style/Theme.Qianwen`（继承 `Theme.AppCompat.Light.NoActionBar`）；宿主应用主题不受影响，但宿主工程必须有 AppCompat（库已用 `api` 暴露）。 |
| 明文 HTTP | 库默认访问 `http://10.0.2.2:8787` 明文；宿主若关闭明文流量，需在 `networkSecurityConfig` 放行或改用 https。 |
| R8 / 混淆 | 库自带 `consumer-rules.pro`，宿主 `minifyEnabled true` 时自动生效，**不需要手动 copy 规则**。 |
| 依赖版本 | okhttp 4.12.0 / gson 2.11.0 会进入宿主 runtime classpath，宿主已在用其它大版本时需先对齐。 |

### 9.2 接法一：源码模块 include（推荐）

```groovy
// 宿主 settings.gradle
include ':qianwen'
project(':qianwen').projectDir = file('<qwen-demo 路径>/apps/android/app')
```

再 `implementation project(':qianwen')`。两个注意点：

1. 源码 include 时本目录的 `gradle.properties`（`android.useAndroidX` / `nonTransitiveRClass`）**不生效**，宿主必须自己开 `android.useAndroidX=true`；
2. `settings.gradle` / `build.gradle`（本目录根）只用于独立构建，宿主会忽略。

### 9.3 接法二：AAR

```bash
cd apps/android
./gradlew :app:assembleRelease   # 产物 app/build/outputs/aar/app-release.aar
```

宿主 `implementation files('libs/app-release.aar')`，并需自行声明 appcompat（以及 recyclerview / lifecycle / okhttp / gson，AAR 不携带传递依赖），同时手动 copy `consumer-rules.pro` 里的规则。

### 9.4 已知约束

1. 资源名未加库前缀（`item_message.xml`、`item_news.xml`，id 如 `content` / `status` / `error` / `role`）；宿主若有同名资源会互相覆盖，建议后续统一加 `qianwen_` 前缀。
2. 库内 Activity 直接继承 `AppCompatActivity`，没有预留宿主 Base Activity / 边到边（edge-to-edge）扩展点。
3. 可配置项只有服务端地址；快照文件名（SharedPreferences `qianwen_native_store`）暂不可配置。
