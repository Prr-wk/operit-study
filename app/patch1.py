import re

p = '/root/doubaoteacher/src/com/operit/doubaoteacher/MainActivity.java'
src = open(p, encoding='utf-8').read()
lines = src.split('\n')


def find_line(pred, start=0):
    for i in range(start, len(lines)):
        if pred(lines[i]):
            return i
    return -1


def func_end(start):
    """从方法声明的行开始，按大括号配平找到方法最后一行"""
    depth = 0
    begun = False
    for i in range(start, len(lines)):
        for ch in lines[i]:
            if ch == '{':
                depth += 1
                begun = True
            elif ch == '}':
                depth -= 1
        if begun and depth <= 0:
            return i
    return start


def replace_func(sig_pred, new_body):
    global lines
    s = find_line(sig_pred)
    if s < 0:
        print('NOT FOUND', sig_pred)
        return
    e = func_end(s)
    lines = lines[:s] + new_body.split('\n') + lines[e + 1:]
    print('replaced at', s, 'to', e)


def delete_func(sig_pred):
    global lines
    s = find_line(sig_pred)
    if s < 0:
        print('NOT FOUND', sig_pred)
        return
    e = func_end(s)
    # 连同前面的 @Override 行一起删
    if s > 0 and lines[s - 1].strip() == '@Override':
        s -= 1
    lines = lines[:s] + lines[e + 1:]
    print('deleted', s, 'to', e)


replace_func(lambda l: 'private void styleStatusBar()' in l,
             '    private void styleStatusBar() {\n'
             '        // 用较老的 android.jar 编译，系统栏颜色交给主题，不单独设置\n'
             '    }')

replace_func(lambda l: 'private void showPickDialog()' in l,
             '    private void showPickDialog() {\n'
             '        pickFromGallery();\n'
             '    }')

delete_func(lambda l: 'private void takePhoto()' in l)
delete_func(lambda l: 'public void onRequestPermissionsResult' in l)

out = '\n'.join(lines)
open(p, 'w', encoding='utf-8').write(out)
print('written', len(out))