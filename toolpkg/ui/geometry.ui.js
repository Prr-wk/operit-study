/*
 * geometry.ui.js - 几何图形 / 圈画控件 (Compose DSL screen factory, Canvas based)
 *
 * Payload schema (inside <study_geometry> ... </study_geometry>):
 * {
 *   "title": "几何图形",
 *   "view": { "w": 100, "h": 100, "yDown": false },  // 逻辑坐标系, 默认100x100, y向上
 *   "shapes": [
 *     { "type": "line",   "x1":0,"y1":0,"x2":10,"y2":0,"color":"#1976D2","strokeWidth":2 },
 *     { "type": "circle", "cx":5,"cy":5,"r":3,"color":"#E91E63","filled":false },
 *     { "type": "polygon","points":[[0,0],[10,0],[10,10]],"color":"#388E3C","filled":false,"closed":true },
 *     { "type": "path",   "points":[[0,0],[3,4],[7,2]],"color":"#F57C00" },
 *     { "type": "point",  "x":5,"y":5,"label":"A" },
 *     { "type": "text",   "x":5,"y":5,"text":"∠A","color":"#000" }
 *   ]
 * }
 */

var UNIT = "dp"; // 所有绘图命令显式声明单位，防止宿主按 fraction(0~1) 解释导致坐标出界、画布全白

function normShape(s) {
  return s && typeof s === "object" ? s : {};
}

function renderGeometry(g, UI, Modifier, opts) {
    opts = opts || {};
    g = g || {};

    const viewW = (typeof opts.width === "number" && opts.width > 0) ? opts.width : 320;
    const viewH = (typeof opts.height === "number" && opts.height > 0) ? opts.height : 320;

    const view = g.view || {};
    const vw = typeof view.w === "number" && view.w > 0 ? view.w : 100;
    const vh = typeof view.h === "number" && view.h > 0 ? view.h : 100;
    const yDown = view.yDown === true;

    const pad = 16;
    const plotW = Math.max(1, viewW - 2 * pad);
    const plotH = Math.max(1, viewH - 2 * pad);

    function PX(x) {
      return pad + (x / vw) * plotW;
    }
    function PY(y) {
      if (yDown) return pad + (y / vh) * plotH;
      return viewH - pad - (y / vh) * plotH;
    }

    const commands = [];
    const shapes = Array.isArray(g.shapes) ? g.shapes : [];
    const defaultColor = "#1976D2";

    for (let i = 0; i < shapes.length; i++) {
      const s = normShape(shapes[i]);
      const color = s.color || defaultColor;
      const sw = typeof s.strokeWidth === "number" ? s.strokeWidth : 2;
      const filled = s.filled === true;

      if (s.type === "line") {
        commands.push({
          type: "line",
          x1: PX(s.x1 || 0), y1: PY(s.y1 || 0),
          x2: PX(s.x2 || 0), y2: PY(s.y2 || 0),
          color: color, strokeWidth: sw, unit: UNIT
        });
      } else if (s.type === "circle") {
        commands.push({
          type: "circle",
          cx: PX(s.cx || 0), cy: PY(s.cy || 0),
          radius: ((s.r || 1) / vw) * plotW,
          color: color, strokeWidth: sw, filled: filled, unit: UNIT
        });
      } else if (s.type === "polygon" || s.type === "path") {
        const pts = Array.isArray(s.points) ? s.points : [];
        const ops = [];
        for (let k = 0; k < pts.length; k++) {
          const p = pts[k];
          if (!Array.isArray(p) || p.length < 2) continue;
          if (k === 0) ops.push({ type: "moveTo", x: PX(p[0]), y: PY(p[1]) });
          else ops.push({ type: "lineTo", x: PX(p[0]), y: PY(p[1]) });
        }
        const closed = s.closed === true || s.type === "polygon";
        if (closed && ops.length > 0) ops.push({ type: "close" });
        if (ops.length > 0) {
          commands.push({
            type: "drawPath", path: ops, color: color,
            strokeWidth: sw, style: filled ? "fill" : "stroke", unit: UNIT
          });
        }
      } else if (s.type === "point") {
        commands.push({
          type: "circle",
          cx: PX(s.x || 0), cy: PY(s.y || 0), radius: 3,
          color: color, filled: true, unit: UNIT
        });
        if (s.label) {
          commands.push({
            type: "drawText", text: String(s.label),
            x: PX(s.x || 0) + 5, y: PY(s.y || 0) - 5,
            color: color, fontSize: 12, unit: UNIT
          });
        }
      } else if (s.type === "text") {
        commands.push({
          type: "drawText", text: String(s.text || ""),
          x: PX(s.x || 0), y: PY(s.y || 0),
          color: color, fontSize: typeof s.fontSize === "number" ? s.fontSize : 13, unit: UNIT
        });
      }
    }

    const canvas = UI.Canvas({
      commands: commands,
      modifier: Modifier
        .fillMaxWidth()
        .height(320)
        .background("#FFFFFF"),
      onSizeChanged: opts.onSizeChanged
    });

    return UI.Column({ modifier: Modifier.padding(8) }, [
      UI.Text({
        text: g.title || "几何图形",
        fontSize: 16,
        fontWeight: "bold"
      }),
      UI.Spacer({ height: 6 }),
      canvas
    ]);
}

function StudyGeometryScreen(ctx) {
    const g = ctx.useState("study_geometry", {})[0] || {};
    const sizeState = ctx.useState("study_geometry_size", { width: 320, height: 320 });
    const size = sizeState[0];
    const setSize = sizeState[1];

    function saneSize(v, def) {
      return (typeof v === "number" && v > 0 && v < 8000) ? v : def;
    }
    const viewW = saneSize(size && size.width, 320);
    const viewH = saneSize(size && size.height, 320);

    return renderGeometry(g, ctx.UI, ctx.Modifier, {
      width: viewW,
      height: viewH,
      onSizeChanged: function (evt) {
        if (
          evt &&
          typeof evt.width === "number" && typeof evt.height === "number" &&
          evt.width > 0 && evt.height > 0 &&
          evt.width < 8000 && evt.height < 8000
        ) {
          if (evt.width !== viewW || evt.height !== viewH) {
            setSize({ width: evt.width, height: evt.height });
          }
        }
      }
    });
}

exports.__esModule = true;
exports.default = StudyGeometryScreen;
exports.renderGeometry = renderGeometry;