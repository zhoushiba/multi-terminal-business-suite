#!/usr/bin/env node
/**
 * 生成与后端 PasswordHasher 完全一致的密码散列串，供 db/h2/data.sql 与 db/data.sql 的种子数据使用。
 *
 * 后端实现（backend/src/main/java/com/painuo/suite/security/PasswordHasher.java）：
 *   PBKDF2WithHmacSHA256，12 万次迭代，16 字节随机盐，256 位散列
 *   存储格式 pbkdf2$120000$<saltHex>$<hashHex>
 *
 * 用法：
 *   node scripts/hash-password.mjs admin123
 *   node scripts/hash-password.mjs admin123 editor123 --salt            # 固定盐，便于复现
 */
import { pbkdf2Sync, randomBytes } from 'node:crypto';

const ITERATIONS = 120000;
const KEY_BYTES = 32;
const SALT_BYTES = 16;

const args = process.argv.slice(2);
const fixedSalt = args.includes('--salt');
const passwords = args.filter((a) => !a.startsWith('--'));

if (passwords.length === 0) {
  console.error('用法: node scripts/hash-password.mjs <password> [password...] [--salt]');
  process.exit(1);
}

for (const [index, password] of passwords.entries()) {
  // --salt 时用可复现的盐（密码 + 序号派生），保证每次生成结果一致，方便把种子数据写进仓库
  const salt = fixedSalt
    ? Buffer.from(`painuo-suite-seed-${String(index).padStart(2, '0')}`).subarray(0, SALT_BYTES)
    : randomBytes(SALT_BYTES);
  const hash = pbkdf2Sync(password, salt, ITERATIONS, KEY_BYTES, 'sha256');
  console.log(`${password}\tpbkdf2$${ITERATIONS}$${salt.toString('hex')}$${hash.toString('hex')}`);
}
