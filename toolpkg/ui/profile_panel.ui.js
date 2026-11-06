/*
 * profile_panel.ui.js - 学习档案面板 (主页侧边栏「插件」入口)
 *
 * 作用：展示学习档案 profile.json 中的知识点关键词及其「有效权重」(effective)。
 * 数据来源：调用子包工具 study_profile:profile_get（只读）。
 *
 * 设计要点（遵循 COMPOSE_DSL_RULES.md）：
 *   - 渲染期间保持纯净：不在 screen 构造里调用工具/写文件；
 *     网络式副作用统一放在组件 onLoad 或按钮 onClick 中触发。
 *   - ctx.callTool 返回值形态不固定（对象或 JSON 文本），两种都兼容处理。
 *   - 用 useRef 做加载防重入，避免重复点击并发请求。
 */

var TOOL = "study_profile:profile_get";

/* 把 callTool 的返回归一化为 { points?, error? } */
function normalize(raw) {
  var obj = raw;

  // 返回值可能是 JSON 文本（不同 Operit 版本行为不同）
  if (typeof raw === "string") {
    try {
      obj = JSON.parse(raw);
    } catch (e) {
      return { error: "返回内容无法解析为 JSON" };
    }
  }

  if (obj === null || obj === undefined) {
    return { error: "工具返回为空" };
  }
  if (typeof obj !== "object") {
    return { error: "工具返回类型异常: " + typeof obj };
  }
  if (obj.success === false) {
    return { error: obj.message || "读取档案失败" };
  }

  var data = obj.data && typeof obj.data === "object" ? obj.data : obj;
  var points = data && Array.isArray(data.points) ? data.points : [];
  return { points: points };
}

function fmtNum(n) {
  if (typeof n !== "number" || !isFinite(n)) return "-";
  return n.toFixed(2);
}

function intOr0(n) {
  return typeof n === "number" && isFinite(n) ? Math.round(n) : 0;
}

function StudyProfilePanelScreen(ctx) {
  var UI = ctx.UI;
  var Modifier = ctx.Modifier;

  var ps = ctx.useState("sp_points", null);
  var points = ps[0];
  var setPoints = ps[1];

  var es = ctx.useState("sp_err", "");
  var errMsg = es[0];
  var setErrMsg = es[1];

  var loadingRef = ctx.useRef("sp_loading", false);

  function load() {
    if (loadingRef.current) return;
    loadingRef.current = true;

    return Promise.resolve()
      .then(function () {
        return ctx.callTool(TOOL, {});
      })
      .then(function (raw) {
        var r = normalize(raw);
        if (r.error) {
          setErrMsg(r.error);
          setPoints([]);
        } else {
          setErrMsg("");
          setPoints(r.points);
        }
      })
      .catch(function (e) {
        setErrMsg("调用失败: " + (e && e.message ? e.message : String(e)));
        setPoints([]);
      })
      .then(function () {
        loadingRef.current = false;
      });
  }

  var children = [];

  children.push(
    UI.Text({
      text: "学习档案 · 知识点权重",
      fontSize: 18,
      fontWeight: "bold"
    })
  );
  children.push(UI.Spacer({ height: 4 }));
  children.push(
    UI.Text({
      text: "权重随掌握情况衰减（半衰期 7 天），effective 为当前有效权重。",
      fontSize: 12,
      color: "#757575"
    })
  );
  children.push(UI.Spacer({ height: 8 }));

  children.push(
    UI.Button({
      text: points === null ? "加载中…" : "刷新",
      enabled: points !== null,
      onClick: load
    })
  );
  children.push(UI.Spacer({ height: 8 }));

  if (points === null) {
    children.push(UI.CircularProgressIndicator({}));
    children.push(UI.Spacer({ height: 6 }));
    children.push(
      UI.Text({ text: "正在读取学习档案…", fontSize: 13, color: "#757575" })
    );
  } else if (errMsg) {
    children.push(
      UI.Text({ text: errMsg, fontSize: 14, color: "#D32F2F" })
    );
  } else if (points.length === 0) {
    children.push(
      UI.Text({
        text: "暂无知识点记录。先让 AI 出题、讲题或记录错题后，这里会显示对应关键词与权重。",
        fontSize: 13,
        color: "#757575"
      })
    );
  } else {
    var maxEff = 0;
    for (var i = 0; i < points.length; i++) {
      var e0 = typeof points[i].effective === "number" ? points[i].effective : 0;
      if (e0 > maxEff) maxEff = e0;
    }
    if (maxEff <= 0) maxEff = 1;

    var items = [];
    for (var k = 0; k < points.length; k++) {
      items.push(buildItem(UI, Modifier, points[k], maxEff));
    }

    children.push(UI.LazyColumn({ spacing: 8, content: items }));
  }

  // onLoad 触发首次读取（副作用不放在渲染路径里）
  return UI.Column(
    {
      modifier: Modifier.fillMaxSize().padding(12),
      onLoad: load
    },
    children
  );
}

function buildItem(UI, Modifier, p, maxEff) {
  var eff = typeof p.effective === "number" ? p.effective : 0;
  var ratio = Math.max(0, Math.min(1, eff / maxEff));

  var title = p.point || "(未命名知识点)";
  var subject = p.subject || "未分类";

  return UI.Card(
    {
      modifier: Modifier.fillMaxWidth(),
      elevation: 1
    },
    [
      UI.Column({ modifier: Modifier.fillMaxWidth().padding(10) }, [
        UI.Row(
          {
            modifier: Modifier.fillMaxWidth(),
            horizontalArrangement: "spaceBetween"
          },
          [
            UI.Text({
              text: title,
              fontSize: 15,
              fontWeight: "bold"
            }),
            UI.Text({
              text: "有效 " + fmtNum(eff),
              fontSize: 13,
              color: "#1976D2"
            })
          ]
        ),
        UI.Spacer({ height: 6 }),
        // 相对权重条（用宽度的兄弟占位实现）
        UI.LinearProgressIndicator({ progress: ratio }),
        UI.Spacer({ height: 4 }),
        UI.Text({
          text:
            subject +
            " · 原始 " +
            fmtNum(p.weight) +
            " · 错 " +
            intOr0(p.errors) +
            " / 练 " +
            intOr0(p.hits),
          fontSize: 12,
          color: "#757575"
        })
      ])
    ]
  );
}

exports.__esModule = true;
exports.default = StudyProfilePanelScreen;
