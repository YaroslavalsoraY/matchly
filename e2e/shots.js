const { chromium } = require('playwright');
const BASE = 'http://localhost:8080';
const OUT = process.argv[2] || (__dirname + '/clean/');

async function login(page, email, password) {
  await page.goto(BASE + '/#/login');
  await page.waitForSelector('#login-form');
  await page.fill('#login-form [name=email]', email);
  await page.fill('#login-form [name=password]', password);
  await page.click('#login-form button[type=submit]');
}
const settle = (page) => page.waitForTimeout(600);

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 860 }, locale: 'ru-RU' });
  const page = await ctx.newPage();
  const shot = async (name) => { await settle(page); await page.screenshot({ path: OUT + name + '.png', fullPage: true }); console.log('shot', name); };

  // 1. страница входа
  await page.goto(BASE + '/');
  await page.waitForSelector('#login-form');
  await shot('01-login');

  // 2. время жизни уведомления: ошибка входа -> toast -> через 4.5 с исчезает
  await page.fill('#login-form [name=email]', 'nobody@matchly.local');
  await page.fill('#login-form [name=password]', 'wrong-password');
  await page.click('#login-form button[type=submit]');
  await page.waitForSelector('.toast--error');
  const text = await page.textContent('.toast--error');
  await page.waitForTimeout(4500);
  const remaining = await page.$$eval('.toast', (els) => els.length);
  if (remaining !== 0) throw new Error('уведомление не исчезло за 4.5 с, осталось ' + remaining);
  console.log('toast lifetime OK:', text.trim());

  // 3. лента демо-пользователя
  await login(page, 'demo3@matchly.local', 'demo1234');
  await page.waitForSelector('.deck-card');
  await page.waitForSelector('#card-photo img');
  await page.waitForTimeout(3800);   // ждём, пока уйдёт «С возвращением!»
  await shot('02-discover');

  // 4. матчи: ищем демо-пользователя, у которого они есть
  const admin = await (await ctx.request.post(BASE + '/api/auth/login', { data: { email: 'admin@matchly.local', password: 'admin123' } })).json();
  let withMatches = null;
  for (let n = 1; n <= 30 && !withMatches; n++) {
    const res = await ctx.request.post(BASE + '/api/auth/login', { data: { email: `demo${n}@matchly.local`, password: 'demo1234' } });
    if (!res.ok()) continue;
    const token = (await res.json()).token;
    const matches = await (await ctx.request.get(BASE + '/api/matches', { headers: { Authorization: 'Bearer ' + token } })).json();
    if (matches.length >= 2) withMatches = `demo${n}@matchly.local`;
  }
  if (withMatches) {
    await page.click('#logout');
    await page.waitForSelector('#login-form');
    await login(page, withMatches, 'demo1234');
    await page.waitForSelector('.deck-card, .empty');
    await page.click('a[href="#/matches"]');
    await page.waitForSelector('.match-card');
    await page.waitForTimeout(3800);
    await shot('03-matches');
    console.log('matches shot for', withMatches);
  }

  // 5. анкета
  await page.click('a[href="#/profile"]');
  await page.waitForSelector('#profile-form');
  await page.waitForSelector('#photo-preview img');
  await shot('04-profile');

  // 6. администратор
  await page.click('#logout');
  await page.waitForSelector('#login-form');
  await login(page, 'admin@matchly.local', 'admin123');
  await page.waitForSelector('#profile-form');
  await page.waitForTimeout(3800);
  await page.click('a[href="#/admin"]');
  await page.waitForSelector('.tiles .tile');
  await shot('05-admin-stats');
  await page.click('.tab[data-tab=users]');
  await page.waitForSelector('tbody tr');
  await shot('06-admin-users');
  await page.click('.tab[data-tab=interests]');
  await page.waitForSelector('#interest-add');
  await shot('07-admin-interests');
  await page.click('.tab[data-tab=settings]');
  await page.waitForSelector('input[name=default-strategy]', { state: 'attached' });
  await shot('08-admin-settings');

  // 7. Swagger UI
  await page.goto(BASE + '/swagger-ui/index.html');
  await page.waitForSelector('.opblock-tag', { timeout: 20000 });
  await page.waitForTimeout(800);
  await page.screenshot({ path: OUT + '09-swagger.png', fullPage: false });
  console.log('shot 09-swagger');

  // 8. мобильная лента
  const mobile = await browser.newContext({ viewport: { width: 400, height: 820 }, locale: 'ru-RU', deviceScaleFactor: 2 });
  const mp = await mobile.newPage();
  await login(mp, 'demo5@matchly.local', 'demo1234');
  await mp.waitForSelector('.deck-card');
  await mp.waitForSelector('#card-photo img');
  await mp.waitForTimeout(3800);
  await mp.screenshot({ path: OUT + '10-mobile.png', fullPage: false });
  console.log('shot 10-mobile');

  await browser.close();
  console.log('SHOTS DONE');
})().catch((e) => { console.error('SHOTS FAILED:', e.message); process.exit(1); });
