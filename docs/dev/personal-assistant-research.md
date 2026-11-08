# 个人助理调研：Muse、Today 与开源实现

调查日期：2026-11-06。星标、Fork、许可证和最近推送时间是这一天用 `gh api repos/<owner>/<repo>` 读到的快照，之后会变。第 4.5 节和第 4.6 节是同日晚些时候补的仓库，数字单独列在那两节，不回头改第 4 节的表。产品能力以各家当天仍能打开的官方说明为准。没有安装这些产品，也没有读它们的私有实现。

这份文档回答一件事：若要把 Mika 收成一个能扩展的个人助理，外面已经做成的产品在做什么，哪些做法值得吸收，哪些不该搬进来。手机 GUI 自动化的同类对照仍以 [project-analysis.md](project-analysis.md) 为准（调查日 2026-11-01）。代码现状以 [architecture.md](architecture.md) 为准。据此写的方案是 [personal-assistant-prd.md](personal-assistant-prd.md)。

## 1. 调查范围

「Muse」在 2026 年同时指两件不同的东西，必须分开：

| 名字 | 是什么 | 和 Mika 的关系 |
| --- | --- | --- |
| Meta Muse | Meta 在 2026-09-08 发布的消费级个人 Agent，跑在云上的隔离 Linux 虚拟机里 | 产品形态和安全边界的参照，不是可以抄代码的实现 |
| Zer0Qing/Muse | 开源 Android 个人 Agent，GPL-3.0，跑在用户自己的手机上 | 同平台的产品参照。许可证与 Mika 的 Apache-2.0 不能混源码 |

「Today」指 [today.ai](https://today.ai/articles/blog/what-is-today) 上的个人 Agent（Mac、iPhone、Android）。没有找到它的公开实现仓库，下面只采用它自己的产品说明。

GitHub 上另选了几类实现，用来看扩展是怎么接的，而不是按星标排名。手机上靠无障碍点屏幕的项目，[project-analysis.md](project-analysis.md) 已经列过，这里不重复。

## 2. Meta Muse

官方说明：

- 产品页：[muse.ai](https://muse.ai/)
- 设计说明：Mona Sarantakos、Christine Awad，[How We Designed Muse](https://muse.ai/blog/how-we-designed-muse)，2026-09-08
- 安全说明：[How We Built Safety Into Muse](https://muse.ai/blog/security-and-safety-for-ai-agents-our-approach-with-muse)，2026-09-08
- 隐私：[Muse Privacy Policy](https://muse.ai/privacy)
- 连接器接入：[Connector guidelines](https://platform.muse.ai/docs)

它要做的事是：用户给出一个任务或一个长期目标，Agent 在自己的电脑上拆计划、用浏览器和连接器推进，应用关掉以后继续做，有实质变化或需要决定时才回来。

设计说明里写明的产品决定：

- **一台自己的电脑。** 文件系统、终端、浏览器都在。它可以为当前任务写代码、做文档、PDF、网页。购物结账用一次性卡号，真实卡号不给商户，也不给 Agent。
- **一条主对话，外加侧对话。** 主对话可以打断、可以连着丢多件事，不必等上一条答完。话题变复杂时用侧对话隔开上下文。记忆跨对话保留。
- **主动，但出声的门槛高。** 后台工作做完以后，先判断值不值得打扰。用户可以把主动消息关掉、调低或调高。
- **目标页。** 长期目标有独立的一页，能看到计划做到哪了。聊天里也可以谈这些目标。
- **确认不靠对话。** 设计说明把两件事定为必须用确定性界面：结构化的同意 / 拒绝卡片，以及凭据的安全存放。浏览可以继续；难撤销的动作（写信、付钱）停下来问。他们明确要避免「横幅看多了就一律点同意」。
- **产物。** 行程、开支看板这类结果不必是一大段聊天文字，可以是聊天里发出、也能单独留下的一块界面。
- **不知道从哪开始。** 早期测试里能力太多，用户无从下手。所以有上手提示、想法页，以及根据已有目标提出的下一步。

安全说明把执行和放行拆开：

- 每个用户一台隔离的 Linux 虚拟机，里面有浏览器、存储、Agent 和连接器凭据。
- **Sentinel** 是同一台机器上、系统层隔开的另一个主体。Muse 只能提议。连接器动作和一切出网，只有 Sentinel 能放行。
- 运行环境里的代码只拿得到代用令牌。请求被允许之后，Sentinel 在网络边界把代用令牌换成真凭据。Agent 本身拿不到秘密。
- 需要问人时，确认框在客户端里单独出现，不走和 Muse 的那条对话。同意绑在具体的连接器、目的地和用途上，不是一句「以后都允许」。
- 隐私政策写：默认不会在未经同意时做「发送邮件」这类重要动作。许多连接器可以收成只读。连接器只交换完成动作所需的数据，例如接上邮箱不等于把整个收件箱下载下来，除非用户明确要求。

连接器平台要求提交方标明每个工具的读写分类、是否敏感、副作用和速率限制。审核按工具放行，连接器过审不等于里面每个工具都过审。

公开报道里还有两点，只作背景，不作为方案依据：Everpod 在 2026-10-02 的说明里写，Muse 工作区里有可编辑的 `Soul.md`、`Identity.md`、`Memory.md`，和 OpenClaw 的同名文件对应；Amazon 在发布后两周限制了它的购物。这两点来自第三方转述，本调查没有打开那些文件。

## 3. Today

来源：[What Is Today?](https://today.ai/articles/blog/what-is-today)，Today 团队，2026-08-03 发布，2026-09-15 更新。应用商店文案与此一致（[Google Play](https://play.google.com/store/apps/details?id=ai.today.android.app)、App Store 条目）。没有公开仓库，架构细节（模型放在哪、云电脑怎么隔离）只知道它自己说的「前沿模型、任务执行、常开的云电脑」，下面不把这句展开成实现。

它把自己定义成个人 Agent，并用三件事和聊天框分开：

| | 聊天框 | Today 写明要做的 |
| --- | --- | --- |
| 记忆 | 这次会话里的上下文 | 可查看、可改、可删的结构化事实：人、项目、偏好、承诺。改一行，下一句建议就跟着变 |
| 打开时看见什么 | 空的输入框 | 今天的安排，以及自上次查看以来变了什么 |
| 做到外部世界 | 用户自己复制出去 | 起草、找空档、重排下午；邮件、日历改动、给别人发消息，点头之后才发生 |

主动被收成两个锚点，而不是全天插话：

- **早晨简报**：会改这一天的少数几件事，例如会议被挪了、航班该值机了、明天到期。
- **晚间收束**：今天滑过去的事往前挪，并问一个决定它去哪的问题。

它写明的门槛是：每条主动消息都得是「你希望一个人来告诉你的」。没变的、来得及以后再说的，折起来。主动不等于自主：发现和提议由它做，判断由用户一次点头或改一个数字完成。

连接器是可选、可撤销的：日历、邮件、笔记、文件、健康（Apple HealthKit、Android Health Connect）。没有连接器时，它仍能靠对话积累记忆，只是能做完的事变少。建议必须落在它真能做完的动作上，避免提出一个它挪不了的会议。社区技能用来安装可重复的做法，不必每次重写提示词。技能和连接器的名单它说会继续加；架构假设不变：动作有范围、人来确认、每次跑完更新记忆。

## 4. 开源实现

下表的数字都是 2026-11-06 的 GitHub API 快照。

| 项目 | 星 / Fork | 许可 | 最近推送 | 拿它看什么 |
| --- | --- | --- | --- | --- |
| [openclaw/openclaw](https://github.com/openclaw/openclaw) | 394042 / 82805 | MIT | 2026-11-06 | 渠道、技能、定时、策略在代码里执行 |
| [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) | 261083 / 57720 | MIT | 2026-11-06 | OpenClaw 自己用来对照的单进程实现 |
| [Zer0Qing/Muse](https://github.com/Zer0Qing/Muse) | 71 / 13 | GPL-3.0 | 2026-11-05 | 手机上的个人 Agent 产品面 |
| [khoj-ai/khoj](https://github.com/khoj-ai/khoj) | 38019 / 2535 | AGPL-3.0 | 2026-08-02 | 自托管的第二大脑加定时自动化 |
| [mem0ai/mem0](https://github.com/mem0ai/mem0) | 68513 / 8140 | Apache-2.0 | 2026-11-06 | 可插入的记忆层，本身不是助理 |
| [letta-ai/letta](https://github.com/letta-ai/letta) | 25369 / 2669 | Apache-2.0 | 2026-10-18 | 有状态、会改自己记忆的 Agent 平台 |
| [browser-use/browser-use](https://github.com/browser-use/browser-use) | 120189 / 13406 | MIT | 2026-11-06 | 浏览器这一只手，不是整天的助理 |
| [Dodaios/Dodaios](https://github.com/Dodaios/Dodaios) | 334 / 123 | Apache-2.0 | 2026-11-06 | 本机桌面 Agent OS，原名 Personal Jarvis |
| [cedricziel/assistant](https://github.com/cedricziel/assistant) | 6 / 1 | MIT | 2026-11-05 | 技能目录加人工复核的自我改进 |
| [ridingbytes/kaisho](https://github.com/ridingbytes/kaisho) | 4 / 1 | MIT | 2026-10-12 | 日历、任务、笔记经 MCP 交给已有的编程助手 |

星标差这么多，是因为 OpenClaw、Hermes、Khoj、mem0、Letta、browser-use 是平台。Zer0Qing/Muse、Dodaios、cedricziel/assistant、Kaisho 是个人助理这个产品形状的小实现。方案取的是形状，不是星标。

### 4.1 OpenClaw：策略在代码里，能力在外面

README（2026-11-06 读取的 `main`）写：它跑在用户自己的机器上，从 Discord、iMessage、Slack、Teams、Telegram、WhatsApp 等二十多个渠道遇见用户；一个 Gateway 管会话、工具、事件和渠道；模型、技能、插件可以换。状态、记忆和凭据在用户硬件上。默认不把诊断发出去。

[Why OpenClaw](https://docs.openclaw.ai/start/why-openclaw) 把安全主张收成一句：策略由代码执行，而不是写在系统提示词里请模型遵守。同一页写明，这是配置后的架构，不是默认认证；沙箱和命令审批默认关着，默认形态是一台机器上的单人助理。原生插件在进程内，不在沙箱里。秘密可以用句柄和出网时替换，使受保护的值不进模型上下文；做不到这一点的条目仍有暴露风险。

和本调查有关的扩展方式：

- 技能遵循 Agent Skills 规格，按目录装，用到再读。
- 自己既是 MCP 客户端（Streamable HTTP、SSE、stdio，带 OAuth），也是 MCP 服务器。
- 渠道是插件。未知私聊默认要配对，不把每条进来的话都当成主人。
- 定时是产品的一等能力，和工具、记忆、技能并列。社区配置里常见 `HEARTBEAT.md`：一份定期检查清单，没有值得说的就不出声。
- 工作区用 Markdown 分开「我是谁、用户是谁、怎么做事、长期记住什么」。技能不能改写行为底线。Everpod 认为 Meta Muse 的灵魂文件和这套同形；本调查只把这当作外部观察。

不照搬的部分：默认不沙箱、进程内原生插件、多人 Gateway、为二十个聊天网络各写一个渠道。Mika 的脚本已经在按对话隔离的沙箱里，这一头比 OpenClaw 的默认更严，不必为了「像 OpenClaw」放宽。

### 4.2 Hermes：对照物，不是样品

OpenClaw 的说明引用 Hermes 安全政策里的一句：对抗性模型面前，唯一的安全边界是操作系统。并指出 Hermes 的插件 API 很宽，同意用的是一组能力编号，不是把执行和凭据拆开。Hermes 星标高，适合当作「单进程里什么都有」的对照。Mika 已经把改变外部世界的动作收进 `ActionExecutor`，不需要再引入一个插件进程模型。

### 4.3 Zer0Qing/Muse：手机上的另一套产品

README（2026-11-06 读取）把产品收成四句：换会话、换渠道、换模型仍是同一个它；没人叫也会推进；能在手机上动手；关掉再开还在。

和 Mika 重叠的能力：无障碍、Shell、虚拟屏、浏览器、终端、定时任务、结果回到会话、本地数据库、模型供应商可换、Keystore、工具分层加载（它用 `find_tools` 按需装载长尾工具）。

它多出来、且和「个人助理」直接相关的部分：

- **四层记忆**：对话、事实提取、滚动摘要、后台编译去重。关键事实（医疗、财务、身份）不衰减，日常事实按使用淡忘。每条可追溯来源，可改权重。
- **牵挂账本**：记下以后想问的事，到时机挑一条说，而不是空转找话。出声次数还会按用户有没有理它来收。
- **安静时段**：该睡的时候不响。
- **五个聊天渠道**共用一个人格，每个联系人单独的上下文。
- **风险档**：信任 / 询问 / 严格，加命令白名单和硬超时。
- **备份**：设置里有数据备份页。
- **悬浮胶囊速记**，落到本机速记库。

许可证是 GPL-3.0。Mika 是 Apache-2.0，不能把它的源码并进来。上面只使用 README 里公开的产品事实。它的人设、心情四维、多助手群聊、根权限读其他应用数据，不进入方案：这些要么是另一类产品，要么放宽了 Mika 已经守住的边界。

### 4.4 小仓库里重复出现的接法

这些仓库星标低，单独都不构成产品。它们用几乎同一套零件：

- **[cedricziel/assistant](https://github.com/cedricziel/assistant)**：技能是符合 Agent Skills 的 `SKILL.md` 目录；执行痕迹用来提议修改技能，人用 `/review` 决定收不收；`schedule-task` 可以是 cron 或一次性；MCP 把技能暴露给别的客户端。
- **[ridingbytes/kaisho](https://github.com/ridingbytes/kaisho)**：任务、时间、笔记留在本地文本或 SQL 里，经 MCP 给 Claude Code、Cursor 这一类工具。定时作业跑一段提示词，模板用 YAML。工具分读、写、破坏性三档，有 URL 允许名单和每次会话的写入上限。
- **[Dodaios/Dodaios](https://github.com/Dodaios/Dodaios)**：桌面端。README 写每个动作经过风险检查（safe、monitor、ask、block），每次运行留记录；重复做的事沉淀成技能、常规或一个专门的 Agent，并且可以看见、可以撤销。技能、MCP、插件从一个市场安装。产物（页面、报告、看板）集中在一处。
- **[mastra-ai/personal-assistant-example](https://github.com/mastra-ai/personal-assistant-example)**：一个示例，不是产品。每日流程是固定的几步（新闻、仓库动态），邮件和待办经 MCP。说明「早晨简报」常常是一条编排，不是新的 Agent 循环。
- **[gashel01/schedulermcp](https://github.com/gashel01/schedulermcp)**：把 cron、间隔、一次性和条件监视做成给 Agent 用的时钟，失败指数退避，存储可换 SQLite。Mika 的 `TriggerEngine` 已经覆盖前三种；缺的是安静时段和失败合并，不是再接一个调度器。

mem0 和 Letta 解决的是「记忆怎么存、怎么改」。Mika 已有事实、偏好、需批准的做法，以及加密文件。方案不把记忆换成向量库。Khoj 解决的是「自己的文档能被问到」，和 Mika 的网页加沙箱研究重叠，不单独立项。browser-use 只覆盖浏览器，Mika 的内置浏览器已经是这只手。

### 4.5 主动去找：OpenMuse、dsh-go、OpenGrok

这一节回答后续的一个要求：助手可以自己去外面看，把值得看的东西带回来，但要先了解用户，再给能站住的建议。三个名字各自对应不止一个仓库。下表与第 4 节分开，仍是 2026-11-06 的 `gh api` 快照。

| 仓库 | 星 / Fork | 许可 | 最近推送 | 它实际是什么 |
| --- | --- | --- | --- | --- |
| [CopilotKit/openmuse](https://github.com/CopilotKit/openmuse) | 6263 / 915 | MIT | 2026-11-05 | 带浏览器和任务工人的个人 Agent，alpha |
| [OpenMuseAgent/OpenMuse](https://github.com/OpenMuseAgent/OpenMuse) | 21 / 3 | MIT | 2026-10-19 | 0.1.0–0.6.0 的说明归档；GitHub 的 archived 标志仍是 false |
| [nano-muse/nanoMuse](https://github.com/nano-muse/nanoMuse) | 3255 / 394 | GPL-3.0 | 2026-11-06 | 上面那个仓库声明的续作 |
| [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) | 12653 / 575 | AGPL-3.0 | 2026-11-06 | DeepSeek Harness 的 Android 客户端 |
| [JopenChen/dsh-go](https://github.com/JopenChen/dsh-go) | 19 / 1 | MIT | 2026-09-12 | 进程内的 Harness 接缝，纯 Go |
| [john-walks-slow/dsh-proactive](https://github.com/john-walks-slow/dsh-proactive) | 14 / 1 | README 写 MIT；API 的 SPDX 为空 | 2026-10-26 | 宿主级闹钟，没事就静默收回 |
| [a740411040/dsh-assist](https://github.com/a740411040/dsh-assist) | 4 / 1 | MIT | 2026-11-01 | 可选的空闲闲聊，无人理就停 |
| [deepseek-ai/dsh-superagent-prototype](https://github.com/deepseek-ai/dsh-superagent-prototype) | 20105 / 2326 | MIT | 2026-11-03 | 面向一个代码仓库的主动编程助手，研究原型 |
| [oracle/opengrok](https://github.com/oracle/opengrok) | 5027 / 845 | API 返回 NOASSERTION | 2026-11-05 | 源码搜索引擎 |
| [OnlyTerp/opengrok](https://github.com/OnlyTerp/opengrok) | 507 / 61 | MIT | 2026-09-22 | 给已经装好的 Grok Bot 换模型 |
| [diggerhq/openmuse](https://github.com/diggerhq/openmuse) | 51 / 7 | API 未返回 SPDX | 2026-09-11 | 部署在 OpenComputer 上的个人助理 |

用这三个名字还能搜到桌面多机器人和编程助手分叉。那些仓库的 README 这次没有打开，下面不写它们的行为。

**OpenGrok 不是调研产品。** `oracle/opengrok` 的仓库说明是用 Java 写的源码搜索和交叉引用。`OnlyTerp/opengrok` 的说明是在已有的 Grok Bot 安装里换成本地配置的模型，密钥留在本机，它自己不宿主一个会出去找资料的 Agent。Mika 已经是手机上的助手，不需要一个模型边车，也不需要一个源码搜索引擎来完成「了解我再分享」。

**OpenMuse 有三家，Feed 只取归档说明里的形状。** [OpenMuseAgent/OpenMuse 的中文 README](https://github.com/OpenMuseAgent/OpenMuse/blob/main/README_zh.md) 写明：本仓库迁到 nanoMuse，这里留下 0.1.0–0.6.0，不再维护。同一份说明里的 Feed 是：用户用自己的话写下 Feed 指令，Agent 按它对用户的了解和用户让它关注的东西，每天或随时写几篇短帖。Ideas 来自目标、记忆和近期对话，点一下就发出去。Goals 有主动性旋钮和安静时段。记忆可看、可改、可忘；0.5.0 起还可以按含义召回。Sentinel 挡在每次工具调用前面。这些句子来自不再维护的 0.6.0 说明，不是对 nanoMuse 现行代码的核对。nanoMuse 当天的许可证是 GPL-3.0，仓库说明还包含 Android，以及对没有接口的应用做手机 GUI。Mika 不并入这份代码，也不为了分享去多做一层操作微信或支付宝的界面。

[CopilotKit/openmuse](https://github.com/CopilotKit/openmuse) 的 README（当天的 `main`，MIT，自称为 alpha）把和「带回来」直接相关的两格写成：

- **Ideas**：建议带来源证据；可以改、接受或丢掉。已经发出的回复和已经做完的同类工作不再出现。
- **Goals & Tracking**：对公开页面做反复检查，等变化、等某段文字出现，或等美元价格跨过线；警报去重，失败则退避。

它的演示是先查 Hacker News，或先给水族馆行程做出带来源的卡片、把偏好收窄，再确认选择。公开网页搜索默认开着，走 Parallel 的免密钥 Search MCP；README 写明查询和上下文（可能含对话或任务里的信息）会送到 Parallel。Mika 已有的 `web_search` 只把查询词和条数发给用户自己配置的端点。方案沿用这个端点，不内置一家会收到对话的搜索服务。CopilotKit 的常驻浏览器、Linux 工作区和接管台是另一台电脑，不搬。

[diggerhq/openmuse](https://github.com/diggerhq/openmuse) 只核对了 API 说明：一个部署出去的个人助理，建在 OpenComputer 上。云上电脑已经在第 5 节选过边，这里只为了把名字分开。

**dsh-go 里和主动性有关的是闹钟和确认，不是那个 Go 内核。** [JopenChen/dsh-go](https://github.com/JopenChen/dsh-go) 的说明是把 Harness 的回合、目标和技能接缝做成进程内的 Go 库。它不是个人助理，下面不从它取产品行为。

[ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) 的 README 写：代码以 AGPL-3.0 发布，`sdk/` 和 `examples/` 为 MIT；名称、标识和吉祥物不在这份授权里。方案这些目录都不复制。2026-11-05 发布的 [v1.0.11 说明](https://github.com/ccch1mneyyy/dsh-Go/releases/tag/v1.0.11) 写了一条和 Mika 已经定下的常规相符的规矩：人在页面或 App 里自己新建、修改定时任务，不再二次确认；助手经工具新建或修改，仍要单独确认，拒绝或两分钟内没有确认就什么都不保存。同一则说明写明，这次引擎升级还没有在真机上用真实模型账号验证过。方案采用的是「助手提议的计划，用户保存之前不存在」，不采用那两分钟的计时，也不把这则发布说明当成已经在手机上复核过的行为。

[dsh-proactive](https://github.com/john-walks-slow/dsh-proactive) 的 README 把「有事才出声」做成宿主级闹钟：到点唤醒冷会话；模型若认为没什么好说，调用 `proactive_reclaim`，这一轮收成用户看不见的墓碑。唤醒消息标明是系统唤醒，不装作用户刚发来的话。安静时段、每日可见消息预算也在宿主上。它的可见消息还可以顺着 Harness 已有路由进到聊天软件；Mika 的调研结果留在自己的对话里。

[dsh-assist](https://github.com/a740411040/dsh-assist) 是另一件小事：空闲后主动聊一句，要用户自己开启。README 的例子是空闲 15 分钟、两次至少隔 30 分钟、连续两次没人理就暂停、23:00–08:00 不说话。公开导出不含已存记忆。它声明真实模型上的主动聊天还没验证，默认内容是一个陪伴人设。方案只取「连续没人看就停」，不取人设，也不取空闲了就找话。

[dsh-superagent](https://github.com/deepseek-ai/dsh-superagent-prototype) 的 README 把它定义成对着一个代码仓库的主动编程助手，并写明是研究原型，不是正式产品。有用的一句是：尽量少占人的注意力，尽量多给出能用的结果；能行动的代理也可以浪费整组人的注意力，所以只在被要求时、或在团队写下的规则允许时行动，只在必须有人处理或结果用得上时开口。它可选地再听一个办公入口。本方案不接新的入口。README 没有把「先观察、先不做事」写成已经实现的开关，这里也不写成它的能力。

### 4.6 OpenMinis 与 Operit：强在哪，以及什么不能并进来

用户把目标定成市面上最强的个人助理。这两家是 2026-11-06 当天最接近这句话的开源产品。数字是同日的 `gh api`。没有安装它们，也没有把它们的任务跑一遍。

| 仓库 | 星 / Fork | 许可 | 最近推送 | 它实际是什么 |
| --- | --- | --- | --- | --- |
| [OpenMinis/OpenMinis](https://github.com/OpenMinis/OpenMinis) | 5784 / 734 | GPL-3.0 | 2026-11-06 | iOS 与 Android 上的本机 Agent。README 说仓库是私有开发树的镜像，不接受拉取请求 |
| [OpenMinis/MinisSkills](https://github.com/OpenMinis/MinisSkills) | 593 / 97 | MIT | 2026-11-06 | 给 Minis 用的技能集 |
| [AAswordman/Operit](https://github.com/AAswordman/Operit) | 9299 / 805 | LGPL-3.0 | 2026-11-06 | Android Agent。README 写主代码是 LGPL-3.0-only，工具和示例可能另有许可 |
| [AAswordman/Operit2](https://github.com/AAswordman/Operit2) | 622 / 137 | AGPL-3.0 | 2026-11-06 | 跨设备的下一代，Rust 核心，预览 |

检索里还有同名的下游包。那些仓库这次没有打开，下面不写它们的行为。

**OpenMinis 强在设备里的生活，不在另一套主循环。** [README](https://github.com/OpenMinis/OpenMinis/blob/main/README.md) 写：自备模型密钥，Alpine 沙箱，浏览器，`SKILL.md` 用到再读，记忆跨会话。设备工具包括健康、日历、提醒、通讯录、HomeKit、蓝牙、剪贴板、媒体、闹钟。工作区用 `minis://` 分开。它声称给 Claude、Codex、OpenClaw 或 Hermes 写的技能一般能原样跑。用例是把一餐写进 Apple Health、用快捷指令把时间线读成闹钟、把群消息收成提醒、把分享收成日程。2026-10-25 的 Android 1.15 发布说明写了：助手可以在对话卡片里提问，后台时通知能当场答，未答的问题在 App 重启后仍能答；用量页按今天、7 天、30 天和全部，并按服务商与模型拆开；长任务先裁旧的思考和工具输出，再做摘要；子任务卡片能看见、能引导、能停，最多 6 个对话同时跑、每个对话 3 个子任务。这些是发布说明里的句子，不是这次在真机上复核的结果。许可证是 GPL-3.0，因为它链了 iSH 和 PRoot 的 GPL 组件。方案不复制这份代码，也不复制 MinisSkills。

**Operit 强在 Android 上的工具面，许可和默认询问都不适合并进 Mika。** [README](https://github.com/AAswordman/Operit/blob/main/README.md) 把产品写成任务工作区：附件、工具进度、文件变化。模型可以是云端、兼容端点、MNN 或 llama.cpp 的 GGUF。手机自动化走无障碍、Shizuku 提供的 ADB，或 Root，另有 PhoneAgent/AutoGLM。终端默认是 PRoot 里的 Ubuntu 24.04。记忆是可编辑的图，外加角色卡和多角色群聊，每个角色自带模型、记忆、工具包和 MCP。扩展是一个市场：脚本、ToolPkg、技能、MCP，再加可视化工作流。工具权限是三项：自动允许、每次询问、拒绝，README 写默认是每次询问。数据在本机，云端只是用户自己配的模型端点。Android 8.0 起、仅 arm64。主代码 LGPL-3.0。方案不复制。

**Operit2 要的是多台设备接着干，而且它自己说还没做完。** [README](https://github.com/AAswordman/Operit2/blob/main/README.md) 写每个跑着核心的实例是一个 `CoreNode`，`Space` 负责协作和持久同步，`Binding` 记下下一步由哪台接着做。交接发生在工具结果已经落下、下一次模型请求还没开始的时候；不搬走进行中的模型请求、终端或浏览器。密钥和本机权限不因加入 Space 就复制到别的节点。README 标明这是预览，完整体验仍在做，许可证是 AGPL-3.0。Mika 已经是电脑把任务交给手机、手机执行。方案保持这条方向，不另做一套要同步运行时的设备空间。

和 Mika 当天代码对过的部分：

- `ask_user` 已经能带 2–8 个选项。`UserExchange.awaitAnswer` 在无人在场时先把问题和选项送达并写入 `PendingAskStore`，再返回 `no_answer`，不等待。人在场时会等，时限是现有的 `inputTimeoutMs`。
- 进程被杀后 `RunRecovery` 告知中断，架构写明不从屏幕中间续跑。
- Token 用量页已经在设置里，按服务商叠图，范围包括 24 小时、7 天、30 天。`ContextCompressor` 已经会压缩上下文。这两项不因为 OpenMinis 1.15 有类似页面就重做。
- `spawn_agent` 只读，一次一个，默认 15 步、最多 40 步，只有网页和沙箱。Android 14 以下本来就一次一个界面任务。不把同时跑 6 个对话当成更强。

## 5. 各家实际上收敛的地方

把营销词去掉，能对上的只有这些：

1. **打开时看见今天，而不是空输入框。** Today 把这写成产品定义。Meta Muse 用目标页和正在做的事补上「现在在忙什么」。
2. **主动有配额。** Today 只有早晚两个锚点，并且没变化就折叠。Meta Muse 做完后台工作再判断值不值得出声。Zer0Qing/Muse 有安静时段，还按用户理不理它来限制主动消息。
3. **难撤销的外部动作由代码拦住，用卡片问人。** Meta Muse 的 Sentinel 不在对话里问。Today 写明发邮件、改日历、给别人发消息都要确认。OpenClaw 把「策略在代码里」写成架构要求，尽管它的默认安装并没有把沙箱打开。Dodaios 用四档风险。Kaisho 把破坏性工具单独限额。
4. **凭据不进模型上下文。** Meta Muse 用代用令牌，在网络边界替换。OpenClaw 用秘密句柄。两边都承认没走这条路径的值仍然会漏。
5. **记忆是可编辑的事实，不是聊天气泡。** Today 用行。Zer0Qing/Muse 用带来源的事实，并保护少数关键类。OpenClaw 用一份短的 `MEMORY.md` 加重文件。改一行就要影响下一次。
6. **扩展是目录和连接，不是往主循环里注册代码。** 技能是 Markdown 目录（Agent Skills 这一路）。真正的接口走 MCP 或审核过的连接器，并标明读还是写。定时任务引用一段目标和一份技能，本身不是新工具。
7. **自我改进要人点头。** cedricziel/assistant 先提议再 `/review`。Mika 已经这样做：个人技能和 App 知识先草稿、再试用。
8. **渠道只是嘴，主人要单独绑定。** OpenClaw 对未知私聊默认配对。Mika 的 `OwnerIdentity` 已是这个形状。微信渠道的 `confirms` 为假，付款和接管仍回手机。
9. **向外找东西之前，先有用户自己确认过的关注；没有新的对得上的来源就不出声。** OpenMuse 归档说明里的 Feed 要求用户写下关注什么。CopilotKit 的 Ideas 带来源，丢掉之后同类不再来，盯住的是用户指定的公开页面。dsh-proactive 没事就收回。dsh-assist 在连续无人回应后暂停。dsh-superagent 把少占注意力写成工作方式。Today 已有的那句仍适用：主动消息得是你希望一个人来告诉你的。
10. **问人的那一句要送到人手里，答完再继续；答不了就不猜外部动作。** OpenMinis 1.15 把问题放进通知，并说重启后还能答。Operit 用每次询问换安全，默认把能做的事也问一遍。Mika 已有 `ask_user` 和执行入口的分档。要补的是无人在场时问题也送达，而不是改成每个工具都问，也不是从死掉的屏幕接着点。

同样清楚的分歧，方案里要选边：

- **电脑在哪。** Meta Muse 和 Today 用云上的常驻电脑。Zer0Qing/Muse、OpenClaw、Mika 把执行留在用户自己的设备。Mika 继续留在手机：登录态、虚拟屏和沙箱已经在这里。不为了像 Muse 加一台云主机。
- **工具有多少。** Zer0Qing/Muse 宣称 200 多个工具，再用检索装载。Mika 故意把手机、网页、沙箱折起来，用到再展开。继续折，不把工具目录做大。
- **人格。** Meta Muse 和 Zer0Qing/Muse 都花了很多界面在名字、形象、心情上。Today 几乎不谈这个。个人助理的第一天价值在安排、确认和结果，不在人设。

## 6. 对 Mika 的最终建议

Mika 已经有办事的主循环、虚拟屏、浏览器、按对话隔离的沙箱、定时与条件触发、加密记忆、个人技能、MCP 客户端和服务器、微信这一个外部渠道。缺的是个人助理的用法层，以及让这个用法层能被装进来的扩展格式。

建议按这个顺序做。每条都对应上面某一条收敛，并写明不做什么。方案细节、验收和阶段在 [personal-assistant-prd.md](personal-assistant-prd.md)。

1. **规矩放进 `ActionExecutor`。** `IrrevAction.send`（发送、发布、转发、回复）和 `IrrevAction.delete` 默认先问；允许名单里的对象可以直接发送；问不到人就留稿或不删。付款和下单保持现在的代码：询问模式才问，判定为付款的那一下仍交给用户亲手付；自主模式在 `ConfirmDecision.judge` 开头就放行，连付款也不拦。自主模式不放宽发送和删除，也不让定时任务或电脑交来的任务在夜里发出去。精确的「提交」这一版不动，避免把保存笔记也拦进确认。这是 Meta Muse、Today 在做、而 Mika 还没做到发送和删除上的那条。不另做第二个模型当 Sentinel：同意卡片已经有，发送和删除走同一张。
2. **按包名禁止。** 某个 App 不许发帖、不许删除、不许付款。禁止优先于允许名单。这比全局只有询问和自主两档更接近 Dodaios、Kaisho 的分档，又不用引入四档名词。
3. **定时任务的安静。** 免打扰时段里，模型任务推迟，固定提醒仍响。连续失败并成一条。创建和修改时写出接下来三次。路线图第 4 条已经列了这三件，本调查说明它们是个人助理的门槛，不是锦上添花。不新接一个调度服务。
4. **花费上限。** 自备模型密钥的产品，今天和每条常规都要能在调用前停。用量账本已经有了。订阅制的 Muse、Today 不突出这一点，对 Mika 反而更要紧。
5. **只读的系统来源。** 读今天和明天的日程、读下一个闹钟。现在只能经系统接口设闹钟、打开一个填好的新日程。早晨常规只依赖这两个读，不打开任何聊天或办公 App。通知摘要默认关闭，放在后面，作为还没有邮箱连接器时的本地替代。
6. **常规是数据。** 一条常规固定何时做、做什么、受哪条规矩、做到哪停。常用说法、分享进来之后怎么处理、工作日早晨，都是常规。导入的常规模板默认关着，用户启用才登记。这是 Today 的社区技能、Kaisho 的 YAML、OpenClaw 的心跳清单的交集，落在 Mika 已有的 `schedule_task` 上面。
7. **今天这一页是投影。** 列出下一个日程、下一个闹钟、等你点头的动作、今天各条常规的结果、失败的几条。点结果回到原对话。不新造任务系统，不把多段对话收成 Meta Muse 那种一条主聊天。
8. **出声要有变化。** 早晨常规和昨天一样、闹钟也对，就只在今天这一页记一行「无变化」，不发通知。这是 Today 的折叠和 Meta Muse 的「值得打扰」在 Mika 里能做的最小版本。
9. **扩展包 = 知识 + 技能 + 沙箱脚本 + 可选的常规模板。** 个人技能今天只是 Markdown，脚本只能打进 APK。包补上这一半，校验沿用现在的技能校验。脚本默认断网。包不能注册 Kotlin 工具，不能改审批，不能自带令牌。接口类能力继续走已经做好的 MCP 客户端。渠道继续走 `Channel`，这一轮不新增。
10. **轻账和加密导出。** 运行截图仍只留最近三十次。另记何时、哪条常规、结局一句话、产出的文件名，供跨对话搜索。导出记忆、画像、技能、包、常规和名单，不导出服务商密钥。Zer0Qing/Muse 有备份页；Today 用云同步。Mika 选择本机加密文件。
11. **自我改进保持人点头。** 现有的技能草稿和 App 知识草稿已经符合 cedricziel/assistant 的 `/review`。不自动写进正式技能。
12. **主动调研是一条默认关闭的常规，不是一条帖子流。** 名字用「值得看」。关注名单写在这条常规上，用户保存才有；名单空着就不搜索、不通知。到点只给 `web_search`、`web_read_page`、`memory_search` 和回答。现有的 `info-research` 可以列入技能索引，技能正文不能把工具表撑开。每条建议写明对上了哪一条关注、主张、这次读过的网址。同一网址或同一主张不再说。用户点「不要这类」就记下一条偏好。连续两次通知都没被打开，这条常规暂停。结果出现在原对话和今天这一页，不发到别的 App。另有一条默认关闭的模板「盯这个页面」：用户填网址和要等的变化，第一次只记账。这是 CopilotKit 的 Ideas 和页面检查、OpenMuse 归档里「按你写下的指令写」、dsh-proactive 的静默收回、dsh-assist 的无人理就停，收成 Mika 已有的常规。不新做 Feed，不新做第二个模型来判断值不值得说，不新做 `proactive_reclaim` 这种工具。没变化时回答「无变化」，出声规则仍是上面第 8 条「出声要有变化」。
13. **最强指办得成、问得住，并且跟得上 Mika 上游。** 和 OpenMinis、Operit 比，Mika 已经强在已登录 App 上的虚拟屏、电脑把任务交到手机、按动作而不是按每个工具询问、只读子任务、按对话隔离的沙箱，以及已经做好的用量页和上下文压缩。方案补上的是用法层：规矩、今天、常规、值得看，加上无人在场时 `ask_user` 也把问题和选项送达。送达之后这次运行仍以 `no_answer` 结束，不挂起。用户以后回答，是同一条对话里一次新的运行，不恢复上次的虚拟屏。实现放在分支 `personal-assistant`，上游先进入 `main`，再合并进这条分支，避免新功能把 Mika 的更新挡在外面。不换掉 Alpine，不内置 MNN 或 llama.cpp，不建 ToolPkg 市场、角色卡、图记忆、可视化工作流，也不做 Operit2 那种还在预览中的跨设备同步。别人的 `SKILL.md` 缺了 Mika 的工具就失败，不承诺原样能跑。

明确不做：

- 每用户一台云虚拟机、代用令牌体系、连接器商店。凭据继续进 Android Keystore；MCP 令牌继续不进提示词。代用令牌留到真的有脚本能看见环境变量时再议。
- 人设、形象、心情、多助手群聊。
- 把 Zer0Qing/Muse 的 GPL 代码、OpenClaw 的 Gateway，或任何一个插件运行时并进仓库。
- 根权限、读其他应用的私有数据、200 个工具。
- 秒级监视任意 App。条件触发保持低频，先盯日历和下一个闹钟。
- 从中断的屏幕接着跑。架构已经定为告知后重来。
- 再接一个即时通讯渠道。电脑连接和微信已经是两条嘴。调研结果也不从这两条嘴发出去。
- 从屏幕文字、通知或其他 App 推断一份兴趣档案。关注只来自用户保存的名单，以及他已经能改的记忆。
- 每天自动写几篇、不经名单的 Feed。OpenMuse 归档说明里的日更是它们的默认，不是 Mika 的。
- 把 nanoMuse 的 GPL-3.0 代码，或 dsh-Go 的 AGPL-3.0 代码，并进仓库。
- 为了分享去操作微信、支付宝或其他没有接口的 App。手机 GUI 留在用户当次交代的任务里。
- 内置一家会收到对话内容的搜索服务。搜索继续走用户配置的 `web_search` 端点。
- 把 OpenMinis 的 GPL-3.0、Operit 的 LGPL-3.0、Operit2 的 AGPL-3.0 代码并进仓库。MinisSkills 虽是 MIT，也不整份搬进来。
- 把工具权限改成 Operit 那种默认每次询问。那样会比现在更常打断，不是更强。
- 在 App 里嵌 MNN、llama.cpp，或把沙箱从 Alpine 换成 Ubuntu。模型继续走现有的服务商配置。
- 做一套跨设备同步运行时。电脑继续把任务交给这台手机，并取回结果。
- 为了同时跑更多对话而放宽 Android 14 以下一次一个界面任务的限制。
- 从被杀死的屏幕中间接着跑。未答的问题可以留下，屏幕状态不恢复。

## 7. 调查边界

- Meta Muse 的内部文件（`Soul.md` 等）只在第三方文章里出现，本文不把它当成官方规格。
- Today 没有源码。云电脑、跨设备同步的安全边界未知，所以方案不模仿它的同步。
- OpenClaw 文档写明沙箱默认关闭。引用它的「策略在代码里」，不引用它的默认部署当安全样品。
- Zer0Qing/Muse 只读了 README。71 星，不能把它的完成率当成已经核实。
- 星标用的是本机 `gh api`。OpenClaw、Hermes 的星标数量级很大，只用于说明它们是平台；方案不按星标取舍。
- 上游 Mika 仓库 `wangzan101/Mika` 在同一天是 7331 星、1690 Fork、Apache-2.0，最近推送 2026-11-06。本地克隆的远程是另一份 Fork，星标以这个上游快照为准，只说明项目仍在快速变，不进入方案。
- 没有跑这些产品的真机任务，所以「简报是否真的安静」「确认是否真的绑在一次动作上」都只是它们的书面承诺。Mika 的方案用验收条款把对应行为定成可测的，不依赖这些承诺。
- 第 4.5 节没有安装 OpenMuse、nanoMuse、CopilotKit/openmuse、dsh-Go 或那几个插件，也没有跑它们的任务。Feed、Ideas、页面检查、两分钟确认、静默收回，都是当天 README 或 v1.0.11 发布说明里的句子。
- dsh-Go 自己的 v1.0.11 说明写了引擎升级尚未在真机上用真实模型验证。dsh-assist 的 README 写了真实模型上的主动聊天尚未验证。这两句限制了引用的分量。
- nanoMuse 只核对了仓库 API（GPL-3.0）和 OpenMuse 归档 README 里指向它的那段话，没有把续作的现行界面写成方案依据。
- dsh-proactive 的 GitHub API 没有返回 SPDX。MIT 只来自它的 README。diggerhq/openmuse 的 API 也没有 SPDX，因此不写它的许可证。
- `oracle/opengrok` 的 API 许可证字段是 NOASSERTION。它是源码搜索，方案不使用它的代码，也不把字段改写成某一种开源许可证。
- 第 4.6 节没有安装 OpenMinis 或 Operit。Android 1.15 的提问、用量、子任务数量来自 2026-10-25 的发布说明。Operit 的默认询问、Ubuntu、图记忆和 Operit2 的 Space 来自各仓库当天的 README。用量页和上下文压缩在 Mika 里已经有，方案不把它们写成缺口。

## 8. 来源

- Meta Muse 产品、设计、安全、隐私、连接器指南：见第 2 节链接。设计与安全两篇的日期都是 2026-09-08。
- Today 产品说明：见第 3 节。发布 2026-08-03，更新 2026-09-15。
- OpenClaw README：`https://github.com/openclaw/openclaw`，2026-11-06 读取 `main`。架构主张：`https://docs.openclaw.ai/start/why-openclaw`。
- Zer0Qing/Muse README：`https://github.com/Zer0Qing/Muse`，2026-11-06 读取。
- 第 4 节表格：2026-11-06 的 GitHub API。Kaisho、cedricziel/assistant、Dodaios 的行为描述来自各仓库当天的 README。
- 第 4.5 节表格：同日较晚的一次 `gh api`。OpenMuse 的 Feed、Ideas、Goals 来自 `https://github.com/OpenMuseAgent/OpenMuse/blob/main/README_zh.md`。CopilotKit 的 Ideas、页面检查和 Parallel 搜索来自 `https://github.com/CopilotKit/openmuse` 的 README。dsh-Go 的确认规矩来自 `https://github.com/ccch1mneyyy/dsh-Go/releases/tag/v1.0.11`（2026-11-05），不是它的 README 正文。dsh-proactive、dsh-assist、dsh-superagent、JopenChen/dsh-go、oracle/opengrok、OnlyTerp/opengrok 的行为描述来自各仓库当天能打开的 README 或 API 说明。
- 第 4.6 节：2026-11-06 的 `gh api`。OpenMinis README 与 Android 1.15 发布说明（`https://github.com/OpenMinis/OpenMinis/releases/tag/1.15`，发布于 2026-10-25）。Operit 与 Operit2 的 README 于同日读取。
- Mika 侧：`docs/dev/architecture.md`、`docs/dev/roadmap.md`、`docs/dev/knowledge-format.md`、`docs/dev/skill-scripts.md`。主动调研用到的现有技能是 `skills/info-research`。`ask_user` 的无人在场行为以 `UserExchange.awaitAnswer` 为准。

## 9. 写完核对

写完两份文档后，对照本仓库代码又核了一遍，并改了方案里三处说宽了的话：

- 自主模式不是「付款仍要亲手付」。`ConfirmDecision.judge` 在自主时直接 `Decision.Clear`，付款也不拦。方案改成付款保持这段代码，发送和删除才是新规矩。
- `IrrevAction` 在 `core/risk/Irreversible.kt`，取值是 send、pay、delete、order、submit、other。送达只覆盖 send。没有应付金额的 submit 不在这一版里改，因为「提交」也会打在保存按钮上。屏上已有应付金额的删除仍走现有付款档（`ClassifiedRisk`）。
- 发送对象的核对今天就只认标题栏（`ConfirmationGate`）。验收沿用这一点，不新造一套对象匹配。

星标、许可证和推送时间没有改，仍是 2026-11-06 的 GitHub API 快照。Meta Muse 与 Today 的产品句子没有改成代码里没有的能力。

同日补上第 4.5 节和方案里的「值得看」之后又对过一次代码，没有改第 6.1 节的发送和删除，也没有改早晨常规的工具表：

- 网页能力是整组折叠的。`WebCapability.folds` 为真，`WebGroup` 里除了 `web_read_page`（`Risk.Read`）还有 `web_navigate`（`Risk.Write`）、交互和上传（`Risk.External`）、执行脚本。所以「值得看」不能写成「把网页能力打开，请模型只读」。方案要求按这一次运行收窄工具表，和早晨常规同一套收窄，名单不同。
- `web_search` 不在这组里。它是 `Risk.Read`，只有用户配置了搜索端点才可用，请求里是查询词和条数。失败时工具自己会建议改用浏览器；「值得看」里这条退路不生效，因为写网页的工具不在表里。
- 记忆工具是 `memory_search`、`memory_save`、`memory_edit`。调研运行只留搜索。用户点「不要这类」由应用写入偏好，不让无人值守的运行改记忆。
- 第 4.5 节的星标是后一次 API 读取。第 4 节的表没有用这次读数去改。
- `info-research` 的入口是 `web_navigate`，并且会进小红书、知乎这类 App。值得看的目标写明这两步改用 `web_search` 和 `web_read_page`，因为这趟运行的工具表里没有它们。没有把技能正文改成另一套调研流程。
- OpenMinis、Operit 看起来更全的部分，凡是 Mika 已经有的（用量页、上下文压缩、`ask_user`、只读子任务）都没有写成新阶段。阶段 G 只补「无人在场时问题在返回 `no_answer` 之前没有送达」。发送和删除的规矩、早晨常规的工具表没有放宽。
