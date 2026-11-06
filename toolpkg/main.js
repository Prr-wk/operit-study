/*
 * Study Assistant ToolPkg - package-level entry (main context).
 *
 * Responsibilities:
 *  - register XML render plugins so the model can emit generative teaching
 *    widgets inside chat (当堂检测选择题 / 函数图像 / 几何圈画).
 *
 * Each registered tag intercepts `<tag>...</tag>` emitted by the model,
 * parses the JSON payload inside, and returns a Compose DSL screen.
 */

const quizUi = require("./ui/quiz.ui.js");
const graphUi = require("./ui/graph.ui.js");
const geometryUi = require("./ui/geometry.ui.js");
const profilePanelUi = require("./ui/profile_panel.ui.js");

// Stable route ids (opened from main sidebar).
const PROFILE_PANEL_ROUTE =
  "toolpkg:com.operit.study_assistant:ui:study_profile_panel";
// 「肥鱼老师」侧边栏入口：进入即打开 App（未装先装）
const TEACHER_LAUNCH_ROUTE =
  "toolpkg:com.operit.study_assistant:ui:teacher_app_launch";

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

// Extract the raw text inside the outermost XML tag and try to parse JSON.
// Accepts either a bare JSON body or a body wrapped in an optional CDATA.
function parsePayload(event) {
  const payload = (event && event.eventPayload) || event || {};
  const raw = payload.xmlContent || "";
  let body = raw;

  // strip the outer tag if present: <tag ...> ... </tag>
  const tag = payload.tagName;
  if (tag) {
    const open = new RegExp("<" + tag + "(?:\\s[^>]*)?>", "i");
    const close = new RegExp("</" + tag + "\\s*>", "i");
    body = body.replace(open, "").replace(close, "");
  }
  body = body.trim();

  // unwrap CDATA
  const cdata = body.match(/^<!\[CDATA\[([\s\S]*)\]\]>$/);
  if (cdata) {
    body = cdata[1].trim();
  }

  try {
    return JSON.parse(body);
  } catch (e) {
    // fall back: expose the raw text so screens can still render something.
    return { _raw: body, _parseError: String(e && e.message ? e.message : e) };
  }
}

function tagOf(event) {
  const payload = (event && event.eventPayload) || event || {};
  return payload.tagName || "";
}

// ---------------------------------------------------------------------------
// XML render hooks
// ---------------------------------------------------------------------------

function onQuizRender(event) {
  if (tagOf(event) !== "study_quiz") {
    return { handled: false };
  }
  const data = parsePayload(event);
  return {
    handled: true,
    composeDsl: {
      // closure captures the parsed data (most reliable channel)
      // screen 必须是可序列化引用（字符串模块路径），不能直接传函数对象
      screen: "ui/quiz.ui.js",
      state: { study_quiz: data }
    }
  };
}

function onGraphRender(event) {
  if (tagOf(event) !== "study_graph") {
    return { handled: false };
  }
  const data = parsePayload(event);
  return {
    handled: true,
    composeDsl: {
      // screen 必须是可序列化引用（字符串模块路径），不能直接传函数对象
      screen: "ui/graph.ui.js",
      state: { study_graph: data }
    }
  };
}

function onGeometryRender(event) {
  if (tagOf(event) !== "study_geometry") {
    return { handled: false };
  }
  const data = parsePayload(event);
  return {
    handled: true,
    composeDsl: {
      // screen 必须是可序列化引用（字符串模块路径），不能直接传函数对象
      screen: "ui/geometry.ui.js",
      state: { study_geometry: data }
    }
  };
}

// ---------------------------------------------------------------------------
// registration
// ---------------------------------------------------------------------------

function registerToolPkg() {
  ToolPkg.registerXmlRenderPlugin({
    id: "study_assistant_quiz",
    tag: "study_quiz",
    function: onQuizRender
  });

  ToolPkg.registerXmlRenderPlugin({
    id: "study_assistant_graph",
    tag: "study_graph",
    function: onGraphRender
  });

  ToolPkg.registerXmlRenderPlugin({
    id: "study_assistant_geometry",
    tag: "study_geometry",
    function: onGeometryRender
  });

  // registerUiRoute 的 screen 必须是「可序列化引用」：字符串模块路径，
  // 或由 require 系统打上 __operit_toolpkg_module_path 标记的函数。
  // 直接传普通函数会抛 "requires a serializable screen reference"。
  // 这里用字符串模块路径（相对 toolpkg 根目录），最稳妥。
  ToolPkg.registerUiRoute({
    id: "study_profile_panel",
    route: PROFILE_PANEL_ROUTE,
    runtime: "compose_dsl",
    screen: "ui/profile_panel.ui.js",
    title: {
      zh: "学习档案",
      en: "Study Profile"
    }
  });

  ToolPkg.registerNavigationEntry({
    id: "study_profile_panel_sidebar",
    route: PROFILE_PANEL_ROUTE,
    surface: "main_sidebar_plugins",
    title: {
      zh: "学习档案",
      en: "Study Profile"
    },
    icon: "menu_book",
    order: 10
  });

  // 肥鱼老师：侧边栏入口，点开即启动 App（未安装时自动用内置安装包装好）
  ToolPkg.registerUiRoute({
    id: "teacher_app_launch",
    route: TEACHER_LAUNCH_ROUTE,
    runtime: "compose_dsl",
    screen: "ui/teacher_app.ui.js",
    title: {
      zh: "肥鱼老师",
      en: "Teacher App"
    }
  });

  ToolPkg.registerNavigationEntry({
    id: "teacher_app_launch_sidebar",
    route: TEACHER_LAUNCH_ROUTE,
    surface: "main_sidebar_plugins",
    title: {
      zh: "肥鱼老师",
      en: "Teacher App"
    },
    icon: "auto_stories",
    order: 11
  });

  return true;
}

exports.registerToolPkg = registerToolPkg;
exports.onQuizRender = onQuizRender;
exports.onGraphRender = onGraphRender;
exports.onGeometryRender = onGeometryRender;
