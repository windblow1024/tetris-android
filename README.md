# 俄罗斯方块 — Android

将 Python/Pygame 版俄罗斯方块移植到原生 Kotlin + Android Canvas。

## 功能特性

| 功能 | 状态 |
|------|------|
| 7-bag 随机算法 | ✅ |
| SRS 旋转 + Wall Kick | ✅ |
| Lock Delay（500ms / 15次） | ✅ |
| 幽灵方块（Ghost） | ✅ |
| Hold 区 | ✅ |
| Next 预览 | ✅ |
| 计分系统（100/300/500/800 × 等级） | ✅ |
| 等级系统（每10行升级） | ✅ |
| 最高分本地持久化 | ✅ |
| DAS 连发（长按加速） | ✅ |
| 暂停/继续 | ✅ |
| 触屏按键 + 键盘（模拟器） | ✅ |
| 音效 | ❌（可后续扩展） |

## 构建方式

### 方式一：Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio) 最新版
2. 打开 `File → Open`，选择 `tetris-android` 目录
3. 等待 Gradle 同步完成（首次会自动下载依赖）
4. 连接手机或启动模拟器
5. 点击 Run ▶ 构建并安装

### 方式二：命令行 Gradle

```bash
# 需要先配置 ANDROID_HOME 环境变量
cd tetris-android
./gradlew assembleDebug
# APK 位于 app/build/outputs/apk/debug/app-debug.apk
```

## 操作说明

### 触屏按键

```
┌─────────────┐
│   ◀  ▼  ▲  ⬇  C  │
└─────────────┘
```

| 按钮 | 操作 |
|------|------|
| ◀ ▶ | 左/右移动（长按自动连发） |
| ▼ | 软降（加速下落） |
| ▲ | 顺时针旋转 |
| ⬇ | 硬降（一键落底） |
| C | Hold（保留/交换当前方块） |

- **点击棋盘区域** = 旋转
- **游戏结束界面点按** = 重新开始

### 键盘（模拟器/外接键盘）

| 按键 | 操作 |
|------|------|
| ← → | 移动 |
| ↓ | 软降 |
| ↑ | 旋转 |
| 空格 | 硬降 |
| C | Hold |
| P | 暂停 |
| R | 重新开始 |

## 项目结构

```
tetris-android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/java/com/tetris/
│       ├── MainActivity.kt    # 入口 + 生命周期 + 最高分持久化
│       ├── TetrisView.kt      # 自定义 View（渲染 + 触控 + 游戏循环）
│       ├── Board.kt           # 棋盘（碰撞检测、消行、锁定、影子）
│       ├── Game.kt            # 游戏状态机（得分、等级、Hold、7-bag）
│       ├── Tetromino.kt       # 方块数据（形状旋转、Wall Kick）
│       └── Constants.kt       # 全局常量
├── build.gradle.kts
└── settings.gradle.kts
```

## 对应关系

| Python 模块 | Kotlin 类 | 说明 |
|-------------|-----------|------|
| `main.py` | `MainActivity.kt` | 入口 + 生命周期 |
| `game.py` | `Game.kt` | 游戏状态机 |
| `board.py` | `Board.kt` | 棋盘逻辑 |
| `tetromino.py` | `Tetromino.kt` | 方块定义 |
| `constants.py` | `Constants.kt` | 常量 |
| `renderer.py` | `TetrisView.kt` | 渲染（Canvas） |
| `input_handler.py` | `TetrisView.kt` + `MainActivity.kt` | 输入处理 |
| `sounds.py` | — | 未移植 |
