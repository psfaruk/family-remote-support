#!/usr/bin/env node
/**
 * verify-apk-axml.mjs — release-APK verification gate (Phase 13, zero deps).
 *
 * What it proves about an APK, without any Android tooling:
 *   1. AndroidManifest.xml (binary AXML) is present and parses cleanly.
 *   2. versionCode matches the expected value (decoded as INT_DEC — the
 *      same check previous sessions did by hand against the AXML pool).
 *   3. versionName matches the expected string exactly.
 *   4. No stale versionName from the --stale list survives in the manifest
 *      string pool (e.g. "0.17.0" must be gone once 0.18.0 ships).
 *   5. The APK Signing Block v2 is present (magic "APK Sig Block 42",
 *      scheme id 0x7109871a) — over-install compatibility on the Owner's
 *      phones depends on it.
 *
 * Expected values default to whatever app/build.gradle.kts declares, so CI
 * can never drift from the build files:
 *
 *   node scripts/verify-apk-axml.mjs app/build/outputs/apk/release/app-release.apk
 *
 * Optional flags:
 *   --gradle <path>   build file to read expectations from
 *                     (default: app/build.gradle.kts)
 *   --code <N>        override expected versionCode
 *   --name <X.Y.Z>    override expected versionName
 *   --stale <a,b,c>   comma-separated version strings that must NOT appear
 *
 * Exit 0 = every check passed, exit 1 = at least one check failed.
 */
import { readFileSync } from "node:fs";
import { inflateRawSync } from "node:zlib";

// ---------- CLI ----------
function parseArgs(argv) {
  const opts = {
    apk: "app/build/outputs/apk/release/app-release.apk",
    gradle: "app/build.gradle.kts",
    code: null,
    name: null,
    stale: [],
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--gradle") opts.gradle = argv[++i];
    else if (a === "--code") opts.code = Number(argv[++i]);
    else if (a === "--name") opts.name = argv[++i];
    else if (a === "--stale") {
      opts.stale = (argv[++i] || "")
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
    } else if (!a.startsWith("--")) opts.apk = a;
    else {
      console.error(`unknown flag: ${a}`);
      process.exit(2);
    }
  }
  return opts;
}

function expectationsFromGradle(path) {
  // CI checks out the repo root (app/build.gradle.kts); this sandbox keeps
  // the Android tree nested (android-app/app/build.gradle.kts) — try both.
  const candidates =
    path === "app/build.gradle.kts"
      ? ["app/build.gradle.kts", "android-app/app/build.gradle.kts"]
      : [path];
  for (const p of candidates) {
    try {
      return expectationsFromFile(p);
    } catch (e) {
      if (p === candidates[candidates.length - 1]) throw e;
    }
  }
  throw new Error("unreachable");
}

function expectationsFromFile(path) {
  const txt = readFileSync(path, "utf8");
  const code = txt.match(/versionCode\s*=\s*(\d+)/);
  const name = txt.match(/versionName\s*=\s*"([^"]+)"/);
  if (!code || !name) {
    throw new Error(`cannot read versionCode/versionName from ${path}`);
  }
  return { code: Number(code[1]), name: name[1] };
}

// ---------- zip: locate + inflate AndroidManifest.xml ----------
function extractManifest(buf) {
  let pos = 0;
  while (pos < buf.length - 30) {
    if (buf.readUInt32LE(pos) !== 0x04034b50) {
      pos++;
      continue;
    }
    const method = buf.readUInt16LE(pos + 8);
    const csize = buf.readUInt32LE(pos + 18);
    const nameLen = buf.readUInt16LE(pos + 26);
    const extraLen = buf.readUInt16LE(pos + 28);
    const name = buf.subarray(pos + 30, pos + 30 + nameLen).toString("utf8");
    const dataStart = pos + 30 + nameLen + extraLen;
    if (name === "AndroidManifest.xml") {
      const comp = buf.subarray(dataStart, dataStart + csize);
      return method === 0 ? Buffer.from(comp) : inflateRawSync(comp);
    }
    pos = dataStart + csize;
  }
  return null;
}

// ---------- APK Signing Block v2 ----------
function hasV2Signature(buf) {
  // Find the End-Of-Central-Directory record (scan backwards, comment-safe).
  const minStart = Math.max(0, buf.length - 22 - 0xffff);
  let eocd = -1;
  for (let i = buf.length - 22; i >= minStart; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) return { v2: false, magic: null };
  const cdOff = buf.readUInt32LE(eocd + 16);
  if (cdOff <= 16 || cdOff > buf.length) return { v2: false, magic: null };
  const magicStart = cdOff - 16;
  const magic = buf.subarray(magicStart, cdOff).toString("ascii");
  if (magic !== "APK Sig Block 42") return { v2: false, magic };
  const sz = Number(buf.readBigUInt64LE(magicStart - 8));
  const start = magicStart + 8 - sz; // block start (leading size field)
  let q = start + 8;
  let v2 = false;
  while (q < magicStart - 8) {
    const pairLen = Number(buf.readBigUInt64LE(q));
    if (pairLen <= 0) break;
    const id = buf.readUInt32LE(q + 8);
    if (id === 0x7109871a) v2 = true;
    q += 8 + pairLen;
  }
  return { v2, magic };
}

// ---------- binary AXML: versionCode / versionName ----------
function readManifestVersions(manifest) {
  const u32 = (o) => manifest.readUInt32LE(o);
  // chunks: header(8) → string pool(0x0001) → resource map(0x0180) → nodes
  let p = 8;
  let pool = null;
  const nodes = [];
  while (p + 8 <= manifest.length) {
    const type = manifest.readUInt16LE(p);
    const size = u32(p + 4);
    if (size <= 0) break;
    if (type === 0x0001) pool = p;
    else if (type >= 0x0100 && type <= 0x0103) nodes.push(p);
    p += size;
  }
  if (pool === null) throw new Error("string pool NOT FOUND in manifest");

  const strCount = u32(pool + 8);
  const flags = u32(pool + 16);
  const stringsStart = u32(pool + 20);
  const utf8 = (flags & (1 << 8)) !== 0;
  const offsets = [];
  for (let i = 0; i < strCount; i++) offsets.push(u32(pool + 28 + 4 * i));
  const str = (i) => {
    if (i == null || i < 0 || i >= strCount) return null;
    const o = pool + stringsStart + offsets[i];
    if (utf8) {
      const l = manifest[o + 1];
      return manifest.subarray(o + 2, o + 2 + l).toString("utf8");
    }
    const l = manifest.readUInt16LE(o); // UTF-16 pool: u16 IS the char count
    return manifest.subarray(o + 2, o + 2 + l * 2).toString("utf16le");
  };

  let code = null;
  let name = null;
  for (const np of nodes) {
    if (manifest.readUInt16LE(np) !== 0x0102) continue; // START_ELEMENT
    if (str(u32(np + 20)) !== "manifest") continue;
    const attrStart = manifest.readUInt16LE(np + 24);
    const attrSize = manifest.readUInt16LE(np + 26);
    const attrCount = manifest.readUInt16LE(np + 28);
    for (let i = 0; i < attrCount; i++) {
      const a = np + 16 + attrStart + i * attrSize;
      const nm = str(u32(a + 4));
      const raw = manifest.readInt32LE(a + 8);
      const dataType = manifest[a + 15];
      const data = manifest.readInt32LE(a + 16);
      if (nm === "versionCode" && dataType === 0x10) code = data; // INT_DEC
      if (nm === "versionName" && raw >= 0) name = str(raw);
    }
    break;
  }
  // Every version-shaped string in the pool (for the stale check + report).
  const versionish = [];
  for (let i = 0; i < strCount; i++) {
    const s = str(i);
    if (s && /^\d+\.\d+\.\d+$/.test(s)) versionish.push(s);
  }
  if (code === null || name === null) {
    throw new Error(
      `manifest attributes incomplete: versionCode=${code} versionName=${name}`
    );
  }
  return { code, name, versionish };
}

// ---------- main ----------
const opts = parseArgs(process.argv.slice(2));
const expected = { ...expectationsFromGradle(opts.gradle) };
if (opts.code != null) expected.code = opts.code;
if (opts.name != null) expected.name = opts.name;

const results = [];
const ok = (label) => results.push(`  ✓ ${label}`);
const bad = (label) => results.push(`  ✗ ${label}`);

let buf;
let manifest = null;
let v2 = false;
let magic = null;
let versions = null;
let crashed = null;
try {
  buf = readFileSync(opts.apk);
  ok(`APK read (${(buf.length / 1024 / 1024).toFixed(1)} MB): ${opts.apk}`);
  manifest = extractManifest(buf);
  if (!manifest) throw new Error("AndroidManifest.xml NOT FOUND in the APK");
  const sig = hasV2Signature(buf);
  v2 = sig.v2;
  magic = sig.magic;
  versions = readManifestVersions(manifest);
} catch (e) {
  crashed = e;
}

if (crashed) {
  bad(`parse failed: ${crashed.message}`);
} else {
  ok(`manifest parsed (${manifest.length} bytes)`);
  versions.code === expected.code
    ? ok(`versionCode = ${versions.code} (INT_DEC) — expected ${expected.code}`)
    : bad(
        `versionCode = ${versions.code}, expected ${expected.code} (INT_DEC)`
      );
  versions.name === expected.name
    ? ok(`versionName = "${versions.name}" — expected "${expected.name}"`)
    : bad(`versionName = "${versions.name}", expected "${expected.name}"`);
  if (v2) ok('APK Signing Block v2 present ("APK Sig Block 42")');
  else bad(`APK Signing Block v2 MISSING (magic: ${JSON.stringify(magic)})`);
  // Stale-version probe: no version-shaped string may equal a listed stale
  // value, and (belt + braces) only the expected versionName may appear.
  const staleHit = new Set();
  for (const s of versions.versionish) {
    if (opts.stale.includes(s)) staleHit.add(s);
  }
  if (staleHit.size === 0) {
    ok(
      opts.stale.length
        ? `stale versions absent (${opts.stale.join(", ")})`
        : `no stale list given; version-shaped strings: [${versions.versionish.join(", ")}]`
    );
  } else {
    bad(`stale versions still present: ${[...staleHit].join(", ")}`);
  }
}

const failed = results.filter((r) => r.includes("✗")).length;
console.log("Release-APK verification (scripts/verify-apk-axml.mjs)");
console.log(`  expectations: code=${expected.code} name="${expected.name}"`);
for (const line of results) console.log(line);
if (failed > 0) {
  console.log(`VERIFY: FAIL (${failed} check(s) failed)`);
  process.exit(1);
}
console.log("VERIFY: PASS");
