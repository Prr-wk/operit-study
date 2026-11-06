> 系统讲题（板书/分步/自评）由独立 App「肥鱼老师」（com.operit.doubaoteacher）承担。
> 本插件只负责：学习档案（权重+错题本）与课堂控件（当堂检测/函数图像/几何圈画）。

# 学习助手 ToolPkg

Operit 插件（ToolPkg 格式），把"学习助手"做进 Operit 聊天里。

## 组成

| 路径 | 作用 |
|---|---|
| `manifest.json` | 包清单：`toolpkg_id=com.operit.study_assistant`，API `1.0.1`，声明子包 `study_profile`。 |
| `main.js` | 包级入口。`registerToolPkg()` 中注册 3 个 XML 渲染插件（`study_quiz` / `study_graph` / `study_geometry`）。 |
| `packages/study_profile.js` | 子包：学习档案引擎（8 个工具），工具名形如 `study_profile:*`。 |
| `ui/quiz.ui.js` | Compose DSL 屏：当堂检测选择题（可作答、判分、显示解析）。 |
| `ui/graph.ui.js` | Compose DSL 屏：函数图像（Canvas 绘制坐标轴/网格/曲线/散点）。 |
| `ui/geometry.ui.js` | Compose DSL 屏：几何图形与圈画（Canvas 绘制点/线/圆/多边形/路径/文字）。 |
| `ui/profile_panel.ui.js` | Compose DSL 屏：学习档案面板（主页侧边栏入口，展示知识点与有效权重）。 |

## 工作机制

模型在聊天回复中输出 XML 标签，例如：

```xml
<study_quiz>{"question":"...","options":["..."],"answer":0,"explain":"..."}</study_quiz>
```

`main.js` 注册的 XML 渲染插件拦截该标签，解析标签内 JSON，返回 `composeDsl.screen`（由对应 `ui/*.ui.js` 的 `makeScreen(data)` 生成），客户端就地渲染成交互控件。

## 侧边栏入口

`main.js` 通过两个调用把「学习档案」面板挂到主页侧边栏：

- `ToolPkg.registerUiRoute({ id:"study_profile_panel", route:PROFILE_PANEL_ROUTE, runtime:"compose_dsl", screen:"ui/profile_panel.ui.js" })`
- `ToolPkg.registerNavigationEntry({ id:"study_profile_panel_sidebar", route:PROFILE_PANEL_ROUTE, surface:"main_sidebar_plugins", icon:"menu_book", order:10 })`

> ⚠️ 关键约束：`registerUiRoute` 的 `screen` 必须是**可序列化引用**——字符串模块路径（相对 toolpkg 根目录），或由 require 系统打上 `__operit_toolpkg_module_path` 标记的函数 / `.default` 对象。**直接传普通函数会抛 `requires a serializable screen reference`，导致整包注册失败、容器加载回滚。** 同理，XML 渲染插件返回的 `composeDsl.screen` 也用字符串模块路径。

## 本地调试 / 重新烧录

```text
operit_editor:debug_install_toolpkg
  source_path = /sdcard/Download/Operit/dev_package/study_assistant_toolpkg
  activate_subpackages = study_profile
```

## 配套 Skill

`/sdcard/Download/Operit/skills/study_assistant/SKILL.md`：定义教学行为、工具用法与三个标签的 JSON 结构。

## 状态

- [x] 三个讲解控件（`study_quiz` / `study_graph` / `study_geometry`）的 XML 渲染插件。
- [x] 学习档案面板 + 主页侧边栏入口注册（`main_sidebar_plugins`）。
- [x] ToolPkg 打包烧录通过（`debug_install_toolpkg` → `success: true`，`related_load_errors: {}`）。
- [ ] 在真实聊天中端到端验证三个控件的渲染与交互。
- [ ] 确认发布路线（本地分发 / GitHub Release / Operit 市场）。
