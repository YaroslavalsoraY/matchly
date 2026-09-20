const { chromium } = require('playwright');
const BASE = process.env.MATCHLY_URL || 'http://localhost:8080';
const SHOTS = __dirname + '/shots/';
const stamp = Date.now();
const emailA = `alisa${stamp}@e2e.local`;
const emailB = `boris${stamp}@e2e.local`;
const PASSWORD = 'Password1';
const consoleErrors = [];
const failedRequests = [];

async function api(request, method, url, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  const res = await request.fetch(BASE + url, { method, headers, data: body ? JSON.stringify(body) : undefined });
  if (!res.ok()) throw new Error(`${method} ${url} -> ${res.status()} ${await res.text()}`);
  return res.status() === 204 ? null : res.json();
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ru-RU' });
  const page = await context.newPage();
  page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()); });
  page.on('response', (r) => { if (r.status() >= 500) failedRequests.push(`${r.status()} ${r.url()}`); });
  const step = (name) => console.log('✔ ' + name);
  const shot = (name) => page.screenshot({ path: SHOTS + name + '.png', fullPage: true });

  try {
    // Борис: создаём через API, чтобы он уже ждал Алису лайком
    const b = await api(context.request, 'POST', '/api/auth/register', { email: emailB, password: PASSWORD });
    await api(context.request, 'PUT', '/api/profiles/me', {
      displayName: 'Борис', birthDate: '1996-03-03', gender: 'MALE', lookingFor: 'FEMALE', ageMin: 25, ageMax: 35,
      city: 'Тестоград', bio: 'Люблю тесты и кофе', contact: '@boris_e2e', interestIds: [1, 8, 17],
    }, b.token);
    step('Борис зарегистрирован через API');

    // 1. Страница входа
    await page.goto(BASE + '/');
    await page.waitForSelector('#login-form');
    await shot('01-login');
    step('страница входа открылась');

    // 2. Регистрация Алисы
    await page.click('a[href="#/register"]');
    await page.waitForSelector('#register-form');
    await page.fill('#register-form [name=email]', emailA);
    await page.fill('#register-form [name=password]', 'short');
    await page.click('#register-form button[type=submit]');
    await page.waitForSelector('[data-error-for=password]:not(:empty)');
    step('валидация пароля показана под полем');
    await page.fill('#register-form [name=password]', PASSWORD);
    await page.click('#register-form button[type=submit]');
    await page.waitForSelector('#profile-form');
    if (!page.url().endsWith('#/onboarding')) throw new Error('после регистрации ожидался онбординг, url=' + page.url());
    await shot('02-onboarding');
    step('после регистрации открыт онбординг');

    // 3. Онбординг с фото
    await page.fill('[name=displayName]', 'Алиса');
    await page.fill('[name=birthDate]', '1996-06-06');
    await page.selectOption('[name=gender]', 'FEMALE');
    await page.selectOption('[name=lookingFor]', 'MALE');
    await page.fill('[name=ageMin]', '29');
    await page.fill('[name=ageMax]', '31');
    await page.fill('[name=city]', 'Тестоград');
    await page.fill('[name=contact]', '@alisa_e2e');
    await page.fill('[name=bio]', 'Тестирую Matchly');
    await page.click('#profile-form button[type=submit]');
    await page.waitForSelector('[data-error-for=interestIds]:not(:empty)');
    step('без интересов форма не отправляется');
    for (const id of [1, 8, 17]) await page.click(`.chip[data-id="${id}"]`);
    await page.setInputFiles('#photo-input', __dirname + '/photo.png');
    await page.waitForSelector('#photo-preview img');
    await page.click('#profile-form button[type=submit]');
    await page.waitForSelector('.deck-card', { timeout: 15000 });
    step('анкета создана, открылась лента');

    // 4. Лента: стратегия CONTENT, Борис должен быть первым (идентичные интересы, город, возраст)
    await page.click('label.strategy:has(input[value=CONTENT])');
    await page.waitForSelector('.deck-card');
    await page.waitForFunction(() => document.querySelector('.deck-card h2')?.textContent.includes('Борис'), null, { timeout: 10000 });
    const reasons = await page.$$eval('.reasons li', (els) => els.map((e) => e.textContent));
    if (!reasons.some((r) => r.includes('Общие интересы'))) throw new Error('ожидалась причина про общие интересы: ' + reasons);
    await page.waitForSelector('#card-photo span');   // у Бориса нет фото: показывается инициал
    await shot('03-discover');
    step('в ленте первый Борис с объяснением, без фото показан инициал');

    // пропуск клавишей и возврат к Борису через смену стратегии не нужен: пропустим кого-то другого позже
    // Борис лайкает Алису через API, затем Алиса лайкает в интерфейсе -> матч
    const alisaProfile = await api(context.request, 'GET', '/api/profiles/me', null, await page.evaluate(() => localStorage.getItem('matchly.token')));
    await api(context.request, 'POST', '/api/reactions', { targetProfileId: alisaProfile.id, type: 'LIKE' }, b.token);
    await page.click('#like');
    await page.waitForSelector('#modal:not([hidden]) .match-modal');
    const contact = await page.textContent('#modal .contact-box strong');
    if (contact.trim() !== '@boris_e2e') throw new Error('в модальном окне ожидался контакт Бориса, получено ' + contact);
    await shot('04-match-modal');
    step('взаимный лайк показал окно «Это матч!» с контактом');

    await page.click('#modal a[href="#/matches"]');
    await page.waitForSelector('.match-card');
    const matchText = await page.textContent('.match-card');
    if (!matchText.includes('Борис') || !matchText.includes('@boris_e2e')) throw new Error('карточка матча неполная: ' + matchText);
    await shot('05-matches');
    step('страница матчей показывает Бориса с контактом');

    // 5. Лента: пропуск клавиатурой
    await page.click('a[href="#/discover"]');
    await page.waitForSelector('.deck-card');
    const before = await page.getAttribute('.deck-card', 'data-id');
    await page.keyboard.press('ArrowLeft');
    await page.waitForFunction((prev) => { const c = document.querySelector('.deck-card'); return !c || c.getAttribute('data-id') !== prev; }, before);
    step('пропуск стрелкой сменил карточку');

    // 6. Профиль: фото отображается, редактирование сохраняется
    await page.click('a[href="#/profile"]');
    await page.waitForSelector('#profile-form');
    await page.waitForSelector('#photo-preview img');
    await page.fill('[name=displayName]', 'Алиса Тест');
    await page.click('#profile-form button[type=submit]');
    await page.waitForSelector('.toast--success');
    await page.waitForFunction(() => document.querySelector('[name=displayName]')?.value === 'Алиса Тест');
    await shot('06-profile');
    step('анкета отредактирована, фото на месте');

    // 7. Разрыв матча через диалог подтверждения
    await page.click('a[href="#/matches"]');
    await page.waitForSelector('[data-unmatch]');
    await page.click('[data-unmatch]');
    await page.click('#confirm-ok');
    await page.waitForSelector('.empty');
    step('матч разорван, показано пустое состояние');

    // 8. Выход и вход администратором
    await page.click('#logout');
    await page.waitForSelector('#login-form');
    await page.fill('#login-form [name=email]', 'admin@matchly.local');
    await page.fill('#login-form [name=password]', 'admin123');
    await page.click('#login-form button[type=submit]');
    await page.waitForSelector('#profile-form');   // у администратора нет анкеты -> онбординг
    await page.click('a[href="#/admin"]');
    await page.waitForSelector('.tiles .tile');
    const tiles = await page.$$eval('.tile__value', (els) => els.map((e) => e.textContent));
    if (Number(tiles[0]) < 3) throw new Error('статистика пользователей выглядит неверно: ' + tiles);
    await shot('07-admin-stats');
    step('админ: статистика с плитками и топом интересов');

    await page.click('.tab[data-tab=users]');
    await page.waitForSelector('#user-search');
    await page.fill('#user-search', 'boris' + stamp);
    await page.waitForFunction((email) => document.body.textContent.includes(email) && document.querySelectorAll('tbody tr').length === 1, emailB);
    await page.click('[data-block]');
    await page.waitForSelector('.badge--blocked');
    await page.click('[data-unblock]');
    await page.waitForSelector('.badge--ok');
    await shot('08-admin-users');
    step('админ: поиск, блокировка и разблокировка пользователя');

    await page.click('.tab[data-tab=interests]');
    await page.waitForSelector('#interest-add');
    const interestName = 'E2E-' + stamp;
    await page.fill('#interest-add [name=name]', interestName);
    await page.click('#interest-add button[type=submit]');
    await page.waitForFunction((n) => [...document.querySelectorAll('tbody input[data-field=name]')].some((i) => i.value === n), interestName);
    const row = page.locator('tbody tr', { has: page.locator(`input[value="${interestName}"]`) });
    await row.locator('[data-remove]').click();
    await page.click('#confirm-ok');
    await page.waitForFunction((n) => ![...document.querySelectorAll('tbody input[data-field=name]')].some((i) => i.value === n), interestName);
    step('админ: интерес добавлен и удалён');

    await page.click('.tab[data-tab=settings]');
    await page.waitForSelector('input[name=default-strategy]', { state: 'attached' });
    await page.click('label.strategy:has(input[name=default-strategy][value=CONTENT])');
    await page.waitForSelector('.toast--success');
    await page.waitForFunction(() => document.querySelector('input[name=default-strategy][value=CONTENT]')?.checked);
    await page.click('label.strategy:has(input[name=default-strategy][value=HYBRID])');
    await page.waitForFunction(() => document.querySelector('input[name=default-strategy][value=HYBRID]')?.checked);
    await page.click('#seed-demo');
    await page.waitForFunction(() => [...document.querySelectorAll('.toast')].some((t) => t.textContent.includes('уже есть') || t.textContent.includes('Создано анкет')));
    await shot('09-admin-settings');
    step('админ: алгоритм по умолчанию переключён и возвращён, кнопка демо-данных отвечает');

    // 9. Удаление пользователей теста через админ-API
    const admin = await api(context.request, 'POST', '/api/auth/login', { email: 'admin@matchly.local', password: 'admin123' });
    for (const email of [emailA, emailB]) {
      const found = await api(context.request, 'GET', '/api/admin/users?q=' + encodeURIComponent(email), null, admin.token);
      for (const u of found.content) await api(context.request, 'DELETE', '/api/admin/users/' + u.id, null, admin.token);
    }
    step('тестовые пользователи удалены');

    // мобильная вёрстка
    const mobile = await browser.newContext({ viewport: { width: 400, height: 820 }, locale: 'ru-RU' });
    const mp = await mobile.newPage();
    await mp.goto(BASE + '/');
    await mp.waitForSelector('#login-form');
    await mp.fill('#login-form [name=email]', 'demo3@matchly.local');
    await mp.fill('#login-form [name=password]', 'demo1234');
    await mp.click('#login-form button[type=submit]');
    await mp.waitForSelector('.deck-card', { timeout: 15000 });
    await mp.waitForSelector('#card-photo img', { timeout: 15000 });   // SVG-аватар демо-анкеты загружен через blob-URL
    const overflow = await mp.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    if (overflow) throw new Error('на ширине 400px есть горизонтальная прокрутка');
    await mp.screenshot({ path: SHOTS + '10-mobile-discover.png', fullPage: true });
    await mobile.close();
    step('мобильная ширина 400px без горизонтальной прокрутки');

    console.log('\nALL E2E STEPS PASSED');
  } catch (e) {
    await shot('99-failure');
    console.error('\nE2E FAILED:', e.message);
    process.exitCode = 1;
  } finally {
    console.log('console errors:', consoleErrors.length ? consoleErrors : 'none');
    console.log('5xx responses:', failedRequests.length ? failedRequests : 'none');
    await browser.close();
  }
})();
