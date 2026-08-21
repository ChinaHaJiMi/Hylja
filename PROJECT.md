# Hylia - 数字健康守护应用

## 概述

Hylia 是一款 Android 数字健康应用，通过无障碍服务实时检测手机屏幕上的不良内容，并以「微感拦截」方式引导用户做出自律决策。应用内置积分奖励系统，将自律行为转化为正向反馈闭环。

## 核心功能

### 内容识别
- **实时检测**：通过无障碍服务监听屏幕内容变化
- **多类别分类**：识别 6 类内容
  - 正常内容
  - 营销带货
  - 伪科学谣言
  - 低俗烂梗
  - 引战攻击
  - 无聊爽文
- **双层分类器**：规则引擎（关键词加权 + 启发式特征）+ 未来扩展深度学习模型

### 微感拦截
- 当检测到不良内容时，弹出悬浮卡片显示：
  - 内容类别与置信度
  - 判定依据（命中关键词）
  - 内容预览
- 提供三个操作选项：**返回**（自律）、**继续访问**、**误判反馈**

### 督学积分系统
| 行为 | 积分 |
|------|------|
| 拦截后选择「返回」 | +10 |
| 完成专注番茄钟 | +20 |
| 达成每日目标 | +30 |
| 连续达标连击加成 | +5/level/天（上限 +50） |
| 误判反馈被采纳 | +5 |
| 超额「继续访问」 | -10 |

### 用户档案
- 基于 SharedPreferences 持久化
- 响应式状态流（StateFlow）
- 每日自动重置统计
- 可配置参数：灵敏度、奖励开关、每日目标

## 技术架构

```
com.hylia.app/
├── core/                    # 核心数据模型
│   ├── ContentCategory.kt   # 内容类别枚举
│   ├── ClassificationResult.kt  # 分类结果
│   └── Sensitivity.kt       # 灵敏度配置
├── ml/                      # 机器学习模块
│   ├── TextClassifier.kt    # 分类器接口
│   └── RuleClassifier.kt    # 规则引擎实现
├── prefilter/               # 预过滤模块
│   └── NodeTextExtractor.kt # 无障碍节点文本提取
├── intervention/            # 拦截干预模块
│   ├── InterventionController.kt  # 悬浮窗控制器
│   ├── InterceptorOverlay.kt      # 拦截卡片 UI
│   ├── InterventionListener.kt    # 用户决策回调
│   └── OverlayAction.kt           # 操作枚举
├── coaching/                # 督学引擎
│   └── CoachingEngine.kt    # 积分与连击管理
├── profile/                 # 用户档案
│   └── ProfileStore.kt      # 状态持久化
├── accessibility/           # 无障碍服务
│   └── HyliaAccessibilityService.kt  # 核心守护服务
└── ui/                      # 界面层
    ├── MainScreen.kt        # 主界面
    ├── SettingsScreen.kt    # 设置界面
    ├── SettingsActivity.kt  # 设置 Activity
    └── Theme.kt             # 主题配置
```

## 工作流程

```
屏幕内容变化
    ↓
无障碍服务捕获事件
    ↓
NodeTextExtractor 提取文本
    ↓
RuleClassifier 分类（关键词 + 启发式）
    ↓
┌─────────────────────────────┐
│  命中不良内容？              │
│  ├─ 是 → InterventionController 显示拦截卡片
│  │       ↓
│  │   用户决策
│  │   ├─ 返回 → CoachingEngine +10 分
│  │   ├─ 继续 → CoachingEngine -10 分
│  │   └─ 误判 → CoachingEngine +5 分
│  └─ 否 → 不干预
└─────────────────────────────┘
```

## 权限需求

| 权限 | 用途 |
|------|------|
| `BIND_ACCESSIBILITY_SERVICE` | 监听屏幕内容 |
| `INTERNET` | 未来模型更新 |
| `POST_NOTIFICATIONS` | 状态通知 |

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose + 经典 View（拦截卡片）
- **异步**：Kotlin Coroutines + StateFlow
- **数据持久化**：SharedPreferences
- **构建工具**：Gradle (Kotlin DSL)
- **目标 SDK**：35
- **最小 SDK**：26

## 版本规划

| 里程碑 | 目标 |
|--------|------|
| M0 | 规则引擎 + 基础拦截 + 积分系统 |
| M1 | 接入量化深度学习模型（MNN/TFLite） |
| M2 | 误判样本匿名上报 + 模型迭代 |
| M3 | 专注番茄钟 + 更多自律激励 |

## 当前状态

- 版本：v0.1.0
- 阶段：M0 原型验证
- 已实现：规则分类器、无障碍拦截、积分系统、基础 UI
