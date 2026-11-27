const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const root = path.resolve(__dirname, '..', '..');
const shipped = require(path.join(root, 'app/src/main/assets/extensions/semo/psl.js'));

function check(api, cases) {
  for (const k in cases) assert.strictEqual(api.registrable(k), cases[k], k);
}

check(shipped, {
  'www.example.co.uk': 'example.co.uk',
  'a.b.example.com': 'example.com',
  'shop.example.com.sa': 'example.com.sa',
  'x.example.com.eg': 'example.com.eg',
  'user.github.io': 'user.github.io',
  'a.user.github.io': 'user.github.io',
  'co.uk': 'co.uk',
  'localhost': 'localhost',
  '10.0.0.1': '10.0.0.1',
  'example.org': 'example.org',
  'a.b.ck': 'a.b.ck',
  'www.ck': 'www.ck',
  'sub.www.ck': 'www.ck',
  'tracker.example.co.jp': 'example.co.jp',
  'WWW.Example.CO.UK.': 'example.co.uk',
  '': '',
  '[::1]': '[::1]',
  'fe80::1': 'fe80::1',
  '192.168.1.20': '192.168.1.20',
  'shop.example.com.br': 'example.com.br',
  'a.b.example.co.in': 'example.co.in',
  'x.example.com.cn': 'example.com.cn',
  'm.example.co.za': 'example.co.za',
  'www.example.co.kr': 'example.co.kr',
  'www.example.com.tr': 'example.com.tr',
  'a.example.com.mx': 'example.com.mx',
  'www.example.co.nz': 'example.co.nz',
  'www.example.com.sg': 'example.com.sg',
  'www.example.com.pk': 'example.com.pk',
  'www.example.com.ng': 'example.com.ng',
  'www.example.com.ua': 'example.com.ua',
  'www.example.com.ar': 'example.com.ar',
  'www.example.gov.au': 'example.gov.au',
  'x.y.example.edu.au': 'example.edu.au',
  'mysite.blogspot.com': 'mysite.blogspot.com',
  'a.mysite.blogspot.com': 'mysite.blogspot.com',
  'app.herokuapp.com': 'app.herokuapp.com',
  'x.app.herokuapp.com': 'app.herokuapp.com',
  'proj.appspot.com': 'proj.appspot.com',
  'site.pages.dev': 'site.pages.dev',
  'w.site.workers.dev': 'site.workers.dev',
  'app.vercel.app': 'app.vercel.app',
  'app.netlify.app': 'app.netlify.app',
  'app.web.app': 'app.web.app',
  'app.firebaseapp.com': 'app.firebaseapp.com',
  'bucket.s3.amazonaws.com': 'bucket.s3.amazonaws.com',
  'd111.cloudfront.net': 'd111.cloudfront.net',
  'single': 'single',
  'example': 'example',
});

assert.notStrictEqual(shipped.registrable('a.co.uk'), shipped.registrable('b.co.uk'));
assert.notStrictEqual(shipped.registrable('a.github.io'), shipped.registrable('b.github.io'));
assert.strictEqual(shipped.registrable('a.example.com'), shipped.registrable('b.example.com'));

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'psl-'));
const out = path.join(tmp, 'psl.js');
const run = spawnSync('python3', [
  path.join(root, 'tools/update_psl.py'), '--input', path.join(__dirname, 'psl_fixture.dat'), '--output', out
], { encoding: 'utf8' });
assert.strictEqual(run.status, 0, run.stderr);
const generated = require(out);

check(generated, {
  'a.b.example.co.uk': 'example.co.uk',
  'example.com': 'example.com',
  'www.example.org.uk': 'example.org.uk',
  'x.y.example.com.au': 'example.com.au',
  'x.example.co.jp': 'example.co.jp',
  'a.b.kobe.jp': 'a.b.kobe.jp',
  'b.kobe.jp': 'b.kobe.jp',
  'c.city.kobe.jp': 'city.kobe.jp',
  'sub.city.kobe.jp': 'city.kobe.jp',
  'a.b.ck': 'a.b.ck',
  'www.ck': 'www.ck',
  'a.www.ck': 'www.ck',
  'x.user.github.io': 'user.github.io',
  'user.github.io': 'user.github.io',
  'github.io': 'github.io',
  'my.blogspot.com': 'my.blogspot.com',
  'x.foo.compute.amazonaws.com': 'x.foo.compute.amazonaws.com',
  'foo.compute.amazonaws.com': 'foo.compute.amazonaws.com',
  'www.xn--fiqs8s': 'www.xn--fiqs8s',
  'm.xn--55qx5d.cn': 'm.xn--55qx5d.cn',
  'a.m.xn--55qx5d.cn': 'm.xn--55qx5d.cn',
  'x.xn--bcher-kva.example': 'x.xn--bcher-kva.example',
  'shop.example.com.cn': 'example.com.cn',
  'unknowntld': 'unknowntld',
  'foo.unknowntld': 'foo.unknowntld',
});
assert.ok(fs.readFileSync(out, 'utf8').includes('xn--55qx5d.cn'));

fs.rmSync(tmp, { recursive: true, force: true });
console.log('psl ok');
