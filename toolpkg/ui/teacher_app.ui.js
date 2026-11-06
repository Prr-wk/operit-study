/*
 * teacher_app.ui.js - 「肥鱼老师」侧边栏入口
 *
 * 行为：进入页面即自动执行
 *   1) 调 teacher_app:teacher_app_open
 *      （该工具内部逻辑：已安装→直接启动；未安装→导出内置 APK 安装后启动）
 *   2) 再调 teacher_app:teacher_app_status 获取最终状态与版本
 *
 * 设计要点（对齐 profile_panel.ui.js）：
 *   - 渲染期间保持纯净，副作用只放在 onLoad / onClick
 *   - callTool 返回值可能是对象或 JSON 文本，两种都兼容
 *   - useRef 防重入，避免 onLoad 与点击并发
 */

var OPEN_TOOL = "teacher_app:teacher_app_open";
var STATUS_TOOL = "teacher_app:teacher_app_status";

/* 归一化 callTool 返回 */
function pickResult(raw) {
  var obj = raw;
  if (typeof raw === "string") {
    try {
      obj = JSON.parse(raw);
    } catch (e) {
      return { ok: false, text: "返回内容无法解析" };
    }
  }
  if (obj === null || obj === undefined) {
    return { ok: false, text: "工具返回为空" };
  }
  if (typeof obj !== "object") {
    return { ok: false, text: "工具返回类型异常: " + typeof obj };
  }

  var ok = obj.success !== false;
  var data = obj.data && typeof obj.data === "object" ? obj.data : {};
  return {
    ok: ok,
    text: obj.message || (ok ? "已完成" : "操作失败"),
    installed: data.installed === true,
    version: data.version ? String(data.version) : ""
  };
}

function TeacherAppScreen(ctx) {
  var UI = ctx.UI;
  var Modifier = ctx.Modifier;

  // working | done | failed
  var ss = ctx.useState("ta_state", "working");
  var state = ss[0];
  var setState = ss[1];

  var ms = ctx.useState("ta_msg", "正在检查「肥鱼老师」…");
  var msg = ms[0];
  var setMsg = ms[1];

  var vs = ctx.useState("ta_ver", "");
  var ver = vs[0];
  var setVer = vs[1];

  var busyRef = ctx.useRef("ta_busy", false);

  function launch() {
    if (busyRef.current) return;
    busyRef.current = true;

    setState("working");
    setMsg("正在检查「肥鱼老师」是否已安装…");

    return Promise.resolve()
      .then(function () {
        return ctx.callTool(OPEN_TOOL, {});
      })
      .then(function (raw) {
        var r = pickResult(raw);
        if (r.ok) {
          setState("done");
          setMsg("已打开「肥鱼老师」");
        } else {
          setState("failed");
          setMsg(r.text);
        }
        return ctx.callTool(STATUS_TOOL, {});
      })
      .then(function (raw2) {
        var r2 = pickResult(raw2);
        if (r2.installed) {
          setVer(r2.version || "");
        }
      })
      .catch(function (e) {
        setState("failed");
        setMsg("失败：" + (e && e.message ? e.message : String(e)));
      })
      .then(function () {
        busyRef.current = false;
      });
  }

  var children = [];

  children.push(
    UI.Text({ text: "肥鱼老师", fontSize: 18, fontWeight: "bold" })
  );
  children.push(UI.Spacer({ height: 4 }));
  children.push(
    UI.Text({
      text: "点开后会自动检查：已安装就直接启动，未安装会先用插件内置的安装包装好再启动。",
      fontSize: 12,
      color: "#757575"
    })
  );
  children.push(UI.Spacer({ height: 10 }));

  // 状态块
  var statusText = state === "done"
    ? "已安装" + (ver ? "（v" + ver + "）" : "")
    : (state === "failed" ? "未完成" : "处理中…");
  var statusColor = state === "done" ? "#0C9B52" : (state === "failed" ? "#D32F2F" : "#1976D2");

  children.push(
    UI.Card({ modifier: Modifier.fillMaxWidth(), elevation: 1 }, [
      UI.Column({ modifier: Modifier.fillMaxWidth().padding(10) }, [
        UI.Text({ text: statusText, fontSize: 15, fontWeight: "bold", color: statusColor }),
        UI.Spacer({ height: 4 }),
        UI.Text({ text: msg, fontSize: 13, color: "#424242" })
      ])
    ])
  );

  if (state === "working") {
    children.push(UI.Spacer({ height: 10 }));
    children.push(UI.LinearProgressIndicator({}));
  }

  children.push(UI.Spacer({ height: 12 }));
  children.push(
    UI.Button({
      text: state === "working" ? "处理中…" : "再打开一次",
      enabled: state !== "working",
      onClick: launch
    })
  );

  children.push(UI.Spacer({ height: 10 }));
  children.push(
    UI.Text({
      text: "若安装失败，请确认 Operit 已获得 Root / Shizuku（安装需要复制到 /data/local/tmp 再执行 pm install）。",
      fontSize: 11,
      color: "#9E9E9E"
    })
  );

  return UI.Column(
    { modifier: Modifier.fillMaxSize().padding(12), onLoad: launch },
    children
  );
}

exports.__esModule = true;
exports.default = TeacherAppScreen;