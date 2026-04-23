# RunIt Plugin for JetBrains IDEs

一键运行自定义脚本的 JetBrains IDE 插件。

## 功能特性

- **顶部工具栏按钮**：在 IDE 主工具栏添加一键运行按钮
- **TOML 配置**：使用 `.runit/runit.toml` 管理命令配置
- **可视化配置**：通过 GUI 对话框添加/编辑/删除操作
- **内置图标**：提供多种内置图标区分不同类型的操作
- **命令执行**：输出到 IDE 内置 Run 工具窗口，支持 Windows PowerShell 和 Unix Shell

## 快速开始

### 1. 安装插件

通过 Gradle 构建：
```bash
./gradlew buildPlugin
```

构建完成后，插件包位于 `build/distributions/run-it-1.0.0.zip`，通过 IDE 的 **Settings → Plugins → Install from Disk** 安装。

### 2. 配置命令

首次使用点击工具栏按钮，选择 **添加操作**，在弹出的对话框中填写：
- **名称**：操作的显示名称
- **图标**：选择一个内置图标
- **要运行的命令**：要执行的脚本命令

配置会自动保存到项目目录下的 `.runit/runit.toml` 文件中。

### 3. 运行命令

- **点击主按钮**：直接运行列表中的第一个操作
- **点击下拉箭头**：展开菜单，选择要运行的操作
- **管理操作**：编辑或删除已有操作

## TOML 配置示例

```toml
version = 1

[[actions]]
name = "Build Plugin"
icon = "run"
command = "./gradlew.bat buildPlugin"

[[actions]]
name = "Clean Build"
icon = "clean"
command = "./gradlew.bat clean build"

[[actions]]
name = "Run Tests"
icon = "test"
command = "./gradlew.bat test"
```

## 内置图标

| 图标key | 说明 |
|---------|------|
| run | 运行（默认） |
| clean | 清理 |
| build | 构建 |
| test | 测试 |
| deploy | 部署 |
| terminal | 终端 |
| debug | 调试 |
| refresh | 刷新 |

## 开发环境

- **语言**: Java 17
- **构建工具**: Gradle 8.5 + IntelliJ Platform Gradle Plugin 2.x
- **目标平台**: IntelliJ IDEA 2023.2.8+

## 许可证

MIT
