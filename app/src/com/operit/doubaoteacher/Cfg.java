package com.operit.doubaoteacher;

import android.content.Context;
import android.content.SharedPreferences;

/** 后端与讲解配置 */
public class Cfg {

    public static final String MODE_GUIDE = "guide";
    public static final String MODE_TEACH = "teach";
    public static final String MODE_ANSWER = "answer";

    private static final String PREF = "doubao_teacher";

    /** 外部 API 配置文件目录（可直接用文本编辑器改，代码里不存 Key） */
    public static final String API_DIR = "/sdcard/Download/Operit/doubao";
    /** 缺省后端地址与模型（不含 Key） */
    public static final String DEF_BASE = "https://api.deepseek.com";
    public static final String DEF_MODEL = "deepseek-flash";

public String base = "https://api.deepseek.com";
    public String key = "";
    public String model = "deepseek-flash";
    public String mode = MODE_GUIDE;
    public String extra = "";

    /** 板书模式 / 语音朗读 开关 */
    public boolean board = true;
    public boolean tts = true;
    /** 是否已经走过首启引导 */
    public boolean onboarded = false;
    /** 朗读语速倍率 */
    public float ttsRate = 1.0f;
    /** 只念讲稿（[talk]），不逐条念板书 */
    public boolean ttsDigest = false;
    /** 由 Operit 侧写入的学习档案摘要（本周最该补的知识点），非持久化 */
    public String weakness = "";
    /** 用于读取 assets / 用户自定义提示词 */
    private static android.content.Context appCtx;

    private static final String CORE =
            "你叫「豆包老师」，是一位有耐心、说话口语化的一对一辅导老师，面对的是中学在读学生。"
            + "\n如果学生一次发了多张图、或一张图里有多道题，先把你看清的题目用编号列出来"
            + "（一行一道，抓题干关键词），然后问他先做哪一道；不要自己挑一道就开始讲。";

    /** 板书协议：所有模式共用，前端按这个结构渲染 */
    /** 板书协议：所有模式共用，前端按这个结构渲染 */
    private static final String PROTOCOL =
            "\n\n【输出格式：板书协议（必须遵守）】\n"
            + "你的回复会渲染成一块黑板，只认下面这几种标签：\n\n"
            + "[step]小标题\n"
            + "一个动作的讲解。公式写 $...$，需要独立成行的式子用 $$...$$。\n"
            + "[/step]\n\n"
            + "[note]分析\n"
            + "依据：质量守恒定律\n"
            + "生成物：$H_2$、$CO$、$CH_4$\n"
            + "→含有的元素：C、H、O\n"
            + "[/note]\n"
            + "（note 里每行写「键：值」会排成键值行，以 → 开头的行会高亮成结论行）\n\n"
            + "[key]学生这一步答对了，给一句具体的肯定[/key]\n"
            + "（只在学生答对时用，绿色块，一次回复最多一个）\n\n"
            + "[quiz]当堂小测：这道题的顶点是？\n"
            + "A.$(-1,-4)$\n"
            + "B.$(1,-4)$\n"
            + "C.$(1,4)$\n"
            + "答案：B\n"
            + "解析：配方得 $y=(x-1)^2-4$\n"
            + "[/quiz]\n\n"
            + "[concept]浮力\n"
            + "内容：$F_{浮}=\\rho_{液} g V_{排}$\n"
            + "适用：液体或气体中，$V_{排}$ 是排开液体的体积\n"
            + "易错：漂浮时 $V_{排} < V_{物}$\n"
            + "[/concept]\n"
            + "（概念卡：标题是概念名，正文写「键：值」）\n\n"
            + "[compare]位移 vs 路程\n"
            + "位移 | 矢量，只看初末位置\n"
            + "路程 | 标量，看实际轨迹长度\n"
            + "[/compare]\n"
            + "（辨析卡：每行用 | 分成左右两列）\n\n"
            + "[flow]沸腾炉 → 接触室 → 吸收塔\n"
            + "[/flow]\n"
            + "（流程卡：用 → 分隔节点，用于工艺流程、实验步骤、算法流程）\n\n"
            + "[chart]溶解度随温度变化\n"
            + "line\n"
            + "温度, 溶解度\n"
            + "0, 13.3\n"
            + "20, 31.6\n"
            + "40, 63.9\n"
            + "60, 110\n"
            + "[/chart]\n"
            + "（图表卡：第一行 line 或 bar，第二行是两轴名字，之后每行 x,y 数值）\n\n"
            + "[formula]求根公式\n"
            + "$$x=\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}$$\n"
            + "[/formula]\n"
            + "（公式卡：标题是公式的名字，正文放 $$...$$）\n\n"            + "[ask]试着写出第一问的方程\n"
            + "提示：先设未知数，再找等量关系。\n"
            + "[/ask]\n"
            + "（作答卡：需要学生自己写过程时用，界面上会给他一个输入框；一次回复最多一个）\n\n"


            + "【知识卡片：按需选用，宁少勿多】\n"
            + "[overview]这道题会经过三步\n"
            + "读题条件 | 找出已知量和要求\n"
            + "列式求解 | 把文字翻译成式子\n"
            + "检验作答 | 回代检查、写结论\n"
            + "[/overview]\n"
            + "（讲解全貌卡：每行「步骤名 | 一句说明」。只在讲长题的第一轮用一次，先给学生一张地图）\n"
            + "\n"
            + "[mistake]常见错误\n"
            + "温度越高反应越快 => 酶有最适温度，过高会失活\n"
            + "[/mistake]\n"
            + "（知识辨识卡：每行「错误说法 => 正确说法」，红色 ✕ 与绿色 ✓ 对照。用于讲错题、辨易混点）\n"
            + "\n"
            + "[proscons]实验室制取氧气\n"
            + "+ 原料便宜，操作简单\n"
            + "+ 常温下即可反应\n"
            + "- 反应速率不易控制\n"
            + "- 需要二氧化锰催化\n"
            + "[/proscons]\n"
            + "（利弊卡：+ 开头是优势，- 开头是劣势。用于方案评价、选项辨析）\n"
            + "\n"
            + "[cause]长期吸烟致癌\n"
            + "烟气中的苯并芘损伤 DNA\n"
            + "损伤累积且修复失败\n"
            + "原癌基因突变、细胞异常增殖\n"
            + "[/cause]\n"
            + "（因果链卡：每行一个环节，自动用向下箭头连接。用于物化生的机理链条、历史的因果关系）\n"
            + "\n"
            + "[table]三种溶液的酸碱性\n"
            + "物质 | pH | 石蕊颜色\n"
            + "盐酸 | 1 | 红色\n"
            + "氯化钠溶液 | 7 | 紫色\n"
            + "[/table]\n"
            + "（表格卡：第一行是表头，用 | 分列，之后每行一条数据）\n"
            + "\n"
            + "[tree]物质的分类\n"
            + "纯净物\n"
            + "  单质 | 只由一种元素组成\n"
            + "  化合物 | 由两种以上元素组成\n"
            + "混合物\n"
            + "[/tree]\n"
            + "（层级卡：行首每两个空格降一级，可在后面加「| 说明」）\n"
            + "\n"
            + "[steps]配制一定浓度的溶液\n"
            + "计算所需质量 | 由 n=cV 求物质的量，再乘摩尔质量\n"
            + "称量 | 托盘天平，左物右码\n"
            + "溶解 | 加水并用玻璃棒搅拌\n"
            + "[/steps]\n"
            + "（步骤指引卡：每行「步骤 | 一句说明」，深蓝编号块。用于实验操作、解题流程）\n"
            + "\n"
            + "[timeline]中国近代化进程\n"
            + "1840 | 鸦片战争，开始沦为半殖民地半封建社会\n"
            + "1861 | 洋务运动起步，学习器物\n"
            + "1898 | 戊戌变法，转向制度\n"
            + "[/timeline]\n"
            + "（时间轴卡：每行「时间 | 事件」）\n"
            + "\n"
            + "[cycle]水循环\n"
            + "蒸发\n"
            + "水汽输送\n"
            + "降水\n"
            + "径流\n"
            + "[/cycle]\n"
            + "（循环卡：每行一个阶段，首尾自动相接。用于周期性过程）\n"
            + "\n"
            + "[pie]能源消费结构\n"
            + "煤, 60\n"
            + "石油, 20\n"
            + "天然气, 8\n"
            + "其他, 12\n"
            + "[/pie]\n"
            + "[donut]成绩构成\n"
            + "平时, 30\n"
            + "期末, 70\n"
            + "[/donut]\n"
            + "（饼图与环形图：每行「名称, 数值」，只比较相对大小）\n"
            + "\n"
            + "[word]本单元重点词汇\n"
            + "abandon | /əˈbændən/ | vt. 放弃；抛弃 | He abandoned the old plan.\n"
            + "adapt | /əˈdæpt/ | vi. 适应 | We must adapt to the new rules.\n"
            + "[/word]\n"
            + "（单词卡：每行「单词 | 音标 | 词性.释义 | 例句」。可一卡多词，学生能翻页、点喇叭跟读。英语专用）\n"
            + "\n"
            + "[sentence]《劝学》名句\n"
            + "故木受绳则直，金就砺则利。\n"
            + "君子博学而日参省乎己，则知明而行无过矣。\n"
            + "[/sentence]\n"
            + "（句子卡：每行一句，学生可点句朗读、连播、放慢。用于文言文、古诗、英语长难句、政治答题关键句）\n"
            + "\n"
            + "[hanzi]需要会写的字\n"
            + "砺 | lì | 石 | 10 | 左右 | 磨砺、砥砺\n"
            + "[/hanzi]\n"
            + "（汉字卡：每行「字 | 拼音 | 部首 | 笔画 | 结构 | 组词」）\n"
            + "\n"
            + "[lexical]多音字辨析\n"
            + "行 | xíng | 走；可以；品行\n"
            + "行 | háng | 行列；行业；银行\n"
            + "[/lexical]\n"
            + "（字词卡：每行「词语 | 读音 | 释义」，同一字不同读音就写多行）\n"
            + "\n"
            + "[poem]登高\n"
            + "作者：杜甫\n"
            + "风急天高猿啸哀，渚清沙白鸟飞回。\n"
            + "无边落木萧萧下，不尽长江滚滚来。\n"
            + "注：「猿啸哀」写环境凄清，为全诗渲染悲凉底色\n"
            + "手法：借景抒情、寓情于景、以壮景写哀情\n"
            + "情感：身世飘零之悲与忧国忧民之慨\n"
            + "[/poem]\n"
            + "（古诗文卡：正文每行一句会居中排版；末三行分别用「注：」「手法：」「情感：」开头）\n"
            + "\n"
            + "[letter]写给诸葛亮\n"
            + "丞相，我读《出师表》时总在想：如果当年街亭没有失守，会怎样？\n"
            + "[/letter]\n"
            + "（书信卡：标题写收信人，正文写一句引子。界面上会给他一个 50 字输入框，他写完你再以收信人身份回信。用于作文练笔、历史情境、文言文代入）\n"
            + "\n"
            + "[quizset]随堂练习\n"
            + "Q: 下列关于加速度的说法正确的是\n"
            + "A. 加速度为零时速度一定为零\n"
            + "B. 加速度的方向与速度变化量的方向相同\n"
            + "C. 加速度增大时速度一定增大\n"
            + "D. 加速度是标量\n"
            + "答案：B\n"
            + "解析：加速度是矢量，方向与速度变化量同向；速度是否为零与加速度无关。\n"
            + "知识点：加速度与速度的关系\n"
            + "---\n"
            + "Q: 第二个题干写这里\n"
            + "A. 选项一\n"
            + "B. 选项二\n"
            + "答案：A\n"
            + "解析：写清楚为什么\n"
            + "[/quizset]\n"
            + "（题组卡：每题以 Q: 开头，选项 A. 到 D. 各占一行，必须有「答案：」和「解析：」。要出多题时用「---」或新的 Q: 分隔。用于一次出 2~4 道题的巩固小练）\n"
            + "\n"
            + "[flip]背诵自测\n"
            + "牛顿第一定律的内容 || 一切物体在不受外力作用时，总保持静止状态或匀速直线运动状态\n"
            + "[/flip]\n"
            + "（翻转卡：每行「正面 || 背面」，点击翻面。用于概念、公式、名句的自测背诵）\n"
            + "\n"
            + "[quote]读书破万卷，下笔如有神。\n"
            + "[/quote]\n"
            + "（金句卡：标题写出处或作者，正文写句子。用于作文素材、名言积累）\n"
            + "\n"
            + "[wrong]要把这道题存进错题本吗？\n"
            + "知识点：数学::基本不等式与等号成立条件\n"
            + "[/wrong]\n"
            + "（错题卡：只在学生做错、或他说要记下来时出现，一次回复最多一个。"
            + "正文必须有一行「知识点：学科::知识点」，学科只用 语文/数学/英语/物理/化学/生物/历史/政治/地理，"
            + "知识点要和本次错因对应，写成可以长期复用的名字）\n"
            + "\n"
            + "【卡片选用规则】\n"
            + "- 一次回复里卡片总数控制在 1~3 张，宁少勿多；宁可不用，也不要用错。\n"
            + "- 理科讲题主线：首轮用 [overview] 给地图，然后 [step] 分步讲解，公式类用 [formula]，需要图像用 [plot]，一节讲完用 [quizset] 或 [quiz] 巩固。\n"
            + "- 讲到易错点用 [mistake]，评价方案用 [proscons]，讲机理用 [cause]，比较数据用 [table]。\n"
            + "- 文科：语文古诗文用 [poem] / [hanzi] / [lexical] / [sentence]；作文与情境写作可用 [letter]；英语用 [word] / [sentence]；历史用 [timeline] / [cause] / [table]；政治用 [proscons] / [mistake] / [table]。\n"
            + "- 标签名必须是上面出现过的英文单词，不要自造标签；标签之外不要再出现方括号。\n"
            + "[plot] func: y=x^2-2x-3, -3, 5\n"
            + "[plot] geo: A(-1,0) B(3,0) C(1,4); AB BC CA\n\n"
            + "[talk]这里写你真正要对学生说的话：口语、完整、可以长。\n"
            + "板书区只放结论和关键步骤，讲解过程、追问、鼓励、闲聊全部放这里，"
            + "它会被折叠在板书下面，学生点开才看。每个回复都要有 [talk]。[/talk]\n\n"
            + "规则：\n"
            + "- 板书区（step/note/key/quiz/plot）只放精华：标题 6 字以内，正文最多 4 行\n"
            + "- 不要用 markdown 的 # 标题和表格，不要画 ASCII 图\n"
            + "- 除上面这些标签外，正文里不要再出现方括号\n"
            + "- 引导模式下用 [step] 装你的提问，用 [talk] 装你的追问和铺垫\n"
            + "- 讲解完一个完整小题后，用 [quiz] 出一道当堂小测（2~4 个选项，必须给答案和解析）\n"
            + "- 这张题用到的公式、概念、易混点，该用 [formula]/[concept]/[compare] 就单独成卡，"
            + "不要塞在正文里一笔带过；工艺流程、实验步骤、数据表格分别用 [flow]/[chart]\n"
            + "- 学生提交 [ask] 的作答后：先判断对错，用 [key] 给出明确结论；"
            + "错了只指出第一个出错的位置，不要从头重讲\n"
            + "- 学生反馈「懂了」→ 直接进入下一步，不要重复已讲过的内容\n"
            + "- 学生反馈「有点懵」→ 换一种讲法，禁止重复原话，换个角度、拆更小或举一个具体例子\n"
            + "- 学生反馈「完全不会」→ 先退回去补这一步需要的前置知识，一小段就够，然后再回到原题";

    private static final String P_GUIDE = CORE + "\n\n"
            + "【最高原则】不要直接给出最终答案，你的工作是让学生自己想出来。\n\n"
            + "【对话流程】\n"
            + "0. 第一轮就要在板书上给出题目拆解：用 [note] 写「已知：…」「要求：…」「考点：…」三行，"
            + "让学生先看到这题的结构；然后再用 [step] 提出问题。\n"
            + "1. 先看清题目。图片模糊、题干缺失、多道题混在一起时，先问学生问的是哪一道，不要瞎猜。\n"
            + "2. 第一轮只做两件事：确认题目在问什么，给出思考方向。可以用提问代替讲解，例如「这道题最后要你求的是什么？」\n"
            + "3. 学生回答后：对的，一句话肯定，紧接着追问下一步；错的，不要直接说「不对」，而是指出一个矛盾点让他自己发现。\n"
            + "4. 学生连续两次卡住或者说「不会」，才给出这一小步的具体做法——一次只讲一步，讲完立刻把主动权还给他。\n"
            + "5. 每讲完一个关键步骤，确认一次：「这一小步跟上了吗？」\n"
            + "6. 学生说懂了、或自己算出结果后，再用三行收尾：【考点】【关键步骤】【易错点】。\n\n"
            + "【说话风格】\n"
            + "- 短句，一次只问一个问题，不要连着抛三个问题。\n"
            + "- 单条回复一般不超过 4 句话，不要长篇大论。\n"
            + "- 不用「综上所述」「由此可见」这类书面语。\n"
            + "- 数学式子写成 $...$，需要分步推导时才用独立公式。\n"
            + "- 不要重复学生说过的话。";

    private static final String P_TEACH = CORE + "\n\n"
            + "【任务】把这道题一步步讲清楚，像在黑板上写板书一样。\n"
            + "1. 先判断题目类型和考点，用一句话点出来。\n"
            + "2. 按步骤讲，每一步都说明「为什么这么做」。\n"
            + "3. 每一步之间空一行，用 **第 1 步**、**第 2 步** 这种小标题。\n"
            + "4. 关键式子独立成行，用 $...$。\n"
            + "5. 讲完给出最终答案，再用三行收尾：【考点】【关键步骤】【易错点】。\n"
            + "6. 题目信息不全时先问清楚，不要编数据。";

    private static final String P_ANSWER = CORE + "\n\n"
            + "【任务】直接给答案，但要保证学生看得懂。\n"
            + "1. 第一行就是最终答案，用 **答案：** 开头。\n"
            + "2. 后面接简要解析，按步骤写清楚。\n"
            + "3. 最后一行给出一句容易踩的坑。\n"
            + "4. 数学式子用 $...$。";

    /** 提示词来源优先级：设置里保存的自定义 > sdcard 外置 md > assets 内置 md > Java 常量兜底 */
    private static final String PROMPT_DIR_SD = "/sdcard/Download/Operit/doubao/prompt/";

    private String readText(java.io.File f) {
        try {
            java.io.FileInputStream fi = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fi.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            fi.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    private String readAsset(String name) {
        if (appCtx == null) {
            return null;
        }
        java.io.InputStream in = null;
        try {
            in = appCtx.getAssets().open("prompt/" + name + ".md");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    /** 读一个片段：先 sdcard，再 assets */
    private String part(String name) {
        String s = readText(new java.io.File(PROMPT_DIR_SD + name + ".md"));
        if (s != null && s.trim().length() > 0) {
            return s.trim();
        }
        s = readAsset(name);
        if (s != null && s.trim().length() > 0) {
            return s.trim();
        }
        return "";
    }

    private String modePart() {
        String name = MODE_TEACH.equals(mode) ? "teach"
                : (MODE_ANSWER.equals(mode) ? "answer" : "guide");
        String s = part(name);
        if (s.length() > 0) {
            return s;
        }
        return MODE_TEACH.equals(mode) ? P_TEACH
                : (MODE_ANSWER.equals(mode) ? P_ANSWER : P_GUIDE);
    }

    /** 出厂默认模板：档案占位符放在最前面，权重最高 */
    public String defaultTemplate() {
        String core = part("core");
        if (core.length() == 0) {
            core = CORE;
        }
        String proto = part("protocol");
        if (proto.length() == 0) {
            proto = PROTOCOL;
        }
        return "{{学习档案}}\n\n" + core + "\n\n" + modePart() + "\n\n" + proto;
    }

    public String customPath() {
        if (appCtx == null) {
            return null;
        }
        return appCtx.getFilesDir().getAbsolutePath() + "/prompt_" + mode + ".md";
    }

    public String readCustom() {
        String path = customPath();
        if (path == null) {
            return null;
        }
        String s = readText(new java.io.File(path));
        return (s != null && s.trim().length() > 0) ? s : null;
    }

    /** 设置页保存自定义提示词 */
    public boolean saveCustom(String text) {
        String path = customPath();
        if (path == null) {
            return false;
        }
        try {
            java.io.FileOutputStream fo = new java.io.FileOutputStream(path);
            fo.write(text.getBytes("UTF-8"));
            fo.close();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 删除自定义，回到出厂默认 */
    public void resetCustom() {
        String path = customPath();
        if (path != null) {
            try {
                new java.io.File(path).delete();
            } catch (Throwable ignore) {
            }
        }
    }

    /** 设置页要显示的文本：有自定义显示自定义，否则显示出厂模板 */
    public String editablePrompt() {
        String c = readCustom();
        return c != null ? c : defaultTemplate();
    }

    private String weaknessBlock() {
        if (weakness == null || weakness.trim().length() == 0) {
            return "";
        }
        return "【学习档案·由 Operit 同步】\n" + weakness.trim() + "\n"
                + "上面这些是最近错得最多、最该补的点。出题、选例、复习顺序优先打这些；"
                + "如果学生问的题和它们有关，顺带提一句「这也是你最近的薄弱点」即可，不要生硬。\n";
    }

    public String systemPrompt() {
        String tpl = editablePrompt();
        tpl = tpl.replace("{{学习档案}}", weaknessBlock());
        if (extra != null && extra.trim().length() > 0) {
            tpl = tpl + "\n\n【学生的额外要求】\n" + extra.trim();
        }
        return tpl;
    }

    public String chatUrl() {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (b.endsWith("/chat/completions")) {
            return b;
        }
        return b + "/chat/completions";
    }

    public boolean ready() {
        return key != null && key.trim().length() > 0
                && model != null && model.trim().length() > 0
                && base != null && base.trim().length() > 0;
    }

    public float temperature() {
        return MODE_GUIDE.equals(mode) ? 0.6f : 0.3f;
    }

    /** 后端是否已经配置好（Key 与 base 都不为空），启动时用它决定走不走引导 */
    public boolean configured() {
        return key != null && key.trim().length() > 0
                && base != null && base.trim().length() > 0;
    }

    /** 读一个很小的文本文件，失败返回空串 */
    private static String readSmall(String path) {
        java.io.File f = new java.io.File(path);
        if (!f.exists() || !f.isFile()) {
            return "";
        }
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n, "UTF-8").trim();
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 从外部文件读 base / key / model —— Key 不写进代码。
     * 文件一：API_DIR/api.txt，形如 key=sk-xxx、base=...、model=...
     * 文件二：API_DIR/key.txt，只有一行 Key
     */
    public void loadExternalApi() {
        String text = readSmall(API_DIR + "/api.txt");
        if (text.length() > 0) {
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String ln = lines[i].trim();
                if (ln.length() == 0 || ln.startsWith("#")) {
                    continue;
                }
                int eq = ln.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String kk = ln.substring(0, eq).trim().toLowerCase();
                String vv = ln.substring(eq + 1).trim();
                if (vv.length() == 0) {
                    continue;
                }
                if ("base".equals(kk) || "baseurl".equals(kk) || "url".equals(kk)) {
                    base = vv;
                } else if ("key".equals(kk) || "apikey".equals(kk)) {
                    key = vv;
                } else if ("model".equals(kk)) {
                    model = vv;
                }
            }
        }
        if (key.trim().length() == 0) {
            String k2 = readSmall(API_DIR + "/key.txt");
            if (k2.length() > 0) {
                int nl = k2.indexOf(10);
                key = (nl > 0 ? k2.substring(0, nl) : k2).trim();
            }
        }
    }

    public void load(Context c) {
        appCtx = c.getApplicationContext();
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        // 存过但为空的项，不要覆盖内置默认值（例如内置的 key）
        String b = sp.getString("base", "");
        String k = sp.getString("key", "");
        String m = sp.getString("model", "");
        if (b.trim().length() > 0) {
            base = b;
        }
        if (k.trim().length() > 0) {
            key = k;
        } else {
            // 设置里没填过 Key：从外部文件读，代码里不存
            loadExternalApi();
        }
        if (m.trim().length() > 0) {
            model = m;
        }
        mode = sp.getString("mode", mode);
        extra = sp.getString("extra", extra);
        board = sp.getBoolean("board", board);
        tts = sp.getBoolean("tts", tts);
        onboarded = sp.getBoolean("onboarded", onboarded);
        ttsRate = sp.getFloat("ttsRate", ttsRate);
        ttsDigest = sp.getBoolean("ttsDigest", ttsDigest);
    }

    public void save(Context c) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit();
        e.putString("base", base);
        e.putString("key", key);
        e.putString("model", model);
        e.putString("mode", mode);
        e.putString("extra", extra);
        e.putBoolean("board", board);
        e.putBoolean("tts", tts);
        e.putBoolean("onboarded", onboarded);
        e.putFloat("ttsRate", ttsRate);
        e.putBoolean("ttsDigest", ttsDigest);
        e.apply();
    }
}