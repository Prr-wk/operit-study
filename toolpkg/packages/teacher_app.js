/* METADATA
{
    "name": "teacher_app",
    "display_name": { "zh": "肥鱼老师", "en": "Teacher App" },
    "description": {
        "zh": "检测并安装「肥鱼老师」讲题 App。",
        "en": "Detect and install the bundled Teacher App."
    },
    "category": "Utility",
    "enabledByDefault": true,
    "tools": [
        { "name": "teacher_app_status", "description": { "zh": "查看「肥鱼老师」安装状态。", "en": "Check Teacher App install status." }, "parameters": [] },
        { "name": "teacher_app_install", "description": { "zh": "安装或重装「肥鱼老师」。", "en": "Install or reinstall the Teacher App." },
          "parameters": [ { "name": "force", "description": { "zh": "已装时是否强制重装", "en": "Force reinstall when installed." }, "type": "boolean", "required": false } ] },
        { "name": "teacher_app_open", "description": { "zh": "打开「肥鱼老师」。", "en": "Launch the Teacher App." }, "parameters": [] }
    ]
}*/

// 肥鱼老师安装包：核心逻辑（普通模块，无 METADATA）
// 由 packages/teacher_app.js（工具）与 main.js（生命周期钩子）共同 require，
// 避免 main.js 直接 require 子包入口导致重复加载。

const TEACHER_PKG = 'com.operit.doubaoteacher';
const APK_RES_KEY = 'doubaoteacher_apk';
const APK_FILE_NAME = 'doubaoteacher.apk';

/**
 * 是否已安装。
 * 注意 1：ToolPkg 运行时里没有全局 Android 对象（Android.packageManager 是 undefined）。
 * 注意 2：Tools.System.listApps 返回的是「App 名 (包名)」字符串，不是纯包名，
 *        所以主路径用 shell 的 pm list packages 判断，listApps 只做兜底。
 */
async function isInstalled() {
    const byShell = await isInstalledByShell();
    if (byShell === true) return true;
    try {
        const r = await Tools.System.listApps(false);
        const pk = r && (r.packages || (r.data && r.data.packages));
        if (Array.isArray(pk)) {
            for (let i = 0; i < pk.length; i++) {
                const s = String(pk[i]);
                if (s === TEACHER_PKG || s.indexOf('(' + TEACHER_PKG + ')') >= 0) return true;
            }
        }
    } catch (e) { /* 忽略 */ }
    return false;
}

/** pm list packages 判断（最可靠） */
async function isInstalledByShell() {
    try {
        const out = await Tools.System.shell('pm list packages ' + TEACHER_PKG);
        const txt = typeof out === 'string'
            ? out
            : ((out && (out.output || out.details || out.message)) || '');
        return String(txt).indexOf('package:' + TEACHER_PKG) >= 0;
    } catch (e) {
        return false;
    }
}

/** 已装则返回版本名，取不到返回空串 */
async function installedVersion() {
    try {
        const out = await Tools.System.shell(
            'dumpsys package ' + TEACHER_PKG + ' | grep versionName'
        );
        const txt = typeof out === 'string'
            ? out
            : ((out && (out.output || out.details || out.message)) || '');
        const m = String(txt).match(/versionName=([^\s]+)/);
        if (m) return m[1];
    } catch (e) { /* 忽略 */ }
    return '';
}

/** 从插件资源导出内置 APK，返回落盘路径；失败返回空串 */
async function exportApk() {
    try {
        const p = await ToolPkg.readResource(APK_RES_KEY, APK_FILE_NAME);
        return p || '';
    } catch (e) {
        return '';
    }
}

/** 把导出到共享盘的 APK 复制到 /data/local/tmp（pm install 读不了 /sdcard，会报 fuse 上下文错误） */
async function stageApk(srcPath) {
    const dst = '/data/local/tmp/doubaoteacher.apk';
    try {
        const out = await Tools.System.shell(
            "cp -f '" + srcPath + "' " + dst + " && chmod 644 " + dst + " && ls -l " + dst
        );
        const txt = typeof out === 'string'
            ? out
            : ((out && (out.output || out.details || out.message)) || '');
        if (String(txt).indexOf('doubaoteacher.apk') >= 0) return dst;
    } catch (e) { /* 落回原路径 */ }
    return srcPath;
}

/** 确保安装：已装且未 force 则跳过 */
async function ensureInstalled(opt) {
    const o = opt || {};
    const before = await isInstalled();
    if (before && !o.force) {
        return {
            success: true,
            installed: true,
            changed: false,
            message: '肥鱼老师已安装，跳过安装',
            data: { package: TEACHER_PKG, installed: true, version: await installedVersion() }
        };
    }

    const apk = await exportApk();
    if (!apk) {
        return {
            success: false,
            installed: before,
            changed: false,
            message: '内置安装包导出失败：资源 ' + APK_RES_KEY + ' 不可用',
            data: { package: TEACHER_PKG }
        };
    }

    // pm install 读不到 /sdcard，先落到 /data/local/tmp
    const staged = await stageApk(apk);

    let raw = '';
    try {
        const res = await Tools.System.installApp(staged);
        if (res) {
            if (typeof res === 'string') raw = res;
            else if (res.details) raw = String(res.details);
            else if (typeof res.toString === 'function') raw = String(res.toString());
            else raw = JSON.stringify(res);
        }
    } catch (e) {
        return {
            success: false,
            installed: before,
            changed: false,
            message: '安装调用异常：' + (e && e.message ? e.message : e),
            data: { package: TEACHER_PKG, apk: staged }
        };
    }

    const after = await isInstalled();
    const ver = after ? await installedVersion() : '';
    return {
        success: after,
        installed: after,
        changed: true,
        message: after
            ? ('已安装肥鱼老师' + (ver ? '（v' + ver + '）' : ''))
            : ('安装未完成，系统返回：' + (raw || '(无输出)')),
        data: { package: TEACHER_PKG, apk: staged, exported: apk, raw: raw, before: before }
    };
}

async function status() {
    const installed = await isInstalled();
    const apk = await exportApk();
    const ver = installed ? await installedVersion() : '';
    return {
        success: true,
        message: installed
            ? ('肥鱼老师已安装' + (ver ? '（v' + ver + '）' : ''))
            : ('肥鱼老师未安装' + (apk ? '，内置安装包已就绪' : '，但内置安装包不可用')),
        data: {
            package: TEACHER_PKG,
            installed: installed,
            version: ver,
            bundledApk: apk || '',
            bundled: !!apk
        }
    };
}

async function open() {
    const installed = await isInstalled();
    if (!installed) {
        const r = await ensureInstalled({});
        if (!r.success) {
            return { success: false, message: '尚未安装，且自动安装未完成：' + r.message, data: r.data };
        }
    }
    try {
        const res = await Tools.System.startApp(TEACHER_PKG);
        const raw = res ? (res.details || String(res)) : '';
        return { success: true, message: '已启动肥鱼老师', data: { package: TEACHER_PKG, raw: raw } };
    } catch (e) {
        return { success: false, message: '启动失败：' + (e && e.message ? e.message : e), data: { package: TEACHER_PKG } };
    }
}


async function teacher_app_status() {
    return await status();
}

async function teacher_app_install(params) {
    const p = params || {};
    const force = (p.force === true || p.force === 'true');
    return await ensureInstalled({ force: force });
}

async function teacher_app_open() {
    return await open();
}

exports.teacher_app_status = teacher_app_status;
exports.teacher_app_install = teacher_app_install;
exports.teacher_app_open = teacher_app_open;
