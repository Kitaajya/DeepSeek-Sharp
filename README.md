# DeepSeek#


注：本项目只做学习使用，禁止篡改和非法售卖！
在终端里和 DeepSeek 聊天的命令行工具。我起名为DeepSeek#（DeepSeek Sharp）。技术栈：**Java 24 · Spring Boot 4 · Spring AI 2 · MySQL 8**。

填一次 API Key，之后 `./gradlew bootRun` 或 `java -jar` 就能直接开聊：

```
────────────────────────────────────────────────────────
  DeepSeek Harness 0.1.0  ·  终端问答
  模型 deepseek-chat    会话 #12
  历史 MySQL · deepseek_sharp
────────────────────────────────────────────────────────
  输入问题回车发送；/help 看命令；/exit 退出。

  API Key sk-abc****wxyz   （来源：本地配置文件）

你 › 用一句话解释什么是递归
AI › 递归就是函数在自己的定义里调用自己，把一个大规模问题拆成同形状的小问题……
```

## 特性

- **第一次运行就让你填 Key**：没配置时直接问，保存到 `~/.deepseek-sharp/config.properties`，不进 git；之后自动读取。
- **流式输出**：回答逐字打印，不用等整段生成完。
- **多轮上下文**：每次请求自动带上本会话最近 20 条消息（`deepseek.history-limit` 可调）。
- **会话存 MySQL**：每条会话、每条消息都落库，`/sessions` 看历史、`/load <id>` 接着聊。
- **MySQL 挂了也能用**：连不上自动降级成内存存储，只是重启后历史会丢。
- **一次性问答**：`--ask "问题"`，方便脚本和管道。

## 环境要求

| 依赖 | 说明 |
| --- | --- |
| JDK 24 | `build.gradle` 里用 toolchain 写死了 24 |
| MySQL 8 | 默认 `localhost:3306`，库名 `deepseek_sharp`（会自动创建） |
| 网络 | 能访问 `https://api.deepseek.com` |

数据库账号默认 `root` / `123456`（写在 `application.properties` 里，可用环境变量覆盖）：

```bash
set DB_USERNAME=root
set DB_PASSWORD=你的密码
```

## 快速开始

```bash
# 1) 跑起来（第一次会提示粘贴 API Key）
gradlew bootRun

# 2) 或者打成可执行 jar
gradlew bootJar
java -jar build/libs/deepseek-harness.jar
```

> 注意：jar 是用 Java 24 编译的，运行时也要用 JDK 24 的 `java`。

**在 IDEA 里点 Run、或在 Gradle/管道里跑、或双击 jar 时，程序会自动新开一个 Windows 自带的 PowerShell 窗口，问答在那边进行**（见下文）。

## API Key 从哪里来

按优先级依次尝试：

1. 命令行参数：`--api-key sk-xxxx`
2. 环境变量：`DEEPSEEK_API_KEY`
3. 本地配置文件：`~/.deepseek-sharp/config.properties`
4. 都没有 → 终端里现场输入，然后写入第 3 步的文件

也可以直接写在 `application.properties` 的 `spring.ai.deepseek.api-key` 里（不建议，容易跟着仓库提交）。

Key 申请地址：<https://platform.deepseek.com/api_keys>。

## 自动打开 PowerShell 窗口

判断逻辑很简单：**当前不是真终端**（`System.console().isTerminal() == false`）就自动开窗，覆盖这几种场景：

| 启动方式 | 结果 |
| --- | --- |
| IDEA 点 Run / Debug | IDEA 控制台只打印一句提示，问答在弹出来的 PowerShell 窗口 |
| `gradlew bootRun`（Gradle 会接管输出） | 同上 |
| 双击 `deepseek-harness.jar` | 同上 |
| 在 PowerShell / cmd 窗口里 `java -jar ...` | 已经是真终端，不弹窗，直接在当前窗口聊 |
| `--ask "问题"` | 脚本模式，永远不弹窗 |

被弹出窗口里跑的是同一个程序，只是多了一个内部参数 `--in-window`，并用环境变量
`DEEPSEEK_HARNESS_IN_WINDOW=1` 标记，所以不会递归开窗。API Key 通过环境变量
`DEEPSEEK_API_KEY` 传给子进程 —— 不会出现在窗口标题、临时脚本或进程参数里。

不想要弹窗就加 `--no-window`：

```bash
gradlew bootRun --args="--no-window"
```

生成的启动脚本在 `%TEMP%\deepseek-harness.ps1`，想看清楚到底执行了什么可以打开它。

## 交互命令

| 命令 | 作用 |
| --- | --- |
| `/help` | 查看帮助 |
| `/new` | 开一个新会话 |
| `/sessions` | 列出最近 10 个会话（含 id） |
| `/load <id>` | 接着某个历史会话聊 |
| `/history` | 看当前会话最近 10 条消息 |
| `/model <名称>` | 切换 `deepseek-chat` / `deepseek-reasoner` |
| `/key` | 换 API Key（保存后重启生效） |
| `/exit` | 退出（`Ctrl+C` 也行） |

## 命令行参数

```
-k, --api-key <key>   指定 API Key（优先级最高）
-a, --ask <问题>      只问一次就退出
-m, --model <模型>    deepseek-chat（默认）/ deepseek-reasoner
-s, --session <id>    继续某个会话，等价于启动后 /load <id>
    --no-window       不自动弹 PowerShell 窗口
    --no-color        关闭 ANSI 颜色（NO_COLOR 环境变量等同）
-h, --help            帮助
-v, --version         版本
```

脚本里用：

```bash
java -jar build/libs/deepseek-harness.jar --ask "把这句话翻译成英文：今天天气不错"
echo "1+1 等于几" | java -jar build/libs/deepseek-harness.jar
```

`--ask` 出错时退出码为 1，方便 `&&` / CI 判断。

## 数据表

程序启动时用 `CREATE TABLE IF NOT EXISTS` 自动建表（库也会按需创建）：

```sql
chat_session(id, title, model, created_at, updated_at)
chat_message(id, session_id, role, content, created_at)   -- role: user / assistant
```

想清空历史：

```sql
USE deepseek_sharp;
TRUNCATE TABLE chat_message;
TRUNCATE TABLE chat_session;
```

## 项目结构

```
src/main/java/org/designer/deepseek_sharp/
├── DeepSeekSharpApplication.java   入口：先解析 Key，再启动 Spring，最后进终端循环
├── cli/
│   ├── CliOptions.java             自己的命令行参数解析
│   ├── ConsoleUi.java              着色、横幅、流式输出
│   ├── PowerShellLauncher.java     不是真终端时，自动开 PowerShell 窗口
│   └── TerminalChat.java           REPL：读一行 → 命令 or 提问 → 流式打印
├── chat/
│   ├── ChatClientConfiguration.java  ChatClient + 系统提示词
│   ├── ChatService.java            拼上下文 → 调 DeepSeek → 落库
│   ├── ChatException.java
│   └── ChatErrorTranslator.java    401/402/429/超时 → 一句人话
├── history/
│   ├── ChatHistoryStore.java       存储接口
│   ├── MySqlChatHistoryStore.java  JdbcTemplate 实现
│   ├── InMemoryChatHistoryStore.java  降级实现
│   └── ChatHistoryConfiguration.java  选一个
└── key/
    ├── ApiKeyResolver.java         参数 → 环境变量 → 文件 → 交互输入
    ├── ApiKeyStore.java            ~/.deepseek-sharp/config.properties
    └── MissingApiKeyException.java
```

## 常见问题

**401 / API Key 无效**：Key 复制少了、被禁用，或环境变量里是旧 Key。用 `/key` 换一个再重启。

**402 / 余额不足**：DeepSeek 账户没钱了，去平台充值。

**429 / 请求太频繁**：等几秒再说，别连发。

**MySQL 连不上**：终端里会显示 `历史 内存（MySQL 不可用…）`，问答功能不受影响。检查 MySQL 服务、端口、账号密码，或改 `application.properties` 里的 `spring.datasource.*`。

**中文乱码**：终端用 UTF-8（Windows Terminal 默认即可）；库和表都是 `utf8mb4`。IDEA 里运行请确认 *File Encodings* 是 UTF-8。

**想换系统提示词**：设置 `deepseek.system-prompt`，例如
`java -jar ... --deepseek.system-prompt="你是简洁的代码助手"`。

## 说明

- `application.properties` 里**故意没有** `spring.ai.deepseek.api-key`：Key 由 `main()` 在 Spring 启动前解析并注入，这样"没 Key → 现场问"和"DeepSeekChatModel 要求 Key 非空"才能同时成立。
- 每次问答的计费按 DeepSeek 官方规则走，本工具不做缓存。
