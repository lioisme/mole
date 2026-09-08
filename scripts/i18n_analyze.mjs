// V42 i18n 工具:en/zh 对照导出 + ja/ko 文件校验
// 用法: node scripts/i18n_analyze.mjs dump   -> en/zh 全量对照(stdout)
//       node scripts/i18n_analyze.mjs check  -> 校验 values-ja/values-ko(键完备/占位符/同文)
import { readFileSync, existsSync } from 'node:fs';

function parse(path) {
  const xml = readFileSync(path, 'utf8');
  const strings = new Map();
  const plurals = new Map();
  let m;
  const reS = /<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g;
  while ((m = reS.exec(xml)) !== null) strings.set(m[1], m[2]);
  const reP = /<plurals name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g;
  while ((m = reP.exec(xml)) !== null) {
    const items = {};
    const reI = /<item quantity="([^"]+)"[^>]*>([\s\S]*?)<\/item>/g;
    let mi;
    while ((mi = reI.exec(m[2])) !== null) items[mi[1]] = mi[2];
    plurals.set(m[1], items);
  }
  return { strings, plurals };
}

function placeholderSet(text) {
  const out = new Set();
  const re = /%(?:\d+\$)?,?[ds]/g;
  let m;
  while ((m = re.exec(text)) !== null) out.add(m[0]);
  return out;
}

const [, , cmd] = process.argv;
const en = parse('app/src/main/res/values/strings.xml');
const zh = parse('app/src/main/res/values-zh/strings.xml');

if (cmd === 'dump') {
  for (const [k, v] of en.strings) {
    const zv = zh.strings.get(k) ?? '<MISSING>';
    const tag = v === zv ? 'SAME' : 'DIFF';
    console.log(`${tag}\t${k}\t${v}\t${zv}`);
  }
  for (const [k, items] of en.plurals) {
    console.log(`PLURAL\t${k}\t${JSON.stringify(items)}\t${JSON.stringify(zh.plurals.get(k) ?? '<MISSING>')}`);
  }
}

if (cmd === 'check') {
  let failed = 0;
  for (const locale of ['ja', 'ko']) {
    const path = `app/src/main/res/values-${locale}/strings.xml`;
    if (!existsSync(path)) { console.log(`${locale}: FILE MISSING`); failed++; continue; }
    const loc = parse(path);
    // 键完备
    const missing = [...en.strings.keys()].filter(k => !loc.strings.has(k));
    const extra = [...loc.strings.keys()].filter(k => !en.strings.has(k));
    const missingP = [...en.plurals.keys()].filter(k => !loc.plurals.has(k));
    if (missing.length) { console.log(`${locale} MISSING STRINGS: ${missing}`); failed++; }
    if (extra.length) { console.log(`${locale} EXTRA STRINGS: ${extra}`); failed++; }
    if (missingP.length) { console.log(`${locale} MISSING PLURALS: ${missingP}`); failed++; }
    // 占位符一致
    for (const [k, v] of en.strings) {
      const lv = loc.strings.get(k);
      if (lv === undefined) continue;
      const a = [...placeholderSet(v)].sort().join('|');
      const b = [...placeholderSet(lv)].sort().join('|');
      if (a !== b) { console.log(`${locale} PLACEHOLDER MISMATCH ${k}: en=[${a}] ${locale}=[${b}]`); failed++; }
    }
    // plurals other 量项占位符
    for (const [k, items] of en.plurals) {
      const li = loc.plurals.get(k);
      if (!li) continue;
      const enOther = items.other ?? Object.values(items)[0];
      const locOther = li.other;
      if (!locOther) { console.log(`${locale} PLURAL ${k}: no 'other' item`); failed++; continue; }
      const a = [...placeholderSet(enOther)].sort().join('|');
      const b = [...placeholderSet(locOther)].sort().join('|');
      if (a !== b) { console.log(`${locale} PLURAL PLACEHOLDER MISMATCH ${k}: en=[${a}] ${locale}=[${b}]`); failed++; }
    }
    console.log(`${locale}: strings=${loc.strings.size} plurals=${loc.plurals.size} failed=${failed}`);
  }
  process.exit(failed ? 1 : 0);
}
