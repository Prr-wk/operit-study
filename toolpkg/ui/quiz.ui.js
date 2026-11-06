/*
 * quiz.ui.js - 当堂检测选择题控件 (Compose DSL screen factory)
 *
 * Payload schema (inside <study_quiz> ... </study_quiz>):
 * {
 *   "title": "当堂检测",            // optional
 *   "question": "题目文本",
 *   "options": ["选项A内容", "选项B内容", ...],
 *   "answer": 0,                    // 正确选项下标 (0-based), optional
 *   "explain": "解析文本"           // optional
 * }
 */

function letter(i) {
  return String.fromCharCode(65 + i); // A, B, C ...
}

function StudyQuizScreen(ctx) {
    const UI = ctx.UI;
    const Modifier = ctx.Modifier;

    const quiz = ctx.useState("study_quiz", {})[0] || {};
    const pickedState = ctx.useState("study_quiz_picked", -1);
    const picked = pickedState[0];
    const setPicked = pickedState[1];
    const submittedState = ctx.useState("study_quiz_submitted", false);
    const submitted = submittedState[0];
    const setSubmitted = submittedState[1];

    // ---- fallback: raw text / parse failure -------------------------------
    if (quiz._raw !== undefined && !quiz.question) {
      const raw = quiz._raw || "（题目解析失败）";
      return UI.Column({ modifier: Modifier.padding(16) }, [
        UI.Text({ text: raw })
      ]);
    }

    const options = Array.isArray(quiz.options) ? quiz.options : [];
    const hasAnswer = typeof quiz.answer === "number" && quiz.answer >= 0;

    const children = [];

    children.push(
      UI.Text({
        text: quiz.title || "当堂检测",
        fontSize: 18,
        fontWeight: "bold"
      })
    );

    children.push(UI.Spacer({ height: 8 }));

    children.push(
      UI.Text({
        text: quiz.question || "（无题干）",
        fontSize: 16
      })
    );

    children.push(UI.Spacer({ height: 12 }));

    for (let i = 0; i < options.length; i++) {
      const isPicked = picked === i;
      const isCorrect = submitted && hasAnswer && i === quiz.answer;
      const isWrongPick = submitted && isPicked && hasAnswer && i !== quiz.answer;

      let mark = "";
      if (submitted && hasAnswer) {
        if (isCorrect) mark = "  ✓";
        else if (isWrongPick) mark = "  ✗";
      }

      const bg = isCorrect
        ? "#C8E6C9"
        : isWrongPick
        ? "#FFCDD2"
        : isPicked
        ? "#BBDEFB"
        : "#F0F0F0";

      children.push(
        UI.Button({
          text: letter(i) + ". " + options[i] + mark,
          modifier: Modifier
            .fillMaxWidth()
            .padding(0, 4, 0, 4)
            .background(bg),
          onClick: function () {
            if (!submitted) setPicked(i);
          }
        })
      );
    }

    children.push(UI.Spacer({ height: 12 }));

    if (!submitted) {
      children.push(
        UI.Button({
          text: "提交答案",
          onClick: function () {
            if (picked >= 0) setSubmitted(true);
          }
        })
      );
    } else {
      const correct = hasAnswer && picked === quiz.answer;
      children.push(
        UI.Text({
          text: hasAnswer
            ? correct
              ? "回答正确 ✓"
              : "回答错误，正确答案：" + letter(quiz.answer)
            : "已提交（本题未提供标准答案）",
          fontSize: 15,
          fontWeight: "bold"
        })
      );

      if (quiz.explain) {
        children.push(UI.Spacer({ height: 8 }));
        children.push(
          UI.Text({ text: "解析：" + quiz.explain, fontSize: 14 })
        );
      }

      children.push(UI.Spacer({ height: 12 }));
      children.push(
        UI.Button({
          text: "重做",
          onClick: function () {
            setPicked(-1);
            setSubmitted(false);
          }
        })
      );
    }

    return UI.Column({ modifier: Modifier.padding(16) }, children);
}

// 静态渲染（可选交互揭示）：供讲题面板复用。
// opts = { revealed?: boolean, onToggle?: function }
//   - 未揭示：只显示题干与选项，底部提示“点击查看答案与解析”，整体可点按。
//   - 已揭示：正确项标 ✓ 并显示解析，底部提示“点击收起”。
// 不传 onToggle 时退化为纯静态（直接显示答案），兼容旧的调用方。
function renderQuizStatic(data, UI, Modifier, opts) {
  opts = opts || {};
  const revealed = opts.revealed === true;
  const onToggle = typeof opts.onToggle === "function" ? opts.onToggle : null;

  const quiz = data || {};

  if (quiz._raw !== undefined && !quiz.question) {
    return UI.Column({ modifier: Modifier.padding(8) }, [
      UI.Text({ text: quiz._raw || "（题目解析失败）", fontSize: 14 })
    ]);
  }

  const options = Array.isArray(quiz.options) ? quiz.options : [];
  const hasAnswer = typeof quiz.answer === "number" && quiz.answer >= 0;
  // 没有交互回调时退化为始终揭示（保持旧行为）
  const showAnswer = onToggle ? revealed : true;

  const children = [];
  children.push(
    UI.Text({ text: quiz.title || "当堂检测", fontSize: 16, fontWeight: "bold" })
  );
  children.push(UI.Spacer({ height: 6 }));
  children.push(UI.Text({ text: quiz.question || "（无题干）", fontSize: 15 }));
  children.push(UI.Spacer({ height: 8 }));

  for (let i = 0; i < options.length; i++) {
    const isCorrect = showAnswer && hasAnswer && i === quiz.answer;
    children.push(
      UI.Text({
        text: letter(i) + ". " + options[i] + (isCorrect ? "  ✓" : ""),
        fontSize: 14,
        color: isCorrect ? "#2E7D32" : "#212121"
      })
    );
  }

  if (showAnswer && quiz.explain) {
    children.push(UI.Spacer({ height: 8 }));
    children.push(
      UI.Text({ text: "解析：" + quiz.explain, fontSize: 13, color: "#555555" })
    );
  }

  if (onToggle) {
    children.push(UI.Spacer({ height: 8 }));
    children.push(
      UI.Text({
        text: showAnswer ? "▲ 点击收起答案" : "▼ 点击查看答案与解析",
        fontSize: 12,
        color: "#1976D2"
      })
    );
  }

  const colModifier = onToggle
    ? Modifier.padding(4).clickable(function () { onToggle(); })
    : Modifier.padding(4);

  return UI.Column({ modifier: colModifier }, children);
}

exports.__esModule = true;
exports.default = StudyQuizScreen;
exports.renderQuizStatic = renderQuizStatic;