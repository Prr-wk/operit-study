# Operit 学习助手

Operit 生态里的「学习」这一套东西：一个 ToolPkg 插件 + 一个原生 Android 讲题 App。

两者分工是硬边界，不要互相越位：

| | 负责 | 不负责 |
|---|---|---|
| **ToolPkg**（`toolpkg/`） | 学习档案（学科/知识点/权重 + 时间衰减）、错题登记、选题、课堂控件（当堂检测选择题 / 函数图像 / 几何圈画）、内置并安装讲题 App | 讲题、板书渲染 |
| **App**（`app/`） | 讲题前端：34 种板书卡片、单画布、TTS、自评闭环、作答卡；只读档案，不回写 | 维护档案、错题权重 |

数据流只有一条：`profile.json` **单向**从档案流向 App，App 不回写档案。

## 目录

```
toolpkg/                  Operit ToolPkg（发布到插件市场的就是它）
├── manifest.json         包声明：子包 study_profile / teacher_app，资源 doubaoteacher_apk
├── main.js               注册 xml 渲染插件、UI 路由、侧边栏入口
├── packages/
│   ├── study_profile.js  学习档案的 7 个工具
│   └── teacher_app.js    肥鱼老师：status / install / open
├── ui/
│   ├── quiz.ui.js        当堂检测选择题
│   ├── graph.ui.js       函数图像
│   ├── geometry.ui.js    几何圈画
│   ├── profile_panel.ui.js  学习档案面板
│   └── teacher_app.ui.js    肥鱼老师入口（进入即启动，未装先装）
└── resources/
    └── doubaoteacher.apk 内置的讲题 App（升级 App 时记得同步替换）

app/                      肥鱼老师 Android 源码
├── src/com/operit/doubaoteacher/   Java 源码
├── res/                           布局与 drawable
├── assets/
│   ├── board.html                 板书渲染（34 种卡片）
│   ├── prompt/*.md                提示词（core/protocol/guide/teach/answer）
│   ├── katex/                     公式渲染
│   └── avatar.png                 头像
└── build.sh                       不用 gradle 的编译打包脚本
```

## 构建

**插件包**（产出 `.toolpkg`）：

```bash
cd toolpkg
python3 - <<'EOF'
import os, zipfile
files = ['manifest.json', 'main.js', 'README.md',
         'packages/study_profile.js', 'packages/teacher_app.js',
         'ui/quiz.ui.js', 'ui/graph.ui.js', 'ui/geometry.ui.js',
         'ui/profile_panel.ui.js', 'ui/teacher_app.ui.js',
         'resources/doubaoteacher.apk']
with zipfile.ZipFile('com.operit.study_assistant.toolpkg', 'w', zipfile.ZIP_DEFLATED) as z:
    for f in files:
        z.write(f, f)
print('ok')
EOF
```

**App**（产出 APK）：需要 `android.jar`（API19 即可）、`dx.jar`、一个签名 keystore，路径在 `build.sh` 里改。

```bash
cd app && bash build.sh
```

## 关于 Key

**这个仓库里没有任何 API Key。**

App 的 Key 从外部文件按优先级读取：

1. App 设置页里填的（本机 SharedPreferences）
2. `/sdcard/Download/Operit/doubao/api.txt`（`key=...` 格式）
3. `/sdcard/Download/Operit/doubao/key.txt`（单行）
4. 缺省 base/model，Key 为空 → 启动进引导页

`Cfg.java` 里 `key` 初始值是空字符串，源码与构建产物都验证过零命中。

## 版本

| 版本 | 内容 |
|---|---|
| v0.2.0 | 移除 `study_lesson` 子包与侧边栏讲题面板，讲题职责移交 App |
| v0.3.0 | 内置肥鱼老师安装包；新增 `teacher_app` 子包（status / install / open）；侧边栏「肥鱼老师」入口 |
| App v3.6 | 首启引导、独立设置页、提示词外部化、Key 移出代码 |

> 本仓库由两个原本独立的本地仓库合并而来（`study_assistant_work`、`doubaoteacher`），
> 早期的细粒度提交历史没有迁移过来，上面的表是从提交信息里整理的要点。

## 许可

待定。
