# XiMultiLogin

## 语言 / Language

- [中文 (简体)](#中文)
- [English](#english)

## 中文

XiMultiLogin 是一个为 Minecraft Bukkit/Spigot/Paper/Folia 服务器设计的多认证方式支持插件，旨在解决 MultiLogin 取消对 Bukkit 平台支持后，社区缺乏替代方案的问题。

当前发布版本为 [v2.3](https://github.com/XiNian-dada/XiMultiLogin/releases/tag/v2.3)，同一个 JAR 可同时运行在 Paper 和 Folia 上。

## 背景

### 为什么需要 XiMultiLogin？

MultiLogin 是一个优秀的 Minecraft 多认证插件，但由于开发团队决定停止对 Bukkit 平台的支持，大量使用 Bukkit/Spigot/Paper 的服务器失去了使用 MultiLogin 的机会。XiMultiLogin 应运而生，旨在填补这一空白。

### 设计目标

- **高性能**：优化的认证流程，最小化对服务器性能的影响
- **高可配置性**：灵活的配置系统，满足不同服务器的需求
- **多认证支持**：支持 Mojang 官方认证和多种 Yggdrasil 协议认证
- **安全可靠**：严格的身份锁定机制，防止身份冒用
- **易于使用**：简洁的配置和命令系统，降低使用门槛

## 核心特性

### 多认证方式支持
- **Mojang 官方认证**：标准 Minecraft 账户登录
- **Yggdrasil 协议认证**：支持 LittleSkin 等第三方皮肤站
- **可扩展架构**：易于添加新的认证提供者

### 安全特性
- **身份锁定机制**：防止"同名不同 UUID"的安全问题
- **严格认证模式**：一旦玩家使用某种认证方式登录，后续登录必须使用相同方式
- **UUID 接管系统**：确保玩家在不同认证方式间切换时保持 UUID 一致性
- **数据库持久化**：重启服务器后玩家身份信息不会丢失

### 管理功能
- **命令系统**：完整的管理命令，支持设置和查询玩家认证方式
- **PAPI 变量支持**：与 PlaceholderAPI 集成，显示玩家认证状态
- **消息配置系统**：可自定义所有提示消息
- **盗版玩家支持**：可选是否允许未通过认证的玩家加入

### 技术特性
- **多版本兼容**：支持 Minecraft 1.16.5 - 1.21+，已验证 Paper/Folia 1.20.1 与 1.21.11
- **动态代理机制**：解决 ClassLoader 隔离问题
- **Record 重构支持**：处理 Java Record 的不可变性
- **反射工具**：跨版本的反射操作，适配不同 NMS 类结构
- **数据库支持**：SQLite（默认）和 MySQL，使用 HikariCP 连接池
- **Folia 调度兼容**：同一个 JAR 在 Bukkit/Paper/Folia 间运行时自动切换调度策略
- **自动化冒烟测试**：内置真实服务端启动脚本，验证插件启用、反射注入和停服恢复

### 已验证的平台矩阵

| 服务端 | 版本 | 状态 |
|------|------|------|
| Paper | 1.20.1-196 | 已验证 |
| Paper | 1.21.11-127 | 已验证 |
| Folia | 1.20.1-17 | 已验证 |
| Folia | 1.21.11-14 | 已验证 |

## 工作原理

### 技术架构

XiMultiLogin 通过以下几个核心组件实现多认证支持：

#### 1. 注入机制（XiInjector）

插件启动时，通过反射将自定义的 `XiSessionService` 注入到 Minecraft 服务器的验证流程中：

```
服务器启动 → XiInjector 初始化 → 注入 XiSessionService → 替换默认 SessionService
```

**技术挑战与解决方案**：

- **ClassLoader 隔离**：插件和服务器的类加载器不同，直接操作会导致类型不匹配
  - 解决方案：使用动态代理创建跨 ClassLoader 的桥接对象

- **Java Record 不可变性**：1.20.2+ 版本使用 Record 存储配置，无法直接修改
  - 解决方案：使用反射读取字段值，重建 Record 对象

- **方法签名变化**：不同版本的方法签名可能不同
  - 解决方案：使用宽松反射匹配和智能参数适配

#### 2. 认证流程（XiSessionService）

当玩家尝试加入服务器时，认证流程如下：

```
玩家登录 → hasJoinedServer 调用 → 检查数据库 → 
  ├─ 有记录 → 仅使用指定认证方式 → 成功/失败
  └─ 无记录 → 遍历所有认证方式 → 成功则记录 → 失败则拒绝
```

**严格认证模式**：

- 如果数据库中存在玩家的认证记录，只使用该记录指定的认证方式
- 如果认证失败，直接拒绝登录，不尝试其他方式
- 这防止了身份冒用：即使有人知道其他认证方式的密码，也无法登录

**UUID 接管系统**：

- 当玩家使用新认证方式登录时，系统会检查是否已有该玩家的 UUID 记录
- 如果有，使用历史 UUID，确保玩家身份一致性
- 这解决了切换认证方式后 UUID 变化导致的数据丢失问题

#### 3. 认证提供者（AuthProvider）

插件使用提供者模式支持多种认证方式：

- **MojangAuthProvider**：调用 Mojang 官方认证 API
- **YggdrasilAuthProvider**：调用 Yggdrasil 协议 API（支持 LittleSkin、HairuoSKY 等）

每个提供者都实现 `authenticate()` 方法，返回玩家的 GameProfile 或 null。

#### 4. 身份守护（IdentityGuard）

`IdentityGuard` 负责管理玩家的身份信息：

- 存储玩家名称、UUID 和认证方式的映射关系
- 防止同名不同 UUID 的安全问题
- 提供 UUID 接管功能，确保身份一致性

#### 5. 数据库管理（DatabaseManager）

支持两种数据库：

- **SQLite**：默认，自动创建 `ximultilogin.db` 文件，适合小型服务器
- **MySQL**：需要手动配置，适合大型服务器，提供更好的并发性能

使用 HikariCP 连接池优化数据库连接。

### 数据流程图

```
玩家登录请求
    ↓
XiSessionService.hasJoinedServer()
    ↓
查询数据库
    ↓
有记录？
    ├─ 是 → 仅使用指定认证方式
    │        ↓
    │     认证成功？
    │        ├─ 是 → UUID 接管 → 返回 GameProfile
    │        └─ 否 → 拒绝登录
    │
    └─ 否 → 遍历所有认证方式
             ↓
          认证成功？
             ├─ 是 → 记录认证方式和 UUID → 返回 GameProfile
             └─ 否 → 允许盗版？
                     ├─ 是 → 创建临时身份 → 返回 GameProfile
                     └─ 否 → 拒绝登录
```

## 安装方法

### 系统要求

- **Java 版本**：Java 8 或更高版本
- **服务器类型**：Bukkit/Spigot/Paper/Folia 1.16.5 - 1.21+
- **依赖插件**：PlaceholderAPI（可选，用于 PAPI 变量支持）

### 安装步骤

1. **下载插件**
   - 从 GitHub Releases 下载最新版本的 `XiMultiLogin-x.x.jar`
   - 或自行编译项目（见开发指南）

2. **安装到服务器**
   - 将 JAR 文件放入服务器的 `plugins` 目录
   - 重启服务器

3. **首次启动**
   - 插件会自动创建配置文件和数据库
   - 默认使用 SQLite 数据库，无需额外配置
   - 检查控制台日志，确保出现 `XiReflection: Session service replacement succeeded` 与 `XiMultiLogin: Session service injected successfully`

## 配置说明

插件启动后会在 `plugins/XiMultiLogin` 目录生成以下文件：

- `config.yml`：主配置文件
- `messages.yml`：消息配置文件
- `ximultilogin.db`：SQLite 数据库文件（如果使用 SQLite）

### 主配置文件（config.yml）

```yaml
# XiMultiLogin 配置文件
# 验证链顺序：从上到下尝试
pipeline:
  # Mojang 官方认证
  - type: MOJANG
    enabled: true
  
  # Yggdrasil 协议认证（LittleSkin）
  - type: YGGDRASIL
    name: "LittleSkin"
    api: "https://littleskin.cn/api/yggdrasil"
    enabled: true
  
  # Yggdrasil 协议认证（HairuoSKY）
  - type: YGGDRASIL
    name: "HairuoSKY"
    api: "https://skin.hairuosky.cn/api/yggdrasil"
    enabled: true

# 数据库配置
database:
  # 数据库类型: SQLite (默认) 或 MySQL
  type: "SQLite"
  
  # MySQL 配置 (仅当 type 为 MySQL 时生效)
  mysql:
    host: "localhost"
    port: 3306
    database: "ximultilogin"
    username: "root"
    password: "password"

# 盗版玩家设置
# 是否允许未通过任何认证的玩家加入（默认为 false）
# 不建议开启，否则可能出现安全性问题
allow_cracked: false
```

#### 配置项详解

**验证链配置（pipeline）**

- `type`：认证类型，支持 `MOJANG` 或 `YGGDRASIL`
- `enabled`：是否启用该认证方式
- `name`：认证提供者名称（仅 `YGGDRASIL` 类型需要，用于标识不同的 Yggdrasil 服务器）
- `api`：Yggdrasil API 地址（仅 `YGGDRASIL` 类型需要）

**数据库配置（database）**

- `type`：数据库类型，可选 `SQLite` 或 `MySQL`
- `mysql`：MySQL 数据库配置（仅当 `type` 为 `MySQL` 时生效）
  - `host`：MySQL 服务器地址
  - `port`：MySQL 端口
  - `database`：数据库名称
  - `username`：数据库用户名
  - `password`：数据库密码

**盗版玩家设置（allow_cracked）**

- `true`：允许未通过任何认证的玩家加入（创建临时身份）
- `false`：拒绝未通过认证的玩家加入（默认）

### 消息配置文件（messages.yml）

```yaml
# XiMultiLogin 消息配置文件
# 支持颜色代码，如 &c, &a 等
# 支持变量替换，如 {player}, {reason} 等

login:
  success: "&a登录成功！欢迎回来，{player}！"
  failed: "&c登录失败：{reason}"
  name_taken: "&c登录失败：该昵称已被使用，请选择其他昵称！"
  auth_changed: "&a认证方式已变更为 {provider}！"
  cracked: "&e登录成功（盗版模式）：欢迎，{player}！"

error:
  authentication: "&c认证失败：无法验证您的身份！"
  no_permission: "&c权限不足：您没有执行此操作的权限！"
  invalid_args: "&c参数错误：请检查命令参数！"
  player_not_found: "&c错误：玩家 {player} 未找到！"
  config_error: "&c配置错误：{reason}"
  database: "&c数据库错误：{reason}"

command:
  success: "&a操作成功！"
  set_auth: "&a成功设置玩家 {player} 的认证方式为 {auth}！"
  get_auth: "&a玩家 {player} 的当前认证方式：{auth}"
  set_allow_cracked: "&a成功设置允许盗版玩家加入：{value}"
  get_allow_cracked: "&a当前允许盗版玩家加入的设置：{value}"
  reloaded: "&a配置已重新加载！"
  messages_reloaded: "&a消息配置已重新加载！"

system:
  enabled: "&aXiMultiLogin 插件已启用！"
  disabled: "&cXiMultiLogin 插件已禁用！"
  messages_loaded: "&a消息配置加载成功！"
  messages_reload: "&a消息配置已重载！"

other:
  unknown_command: "&c未知指令：请使用 /ximultilogin help 查看帮助！"
  help: "&a使用 /ximultilogin help 查看指令帮助！"
  help_menu:
    title: "&6===== XiMultiLogin 指令帮助 ====="
    setauth: "&a/ximultilogin setauth <玩家名> <认证类型> - 设置玩家的认证方式"
    getauth: "&a/ximultilogin getauth <玩家名> - 获取玩家的当前认证方式"
    allowcracked: "&a/ximultilogin allowcracked <true|false> - 设置是否允许盗版玩家加入"
    allowcracked_status: "&a/ximultilogin allowcracked - 查看当前设置"
    reload: "&a/ximultilogin reload - 重新加载配置文件"
    info: "&a/ximultilogin info - 显示插件信息"
    footer: "&6============================="
  info:
    title: "&6===== XiMultiLogin 插件信息 ====="
    version: "&a版本: 2.3"
    author: "&a作者: XiNian-dada"
    description: "&a描述: 多登录方式支持插件"
    command: "&a指令: /ximultilogin help"
    footer: "&6==============================="
```

## 使用教程

### 玩家登录流程

1. **新玩家登录**
   - 玩家尝试加入服务器
   - 插件按配置顺序尝试不同的认证方式
   - 第一个成功的认证方式会被记录到数据库
   - 玩家登录成功，UUID 被记录

2. **老玩家登录**
   - 玩家尝试加入服务器
   - 插件查询数据库，获取该玩家的历史认证方式
   - 只使用该认证方式进行验证
   - 如果验证成功，使用历史 UUID（UUID 接管）
   - 如果验证失败，拒绝登录

3. **盗版玩家登录**（需要 `allow_cracked: true`）
   - 玩家尝试加入服务器
   - 所有认证方式都失败
   - 插件创建临时身份，允许玩家加入
   - 临时身份不会被记录到数据库

### 管理员命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/ximultilogin setauth <玩家名> <认证类型>` | 设置玩家的认证方式 | `ximultilogin.admin` |
| `/ximultilogin getauth <玩家名>` | 获取玩家的当前认证方式 | `ximultilogin.admin` |
| `/ximultilogin allowcracked <true\|false>` | 设置是否允许盗版玩家加入 | `ximultilogin.admin` |
| `/ximultilogin allowcracked` | 查看当前盗版玩家设置 | `ximultilogin.admin` |
| `/ximultilogin reload` | 重新加载配置文件 | `ximultilogin.admin` |
| `/ximultilogin info` | 显示插件信息 | `ximultilogin.admin` |
| `/ximultilogin help` | 显示帮助信息 | 无 |

**认证类型说明**：

- `MOJANG`：Mojang 官方认证
- `YGGDRASIL`：Yggdrasil 协议认证（需要指定具体的 Yggdrasil 服务器名称）

**示例**：

```
# 设置玩家 "Steve" 使用 Mojang 认证
/ximultilogin setauth Steve MOJANG

# 设置玩家 "Alex" 使用 LittleSkin 认证
/ximultilogin setauth Alex LittleSkin

# 查询玩家 "Steve" 的认证方式
/ximultilogin getauth Steve

# 允许盗版玩家加入
/ximultilogin allowcracked true

# 重新加载配置
/ximultilogin reload
```

### PAPI 变量支持

如果服务器安装了 PlaceholderAPI，插件提供以下变量：

| 变量 | 描述 |
|------|------|
| `%ximultilogin_auth_provider%` | 玩家的当前认证方式 |
| `%ximultilogin_uuid%` | 玩家的 UUID |

**示例**：

```
# 在聊天格式中使用
chat-format: "&a[%ximultilogin_auth_provider%] &f{player}: &f{message}"

# 在计分板中使用
scoreboard:
  - "&a认证方式: %ximultilogin_auth_provider%"
  - "&aUUID: %ximultilogin_uuid%"
```

### 常见使用场景

#### 场景 1：服务器同时支持正版和第三方认证

```yaml
pipeline:
  - type: MOJANG
    enabled: true
  - type: YGGDRASIL
    name: "LittleSkin"
    api: "https://littleskin.cn/api/yggdrasil"
    enabled: true
```

新玩家会先尝试 Mojang 认证，失败后尝试 LittleSkin 认证。一旦使用某种方式登录成功，后续登录必须使用相同方式。

#### 场景 2：仅支持第三方认证

```yaml
pipeline:
  - type: YGGDRASIL
    name: "HairuoSKY"
    api: "https://skin.hairuosky.cn/api/yggdrasil"
    enabled: true
```

所有玩家必须使用 HairuoSKY 认证。

#### 场景 3：允许盗版玩家

```yaml
pipeline:
  - type: MOJANG
    enabled: true

allow_cracked: true
```

正版玩家使用 Mojang 认证，盗版玩家可以不认证直接加入（临时身份）。

#### 场景 4：玩家迁移认证方式

```
# 玩家 "Steve" 原来使用 LittleSkin 认证
/ximultilogin getauth Steve
# 输出：玩家 Steve 的当前认证方式：LittleSkin

# 玩家希望迁移到 Mojang 认证
/ximultilogin setauth Steve MOJANG
# 输出：成功设置玩家 Steve 的认证方式为 MOJANG！

# 玩家下次登录时，会使用 Mojang 认证，但 UUID 保持不变
```

## 开发指南

### 环境要求

- **Java 版本**：Java 8 或更高版本
- **Maven 版本**：Maven 3.6 或更高版本
- **Git**：用于版本控制

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/XiNian-dada/XiMultiLogin.git
cd XiMultiLogin

# 编译项目（跳过测试）
mvn package -DskipTests

# 编译并运行测试
mvn package

# 生成的 JAR 文件位于 target/XiMultiLogin-2.3.jar

# 使用根目录中的服务端 JAR 做真实冒烟测试
bash scripts/smoke-all-server-jars.sh
```

如果只想验证单个服务端：

```bash
bash scripts/smoke-server-jar.sh paper-1.21.11-127.jar
bash scripts/smoke-server-jar.sh folia-1.21.11-14.jar
```

### 项目结构

```
XiMultiLogin/
├── src/
│   ├── main/java/com/leeinx/ximultilogin/
│   │   ├── XiMultiLogin.java              # 主类
│   │   ├── auth/                           # 认证相关
│   │   │   ├── AuthProvider.java           # 认证提供者接口
│   │   │   ├── XiSessionService.java       # 会话服务（核心）
│   │   │   └── providers/                  # 认证提供者实现
│   │   │       ├── MojangAuthProvider.java # Mojang 认证
│   │   │       └── YggdrasilAuthProvider.java # Yggdrasil 认证
│   │   ├── command/                        # 命令相关
│   │   │   ├── XiCommandExecutor.java      # 命令执行器
│   │   │   └── XiTabCompleter.java         # TAB 补全
│   │   ├── config/                         # 配置相关
│   │   │   ├── ConfigManager.java          # 配置管理器
│   │   │   └── MessageManager.java         # 消息管理器
│   │   ├── database/                       # 数据库相关
│   │   │   ├── DatabaseManager.java        # 数据库接口
│   │   │   ├── DatabaseFactory.java        # 数据库工厂
│   │   │   ├── SQLiteDatabaseManager.java   # SQLite 实现
│   │   │   └── MySQLDatabaseManager.java   # MySQL 实现
│   │   ├── guard/                          # 安全相关
│   │   │   └── IdentityGuard.java          # 身份守护
│   │   ├── injector/                       # 注入相关
│   │   │   └── XiInjector.java             # 注入器
│   │   ├── papi/                           # PAPI 相关
│   │   │   └── XiPlaceholderExpansion.java # PAPI 变量
│   │   └── reflection/                     # 反射相关
│   │       └── XiReflection.java          # 反射工具
│   ├── main/resources/
│   │   ├── config.yml                      # 配置文件
│   │   ├── messages.yml                    # 消息配置
│   │   └── plugin.yml                      # 插件配置
│   └── test/                               # 测试代码
├── pom.xml                                 # Maven 配置
└── README.md                               # 本文档
```

### 扩展认证方式

如果需要添加新的认证方式，按照以下步骤：

1. **实现 AuthProvider 接口**

```java
package com.leeinx.ximultilogin.auth.providers;

import com.leeinx.ximultilogin.auth.AuthProvider;
import java.util.logging.Logger;

public class CustomAuthProvider implements AuthProvider {
    private final String name;
    private final String apiUrl;
    private final Logger logger;

    public CustomAuthProvider(String name, String apiUrl, Logger logger) {
        this.name = name;
        this.apiUrl = apiUrl;
        this.logger = logger;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object authenticate(String username, String serverId) {
        // 实现认证逻辑
        // 返回 GameProfile 对象或 null
        return null;
    }
}
```

2. **在 ConfigManager 中注册新的认证类型**

3. **在 XiSessionService 中添加对新认证类型的支持**

4. **更新配置文件格式**

### 贡献指南

欢迎提交 Issue 和 Pull Request 来帮助改进这个项目！

1. Fork 仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开 Pull Request

## 常见问题

### Q1: 为什么玩家登录失败？

**A**: 检查以下几点：

1. 确认配置文件中的认证方式已启用（`enabled: true`）
2. 查看控制台日志，确认认证过程
3. 如果是老玩家，确认使用的是正确的认证方式
4. 检查网络连接，确保能访问认证服务器

### Q2: 如何切换玩家的认证方式？

**A**: 使用命令 `/ximultilogin setauth <玩家名> <认证类型>`。

注意：切换认证方式后，玩家的 UUID 不会改变（UUID 接管系统）。

### Q3: 为什么盗版玩家无法加入？

**A**: 检查配置文件中的 `allow_cracked` 设置，确保为 `true`。

### Q4: 如何支持多个 Yggdrasil 服务器？

**A**: 在配置文件中添加多个 YGGDRASIL 类型的配置，每个配置使用不同的 `name`：

```yaml
pipeline:
  - type: YGGDRASIL
    name: "LittleSkin"
    api: "https://littleskin.cn/api/yggdrasil"
    enabled: true
  - type: YGGDRASIL
    name: "HairuoSKY"
    api: "https://skin.hairuosky.cn/api/yggdrasil"
    enabled: true
```

### Q5: 数据库数据会丢失吗？

**A**: 不会。玩家的身份信息（名称、UUID、认证方式）会持久化存储在数据库中，重启服务器不会丢失。

### Q6: 支持哪些 Minecraft 版本？

**A**: 支持 Minecraft 1.16.5 - 1.21+。当前已自动化验证 Paper/Folia 1.20.1 与 1.21.11，并对 1.20.2+ 的 Record 结构做了专门适配。

### Q7: 如何查看玩家的认证方式？

**A**: 使用命令 `/ximultilogin getauth <玩家名>`，或使用 PAPI 变量 `%ximultilogin_auth_provider%`。

### Q8: 插件会影响服务器性能吗？

**A**: 不会。插件使用优化的认证流程和数据库操作，对服务器性能影响极小。数据库操作使用 HikariCP 连接池，进一步优化性能。

## 版本信息

### 当前版本
- **版本号**：2.3

### 版本历史
- **v2.3**：Paper/Folia 兼容版本
  - 同一个 JAR 同时支持 Paper 与 Folia
  - 新增运行时调度桥接，兼容 Bukkit、Paper 和 Folia 调度模型
  - 修复 1.20.1/1.21.x 下的 SessionService 反射注入与恢复
  - 将命令、PAPI 和缓存路径调整为更适合 Folia 的执行方式
  - 新增单元测试与真实服务端冒烟测试脚本

- **v2.0**：安全增强版本
  - 实现深度防御机制，双重阻塞确保安全性
  - 优化临时UUID生成逻辑，使用真实UUID消除LuckPerms警告
  - 增强线程安全设计，使用ConcurrentHashMap处理并发登录
  - 添加详细的安全审计日志，提高可观测性
  - 改进认证失败处理，显示自定义错误消息而非系统默认消息
  - 优化事件监听器优先级，确保安全检查优先执行
  - 完善资源清理机制，防止内存泄漏

- **v1.0**：初始版本
  - 支持 Mojang 和 Yggdrasil 认证
  - SQLite 和 MySQL 数据库支持
  - 身份锁定机制
  - UUID 接管系统
  - 严格认证模式
  - 命令系统
  - PAPI 变量支持
  - 消息配置系统
  - 盗版玩家支持
  - 多版本兼容（1.16.5 - 1.21+）
  - 特别优化 1.20.2+（Record 支持）

## 许可证

本项目采用 MIT 许可证 - 详情请参阅 LICENSE 文件

## 联系方式

- **作者** XiNian-dada
- **项目地址**：https://github.com/XiNian-dada/XiMultiLogin
- **问题反馈**：https://github.com/XiNian-dada/XiMultiLogin/issues

---

**享受使用 XiMultiLogin！** 🎉

## English

XiMultiLogin is a multi-authentication plugin designed for Minecraft Bukkit/Spigot/Paper/Folia servers. It aims to solve the problem of the community lacking alternatives after MultiLogin discontinued support for the Bukkit platform.

The current release is [v2.3](https://github.com/XiNian-dada/XiMultiLogin/releases/tag/v2.3), and the same JAR is intended to run on both Paper and Folia.

## Background

### Why do we need XiMultiLogin?

MultiLogin is an excellent Minecraft multi-authentication plugin, but due to the development team's decision to stop supporting the Bukkit platform, many servers using Bukkit/Spigot/Paper lost the opportunity to use MultiLogin. XiMultiLogin emerged to fill this gap.

### Design Goals

- **High Performance**: Optimized authentication process, minimizing impact on server performance
- **High Configurability**: Flexible configuration system to meet different server needs
- **Multi-Authentication Support**: Support for Mojang official authentication and multiple Yggdrasil protocol authentications
- **Security and Reliability**: Strict identity locking mechanism to prevent identity theft
- **Easy to Use**: Simple configuration and command system to lower the usage threshold

## Core Features

### Multi-Authentication Support
- **Mojang Official Authentication**: Standard Minecraft account login
- **Yggdrasil Protocol Authentication**: Support for third-party skin sites like LittleSkin
- **Extensible Architecture**: Easy to add new authentication providers

### Security Features
- **Identity Locking Mechanism**: Prevent security issues of "same name different UUID"
- **Strict Authentication Mode**: Once a player logs in using a certain authentication method, subsequent logins must use the same method
- **UUID Takeover System**: Ensure UUID consistency when players switch between different authentication methods
- **Database Persistence**: Player identity information is not lost after server restart

### Management Features
- **Command System**: Complete management commands, support setting and querying player authentication methods
- **PAPI Variable Support**: Integration with PlaceholderAPI, displaying player authentication status
- **Message Configuration System**: Customizable all prompt messages
- **Cracked Player Support**: Optional whether to allow players who fail authentication to join

### Technical Features
- **Multi-Version Compatibility**: Support Minecraft 1.16.5 - 1.21+, verified on Paper/Folia 1.20.1 and 1.21.11
- **Dynamic Proxy Mechanism**: Solve ClassLoader isolation issues
- **Record Reconstruction Support**: Handle immutability of Java Record
- **Reflection Tools**: Cross-version reflection operations, adapt to different NMS class structures
- **Database Support**: SQLite (default) and MySQL, using HikariCP connection pool
- **Folia Scheduler Compatibility**: The same JAR switches scheduling strategy automatically on Bukkit, Paper and Folia
- **Automated Smoke Tests**: Includes real server startup scripts to verify plugin enable, reflection injection and restoration on shutdown

### Verified Platform Matrix

| Server | Version | Status |
|------|------|------|
| Paper | 1.20.1-196 | Verified |
| Paper | 1.21.11-127 | Verified |
| Folia | 1.20.1-17 | Verified |
| Folia | 1.21.11-14 | Verified |

## Working Principle

### Technical Architecture

XiMultiLogin implements multi-authentication support through the following core components:

#### 1. Injection Mechanism (XiInjector)

When the plugin starts, it injects the custom `XiSessionService` into the Minecraft server's verification process through reflection:

```
Server startup → XiInjector initialization → Inject XiSessionService → Replace default SessionService
```

**Technical Challenges and Solutions**:

- **ClassLoader Isolation**: Different class loaders for plugins and servers cause type mismatch when operating directly
  - Solution: Use dynamic proxy to create cross-ClassLoader bridge objects

- **Java Record Immutability**: 1.20.2+ versions use Record to store configuration, which cannot be directly modified
  - Solution: Use reflection to read field values and rebuild Record objects

- **Method Signature Changes**: Method signatures may differ between versions
  - Solution: Use loose reflection matching and intelligent parameter adaptation

#### 2. Authentication Process (XiSessionService)

When a player attempts to join the server, the authentication process is as follows:

```
Player login → hasJoinedServer call → Check database → 
  ├─ Has record → Only use specified authentication method → Success/Failure
  └─ No record → Traverse all authentication methods → Record if successful → Reject if failed
```

**Strict Authentication Mode**:

- If there is a player's authentication record in the database, only use the specified authentication method
- If authentication fails, directly reject login, do not try other methods
- This prevents identity theft: even if someone knows the password of another authentication method, they cannot log in

**UUID Takeover System**:

- When a player logs in using a new authentication method, the system checks if there is already a UUID record for that player
- If there is, use the historical UUID to ensure player identity consistency
- This solves the problem of data loss caused by UUID changes after switching authentication methods

#### 3. Authentication Providers (AuthProvider)

The plugin uses a provider pattern to support multiple authentication methods:

- **MojangAuthProvider**: Call Mojang official authentication API
- **YggdrasilAuthProvider**: Call Yggdrasil protocol API (supports LittleSkin, HairuoSKY, etc.)

Each provider implements the `authenticate()` method, returning the player's GameProfile or null.

#### 4. Identity Guard (IdentityGuard)

`IdentityGuard` is responsible for managing player identity information:

- Store the mapping relationship between player name, UUID and authentication method
- Prevent security issues of "same name different UUID"
- Provide UUID takeover functionality to ensure identity consistency

#### 5. Database Management (DatabaseManager)

Supports two databases:

- **SQLite**: Default, automatically creates `ximultilogin.db` file, suitable for small servers
- **MySQL**: Need manual configuration, suitable for large servers, provides better concurrency performance

Use HikariCP connection pool to optimize database connections.

### Data Flow Chart

```
Player login request
    ↓
XiSessionService.hasJoinedServer()
    ↓
Query database
    ↓
Has record?
    ├─ Yes → Only use specified authentication method
    │        ↓
    │     Authentication successful?
    │        ├─ Yes → UUID takeover → Return GameProfile
    │        └─ No → Reject login
    │
    └─ No → Traverse all authentication methods
             ↓
          Authentication successful?
             ├─ Yes → Record authentication method and UUID → Return GameProfile
             └─ No → Allow cracked?
                     ├─ Yes → Create temporary identity → Return GameProfile
                     └─ No → Reject login
```

## Installation Method

### System Requirements

- **Java Version**: Java 8 or higher
- **Server Type**: Bukkit/Spigot/Paper/Folia 1.16.5 - 1.21+
- **Dependency Plugins**: PlaceholderAPI (optional, for PAPI variable support)

### Installation Steps

1. **Download the plugin**
   - Download the latest version of `XiMultiLogin-x.x.jar` from GitHub Releases
   - Or compile the project yourself (see Development Guide)

2. **Install to server**
   - Put the JAR file into the server's `plugins` directory
   - Restart the server

3. **First startup**
   - The plugin will automatically create configuration files and database
   - SQLite database is used by default, no additional configuration required
   - Check the console log and make sure both `XiReflection: Session service replacement succeeded` and `XiMultiLogin: Session service injected successfully` appear

## Configuration Instructions

After the plugin starts, it will generate the following files in the `plugins/XiMultiLogin` directory:

- `config.yml`: Main configuration file
- `messages.yml`: Message configuration file
- `ximultilogin.db`: SQLite database file (if using SQLite)

### Main Configuration File (config.yml)

```yaml
# XiMultiLogin configuration file
# Authentication chain order: try from top to bottom
pipeline:
  # Mojang official authentication
  - type: MOJANG
    enabled: true
  
  # Yggdrasil protocol authentication (LittleSkin)
  - type: YGGDRASIL
    name: "LittleSkin"
    api: "https://littleskin.cn/api/yggdrasil"
    enabled: true
  
  # Yggdrasil protocol authentication (HairuoSKY)
  - type: YGGDRASIL
    name: "HairuoSKY"
    api: "https://skin.hairuosky.cn/api/yggdrasil"
    enabled: true

# Database configuration
database:
  # Database type: SQLite (default) or MySQL
  type: "SQLite"
  
  # MySQL configuration (only effective when type is MySQL)
  mysql:
    host: "localhost"
    port: 3306
    database: "ximultilogin"
    username: "root"
    password: "password"

# Cracked player settings
# Whether to allow players who fail any authentication to join (default: false)
# Not recommended to enable, otherwise security issues may occur
allow_cracked: false

# Debug settings
# Whether to enable debug mode (default: false)
# When enabled, detailed log information will be displayed, including authentication process and error details
debug: false
```

#### Configuration Item Details

**Authentication Chain Configuration (pipeline)**

- `type`: Authentication type, supports `MOJANG` or `YGGDRASIL`
- `enabled`: Whether to enable this authentication method
- `name`: Authentication provider name (only required for `YGGDRASIL` type, used to identify different Yggdrasil servers)
- `api`: Yggdrasil API address (only required for `YGGDRASIL` type)

**Database Configuration (database)**

- `type`: Database type, optional `SQLite` or `MySQL`
- `mysql`: MySQL database configuration (only effective when `type` is `MySQL`)
  - `host`: MySQL server address
  - `port`: MySQL port
  - `database`: Database name
  - `username`: Database username
  - `password`: Database password

**Cracked Player Settings (allow_cracked)**

- `true`: Allow players who fail any authentication to join (create temporary identity)
- `false`: Reject players who fail authentication (default)

**Debug Settings (debug)**

- `true`: Enable debug mode, display detailed log information
- `false`: Disable debug mode, display only key information (default)

### Message Configuration File (messages.yml)

```yaml
# XiMultiLogin message configuration file
# Support color codes, such as &c, &a, etc.
# Support variable replacement, such as {player}, {reason}, etc.

login:
  success: "&aLogin successful! Welcome back, {player}!"
  failed: "&cLogin failed: {reason}"
  name_taken: "&cLogin failed: This nickname has been used, please choose another nickname!"
  auth_changed: "&aAuthentication method has been changed to {provider}!"
  cracked: "&eLogin successful (cracked mode): Welcome, {player}!"

error:
  authentication: "&cAuthentication failed: Unable to verify your identity!"
  no_permission: "&cInsufficient permissions: You do not have permission to perform this operation!"
  invalid_args: "&cInvalid arguments: Please check the command arguments!"
  player_not_found: "&cError: Player {player} not found!"
  config_error: "&cConfiguration error: {reason}"
  database: "&cDatabase error: {reason}"

command:
  success: "&aOperation successful!"
  set_auth: "&aSuccessfully set player {player}'s authentication method to {auth}!"
  get_auth: "&aPlayer {player}'s current authentication method: {auth}"
  set_allow_cracked: "&aSuccessfully set allow cracked players to join: {value}"
  get_allow_cracked: "&aCurrent setting for allowing cracked players to join: {value}"
  reloaded: "&aConfiguration has been reloaded!"
  messages_reloaded: "&aMessage configuration has been reloaded!"

system:
  enabled: "&aXiMultiLogin plugin has been enabled!"
  disabled: "&cXiMultiLogin plugin has been disabled!"
  messages_loaded: "&aMessage configuration loaded successfully!"
  messages_reload: "&aMessage configuration has been reloaded!"

other:
  unknown_command: "&cUnknown command: Please use /ximultilogin help to view help!"
  help: "&aUse /ximultilogin help to view command help!"
  help_menu:
    title: "&6===== XiMultiLogin Command Help ====="
    setauth: "&a/ximultilogin setauth <player> <auth type> - Set player's authentication method"
    getauth: "&a/ximultilogin getauth <player> - Get player's current authentication method"
    allowcracked: "&a/ximultilogin allowcracked <true|false> - Set whether to allow cracked players to join"
    allowcracked_status: "&a/ximultilogin allowcracked - View current setting"
    reload: "&a/ximultilogin reload - Reload configuration files"
    info: "&a/ximultilogin info - Display plugin information"
    footer: "&6============================="
  info:
    title: "&6===== XiMultiLogin Plugin Info ====="
    version: "&aVersion: 2.3"
    author: "&aAuthor: XiNian-dada"
    description: "&aDescription: Multi-login method support plugin"
    command: "&aCommand: /ximultilogin help"
    footer: "&6==============================="
```

## Usage Tutorial

### Player Login Process

1. **New player login**
   - Player tries to join the server
   - Plugin tries different authentication methods in configuration order
   - The first successful authentication method will be recorded to the database
   - Player logs in successfully, UUID is recorded

2. **Old player login**
   - Player tries to join the server
   - Plugin queries the database, gets the player's historical authentication method
   - Only use that authentication method for verification
   - If verification is successful, use historical UUID (UUID takeover)
   - If verification fails, reject login

3. **Cracked player login** (requires `allow_cracked: true`)
   - Player tries to join the server
   - All authentication methods fail
   - Plugin creates temporary identity, allows player to join
   - Temporary identity will not be recorded to the database

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/ximultilogin setauth <player> <auth type>` | Set player's authentication method | `ximultilogin.admin` |
| `/ximultilogin getauth <player>` | Get player's current authentication method | `ximultilogin.admin` |
| `/ximultilogin allowcracked <true\|false>` | Set whether to allow cracked players to join | `ximultilogin.admin` |
| `/ximultilogin allowcracked` | View current cracked player setting | `ximultilogin.admin` |
| `/ximultilogin reload` | Reload configuration files | `ximultilogin.admin` |
| `/ximultilogin info` | Display plugin information | `ximultilogin.admin` |
| `/ximultilogin help` | Display help information | None |

**Authentication Type Description**:

- `MOJANG`: Mojang official authentication
- `YGGDRASIL`: Yggdrasil protocol authentication (need to specify specific Yggdrasil server name)

**Examples**:

```
# Set player "Steve" to use Mojang authentication
/ximultilogin setauth Steve MOJANG

# Set player "Alex" to use LittleSkin authentication
/ximultilogin setauth Alex LittleSkin

# Query player "Steve"'s authentication method
/ximultilogin getauth Steve

# Allow cracked players to join
/ximultilogin allowcracked true

# Reload configuration
/ximultilogin reload
```

### PAPI Variable Support

If the server has PlaceholderAPI installed, the plugin provides the following variables:

| Variable | Description |
|----------|-------------|
| `%ximultilogin_auth_provider%` | Player's current authentication method |
| `%ximultilogin_uuid%` | Player's UUID |

**Examples**:

```
# Use in chat format
chat-format: "&a[%ximultilogin_auth_provider%] &f{player}: &f{message}"

# Use in scoreboard
scoreboard:
  - "&aAuth method: %ximultilogin_auth_provider%"
  - "&aUUID: %ximultilogin_uuid%"
```

### Common Usage Scenarios

#### Scenario 1: Server supports both official and third-party authentication

```yaml
pipeline:
  - type: MOJANG
    enabled: true
  - type: YGGDRASIL
    name: "LittleSkin"
    api: "https://littleskin.cn/api/yggdrasil"
    enabled: true
```

New players will first try Mojang authentication, then LittleSkin authentication if it fails. Once successfully logged in with a certain method, subsequent logins must use the same method.

#### Scenario 2: Only support third-party authentication

```yaml
pipeline:
  - type: YGGDRASIL
    name: "HairuoSKY"
    api: "https://skin.hairuosky.cn/api/yggdrasil"
    enabled: true
```

All players must use HairuoSKY authentication.

#### Scenario 3: Allow cracked players

```yaml
pipeline:
  - type: MOJANG
    enabled: true

allow_cracked: true
```

Official players use Mojang authentication, cracked players can join without authentication (temporary identity).

#### Scenario 4: Player migration authentication method

```
# Player "Steve" originally used LittleSkin authentication
/ximultilogin getauth Steve
# Output: Player Steve's current authentication method: LittleSkin

# Player wants to migrate to Mojang authentication
/ximultilogin setauth Steve MOJANG
# Output: Successfully set player Steve's authentication method to MOJANG!

# Player will use Mojang authentication next time, but UUID remains unchanged
```

## Development Guide

### Environment Requirements

- **Java Version**: Java 8 or higher
- **Maven Version**: Maven 3.6 or higher
- **Git**: For version control

### Build Project

```bash
# Clone repository
git clone https://github.com/XiNian-dada/XiMultiLogin.git
cd XiMultiLogin

# Compile project (skip tests)
mvn package -DskipTests

# Compile and run tests
mvn package

# Generated JAR file is located at target/XiMultiLogin-2.3.jar

# Run real smoke tests with the server jars in the repository root
bash scripts/smoke-all-server-jars.sh
```

If you only want to test a single server:

```bash
bash scripts/smoke-server-jar.sh paper-1.21.11-127.jar
bash scripts/smoke-server-jar.sh folia-1.21.11-14.jar
```

### Project Structure

```
XiMultiLogin/
├── src/
│   ├── main/java/com/leeinx/ximultilogin/
│   │   ├── XiMultiLogin.java              # Main class
│   │   ├── auth/                           # Authentication related
│   │   │   ├── AuthProvider.java           # Authentication provider interface
│   │   │   ├── XiSessionService.java       # Session service (core)
│   │   │   └── providers/                  # Authentication provider implementations
│   │   │       ├── MojangAuthProvider.java # Mojang authentication
│   │   │       └── YggdrasilAuthProvider.java # Yggdrasil authentication
│   │   ├── command/                        # Command related
│   │   │   ├── XiCommandExecutor.java      # Command executor
│   │   │   └── XiTabCompleter.java         # TAB completer
│   │   ├── config/                         # Configuration related
│   │   │   ├── ConfigManager.java          # Configuration manager
│   │   │   └── MessageManager.java         # Message manager
│   │   ├── database/                       # Database related
│   │   │   ├── DatabaseManager.java        # Database interface
│   │   │   ├── DatabaseFactory.java        # Database factory
│   │   │   ├── SQLiteDatabaseManager.java   # SQLite implementation
│   │   │   └── MySQLDatabaseManager.java   # MySQL implementation
│   │   ├── guard/                          # Security related
│   │   │   └── IdentityGuard.java          # Identity guard
│   │   ├── injector/                       # Injection related
│   │   │   └── XiInjector.java             # Injector
│   │   ├── papi/                           # PAPI related
│   │   │   └── XiPlaceholderExpansion.java # PAPI variables
│   │   └── reflection/                     # Reflection related
│   │       └── XiReflection.java          # Reflection tools
│   ├── main/resources/
│   │   ├── config.yml                      # Configuration file
│   │   ├── messages.yml                    # Message configuration
│   │   └── plugin.yml                      # Plugin configuration
│   └── test/                               # Test code
├── pom.xml                                 # Maven configuration
└── README.md                               # This document
```

### Extend Authentication Method

If you need to add a new authentication method, follow these steps:

1. **Implement AuthProvider interface**

```java
package com.leeinx.ximultilogin.auth.providers;

import com.leeinx.ximultilogin.auth.AuthProvider;
import java.util.logging.Logger;

public class CustomAuthProvider implements AuthProvider {
    private final String name;
    private final String apiUrl;
    private final Logger logger;

    public CustomAuthProvider(String name, String apiUrl, Logger logger) {
        this.name = name;
        this.apiUrl = apiUrl;
        this.logger = logger;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object authenticate(String username, String serverId) {
        // Implement authentication logic
        // Return GameProfile object or null
        return null;
    }
}
```

2. **Register new authentication type in ConfigManager**

3. **Add support for new authentication type in XiSessionService**

4. **Update configuration file format**

### Contribution Guide

Welcome to submit Issues and Pull Requests to help improve this project!

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Frequently Asked Questions

### Q1: Why does player login fail?

**A**: Check the following:

1. Confirm the authentication method in the configuration file is enabled (`enabled: true`)
2. Check the console log to confirm the authentication process
3. If it's an old player, confirm using the correct authentication method
4. Check network connection to ensure access to authentication server

### Q2: How to switch player's authentication method?

**A**: Use the command `/ximultilogin setauth <player> <auth type>`.

Note: After switching authentication methods, the player's UUID will not change (UUID takeover system).

### Q3: Why can't cracked players join?

**A**: Check the `allow_cracked` setting in the configuration file, ensure it is `true`.

### Q4: How to support multiple Yggdrasil servers?

**A**: Add multiple YGGDRASIL type configurations in the configuration file, each with a different `name`:

```yaml
pipeline:
  - type: YGGDRASIL
    name: "LittleSkin"
    api: "https://littleskin.cn/api/yggdrasil"
    enabled: true
  - type: YGGDRASIL
    name: "HairuoSKY"
    api: "https://skin.hairuosky.cn/api/yggdrasil"
    enabled: true
```

### Q5: Will database data be lost?

**A**: No. Player identity information (name, UUID, authentication method) is persistently stored in the database and will not be lost after server restart.

### Q6: Which Minecraft versions are supported?

**A**: Supports Minecraft 1.16.5 - 1.21+. Paper/Folia 1.20.1 and 1.21.11 are currently covered by automated smoke tests, and 1.20.2+ has dedicated Record-structure handling.

### Q7: How to view player's authentication method?

**A**: Use the command `/ximultilogin getauth <player>`, or use PAPI variable `%ximultilogin_auth_provider%`.

### Q8: Will the plugin affect server performance?

**A**: No. The plugin uses optimized authentication process and database operations, with minimal impact on server performance. Database operations use HikariCP connection pool for further performance optimization.

## Version Information

### Current Version
- **Version Number**: 2.3

### Version History
- **v2.3**: Paper/Folia compatibility release
  - The same JAR now runs on both Paper and Folia
  - Added a runtime scheduler bridge for Bukkit, Paper and Folia
  - Fixed SessionService reflection injection and restoration on 1.20.1 and 1.21.x
  - Adjusted command, PAPI and cache paths to better fit Folia execution rules
  - Added unit tests and real server smoke-test scripts

- **v2.0**: Security enhanced version
  - Implemented depth defense mechanism, double blocking to ensure security
  - Optimized temporary UUID generation logic, use real UUID to eliminate LuckPerms warnings
  - Enhanced thread-safe design, use ConcurrentHashMap to handle concurrent logins
  - Added detailed security audit logs, improved observability
  - Improved authentication failure handling, display custom error messages instead of system default messages
  - Optimized event listener priority, ensure security checks are executed first
  - Improved resource cleanup mechanism, prevent memory leaks

- **v1.0**: Initial version
  - Support Mojang and Yggdrasil authentication
  - SQLite and MySQL database support
  - Identity locking mechanism
  - UUID takeover system
  - Strict authentication mode
  - Command system
  - PAPI variable support
  - Message configuration system
  - Cracked player support
  - Multi-version compatibility (1.16.5 - 1.21+)
  - Specially optimized for 1.20.2+ (Record support)

## License

This project is licensed under the MIT License - see the LICENSE file for details

## Contact

- **Author**: XiNian-dada
- **Project Address**: https://github.com/XiNian-dada/XiMultiLogin
- **Issue Feedback**: https://github.com/XiNian-dada/XiMultiLogin/issues

---

**Enjoy using XiMultiLogin!** 🎉
