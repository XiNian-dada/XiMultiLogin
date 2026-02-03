# XiMultiLogin

XiMultiLogin 是一个功能强大的 Minecraft 插件，支持多种认证方式，为服务器提供更灵活的玩家登录验证机制。

## 🚀 功能特性

### 多认证方式支持
- **Mojang 官方认证**：标准 Minecraft 账户登录
- **LittleSkin 认证**：支持 LittleSkin 皮肤站账户
- **自定义 Yggdrasil 认证**：可配置其他 Yggdrasil 协议的皮肤站

### 安全特性
- **身份锁定机制**：防止"同名不同 UUID"的安全问题
- **多版本兼容**：支持多个 Minecraft 版本
- **连接超时保护**：HTTP 连接 3秒超时，读取 5秒超时

### 数据库支持
- **默认 SQLite**：开箱即用，无需额外配置
- **支持 MySQL**：可配置使用 MySQL 数据库提高性能
- **连接池优化**：使用 HikariCP 提供稳定的数据库连接

### 技术特性
- **SOLID 设计原则**：模块化架构，易于扩展
- **高性能**：优化的认证流程和数据库操作
- **可测试性**：支持单元测试，确保代码质量

## 📦 安装方法

1. **下载插件**
   - 从 GitHub Actions 构建或手动编译获取 `XiMultiLogin-1.0.jar` 文件

2. **安装到服务器**
   - 将 JAR 文件放入服务器的 `plugins` 目录
   - 重启服务器

3. **首次启动**
   - 插件会自动创建配置文件和数据库
   - 默认使用 SQLite 数据库，无需额外配置

## ⚙️ 配置说明

插件启动后会在 `plugins/XiMultiLogin` 目录生成配置文件：

### 完整配置示例

```yaml
# XiMultiLogin 配置文件
# 验证链顺序：从上到下尝试
pipeline:
  # Mojang 官方认证
  - type: MOJANG
    enabled: true
  # LittleSkin 皮肤站认证
  - type: YGGDRASIL
    name: "LittleSkin"
    api: "https://littleskin.cn/api/yggdrasil"
    enabled: true
  # 自定义 Yggdrasil 皮肤站认证
  - type: YGGDRASIL
    name: "CustomSkinServer"
    api: "https://your-skin-server.com/api/yggdrasil"
    enabled: false

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
```

### 配置项说明

#### 验证链配置 (`pipeline`)
- **`type`**：认证类型，支持 `MOJANG` 或 `YGGDRASIL`
- **`enabled`**：是否启用该认证方式
- **`name`**：认证提供者名称（仅 `YGGDRASIL` 类型需要）
- **`api`**：Yggdrasil API 地址（仅 `YGGDRASIL` 类型需要）

#### 数据库配置 (`database`)
- **`type`**：数据库类型，可选 `SQLite` 或 `MySQL`
- **`mysql`**：MySQL 数据库配置（仅当 `type` 为 `MySQL` 时生效）
  - **`host`**：MySQL 服务器地址
  - **`port`**：MySQL 端口
  - **`database`**：数据库名称
  - **`username`**：数据库用户名
  - **`password`**：数据库密码

## 🎯 工作原理

### 核心认证流程

1. **NMS 注入**
   - 插件启动时通过 `XiInjector` 将 `XiSessionService` 注入到 Minecraft 服务器的验证流程中
   - 这是一个底层注入，发生在服务器启动时，支持 1.16.5 - 1.21+ 全版本

2. **玩家登录请求**
   - 玩家尝试加入服务器时，服务器会调用 `hasJoinedServer` 方法进行验证
   - 这个调用会被重定向到 `XiSessionService`

3. **验证链处理**
   - `XiSessionService` 按配置顺序尝试不同的认证方式
   - 对于每个认证提供者：
     1. 调用 `provider.authenticate()` 方法进行认证
     2. 如果认证成功，获取玩家的 GameProfile
     3. 调用 `IdentityGuard.verifyIdentity()` 验证身份

4. **身份验证**
   - `IdentityGuard` 检查玩家名称与 UUID 的映射关系：
     - 如果是新玩家，记录名称、UUID 和认证方式
     - 如果是老玩家，检查传入的 UUID 是否与存储的 UUID 匹配
     - 如果不匹配（ID重复但UUID不同），拒绝登录

5. **登录结果**
   - 如果任一认证方式成功且身份验证通过，返回 GameProfile，玩家登录成功
   - 如果所有认证方式都失败，返回 null，玩家登录失败
   - 整个过程发生在 `onPlayerJoin` 事件触发之前，确保安全验证

### 技术实现

#### 1. 认证提供者
- **`AuthProvider` 接口**：定义认证提供者的通用方法
- **`MojangAuthProvider`**：实现 Mojang 官方认证
- **`YggdrasilAuthProvider`**：实现 Yggdrasil 协议认证（支持皮肤站）

#### 2. 身份锁定机制
- **`IdentityGuard`**：管理名称-UUID-认证方式的持久化映射
- **数据库存储**：使用 SQLite 或 MySQL 存储身份信息
- **安全保障**：防止同名不同 UUID 的安全问题

#### 3. 数据库管理
- **`DatabaseManager` 接口**：统一数据库操作接口
- **`SQLiteDatabaseManager`**：SQLite 实现，适合小型服务器
- **`MySQLDatabaseManager`**：MySQL 实现，适合大型服务器
- **`HikariCP`**：使用连接池优化数据库连接

#### 4. 反射工具
- **`XiReflection`**：提供跨版本的反射操作
- **支持多版本**：适配不同 Minecraft 版本的 NMS 类结构

### 数据流程

```
玩家登录 → XiSessionService.hasJoinedServer() → 验证链处理 → AuthProvider.authenticate() → IdentityGuard.verifyIdentity() → 数据库操作 → 登录结果
```

### 安全特性

1. **身份锁定**：每个玩家名称绑定唯一的 UUID 和认证方式
2. **认证链**：按顺序尝试多种认证方式，提高登录成功率
3. **超时保护**：HTTP 连接 3秒超时，读取 5秒超时，防止认证阻塞
4. **错误处理**：单个认证提供者失败不影响其他提供者
5. **日志记录**：详细的认证过程日志，便于问题排查

### 性能优化

1. **连接池**：使用 HikariCP 管理数据库连接，减少连接开销
2. **缓存**：优化认证结果缓存，提高重复登录速度
3. **异步操作**：数据库操作使用异步执行，避免阻塞主线程
4. **模块化**：清晰的代码结构，便于维护和扩展

## 🔐 支持的认证方式

### 验证链机制
插件使用验证链（pipeline）机制，按顺序尝试不同的认证方式，直到成功为止。

1. **Mojang 官方认证**
   - 标准 Minecraft 账户登录
   - 使用官方 Mojang 认证服务器
   - 在配置文件中通过 `type: MOJANG` 定义

2. **LittleSkin 认证**
   - 支持 LittleSkin 皮肤站账户
   - 预配置为 `https://littleskin.cn/api/yggdrasil`
   - 在配置文件中通过 `type: YGGDRASIL` 定义

3. **自定义 Yggdrasil 认证**
   - 可配置其他支持 Yggdrasil 协议的皮肤站
   - 通过在配置文件的 pipeline 中添加新的 YGGDRASIL 条目实现
   - 示例：
     ```yaml
     - type: YGGDRASIL
       name: "CustomSkinServer"
       api: "https://your-skin-server.com/api/yggdrasil"
       enabled: true
     ```

## 🗃️ 数据库说明

### SQLite（默认）
- 自动创建 `ximultilogin.db` 文件
- 适合小型服务器
- 无需额外配置

### MySQL
- 需要手动创建数据库
- 适合大型服务器或多服务器环境
- 提供更好的并发性能

## 📝 使用方法

### 玩家登录流程
1. 玩家尝试加入服务器
2. 插件按顺序尝试不同的认证方式
3. 成功认证后，检查身份锁定状态
4. 完成登录，应用皮肤和权限

### 管理员命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/ximultilogin reload` | 重新加载配置 | `ximultilogin.admin` |
| `/ximultilogin info <player>` | 查看玩家认证信息 | `ximultilogin.admin` |
| `/ximultilogin reset <player>` | 重置玩家身份锁定 | `ximultilogin.admin` |

## 🛠️ 开发指南

### 环境要求
- Java 8 或更高版本
- Maven 3.6 或更高版本
- Git

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/yourusername/XiMultiLogin.git
cd XiMultiLogin

# 编译项目（跳过测试）
mvn package -DskipTests

# 编译并运行测试
mvn package
```

### 项目结构

```
XiMultiLogin/
├── src/
│   ├── main/java/com/leeinx/ximultilogin/
│   │   ├── XiMultiLogin.java          # 主类
│   │   ├── auth/                       # 认证相关
│   │   │   ├── AuthProvider.java       # 认证提供者接口
│   │   │   ├── XiSessionService.java   # 会话服务
│   │   │   └── providers/              # 认证提供者实现
│   │   │       ├── MojangAuthProvider.java # Mojang 认证实现
│   │   │       └── YggdrasilAuthProvider.java # Yggdrasil 认证实现
│   │   ├── config/                     # 配置相关
│   │   │   └── ConfigManager.java      # 配置管理器
│   │   ├── database/                   # 数据库相关
│   │   │   ├── DatabaseFactory.java    # 数据库工厂
│   │   │   ├── DatabaseManager.java    # 数据库管理器接口
│   │   │   ├── SQLiteDatabaseManager.java # SQLite 实现
│   │   │   └── MySQLDatabaseManager.java  # MySQL 实现
│   │   ├── guard/                      # 安全相关
│   │   │   └── IdentityGuard.java      # 身份锁定机制
│   │   ├── injector/                   # 注入相关
│   │   │   └── XiInjector.java         # 登录注入器
│   │   └── reflection/                 # 反射相关
│   │       └── XiReflection.java       # 反射工具
│   ├── main/resources/
│   │   ├── config.yml                  # 配置文件
│   │   └── plugin.yml                  # 插件配置
│   └── test/                           # 测试代码
├── pom.xml                             # Maven 配置
└── README.md                           # 本文档
```

### 扩展认证方式

1. **实现 AuthProvider 接口**
2. **在 AuthPipeline 中注册新的认证提供者**
3. **更新配置文件以支持新的认证方式**

## 🔄 CI/CD 配置

项目使用 GitHub Actions 进行持续集成和自动发布：

### 持续集成
- **触发条件**：推送代码到 main/master 分支或创建 Pull Request
- **构建环境**：Ubuntu Latest
- **JDK 版本**：Java 8
- **构建命令**：`mvn package -DskipTests`
- **产物上传**：构建后的 JAR 文件会作为 artifact 上传

### 自动发布
- **触发条件**：推送符合 `v*` 格式的标签（如 `v1.0`）
- **发布流程**：
  1. 执行构建流程
  2. 下载构建产物
  3. 在 GitHub Releases 中创建发布版本
  4. 上传 JAR 文件作为发布附件

### 创建发布版本

#### 方法一：自动标签创建

项目配置了自动标签创建功能，当你推送代码到 main/master 分支时：

1. **自动提取版本**：从 pom.xml 中提取版本号
2. **检查标签**：检查对应的标签是否已存在
3. **创建标签**：如果标签不存在，自动创建并推送标签
4. **触发发布**：标签推送后自动触发发布流程

#### 方法二：手动创建标签

```bash
# 克隆仓库
git clone https://github.com/yourusername/XiMultiLogin.git
cd XiMultiLogin

# 创建并推送标签
git tag v1.0
git push origin v1.0
```

#### 方法三：手动触发工作流

你也可以通过 GitHub Actions 界面手动触发工作流：

1. 进入仓库的 Actions 页面
2. 选择 "CI" 工作流
3. 点击 "Run workflow"
4. 选择分支并设置参数
5. 点击 "Run workflow" 按钮

### 版本管理

- **版本号来源**：发布版本的版本号始终从 pom.xml 中提取
- **标签格式**：标签格式为 `v{版本号}`（如 `v1.0`）
- **自动同步**：确保发布版本与 pom.xml 中的版本号一致

推送代码后，GitHub Actions 会自动创建标签并触发发布流程，上传构建产物到 GitHub Releases。

## 📋 版本信息

### 当前版本
- **版本号**：1.0
- **发布日期**：2026-02-03

### 版本历史
- **v1.0**：初始版本
  - 支持 Mojang 和 LittleSkin 认证
  - SQLite 和 MySQL 数据库支持
  - 身份锁定机制
  - HikariCP 连接池
  - 多版本兼容

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来帮助改进这个项目！

### 贡献指南
1. Fork 仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 详情请参阅 LICENSE 文件

## 📞 联系方式

- **作者**：Bernard Lee
- **项目地址**：https://github.com/yourusername/XiMultiLogin

---

**享受使用 XiMultiLogin！** 🎉