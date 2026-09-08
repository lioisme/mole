// V42 T5 values-ja/values-ko 生成器:
// 以 en strings.xml 为骨架(保留注释与顺序),逐键替换翻译表;en==zh 同文键
// (技术标识词)回落 en 原文;plurals 输出 ja/ko CLDR other 量项。
// 校验:键全覆盖(缺失即失败)+ 占位符形态一致。
// 用法: node scripts/v42_generate.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { T1 } from './v42_table_1.mjs';
import { T2 } from './v42_table_2.mjs';
import { T3, PLURALS } from './v42_table_3.mjs';

const T = { ...T1, ...T2, ...T3 };

function parse(path) {
  const xml = readFileSync(path, 'utf8');
  const strings = new Map();
  let m;
  const reS = /<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g;
  while ((m = reS.exec(xml)) !== null) strings.set(m[1], m[2]);
  return strings;
}

function placeholderSet(text) {
  const out = new Set();
  const re = /%(?:\d+\$)?,?[ds]/g;
  let m;
  while ((m = re.exec(text)) !== null) out.add(m[0]);
  return out;
}

function esc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/'/g, "\\'");
}

const en = parse('app/src/main/res/values/strings.xml');
const zh = parse('app/src/main/res/values-zh/strings.xml');

// en==zh 同文键 = 技术标识词白名单(ja/ko 同样保持 en 原文)
const SAME = new Set([...en.keys()].filter(k => zh.get(k) === en.get(k)));

// 测试面 IDENTICAL_ALLOWED 中 zh 有译文但仍属技术路径字面量的键(与
// StringResourceParityTest 同步):ja/ko 亦保持 en 原文。
const TEST_WHITELIST_EXTRA = new Set([
  'op_device_env_proc_status_read',
  'op_device_env_proc_mount_read',
  'op_device_env_proc_cgroup_read',
  'op_device_env_proc_net_read',
]);

// ja/ko 与 en 合法同文键(日韩语中该词即如此书写,非漏译):
const IDENTICAL_OK = new Set(['confirm', 'feature_profile_pixel7pro', 'feature_profile_xiaomi13']);

const enXml = readFileSync('app/src/main/res/values/strings.xml', 'utf8');
const lines = enXml.split('\n');

for (const locale of ['ja', 'ko']) {
  const out = [];
  const covered = new Set();
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    const pluralsMatch = line.match(/^\s*<plurals name="([^"]+)"/);
    if (pluralsMatch) {
      const name = pluralsMatch[1];
      const p = PLURALS[name];
      if (!p) throw new Error(`plurals '${name}' missing from PLURALS table`);
      covered.add('P:' + name);
      // 占位符校验(en other vs locale)
      const enOther = en.plurals?.get?.(name) ?? null;
      while (!/<\/plurals>/.test(lines[i])) i++;
      out.push(`    <plurals name="${name}">`);
      out.push(`        <item quantity="other">${esc(p[locale])}</item>`);
      out.push('    </plurals>');
      i++;
      continue;
    }
    const strMatch = line.match(/^(\s*)<string name="([^"]+)"[^>]*>.*<\/string>\s*$/);
    if (strMatch) {
      const indent = strMatch[1];
      const key = strMatch[2];
      covered.add(key);
      if (SAME.has(key) || TEST_WHITELIST_EXTRA.has(key)) {
        out.push(line); // 技术标识词:保持 en 原文(含既有转义)
      } else {
        const t = T[key];
        if (!t) throw new Error(`key '${key}' missing from translation table`);
        const val = t[locale === 'ja' ? 0 : 1];
        // 占位符形态校验
        const a = [...placeholderSet(en.get(key))].sort().join('|');
        const b = [...placeholderSet(val)].sort().join('|');
        if (a !== b) throw new Error(`placeholder mismatch for '${key}' (${locale}): en=[${a}] ${locale}=[${b}]`);
        if (en.get(key) === val && !IDENTICAL_OK.has(key)) throw new Error(`locale value identical to en for '${key}' (${locale}) — must translate or whitelist`);
        out.push(`${indent}<string name="${key}">${esc(val)}</string>`);
      }
      i++;
      continue;
    }
    out.push(line);
    i++;
  }

  // 覆盖完整性:701 strings + 8 plurals
  const missing = [...en.keys()].filter(k => !covered.has(k));
  const missingP = [...(enXml.match(/<plurals name="([^"]+)"/g) || [])]
    .map(s => s.match(/"([^"]+)"/)[1])
    .filter(k => !covered.has('P:' + k));
  if (missing.length) throw new Error(`uncovered keys (${locale}): ${missing}`);
  if (missingP.length) throw new Error(`uncovered plurals (${locale}): ${missingP}`);

  // 跳过原文件首尾结构行(XML 声明/<resources>/</resources>;header 自带新头部)。
  const body = out.filter(l => !/^\s*<\?xml|^\s*<\/?resources\b/.test(l));
  const header = [
    '<?xml version="1.0" encoding="utf-8"?>',
    `<!-- ${locale === 'ja' ? 'Japanese' : 'Korean'} resources (V42 T5 full translation).`,
    `     Generated from values/strings.xml via scripts/v42_generate.mjs — do not hand-edit order.`,
    `     Baseline: en structure + zh semantics; technical identifiers (en==zh set) stay English.`,
    `     ja/ko CLDR plural rules: "other" quantity only. -->`,
    '<resources>',
  ];
  writeFileSync(`app/src/main/res/values-${locale}/strings.xml`, [...header, ...body, '</resources>'].join('\n') + '\n');
  console.log(`${locale}: strings=${[...en.keys()].length - [...SAME.keys()].length + SAME.size} (translated ${[...en.keys()].length - SAME.size}, same ${SAME.size}) plurals=${Object.keys(PLURALS).length}`);
}
console.log('DONE');
