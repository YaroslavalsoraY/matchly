/**
 * Корнер-кейсы интерфейса Matchly: маршруты, испорченная сессия, валидация, пустые состояния,
 * блокировка во время работы, двойные клики, XSS, админские граничные случаи, мобильная вёрстка.
 * Требует запущенного приложения на BASE и Node.js 18+.
 */
const { chromium } = require('playwright');
const BASE = process.env.MATCHLY_URL || 'http://localhost:8080';
const stamp = Date.now();
const PASSWORD = 'Password1';
const ADMIN = { email: 'admin@matchly.local', password: 'admin123' };
const consoleErrors = [];
const serverErrors = [];
let passed = 0;

async function api(request, method, url, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  const res = await request.fetch(BASE + url, { method, headers, data: body ? JSON.stringify(body) : undefined });
  if (!res.ok()) throw new Error(`${method} ${url} -> ${res.status()} ${await res.text()}`);
  return res.status() === 204 ? null : res.json();
}
const step = (name) => { passed++; console.log('✔ ' + name); };
const assert = (cond, message) => { if (!cond) throw new Error('assert: ' + message); };
async function waitToast(page, fragment) {
  await page.waitForFunction((f) => [...document.querySelectorAll('.toast')].some((t) => t.textContent.includes(f)), fragment, { timeout: 10000 });
}
async function noHorizontalScroll(page, name) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  assert(overflow <= 0, `горизонтальная прокрутка на экране ${name}: ${overflow}px`);
}
async function login(page, email, password) {
  await page.goto(BASE + '/#/login');
  await page.waitForSelector('#login-form');
  await page.fill('#login-form [name=email]', email);
  await page.fill('#login-form [name=password]', password);
  await page.click('#login-form button[type=submit]');
}
async function fillProfile(page, p) {
  await page.fill('[name=displayName]', p.displayName);
  await page.fill('[name=birthDate]', p.birthDate);
  await page.selectOption('[name=gender]', p.gender);
  await page.selectOption('[name=lookingFor]', p.lookingFor);
  await page.fill('[name=ageMin]', String(p.ageMin));
  await page.fill('[name=ageMax]', String(p.ageMax));
  await page.fill('[name=city]', p.city);
  await page.fill('[name=contact]', p.contact);
  for (const id of p.interests) {
    const chip = page.locator(`.chip[data-id="${id}"]`);
    if (!(await chip.evaluate((el) => el.classList.contains('chip--on')))) await chip.click();
  }
}

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ru-RU' });
  const page = await ctx.newPage();
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  const responses = [];
  page.on('response', (r) => { if (r.status() >= 400) responses.push(`${r.status()} ${r.request().method()} ${r.url()}`); if (r.status() >= 500) serverErrors.push(`${r.status()} ${r.url()}`); });
  page.on('framenavigated', (f) => { if (f === page.mainFrame()) responses.push('NAV ' + f.url()); });
  const admin = await api(ctx.request, 'POST', '/api/auth/login', ADMIN);
  const created = [];

  try {
    // ---- маршруты без сессии ----
    for (const hash of ['', '#/', '#/discover', '#/admin', '#/whatever']) {
      await page.goto(BASE + '/' + hash);
      await page.waitForSelector('#login-form');
    }
    step('без сессии любой адрес ведёт на вход');

    // ---- испорченный токен ----
    await page.evaluate(() => localStorage.setItem('matchly.token', 'garbage.token.value'));
    await page.reload();
    await page.waitForSelector('#login-form');
    assert((await page.evaluate(() => localStorage.getItem('matchly.token'))) === null, 'мусорный токен не удалён');
    step('мусорный токен в localStorage очищается, показан вход');

    // ---- ошибки входа и регистрации, перевод сообщений ----
    await login(page, 'demo1@matchly.local', 'wrong');
    await waitToast(page, 'Неверный email или пароль');
    await page.click('a[href="#/register"]');
    await page.waitForSelector('#register-form');
    await page.fill('#register-form [name=email]', 'demo1@matchly.local');
    await page.fill('#register-form [name=password]', PASSWORD);
    await page.click('#register-form button[type=submit]');
    await waitToast(page, 'уже зарегистрирован');
    await page.fill('#register-form [name=email]', 'not-an-email');
    await page.click('#register-form button[type=submit]');
    await page.waitForFunction(() => document.querySelector('[data-error-for=email]')?.textContent === 'некорректный email');
    step('ошибки входа и регистрации показаны по-русски');

    // ---- новый пользователь: глубокие ссылки без анкеты ----
    const emailA = `corner-a-${stamp}@e2e.local`;
    created.push(emailA);
    await page.fill('#register-form [name=email]', emailA);
    await page.click('#register-form button[type=submit]');
    await page.waitForSelector('#profile-form');
    for (const hash of ['#/discover', '#/matches', '#/admin']) {
      await page.goto(BASE + '/' + hash);
      await page.waitForSelector('#profile-form');
      assert(page.url().endsWith('#/onboarding'), `ожидался онбординг для ${hash}, url=${page.url()}`);
    }
    await page.goto(BASE + '/#/nonexistent');
    await page.waitForFunction(() => document.body.textContent.includes('Страница не найдена'));
    step('без анкеты лента, матчи и админка ведут на онбординг, неизвестный адрес показывает 404');

    // ---- валидация онбординга ----
    await page.goto(BASE + '/#/onboarding');
    await page.waitForSelector('#profile-form');
    const young = new Date(); young.setFullYear(young.getFullYear() - 17);
    await fillProfile(page, { displayName: '<b>Злая Разметка</b>', birthDate: young.toISOString().slice(0, 10), gender: 'FEMALE', lookingFor: 'MALE', ageMin: 40, ageMax: 30, city: 'Тестоград', contact: '@corner_a', interests: [1, 2] });
    await page.click('#profile-form button[type=submit]');
    await page.waitForFunction(() => document.querySelector('[data-error-for=ageRangeValid]')?.textContent.includes('больше'));
    step('диапазон возраста «от» больше «до» подсвечен под полем');
    await page.fill('[name=ageMin]', '25');
    await page.fill('[name=ageMax]', '35');
    await page.click('#profile-form button[type=submit]');
    await page.waitForFunction(() => document.querySelector('[data-error-for=birthDate]')?.textContent.includes('с 18 лет'));
    step('несовершеннолетний возраст показан под полем даты рождения');

    // лимит интересов
    await page.fill('[name=birthDate]', '1996-01-15');
    // добираем выбор до 10 интересов, затем пытаемся выбрать одиннадцатый
    const chips = await page.$$('.chip');
    for (const chip of chips) {
      if ((await page.textContent('#interest-counter')).trim() === '10/10') break;
      if (!(await chip.evaluate((el) => el.classList.contains('chip--on')))) await chip.click();
    }
    const extra = (await Promise.all(chips.map(async (c) => ((await c.evaluate((el) => el.classList.contains('chip--on'))) ? null : c)))).find(Boolean);
    await extra.click();
    await waitToast(page, 'не больше 10');
    const counter = await page.textContent('#interest-counter');
    assert(counter.trim() === '10/10', 'счётчик интересов: ' + counter);
    step('одиннадцатый интерес не выбирается, счётчик 10/10');

    // невалидное фото: анкета сохраняется, ошибка под фото
    await page.setInputFiles('#photo-input', { name: 'evil.png', mimeType: 'image/png', buffer: Buffer.from('definitely not an image, just text') });
    await page.click('#profile-form button[type=submit]');
    await waitToast(page, 'фото не загружено');
    await page.waitForFunction(() => document.querySelector('[data-error-for=photo]')?.textContent.includes('JPEG'));
    assert((await page.inputValue('[name=displayName]')) === '<b>Злая Разметка</b>', 'имя не сохранилось');
    step('анкета сохранена при провале фото, причина показана под фото, режим редактирования');

    // ---- XSS: имя с разметкой отображается как текст ----
    const emailB = `corner-b-${stamp}@e2e.local`;
    created.push(emailB);
    const b = await api(ctx.request, 'POST', '/api/auth/register', { email: emailB, password: PASSWORD });
    await api(ctx.request, 'PUT', '/api/profiles/me', { displayName: 'Борис', birthDate: '1995-02-02', gender: 'MALE', lookingFor: 'FEMALE', ageMin: 25, ageMax: 35, city: 'Тестоград', bio: '<img src=x onerror=alert(1)> длинноеслововообщебезпробеловчтобыпроверитьпереноспереносперенос', contact: '@corner_b', interestIds: [1, 2, 3] }, b.token);
    const tokenA = await page.evaluate(() => localStorage.getItem('matchly.token'));
    const profileA = await api(ctx.request, 'GET', '/api/profiles/me', null, tokenA);

    // ---- пустое состояние ленты ----
    await api(ctx.request, 'PUT', '/api/profiles/me', { displayName: '<b>Злая Разметка</b>', birthDate: '1996-01-15', gender: 'FEMALE', lookingFor: 'MALE', ageMin: 98, ageMax: 99, city: 'Тестоград', contact: '@corner_a', interestIds: [1, 2] }, tokenA);
    await page.goto(BASE + '/#/discover');
    await page.waitForSelector('.empty');
    await page.click('#reload-deck');
    await page.waitForSelector('.empty');
    await page.click('.empty a[href="#/profile"]');
    await page.waitForSelector('#profile-form');
    step('пустая лента: «Обновить» не ломает экран, ссылка ведёт в анкету');

    // ---- лента с кандидатом Борисом ----
    await api(ctx.request, 'PUT', '/api/profiles/me', { displayName: '<b>Злая Разметка</b>', birthDate: '1996-01-15', gender: 'FEMALE', lookingFor: 'MALE', ageMin: 25, ageMax: 35, city: 'Тестоград', contact: '@corner_a', interestIds: [1, 2] }, tokenA);
    await page.goto(BASE + '/#/discover');
    await page.click('label.strategy:has(input[value=CONTENT])');
    await page.waitForFunction(() => document.querySelector('.deck-card h2')?.textContent.includes('Борис'));
    const hasImgTag = await page.$eval('.deck-card__bio', (el) => el.querySelector('img') !== null);
    assert(!hasImgTag, 'разметка из bio выполнилась как HTML');
    await noHorizontalScroll(page, 'лента с длинным словом');
    step('разметка в описании отображается как текст, длинное слово не ломает вёрстку');

    // выбранная стратегия сохраняется после перезагрузки
    await page.click('label.strategy:has(input[value=POPULARITY])');
    await page.waitForSelector('.deck-card, .empty');
    await page.reload();
    await page.waitForSelector('.deck-card, .empty');
    assert(await page.isChecked('input[name=strategy][value=POPULARITY]'), 'стратегия не сохранилась');
    step('выбор алгоритма сохраняется после перезагрузки');

    // двойной клик по лайку = одна реакция: счётчик «в подборке ещё N» уменьшается ровно на 1
    await page.click('label.strategy:has(input[value=CONTENT])');
    await page.waitForFunction(() => document.querySelector('.deck-card h2')?.textContent.includes('Борис'));
    const remainingBefore = parseInt((await page.textContent('.deck-card p.center')).match(/(\d+)/)[1], 10);
    await page.dblclick('#like');
    await page.waitForFunction(() => { const h = document.querySelector('.deck-card h2'); return !h || !h.textContent.includes('Борис'); });
    if (remainingBefore === 0) {
      await page.waitForSelector('.empty');
    } else {
      await page.waitForSelector('.deck-card p.center');
      const remainingAfter = parseInt((await page.textContent('.deck-card p.center')).match(/(\d+)/)[1], 10);
      assert(remainingAfter === remainingBefore - 1, `после двойного клика счётчик ${remainingBefore} -> ${remainingAfter}`);
    }
    const afterLike = await api(ctx.request, 'GET', '/api/recommendations?strategy=CONTENT&limit=50', null, tokenA);
    assert(!afterLike.items.some((i) => i.profile.displayName === 'Борис'), 'после лайка Борис остался кандидатом');
    step('двойной клик по «нравится» даёт одну реакцию и не ломает ленту');

    // ---- глядя на «Злую Разметку» глазами Бориса: имя как текст ----
    const bCtx = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ru-RU' });   // отдельный контекст: свой localStorage
    const bPage = await bCtx.newPage();
    bPage.on('console', (m) => { if (m.type() === 'error') consoleErrors.push('boris: ' + m.text()); });
    await bPage.goto(BASE + '/#/login');
    await bPage.evaluate((t) => localStorage.setItem('matchly.token', t), b.token);
    await bPage.reload();   // переход только по хешу страницу не перезагружает, токен читается при загрузке
    await bPage.goto(BASE + '/#/discover');
    await bPage.click('label.strategy:has(input[value=CONTENT])');
    await bPage.waitForFunction(() => document.querySelector('.deck-card h2')?.textContent.includes('<b>Злая Разметка</b>'));
    assert((await bPage.$('.deck-card h2 b')) === null, 'имя отрендерилось как HTML');
    await bPage.waitForFunction(() => [...document.querySelectorAll('.reasons li')].some((li) => li.textContent.includes('Проявил')), null, { timeout: 5000 }).catch(() => {});
    await bPage.click('#like');
    await bPage.waitForSelector('#modal:not([hidden]) .match-modal');
    assert((await bPage.textContent('#modal .contact-box strong')).trim() === '@corner_a', 'контакт в матче');
    await bPage.keyboard.press('ArrowLeft');   // горячие клавиши при открытом окне игнорируются
    await bPage.waitForTimeout(400);
    assert(!(await bPage.$eval('#modal', (m) => m.hidden)), 'горячая клавиша сработала при открытом окне');
    await bPage.click('#modal button[data-close]');
    step('имя с разметкой показано как текст, матч создан, горячие клавиши в модальном окне не работают');

    // копирование контакта: успех или понятное сообщение
    await bPage.click('a[href="#/matches"]');
    await bPage.waitForSelector('[data-copy]');
    await bPage.click('[data-copy]');
    await waitToast(bPage, '@corner_a');
    // отмена разрыва матча оставляет матч
    await bPage.click('[data-unmatch]');
    await bPage.click('#confirm-cancel');
    await bPage.waitForSelector('#modal[hidden]', { state: 'attached' });
    assert((await bPage.$$('.match-card')).length === 1, 'матч исчез после отмены');
    step('копирование контакта даёт обратную связь, отмена подтверждения ничего не меняет');
    await bCtx.close();

    // ---- блокировка во время сессии ----
    const users = await api(ctx.request, 'GET', '/api/admin/users?q=' + encodeURIComponent(emailA), null, admin.token);
    const userA = users.content[0];
    await api(ctx.request, 'POST', `/api/admin/users/${userA.id}/block`, null, admin.token);
    await page.click('a[href="#/matches"]');
    await page.waitForSelector('#login-form');
    await waitToast(page, 'заблокирован');
    assert((await page.evaluate(() => localStorage.getItem('matchly.token'))) === null, 'токен заблокированного не удалён');
    await login(page, emailA, PASSWORD);
    await waitToast(page, 'заблокирован');
    await api(ctx.request, 'POST', `/api/admin/users/${userA.id}/unblock`, null, admin.token);
    step('блокировка во время работы выбрасывает на вход с причиной, вход заблокированного отклонён');

    // ---- удаление собственного аккаунта ----
    await login(page, emailA, PASSWORD);
    await page.waitForSelector('.deck-card, .empty');
    await page.click('a[href="#/profile"]');
    await page.waitForSelector('#delete-account');
    await page.click('#delete-account');
    await page.fill('#delete-form [name=password]', 'wrong-password');
    await page.click('#delete-form button[type=submit]');
    await page.waitForFunction(() => document.querySelector('#delete-form [data-error-for=password]')?.textContent.includes('Неверный текущий пароль'));
    assert(!page.url().endsWith('#/login'), 'неверный пароль подтверждения выбросил из аккаунта');
    await page.fill('#delete-form [name=password]', PASSWORD);
    await page.click('#delete-form button[type=submit]');
    await page.waitForSelector('#login-form');
    await waitToast(page, 'Аккаунт удалён');
    await login(page, emailA, PASSWORD);
    await waitToast(page, 'Неверный email или пароль');
    created.splice(created.indexOf(emailA), 1);
    step('удаление аккаунта требует верный пароль, после удаления вход невозможен');

    // ---- админские граничные случаи ----
    await login(page, ADMIN.email, ADMIN.password);
    await page.waitForSelector('#profile-form');
    await page.goto(BASE + '/#/admin');
    await page.waitForSelector('.tiles .tile');
    await page.click('.tab[data-tab=users]');
    await page.waitForSelector('#user-search');
    await page.fill('#user-search', 'no-such-user-' + stamp);
    await page.waitForFunction(() => document.body.textContent.includes('Ничего не найдено'));
    await page.fill('#user-search', '');
    await page.waitForFunction(() => document.querySelectorAll('tbody tr').length > 1);
    assert(await page.isEnabled('#next'), 'нет второй страницы при 60+ пользователях');
    await page.click('#next');
    await page.waitForFunction(() => document.body.textContent.includes('Страница 2'));
    await page.click('#prev');
    await page.waitForFunction(() => document.body.textContent.includes('Страница 1'));
    await page.fill('#user-search', ADMIN.email);
    await page.waitForFunction((e) => document.querySelectorAll('tbody tr').length === 1 && document.body.textContent.includes(e), ADMIN.email);
    assert((await page.textContent('tbody tr')).includes('это вы'), 'своя строка не помечена');
    assert((await page.$('tbody tr [data-block], tbody tr [data-delete]')) === null, 'у своей строки есть кнопки блокировки или удаления');
    // отмена удаления пользователя
    await page.fill('#user-search', emailB);
    await page.waitForFunction((e) => document.querySelectorAll('tbody tr').length === 1 && document.body.textContent.includes(e), emailB);
    await page.click('[data-delete]');
    await page.click('#confirm-cancel');
    await page.waitForTimeout(300);
    assert((await page.$$('tbody tr')).length === 1 && (await page.textContent('tbody')).includes(emailB), 'пользователь пропал после отмены');
    step('админ: пустой поиск, пагинация, своя строка, отмена удаления');

    await page.click('.tab[data-tab=interests]');
    await page.waitForSelector('#interest-add');
    await page.fill('#interest-add [name=name]', 'Спорт');
    await page.click('#interest-add button[type=submit]');
    await waitToast(page, 'Такой интерес уже есть');
    await page.fill('#interest-add [name=name]', 'x');
    await page.click('#interest-add button[type=submit]');
    await page.waitForFunction(() => document.querySelector('#interest-add [data-error-for=name]')?.textContent.includes('от 2 до 50'));
    step('админ: дубликат интереса и слишком короткое имя показаны понятно');

    // ---- мобильная вёрстка всех экранов ----
    const mobile = await browser.newContext({ viewport: { width: 400, height: 820 }, locale: 'ru-RU' });
    const mp = await mobile.newPage();
    mp.on('console', (m) => { if (m.type() === 'error') consoleErrors.push('mobile: ' + m.text()); });
    await mp.goto(BASE + '/#/login'); await mp.waitForSelector('#login-form'); await noHorizontalScroll(mp, 'вход');
    await mp.goto(BASE + '/#/register'); await mp.waitForSelector('#register-form'); await noHorizontalScroll(mp, 'регистрация');
    await mp.evaluate((t) => localStorage.setItem('matchly.token', t), b.token);
    await mp.reload();
    await mp.goto(BASE + '/#/discover'); await mp.waitForSelector('.deck-card, .empty'); await noHorizontalScroll(mp, 'лента');
    await mp.goto(BASE + '/#/matches'); await mp.waitForSelector('.match-card, .empty'); await noHorizontalScroll(mp, 'матчи');
    await mp.goto(BASE + '/#/profile'); await mp.waitForSelector('#profile-form'); await noHorizontalScroll(mp, 'анкета');
    await mp.evaluate((t) => localStorage.setItem('matchly.token', t), admin.token);
    await mp.reload();
    await mp.goto(BASE + '/#/admin'); await mp.waitForSelector('.tiles .tile'); await noHorizontalScroll(mp, 'админ: статистика');
    await mp.click('.tab[data-tab=users]'); await mp.waitForSelector('tbody tr'); await noHorizontalScroll(mp, 'админ: пользователи');
    await mp.click('.tab[data-tab=interests]'); await mp.waitForSelector('#interest-add'); await noHorizontalScroll(mp, 'админ: интересы');
    await mobile.close();
    step('мобильная ширина 400px: ни один экран не даёт горизонтальной прокрутки');

    console.log(`\nALL CORNER CASES PASSED (${passed} groups)`);
  } catch (e) {
    await page.screenshot({ path: __dirname + '/corner-failure.png', fullPage: true }).catch(() => {});
    console.error('\nCORNER CASES FAILED:', e.message);
    console.error('url:', page.url());
    console.error('toasts:', await page.$$eval('.toast', (els) => els.map((t) => t.textContent)).catch(() => []));
    console.error('last events:', responses.slice(-12));
    process.exitCode = 1;
  } finally {
    for (const email of created) {
      try {
        const found = await api(ctx.request, 'GET', '/api/admin/users?q=' + encodeURIComponent(email), null, admin.token);
        for (const u of found.content) await api(ctx.request, 'DELETE', '/api/admin/users/' + u.id, null, admin.token);
      } catch (e) { console.error('cleanup failed for', email, e.message); }
    }
    const realErrors = consoleErrors.filter((t) => !/status of (4\d\d)/.test(t));
    console.log('console errors (excluding expected 4xx):', realErrors.length ? realErrors : 'none');
    console.log('5xx responses:', serverErrors.length ? serverErrors : 'none');
    if (realErrors.length || serverErrors.length) process.exitCode = 1;
    await browser.close();
  }
})();
