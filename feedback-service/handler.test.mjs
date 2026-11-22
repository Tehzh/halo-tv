import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import worker, { handle } from './handler.mjs';

const admin = 'a'.repeat(64);
const id = '11111111-1111-1111-1111-111111111111';
const base = 'https://halo-tv.pages.dev';
const issues = 'https://github.com/Null9797/halo-tv/issues/new?template=bug_report.yml';
const req = (path, method = 'GET', key) => new Request(base + path, {
 method, headers: key ? { Authorization: 'Bearer ' + key } : {},
});

async function fixture() {
 const db = new DatabaseSync(':memory:');
 db.exec(readFileSync(new URL('./schema.sql',import.meta.url),'utf8'));
 db.prepare(`INSERT INTO reports
   (id,proof,created,updated,category,edition,version,code,model,android,diagnostics,rate_key)
   VALUES (?, 'proof', ?, ?, 'guard', 'guard', '3.3.5', 250, 'Test TV', '11', 'private report', 'test')`)
   .run(id, Date.now(), Date.now());
 const env = {
  FEEDBACK_ADMIN_HASH: Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(admin))).toString('hex'),
  FEEDBACK_DB: { prepare(sql) {
   assert.match(sql, /^SELECT /);
   return { bind(...args) {
    const statement = db.prepare(sql);
    return { first: () => statement.get(...args), all: () => ({ results: statement.all(...args) }) };
   } };
  } },
 };
 return {db,env};
}

test('legacy submission, receipt and supplement are gone without database access', async () => {
 const env = { get FEEDBACK_DB() { throw Error('legacy endpoint touched storage'); }};
 for (const [path, method] of [['/api/feedback','POST'],['/api/feedback/'+id,'POST'],['/api/feedback/'+id,'GET']]) {
  const response = await handle(req(path,method), env);
  assert.equal(response.status,410);
  assert.equal(response.headers.get('Cache-Control'),'no-store');
  assert.deepEqual(await response.json(),{ error:'feedback_moved',url:issues });
 }
});

test('admin archive requires the existing key and serves private data only to that key', async () => {
 const {db,env} = await fixture();
 try {
  assert.equal((await handle(req('/api/feedback/admin'),env)).status,401);
  assert.equal((await handle(req('/api/feedback/admin/'+id,'GET','b'.repeat(64)),env)).status,401);
  const list = await handle(req('/api/feedback/admin?edition=guard','GET',admin),env);
  assert.equal(list.status,200);
  assert.equal((await list.json()).items[0].id,id);
  const detail = await handle(req('/api/feedback/admin/'+id,'GET',admin),env);
  assert.equal((await detail.json()).diagnostics,'private report');
 } finally { db.close(); }
});

test('historical reports cannot be changed and unrelated requests still go to Pages assets', async () => {
 const {db,env} = await fixture();
 try {
  assert.equal((await handle(req('/api/feedback/admin/'+id,'POST',admin),env)).status,405);
  assert.equal((await handle(req('/api/feedback/admin','GET',admin),{})).status,503);
  env.ASSETS = {fetch: (request) => new Response(new URL(request.url).pathname)};
  const response = await worker.fetch(req('/announcements.json'),env);
  assert.equal(await response.text(),'/announcements.json');
  assert.equal(db.prepare('SELECT count(*) AS total FROM reports').get().total,1);
 } finally { db.close(); }
});
