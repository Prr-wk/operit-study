package com.operit.doubaoteacher;

import java.util.List;

/** 离线自测：不依赖 android.jar，只验 BoardParser + MiniJson */
public class TestBoard {

    public static void main(String[] args) {
        String model = ""
                + "[note]题目拆解\n"
                + "已知：$y=x^2-2x-3$\n"
                + "要求：顶点坐标、最小值\n"
                + "考点：二次函数配方\n"
                + "→开口向上，有最小值\n"
                + "[/note]\n"
                + "[step]先配方\n"
                + "把 $y=x^2-2x-3$ 配成 $y=(x-1)^2-4$。\n"
                + "[/step]\n"
                + "[plot] func: y=x^2-2x-3, -3, 5\n"
                + "[key]配方这一步完全正确[/key]\n"
                + "[quiz]顶点坐标是？\n"
                + "A.$(1,-4)$\n"
                + "B.$(1,4)$\n"
                + "答案：A\n"
                + "解析：由 $y=(x-1)^2-4$ 直接读出\n"
                + "[/quiz]\n"
                + "[talk]好，你先自己算一遍，我在这儿等着。\n"
                + "不用急着对答案。[/talk]";

        List<BoardParser.Block> bs = BoardParser.parse(model);
        System.out.println("blocks = " + bs.size());
        for (int i = 0; i < bs.size(); i++) {
            BoardParser.Block b = bs.get(i);
            System.out.println("[" + i + "] type=" + b.type + " title=" + b.title
                    + " bodyLen=" + b.body.length()
                    + (b.type.equals("plot") ? (" kind=" + b.kind + " expr=" + b.expr
                    + " x=[" + b.xmin + "," + b.xmax + "]") : ""));
        }

        String json = BoardParser.toJson(bs);
        System.out.println("\nJSON:");
        System.out.println(json);

        // 回读校验：JSON 必须能被解析回等量数组
        try {
            Object o = MiniJson.parse(json);
            int n = (o instanceof List) ? ((List<?>) o).size() : -1;
            System.out.println("\nreparse ok, items=" + n + " (expect " + bs.size() + ")");
            if (n != bs.size()) {
                System.out.println("MISMATCH!");
            }
        } catch (Exception e) {
            System.out.println("\nJSON PARSE FAILED: " + e);
        }

        // TTS 分段
        List<String> parts = BoardParser.speakParts(bs);
        System.out.println("\nspeak parts = " + parts.size());
        for (int i = 0; i < parts.size(); i++) {
            System.out.println("  " + i + ": " + parts.get(i).replace('\n', ' '));
        }
    }
}