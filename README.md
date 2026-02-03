# XiMultiLogin

XiMultiLogin 是一个为 Minecraft Bukkit/Spigot/Paper 服务器设计的多认证方式支持插件，旨在解决 MultiLogin 取消对 Bukkit 平台支持后，社区缺乏替代方案的问题。

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
- **多版本兼容**：支持 Minecraft 1.16.5 - 1.21+，特别优化 1.20.2+（暂未测试 稳定性待定）
- **动态代理机制**：解决 ClassLoader 隔离问题
- **Record 重构支持**：处理 Java Record 的不可变性
- **反射工具**：跨版本的反射操作，适配不同 NMS 类结构
- **数据库支持**：SQLite（默认）和 MySQL，使用 HikariCP 连接池

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
- **服务器类型**：Bukkit/Spigot/Paper 1.16.5 - 1.21+
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
   - 检查控制台日志，确保插件正常加载

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
    version: "&a版本: 1.0"
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

# 生成的 JAR 文件位于 target/XiMultiLogin-1.0.jar
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

**A**: 支持 Minecraft 1.16.5 - 1.21+，特别优化了 1.20.2+ 版本（解决了 Record 不可变性问题）。

### Q7: 如何查看玩家的认证方式？

**A**: 使用命令 `/ximultilogin getauth <玩家名>`，或使用 PAPI 变量 `%ximultilogin_auth_provider%`。

### Q8: 插件会影响服务器性能吗？

**A**: 不会。插件使用优化的认证流程和数据库操作，对服务器性能影响极小。数据库操作使用 HikariCP 连接池，进一步优化性能。

## 版本信息

### 当前版本
- **版本号**：1.0
- **发布日期**：2026-02-04

### 版本历史
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

- **作者**：XiLogin Team
- **项目地址**：https://github.com/yourusername/XiMultiLogin
- **问题反馈**：https://github.com/yourusername/XiMultiLogin/issues

---

**享受使用 XiMultiLogin！** 🎉
