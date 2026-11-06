/*
 * graph.ui.js - 函数图像控件 (Compose DSL screen factory, Canvas based)
 *
 * Payload schema (inside <study_graph> ... </study_graph>):
 * {
 *   "title": "函数图像",        // optional
 *   "expr": "x^2 - 2x + 1",     // 以 x 为自变量的表达式, optional
 *   "exprs": [ {"expr":"sin(x)","color":"#E91E63"} ],  // optional 多条曲线
 *   "xmin": -10, "xmax": 10,
 *   "ymin": -10, "ymax": 10,
 *   "points": [[1, 2], [3, 4]], // optional 散点
 *   "xlabel": "x", "ylabel": "y"
 * }
 *
 * 关键约定（防空屏 / 防崩溃）：
 *   - 所有绘图命令显式声明 unit:"dp"，避免宿主把裸数字按 fraction(0~1 相对值)
 *     解释，导致坐标被放大出界、画布全白。
 *   - 画布使用「固定逻辑尺寸」，不再依赖 onSizeChanged 回传值驱动布局，
 *     彻底消除「布局 → 回调 → 改 state → 重新布局」的指数放大死循环。
 */

var UNIT = "dp";
var VIEW_W = 320; // 固定逻辑宽度 (dp)
var VIEW_H = 240; // 固定逻辑高度 (dp)

function buildEvaluator(expr) {
  try {
    var body = String(expr)
      .replace(/π/g, "PI")
      .replace(/\^/g, "**")
      .replace(/\bln\b/g, "log");
    // eslint-disable-next-line no-new-func
    var fn = new Function("x", "with (Math) { return (" + body + "); }");
    return function (x) {
      try {
        var v = fn(x);
        if (typeof v === "number" && isFinite(v)) return v;
        return null;
      } catch (e) {
        return null;
      }
    };
  } catch (e) {
    return null;
  }
}

// 纯渲染函数：不依赖 ctx / hooks，供 lesson_panel 等复用。
function renderGraph(g, UI, Modifier) {
  g = g || {};

  const xmin = typeof g.xmin === "number" ? g.xmin : -10;
  const xmax = typeof g.xmax === "number" ? g.xmax : 10;
  const ymin = typeof g.ymin === "number" ? g.ymin : -10;
  const ymax = typeof g.ymax === "number" ? g.ymax : 10;

  const pad = 24;
  const plotW = Math.max(1, VIEW_W - 2 * pad);
  const plotH = Math.max(1, VIEW_H - 2 * pad);
  const spanX = xmax - xmin || 1;
  const spanY = ymax - ymin || 1;

  function px(x) {
    return pad + ((x - xmin) / spanX) * plotW;
  }
  function py(y) {
    return VIEW_H - pad - ((y - ymin) / spanY) * plotH;
  }

  const commands = [];

  // ---- 网格 ----
  const gridColor = "#E0E0E0";
  for (let gx = Math.ceil(xmin); gx <= xmax; gx++) {
    commands.push({
      type: "line",
      x1: px(gx), y1: py(ymin), x2: px(gx), y2: py(ymax),
      color: gridColor, strokeWidth: 1, unit: UNIT
    });
  }
  for (let gy = Math.ceil(ymin); gy <= ymax; gy++) {
    commands.push({
      type: "line",
      x1: px(xmin), y1: py(gy), x2: px(xmax), y2: py(gy),
      color: gridColor, strokeWidth: 1, unit: UNIT
    });
  }

  // ---- 坐标轴 ----
  const axisColor = "#424242";
  commands.push({
    type: "line", x1: px(xmin), y1: py(0), x2: px(xmax), y2: py(0),
    color: axisColor, strokeWidth: 1.5, unit: UNIT
  });
  commands.push({
    type: "line", x1: px(0), y1: py(ymin), x2: px(0), y2: py(ymax),
    color: axisColor, strokeWidth: 1.5, unit: UNIT
  });

  // ---- 曲线 ----
  const curves = [];
  if (Array.isArray(g.exprs)) {
    for (let i = 0; i < g.exprs.length; i++) curves.push(g.exprs[i]);
  }
  if (g.expr) curves.push({ expr: g.expr, color: "#1976D2" });
  if (curves.length === 0) {
    curves.push({ expr: "x", color: "#1976D2" });
  }

  const palette = ["#1976D2", "#E91E63", "#388E3C", "#F57C00", "#7B1FA2"];
  const samples = 200;

  for (let c = 0; c < curves.length; c++) {
    const color = curves[c].color || palette[c % palette.length];
    const evalFn = buildEvaluator(curves[c].expr);
    if (!evalFn) continue;

    const ops = [];
    let started = false;
    for (let k = 0; k <= samples; k++) {
      const xv = xmin + (spanX * k) / samples;
      const yv = evalFn(xv);
      if (yv === null || yv < ymin - spanY || yv > ymax + spanY) {
        started = false;
        continue;
      }
      const X = px(xv);
      const Y = py(yv);
      if (!started) {
        ops.push({ type: "moveTo", x: X, y: Y });
        started = true;
      } else {
        ops.push({ type: "lineTo", x: X, y: Y });
      }
    }
    if (ops.length > 0) {
      commands.push({
        type: "drawPath", path: ops, color: color,
        strokeWidth: 2, style: "stroke", unit: UNIT
      });
    }
  }

  // ---- 散点 ----
  if (Array.isArray(g.points)) {
    for (let i = 0; i < g.points.length; i++) {
      const p = g.points[i];
      if (!Array.isArray(p) || p.length < 2) continue;
      commands.push({
        type: "circle", cx: px(p[0]), cy: py(p[1]), radius: 3,
        color: "#D32F2F", filled: true, unit: UNIT
      });
    }
  }

  const canvas = UI.Canvas({
    commands: commands,
    modifier: Modifier
      .fillMaxWidth()
      .height(VIEW_H)
      .background("#FFFFFF")
  });

  return UI.Column({ modifier: Modifier.padding(8) }, [
    UI.Text({
      text: g.title || "函数图像",
      fontSize: 16,
      fontWeight: "bold"
    }),
    UI.Spacer({ height: 6 }),
    canvas
  ]);
}

function StudyGraphScreen(ctx) {
  const g = ctx.useState("study_graph", {})[0] || {};
  return renderGraph(g, ctx.UI, ctx.Modifier);
}

exports.__esModule = true;
exports.default = StudyGraphScreen;
exports.renderGraph = renderGraph;
