/* METADATA
{
    "name": "study_profile",
    "display_name": {
        "zh": "学习档案",
        "en": "Study Profile"
    },
    "description": {
        "zh": "学习辅助引擎：维护学习档案（学科+知识点+权重，权重随时间衰减），支持记录错题、更新掌握度、按薄弱点加权选题，并导出/导入档案 JSON。",
        "en": "Study-assistant engine: maintains a learning profile (subject + knowledge point + weight with time decay); records mistakes, updates mastery, picks questions by weakness, and imports/exports the profile JSON."
    },
    "category": "Utility",
    "enabledByDefault": true,
    "tools": [
        {
            "name": "profile_get",
            "description": { "zh": "查看学习档案：按有效权重从高到低列出学科/知识点（有效权重越高越薄弱）。", "en": "View the learning profile sorted by effective weight (higher = weaker)." },
            "parameters": [
                { "name": "subject", "description": { "zh": "可选：只查看某学科", "en": "Optional: filter by subject" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "record_mistake",
            "description": { "zh": "记录错题：提高某知识点的权重。用于批改作业/上传已改试卷登记错题时更新档案。", "en": "Record a mistake: raise a knowledge point's weight." },
            "parameters": [
                { "name": "subject", "description": { "zh": "学科，如 数学", "en": "Subject, e.g. Math" }, "type": "string", "required": true },
                { "name": "point", "description": { "zh": "知识点关键词，如 一元二次方程", "en": "Knowledge point keyword" }, "type": "string", "required": true },
                { "name": "delta", "description": { "zh": "可选：权重增量，默认 2", "en": "Optional: weight delta, default 2" }, "type": "number", "required": false },
                { "name": "reason", "description": { "zh": "可选：错因说明", "en": "Optional: reason" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "update_mastery",
            "description": { "zh": "更新掌握度：掌握良好时降低某知识点权重（可为负 delta）。", "en": "Update mastery: lower a knowledge point's weight when mastered." },
            "parameters": [
                { "name": "subject", "description": { "zh": "学科", "en": "Subject" }, "type": "string", "required": true },
                { "name": "point", "description": { "zh": "知识点关键词", "en": "Knowledge point" }, "type": "string", "required": true },
                { "name": "delta", "description": { "zh": "可选：权重增量，默认 -1（负数降权）", "en": "Optional: delta, default -1" }, "type": "number", "required": false },
                { "name": "reason", "description": { "zh": "可选：说明", "en": "Optional: reason" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "apply_decay",
            "description": { "zh": "对各知识点应用时间衰减并落盘（把衰减正式结算进权重）。", "en": "Apply time decay to all knowledge points and persist." },
            "parameters": []
        },
        {
            "name": "profile_export",
            "description": { "zh": "导出学习档案 JSON（返回内容和文件路径）。", "en": "Export the profile JSON." },
            "parameters": []
        },
        {
            "name": "profile_import",
            "description": { "zh": "用一段 JSON 覆盖导入学习档案。", "en": "Import/overwrite the profile from JSON." },
            "parameters": [
                { "name": "json", "description": { "zh": "档案 JSON 文本", "en": "Profile JSON text" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "profile_reset",
            "description": { "zh": "清空学习档案（谨慎）。", "en": "Reset the learning profile." },
            "parameters": []
        }
    ]
}*/

const StudyProfile = (function () {
    const DIR = '/sdcard/Download/Operit/study_assistant';
    const PROFILE = DIR + '/profile.json';
    const HALF_LIFE_DAYS = 7;                 // 权重衰减半衰期（天）
    const LAMBDA = Math.LN2 / HALF_LIFE_DAYS;
    const DAY = 86400000;
    const now = () => Date.now();

    function emptyProfile() {
        return { version: 1, halfLifeDays: HALF_LIFE_DAYS, points: {}, log: [], updated: now() };
    }

    function extractText(res) {
        if (res == null) return null;
        if (typeof res === 'string') return res;
        return res.content ?? res.text ?? res.data ?? null;
    }

    async function load() {
        try {
            const ex = await Tools.Files.exists(PROFILE);
            if (!ex || !(ex.exists ?? true)) return emptyProfile();
            const r = await Tools.Files.read(PROFILE);
            const txt = extractText(r);
            if (!txt) return emptyProfile();
            const p = JSON.parse(txt);
            if (!p.points) p.points = {};
            if (!p.log) p.log = [];
            return p;
        } catch (e) {
            return emptyProfile();
        }
    }

    async function save(p) {
        p.updated = now();
        try {
            await Tools.Files.mkdir(DIR, true);
        } catch (e) { /* 目录可能已存在，忽略 */ }
        await Tools.Files.write(PROFILE, JSON.stringify(p, null, 2));
    }

    const key = (subject, point) => subject + '::' + point;
    // 有效权重 = 基础权重 × 时间衰减因子
    const eff = (p) => p.weight * Math.exp(-LAMBDA * ((now() - p.updated) / DAY));

    function getPoint(p, subject, point) {
        const k = key(subject, point);
        if (!p.points[k]) {
            p.points[k] = { subject, point, weight: 1, updated: now(), hits: 0, errors: 0 };
        }
        return p.points[k];
    }

    async function bump(subject, point, delta, reason) {
        const p = await load();
        const pt = getPoint(p, subject, point);
        // 先把历史衰减结算进 weight，再加增量
        pt.weight = Math.max(0.1, eff(pt) + delta);
        pt.updated = now();
        // 计数只按 delta 正负判定：errors = 记错/加权次数，hits = 掌握/降权次数。
        // 旧实现用正则匹配 reason 里是否含“错”字，措辞一变统计就错，已废弃。
        if (delta > 0) pt.errors++;
        else pt.hits++;
        p.log.push({ ts: now(), subject, point, delta, reason: reason || '' });
        if (p.log.length > 2000) p.log = p.log.slice(-2000);
        await save(p);
        return { subject, point, weight: +pt.weight.toFixed(2), effective: +eff(pt).toFixed(2), errors: pt.errors, hits: pt.hits };
    }

    async function profile_get(params) {
        const p = await load();
        let arr = Object.values(p.points);
        if (params && params.subject) arr = arr.filter(x => x.subject === params.subject);
        arr = arr
            .map(x => ({ subject: x.subject, point: x.point, weight: +x.weight.toFixed(2), effective: +eff(x).toFixed(2), errors: x.errors, hits: x.hits }))
            .sort((a, b) => b.effective - a.effective);
        const weak = arr.slice(0, 5).map(x => x.subject + '·' + x.point + '(权' + x.effective + ')');
        return {
            success: true,
            message: arr.length ? ('共 ' + arr.length + ' 个知识点，最薄弱：' + weak.join('，')) : '学习档案为空',
            data: { count: arr.length, points: arr, profileFile: PROFILE }
        };
    }

    async function record_mistake(params) {
        const delta = (params.delta === undefined || params.delta === null) ? 2 : Number(params.delta);
        const d = await bump(params.subject, params.point, delta, params.reason || '错题');
        return { success: true, message: '已记录错题：' + params.subject + ' · ' + params.point + '（+' + delta + '）', data: d };
    }

    async function update_mastery(params) {
        const delta = (params.delta === undefined || params.delta === null) ? -1 : Number(params.delta);
        const d = await bump(params.subject, params.point, delta, params.reason || '已掌握');
        return { success: true, message: '已更新掌握度：' + params.subject + ' · ' + params.point + '（' + delta + '）', data: d };
    }


    async function apply_decay() {
        const p = await load();
        let n = 0;
        for (const k of Object.keys(p.points)) {
            const pt = p.points[k];
            pt.weight = Math.max(0.1, eff(pt));
            pt.updated = now();
            n++;
        }
        await save(p);
        return { success: true, message: '已对 ' + n + ' 个知识点应用时间衰减', data: { count: n } };
    }

    async function profile_export() {
        const p = await load();
        return { success: true, message: '已导出学习档案', data: { json: JSON.stringify(p, null, 2), file: PROFILE } };
    }

    async function profile_import(params) {
        const obj = JSON.parse(params.json);
        if (!obj.points) obj.points = {};
        if (!obj.log) obj.log = [];
        await save(obj);
        return { success: true, message: '已导入学习档案', data: { count: Object.keys(obj.points).length } };
    }

    async function profile_reset() {
        await save(emptyProfile());
        return { success: true, message: '已清空学习档案', data: {} };
    }

    return { profile_get, record_mistake, update_mastery, apply_decay, profile_export, profile_import, profile_reset };
})();

exports.profile_get = StudyProfile.profile_get;
exports.record_mistake = StudyProfile.record_mistake;
exports.update_mastery = StudyProfile.update_mastery;
exports.apply_decay = StudyProfile.apply_decay;
exports.profile_export = StudyProfile.profile_export;
exports.profile_import = StudyProfile.profile_import;
exports.profile_reset = StudyProfile.profile_reset;