#!/bin/bash
# 豆包老师（自建版）— 无 gradle 最小构建
# javac + dx + aapt + zipalign + apksigner
cd /root/doubaoteacher || exit 1

AJAR=/tmp/android.jar
DXJAR=/tmp/dx.jar
KS=/root/nfcapp/ks.jks

rm -rf bin build
mkdir -p bin build

echo "=== 0) generate R.java ==="
mkdir -p gen
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$AJAR" -J gen -F build/base.apk 2>&1 | head -20
ls gen/R.java >/dev/null 2>&1 || { echo "R_FAILED"; exit 1; }

echo "=== 1) javac ==="
javac -nowarn --release 8 -encoding UTF-8 -classpath "$AJAR:/tmp/xmlpull-stub.jar" -d bin \
    $(find src gen -name '*.java') > /tmp/javac.log 2>&1
JRC=$?
grep -v 'bootstrap class path' /tmp/javac.log | head -40
if [ $JRC -ne 0 ]; then
  echo "JAVAC_FAILED"
  exit 2
fi

echo "=== 2) dx ==="
java -cp "$DXJAR" com.android.dx.command.Main --dex --min-sdk-version 21 \
    --output=build/classes.dex bin 2>&1 | head -20
[ -f build/classes.dex ] || { echo "DX_FAILED"; exit 3; }

echo "=== 3) aapt ==="
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$AJAR" -F build/base.apk 2>&1 | head -20
[ -f build/base.apk ] || { echo "AAPT_FAILED"; exit 4; }

echo "=== 4) add classes.dex ==="
python3 - <<'PY'
import zipfile
z = zipfile.ZipFile('build/base.apk', 'a', zipfile.ZIP_DEFLATED)
z.write('build/classes.dex', 'classes.dex')
z.close()
print('dex added')
PY
[ -f build/base.apk ] || exit 5

echo "=== 5) zipalign ==="
zipalign -f 4 build/base.apk build/aligned.apk 2>&1 | head -5

echo "=== 6) sign ==="
apksigner sign --ks "$KS" --ks-pass pass:operit123 --key-pass pass:operit123 \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out build/doubaoteacher.apk build/aligned.apk 2>&1 | head -10

echo "=== 7) verify ==="
apksigner verify build/doubaoteacher.apk >/dev/null 2>&1 && echo "SIGNATURE_OK"
ls -l build/doubaoteacher.apk
echo "BUILD_DONE"