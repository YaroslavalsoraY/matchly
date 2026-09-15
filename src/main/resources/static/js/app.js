/**
 * Matchly SPA: хеш-роутер и экраны приложения. Чистый JavaScript без сборки.
 * Экраны: вход, регистрация, анкета (онбординг и редактирование), знакомства, матчи, админ-панель.
 */
const App = (() => {
  const state = { user: null, profile: null, interests: null, strategies: null };
  const PUBLIC_ROUTES = new Set(['/login', '/register']);
  const GENDER = { MALE: 'Мужчина', FEMALE: 'Женщина', OTHER: 'Другое' };
  const PREFERENCE = { MALE: 'Мужчин', FEMALE: 'Женщин', EVERYONE: 'Всех' };
  const CATEGORY = { ACTIVE: 'Активность', CULTURE: 'Культура', FOOD: 'Еда и напитки', HOBBY: 'Хобби', LIFESTYLE: 'Образ жизни', TECH: 'Технологии' };
  const STRATEGY_KEY = 'matchly.strategy';
  const MAX_INTERESTS = 10;

  /** Сообщения API приходят на английском (единый контракт), интерфейс показывает их по-русски. */
  const MESSAGES = {
    'Invalid email or password': 'Неверный email или пароль',
    'Email is already registered': 'Этот email уже зарегистрирован',
    'Account is blocked': 'Аккаунт заблокирован администратором',
    'Authentication is required': 'Нужно войти в аккаунт',
    'Invalid or expired token': 'Сессия истекла, войдите снова',
    'User no longer exists': 'Аккаунт больше не существует',
    'Authentication failed': 'Не удалось подтвердить вход',
    'Access is denied': 'Недостаточно прав для этого действия',
    'Profile not found': 'Анкета ещё не заполнена',
    'Photo file is empty': 'Файл пустой',
    'Photo must not exceed 2 MB': 'Фото больше 2 МБ',
    'Only JPEG, PNG or WebP images are allowed': 'Допустимы только JPEG, PNG или WebP',
    'Cannot read uploaded file': 'Не удалось прочитать файл',
    'You must be at least 18 years old': 'Сервис доступен только с 18 лет',
    'Minimum age must not exceed maximum age': 'Возраст «от» не может быть больше «до»',
    'Age range must be within 18-99': 'Возраст должен быть от 18 до 99',
    'Choose from 1 to 10 interests': 'Выберите от 1 до 10 интересов',
    'Some of the selected interests do not exist': 'Часть интересов уже удалена, обновите страницу',
    'You cannot react to your own profile': 'Нельзя оценить собственную анкету',
    'Interest with this name already exists': 'Такой интерес уже есть',
    'You cannot block your own account': 'Нельзя заблокировать собственный аккаунт',
    'You cannot delete your own account here': 'Нельзя удалить собственный аккаунт здесь',
    'Administrator account cannot be deleted this way': 'Аккаунт администратора так удалить нельзя',
    'Current password is incorrect': 'Неверный текущий пароль',
    'Request contains invalid fields': 'Проверьте выделенные поля',
    'Request conflicts with existing data': 'Данные конфликтуют с уже существующими',
    'Unexpected error occurred': 'Внутренняя ошибка сервера, попробуйте позже',
    'must not be blank': 'обязательное поле',
    'must not be null': 'обязательное поле',
    'must not be empty': 'выберите хотя бы один вариант',
    'must be a well-formed email address': 'некорректный email',
    'must be a past date': 'дата должна быть в прошлом',
    'password must be 8-72 characters long': 'пароль от 8 до 72 символов',
    'ageMin must not exceed ageMax': 'возраст «от» больше возраста «до»',
    'must be greater than or equal to 18': 'не меньше 18',
    'must be less than or equal to 99': 'не больше 99',
  };
  const STATUS_MESSAGES = { 413: 'Файл слишком большой (максимум 2 МБ)', 429: 'Слишком много запросов, подождите', 502: 'Сервер недоступен', 503: 'Сервер временно недоступен', 504: 'Сервер не отвечает' };
  function translate(message, status) {
    if (message && MESSAGES[message]) return MESSAGES[message];
    const size = /^size must be between (\d+) and (\d+)$/.exec(message || '');
    if (size) return size[1] === '0' ? `не больше ${size[2]} символов` : `от ${size[1]} до ${size[2]} символов`;
    if (status && STATUS_MESSAGES[status]) return STATUS_MESSAGES[status];
    return message || 'Что-то пошло не так';
  }

  // ---------- утилиты ----------
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
  const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const app = () => $('#app');
  const initial = (name) => ((String(name || '').match(/[\p{L}\p{N}]/u) || ['?'])[0]).toUpperCase();
  const formatDate = (iso) => new Date(iso).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' });
  const options = (map, selected) => Object.entries(map).map(([k, v]) => `<option value="${k}" ${k === selected ? 'selected' : ''}>${esc(v)}</option>`).join('');
  const groupBy = (list, key) => list.reduce((acc, item) => ((acc[item[key]] = acc[item[key]] || []).push(item), acc), {});
  const maxBirthDate = () => { const d = new Date(); d.setFullYear(d.getFullYear() - 18); return d.toISOString().slice(0, 10); };

  function toast(message, type = 'info') {
    const box = document.createElement('div');
    box.className = 'toast toast--' + type;
    box.textContent = message;
    $('#toasts').appendChild(box);
    requestAnimationFrame(() => box.classList.add('toast--show'));
    setTimeout(() => { box.classList.remove('toast--show'); setTimeout(() => box.remove(), 300); }, 3500);
  }

  function showModal(html) {
    const modal = $('#modal');
    modal.innerHTML = `<div class="modal__backdrop" data-close></div><div class="modal__dialog" role="dialog" aria-modal="true">${html}</div>`;
    modal.hidden = false;
    $$('[data-close]', modal).forEach((el) => (el.onclick = closeModal));
  }
  function closeModal() { const modal = $('#modal'); modal.hidden = true; modal.innerHTML = ''; }

  function confirmDialog(title, text, okLabel = 'Да') {
    return new Promise((resolve) => {
      showModal(`<h3>${esc(title)}</h3><p class="muted">${esc(text)}</p>
        <div class="modal__actions"><button class="btn btn--ghost" id="confirm-cancel">Отмена</button><button class="btn btn--danger" id="confirm-ok">${esc(okLabel)}</button></div>`);
      $('#confirm-ok').onclick = () => { closeModal(); resolve(true); };
      $('#confirm-cancel').onclick = () => { closeModal(); resolve(false); };
      $('#modal .modal__backdrop').onclick = () => { closeModal(); resolve(false); };
    });
  }

  function clearFieldErrors(form) { $$('[data-error-for]', form).forEach((el) => (el.textContent = '')); }
  function showFieldErrors(form, errors) {
    clearFieldErrors(form);
    let shown = false;
    for (const error of errors) {
      const target = form.querySelector(`[data-error-for="${error.field}"]`);
      if (target) { target.textContent = translate(error.message); shown = true; }
    }
    return shown;
  }
  /** Ошибка бизнес-правила (422) относится к конкретному полю формы, если совпал ключ. */
  const RULE_FIELDS = [['years old', 'birthDate'], ['Minimum age', 'ageMax'], ['Age range', 'ageMax'], ['interests', 'interestIds'], ['JPEG', 'photo'], ['Photo', 'photo'], ['password', 'password']];
  function handleError(error, form) {
    if (error instanceof Api.ApiError) {
      if (form && error.errors.length && showFieldErrors(form, error.errors)) { toast('Проверьте выделенные поля', 'error'); return; }
      const message = translate(error.message, error.status);
      if (form && error.status === 422) {
        const hit = RULE_FIELDS.find(([key]) => (error.message || '').includes(key));
        const target = hit && form.querySelector(`[data-error-for="${hit[1]}"]`);
        if (target) { target.textContent = message; target.scrollIntoView({ block: 'center', behavior: 'smooth' }); return; }
      }
      toast(message, 'error');
      return;
    }
    if (error instanceof TypeError) { toast('Нет связи с сервером. Проверьте подключение.', 'error'); return; }
    console.error(error);
    toast('Что-то пошло не так. Попробуйте ещё раз.', 'error');
  }

  async function setAvatar(element, photoUrl, name) {
    if (!element) return;
    element.innerHTML = `<span>${esc(initial(name))}</span>`;
    const src = await Api.imageUrl(photoUrl);
    if (src && element.isConnected) element.innerHTML = `<img src="${src}" alt="Фото: ${esc(name)}">`;
  }

  async function copyText(text) {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        const area = document.createElement('textarea');
        area.value = text;
        area.setAttribute('readonly', '');
        area.style.position = 'fixed';
        area.style.opacity = '0';
        document.body.appendChild(area);
        area.select();
        const ok = document.execCommand('copy');
        area.remove();
        if (!ok) throw new Error('execCommand failed');
      }
      toast('Скопировано: ' + text, 'success');
    } catch (e) {
      toast('Скопируйте вручную: ' + text, 'error');
    }
  }

  // ---------- сессия и справочники ----------
  async function loadSession() {
    if (!Api.getToken()) { state.user = null; state.profile = null; return; }
    try { state.user = await Api.get('/api/auth/me'); await loadProfile(); }
    catch (e) { state.user = null; state.profile = null; Api.setToken(null); }
  }
  async function loadProfile() {
    try { state.profile = await Api.get('/api/profiles/me'); }
    catch (e) { if (e instanceof Api.ApiError && e.status === 404) state.profile = null; else throw e; }
  }
  async function loadInterests() { if (!state.interests) state.interests = await Api.get('/api/interests'); return state.interests; }
  async function loadStrategies(force) { if (!state.strategies || force) state.strategies = await Api.get('/api/recommendations/strategies'); return state.strategies; }

  function logout() {
    Api.setToken(null);
    Object.assign(state, { user: null, profile: null, strategies: null });
    navigate('/login');
    toast('Вы вышли из аккаунта');
  }
  window.addEventListener('matchly:session-expired', (event) => {
    Object.assign(state, { user: null, profile: null });
    navigate('/login');
    toast(translate(event.detail) === event.detail && !event.detail ? 'Сессия завершена, войдите снова' : translate(event.detail), 'error');
  });

  // ---------- роутер ----------
  const routes = {
    '/login': renderLogin, '/register': renderRegister,
    '/onboarding': renderProfileForm, '/profile': renderProfileForm,
    '/discover': renderDiscover, '/matches': renderMatches, '/admin': renderAdmin,
  };
  const currentPath = () => { const path = location.hash.replace(/^#/, ''); return path === '' || path === '/' ? '/discover' : path; };
  function navigate(path) { if (location.hash === '#' + path) route(); else location.hash = path; }

  async function route() {
    closeModal();
    let path = currentPath();
    if (!state.user && !PUBLIC_ROUTES.has(path)) path = '/login';
    if (state.user && PUBLIC_ROUTES.has(path)) path = state.profile ? '/discover' : '/onboarding';
    if (state.user && !state.profile && (path === '/discover' || path === '/matches')) path = '/onboarding';
    if (path === '/admin' && (!state.user || state.user.role !== 'ADMIN')) path = '/discover';
    if (path !== currentPath()) { location.replace('#' + path); return; }
    renderNav(path);
    app().innerHTML = '<div class="loading">Загрузка…</div>';
    try { await (routes[path] || renderNotFound)(); }
    catch (e) {
      handleError(e);
      app().innerHTML = '<div class="empty card"><h2>Не удалось загрузить страницу</h2><button class="btn" onclick="location.reload()">Обновить</button></div>';
    }
  }

  function renderNav(path) {
    const nav = $('#nav');
    const link = (p, label, cls = '') => `<a href="#${p}" class="${cls} ${path === p ? 'active' : ''}">${label}</a>`;
    if (!state.user) { nav.innerHTML = link('/login', 'Войти') + link('/register', 'Регистрация', 'btn btn--small btn--primary'); return; }
    nav.innerHTML = link('/discover', 'Знакомства') + link('/matches', 'Матчи')
      + link(state.profile ? '/profile' : '/onboarding', 'Моя анкета')
      + (state.user.role === 'ADMIN' ? link('/admin', 'Админ') : '')
      + `<button class="nav__logout" id="logout" title="${esc(state.user.email)}">Выйти</button>`;
    $('#logout').onclick = logout;
  }

  function renderNotFound() {
    app().innerHTML = '<div class="empty card"><h2>Страница не найдена</h2><a class="btn btn--primary" href="#/discover">На главную</a></div>';
  }

  // ---------- вход и регистрация ----------
  function renderLogin() {
    app().innerHTML = `
      <section class="auth">
        <div class="auth__hero">
          <h1>Знакомства, которые <span class="accent">подбирают за вас</span></h1>
          <p>Matchly сравнивает интересы, учится на лайках и показывает тех, кто действительно подходит. И честно объясняет, почему.</p>
        </div>
        <form class="card auth__form" id="login-form" novalidate>
          <h2>Вход</h2>
          <label>Email<input name="email" type="email" required autocomplete="email" placeholder="you@example.com"><span class="field-error" data-error-for="email"></span></label>
          <label>Пароль<input name="password" type="password" required autocomplete="current-password"><span class="field-error" data-error-for="password"></span></label>
          <button class="btn btn--primary btn--block" type="submit">Войти</button>
          <p class="muted">Нет аккаунта? <a href="#/register">Зарегистрируйтесь</a></p>
          <p class="muted small">Демо-доступ: demo1@matchly.local / demo1234</p>
        </form>
      </section>`;
    $('#login-form').onsubmit = async (event) => {
      event.preventDefault();
      const form = event.target;
      clearFieldErrors(form);
      try {
        const result = await Api.post('/api/auth/login', Object.fromEntries(new FormData(form)));
        Api.setToken(result.token);
        state.user = result.user;
        await loadProfile();
        toast('С возвращением!', 'success');
        navigate(state.profile ? '/discover' : '/onboarding');
      } catch (e) { handleError(e, form); }
    };
  }

  function renderRegister() {
    app().innerHTML = `
      <section class="auth">
        <div class="auth__hero">
          <h1>Пара минут <span class="accent">до первой подборки</span></h1>
          <p>Создайте аккаунт, заполните анкету и получите людей, подобранных по интересам, возрасту и городу.</p>
        </div>
        <form class="card auth__form" id="register-form" novalidate>
          <h2>Регистрация</h2>
          <label>Email<input name="email" type="email" required autocomplete="email"><span class="field-error" data-error-for="email"></span></label>
          <label>Пароль<input name="password" type="password" required minlength="8" autocomplete="new-password"><span class="hint">Не короче 8 символов</span><span class="field-error" data-error-for="password"></span></label>
          <button class="btn btn--primary btn--block" type="submit">Создать аккаунт</button>
          <p class="muted">Уже есть аккаунт? <a href="#/login">Войти</a></p>
        </form>
      </section>`;
    $('#register-form').onsubmit = async (event) => {
      event.preventDefault();
      const form = event.target;
      clearFieldErrors(form);
      try {
        const result = await Api.post('/api/auth/register', Object.fromEntries(new FormData(form)));
        Api.setToken(result.token);
        state.user = result.user;
        state.profile = null;
        toast('Аккаунт создан. Теперь расскажите о себе', 'success');
        navigate('/onboarding');
      } catch (e) { handleError(e, form); }
    };
  }

  // ---------- анкета ----------
  async function renderProfileForm() {
    const interests = await loadInterests();
    const profile = state.profile;
    const isNew = !profile;
    const selected = new Set(profile ? profile.interests.map((i) => i.id) : []);
    const grouped = groupBy(interests, 'category');
    const val = (key, fallback = '') => esc(profile ? profile[key] ?? fallback : fallback);

    app().innerHTML = `
      <section class="page page--narrow">
        <header class="page__head">
          <h1>${isNew ? 'Расскажите о себе' : 'Моя анкета'}</h1>
          <p class="muted">${isNew ? 'Анкета нужна, чтобы подбирать людей именно для вас. Это займёт пару минут.' : 'Изменения сразу влияют на подборку.'}</p>
          ${isNew && state.user.role === 'ADMIN' ? '<p class="muted small">Администратору анкета не обязательна: разделы «Админ» доступны и без неё.</p>' : ''}
        </header>
        <form id="profile-form" class="card form" novalidate>
          <div class="form__row form__row--photo">
            <div class="avatar avatar--xl" id="photo-preview"><span>${esc(initial(profile?.displayName))}</span></div>
            <div class="stack">
              <label class="btn btn--ghost btn--small">Выбрать фото<input id="photo-input" type="file" accept="image/png,image/jpeg,image/webp" hidden></label>
              ${profile?.photoUrl ? '<button type="button" class="btn btn--ghost btn--small btn--danger-text" id="photo-delete">Удалить фото</button>' : ''}
              <span class="muted small">JPEG, PNG или WebP до 2 МБ</span>
              <span class="field-error" id="photo-error" data-error-for="photo"></span>
            </div>
          </div>
          <div class="grid-2">
            <label>Имя<input name="displayName" required maxlength="50" value="${val('displayName')}"><span class="field-error" data-error-for="displayName"></span></label>
            <label>Дата рождения<input name="birthDate" type="date" required max="${maxBirthDate()}" value="${val('birthDate')}"><span class="field-error" data-error-for="birthDate"></span></label>
            <label>Пол<select name="gender">${options(GENDER, profile?.gender || 'FEMALE')}</select></label>
            <label>Кого ищу<select name="lookingFor">${options(PREFERENCE, profile?.lookingFor || 'MALE')}</select></label>
            <label>Возраст от<input name="ageMin" type="number" min="18" max="99" value="${profile?.ageMin ?? 20}"><span class="field-error" data-error-for="ageMin"></span></label>
            <label>Возраст до<input name="ageMax" type="number" min="18" max="99" value="${profile?.ageMax ?? 35}"><span class="field-error" data-error-for="ageMax"></span><span class="field-error" data-error-for="ageRangeValid"></span></label>
            <label>Город<input name="city" required maxlength="100" placeholder="Москва" value="${val('city')}"><span class="field-error" data-error-for="city"></span></label>
            <label>Контакт для матчей<input name="contact" required maxlength="100" placeholder="@telegram" value="${val('contact')}"><span class="hint">Увидят только те, с кем случится взаимный лайк</span><span class="field-error" data-error-for="contact"></span></label>
          </div>
          <label>О себе<textarea name="bio" maxlength="1000" rows="3" placeholder="Пара слов о том, что вы любите">${val('bio')}</textarea><span class="field-error" data-error-for="bio"></span></label>
          <fieldset class="interests">
            <legend>Интересы <span class="muted small" id="interest-counter">${selected.size}/${MAX_INTERESTS}</span></legend>
            ${Object.entries(grouped).map(([category, list]) => `
              <div class="interests__group"><h4>${esc(CATEGORY[category] || category)}</h4>
                <div class="chips">${list.map((i) => `<button type="button" class="chip ${selected.has(i.id) ? 'chip--on' : ''}" data-id="${i.id}">${esc(i.name)}</button>`).join('')}</div>
              </div>`).join('')}
            <span class="field-error" data-error-for="interestIds"></span>
          </fieldset>
          ${isNew ? '' : `<label class="switch"><input type="checkbox" name="visible" ${profile.visible ? 'checked' : ''}><span>Показывать мою анкету другим</span></label>`}
          <div class="form__actions">
            <button class="btn btn--primary" type="submit">${isNew ? 'Сохранить и начать' : 'Сохранить'}</button>
            ${isNew ? '' : '<button type="button" class="btn btn--ghost btn--danger-text" id="delete-account">Удалить аккаунт</button>'}
          </div>
        </form>
      </section>`;

    if (profile?.photoUrl) setAvatar($('#photo-preview'), profile.photoUrl, profile.displayName);

    let photoFile = null;
    $('#photo-input').onchange = (event) => {
      photoFile = event.target.files[0] || null;
      $('#photo-error').textContent = '';
      if (!photoFile) return;
      if (photoFile.size > 2 * 1024 * 1024) { $('#photo-error').textContent = 'Файл больше 2 МБ'; photoFile = null; return; }
      $('#photo-preview').innerHTML = `<img src="${URL.createObjectURL(photoFile)}" alt="Предпросмотр">`;
    };
    const deletePhoto = $('#photo-delete');
    if (deletePhoto) deletePhoto.onclick = async () => {
      try { state.profile = await Api.del('/api/profiles/me/photo'); toast('Фото удалено'); renderProfileForm(); }
      catch (e) { handleError(e); }
    };

    $$('.chip', app()).forEach((chip) => (chip.onclick = () => {
      const id = Number(chip.dataset.id);
      if (selected.has(id)) selected.delete(id);
      else if (selected.size >= MAX_INTERESTS) { toast(`Можно выбрать не больше ${MAX_INTERESTS} интересов`, 'error'); return; }
      else selected.add(id);
      chip.classList.toggle('chip--on', selected.has(id));
      $('#interest-counter').textContent = `${selected.size}/${MAX_INTERESTS}`;
    }));

    const form = $('#profile-form');
    form.onsubmit = async (event) => {
      event.preventDefault();
      clearFieldErrors(form);
      const data = new FormData(form);
      if (!selected.size) { form.querySelector('[data-error-for="interestIds"]').textContent = 'Выберите хотя бы один интерес'; return; }
      const body = {
        displayName: data.get('displayName').trim(), birthDate: data.get('birthDate'),
        gender: data.get('gender'), lookingFor: data.get('lookingFor'),
        ageMin: Number(data.get('ageMin')), ageMax: Number(data.get('ageMax')),
        city: data.get('city').trim(), bio: data.get('bio').trim(), contact: data.get('contact').trim(),
        interestIds: [...selected], visible: isNew ? true : data.get('visible') === 'on',
      };
      const submit = form.querySelector('button[type=submit]');
      submit.disabled = true;
      let saved = false;
      try {
        state.profile = await Api.put('/api/profiles/me', body);
        saved = true;
        if (photoFile) {
          const upload = new FormData();
          upload.append('file', photoFile);
          state.profile = await Api.put('/api/profiles/me/photo', upload);
        }
        toast(isNew ? 'Анкета создана. Приятных знакомств!' : 'Сохранено', 'success');
        if (isNew) navigate('/discover'); else { renderNav('/profile'); renderProfileForm(); }
      } catch (e) {
        if (saved) {
          // анкета уже сохранена, не прошло только фото: остаёмся в режиме редактирования и показываем причину
          renderNav('/profile');
          await renderProfileForm();
          handleError(e, $('#profile-form'));
          toast(isNew ? 'Анкета сохранена, но фото не загружено' : 'Данные сохранены, но фото не загружено', 'error');
        } else {
          handleError(e, form);
        }
      } finally { submit.disabled = false; }
    };

    const deleteAccount = $('#delete-account');
    if (deleteAccount) deleteAccount.onclick = deleteAccountDialog;
  }

  function deleteAccountDialog() {
    showModal(`<h3>Удалить аккаунт?</h3><p class="muted">Анкета, фото, лайки и матчи будут удалены безвозвратно. Для подтверждения введите пароль.</p>
      <form id="delete-form" novalidate><label>Пароль<input name="password" type="password" required autocomplete="current-password"><span class="field-error" data-error-for="password"></span></label>
      <div class="modal__actions"><button type="button" class="btn btn--ghost" data-close>Отмена</button><button type="submit" class="btn btn--danger">Удалить навсегда</button></div></form>`);
    $('#delete-form').onsubmit = async (event) => {
      event.preventDefault();
      const form = event.target;
      try {
        await Api.del('/api/auth/me', { password: form.password.value });
        closeModal();
        Api.setToken(null);
        Object.assign(state, { user: null, profile: null });
        navigate('/login');
        toast('Аккаунт удалён. Будем рады увидеть вас снова');
      } catch (e) { handleError(e, form); }
    };
  }

  // ---------- знакомства ----------
  const discover = { queue: [], strategy: null, exhausted: false, current: null, seq: 0 };

  async function renderDiscover() {
    const strategies = await loadStrategies();
    const saved = localStorage.getItem(STRATEGY_KEY);
    discover.strategy = strategies.some((s) => s.type === saved) ? saved : (strategies.find((s) => s.isDefault) || strategies[0]).type;
    Object.assign(discover, { queue: [], exhausted: false, current: null });

    app().innerHTML = `
      <section class="discover">
        <aside class="discover__side card">
          <h3>Алгоритм подбора</h3>
          <div class="strategies">${strategies.map((s) => `
            <label class="strategy ${s.type === discover.strategy ? 'strategy--on' : ''}">
              <input type="radio" name="strategy" value="${s.type}" ${s.type === discover.strategy ? 'checked' : ''}>
              <span class="strategy__title">${esc(s.title)}${s.isDefault ? ' <em>по умолчанию</em>' : ''}</span>
              <span class="strategy__desc">${esc(s.description)}</span>
            </label>`).join('')}
          </div>
          <p class="muted small">Клавиши: ← пропустить, → нравится</p>
        </aside>
        <div class="discover__main" id="deck"></div>
      </section>`;

    $$('input[name=strategy]').forEach((radio) => (radio.onchange = () => {
      discover.strategy = radio.value;
      localStorage.setItem(STRATEGY_KEY, radio.value);
      $$('.strategy').forEach((label) => label.classList.toggle('strategy--on', label.querySelector('input').value === radio.value));
      Object.assign(discover, { queue: [], exhausted: false, current: null });
      showNextCard();
    }));
    await showNextCard();
  }

  /** Возвращает false, если за время запроса пользователь сменил алгоритм и ответ устарел. */
  async function fetchMore() {
    const seq = ++discover.seq;
    const result = await Api.get(`/api/recommendations?strategy=${encodeURIComponent(discover.strategy)}&limit=10`);
    if (seq !== discover.seq) return false;
    discover.queue = result.items;
    discover.exhausted = result.items.length === 0;
    return true;
  }

  async function showNextCard() {
    const deck = $('#deck');
    if (!deck) return;
    if (!discover.queue.length && !discover.exhausted) {
      deck.innerHTML = '<div class="loading">Подбираем…</div>';
      try { if (!(await fetchMore())) return; } catch (e) { handleError(e); deck.innerHTML = '<div class="empty card"><h2>Не удалось загрузить подборку</h2><button class="btn btn--primary" id="reload-deck">Повторить</button></div>'; $('#reload-deck').onclick = () => showNextCard(); return; }
    }
    if (!discover.queue.length) {
      discover.current = null;
      deck.innerHTML = `<div class="empty card"><div class="empty__icon">✨</div><h2>Пока никого нового</h2>
        <p class="muted">Вы посмотрели всех, кто подходит под ваши настройки. Попробуйте другой алгоритм, расширьте возраст в анкете или загляните позже.</p>
        <div class="empty__actions"><button class="btn btn--primary" id="reload-deck">Обновить</button><a class="btn btn--ghost" href="#/profile">Настройки поиска</a></div></div>`;
      $('#reload-deck').onclick = () => { discover.exhausted = false; showNextCard(); };
      return;
    }
    const item = discover.queue[0];
    const p = item.profile;
    discover.current = p;
    deck.innerHTML = `
      <article class="deck-card card" data-id="${p.id}">
        <div class="deck-card__photo avatar avatar--card" id="card-photo"><span>${esc(initial(p.displayName))}</span></div>
        <div class="deck-card__body">
          <div class="deck-card__title"><h2>${esc(p.displayName)}, ${p.age}</h2><span class="match-score" title="Оценка совпадения по выбранному алгоритму">${Math.round(item.score * 100)}%</span></div>
          <p class="muted">${esc(p.city)} · ${esc(GENDER[p.gender] || '')}</p>
          ${p.bio ? `<p class="deck-card__bio">${esc(p.bio)}</p>` : ''}
          <div class="chips chips--static">${p.interests.map((i) => `<span class="chip chip--static">${esc(i.name)}</span>`).join('')}</div>
          ${item.reasons.length
            ? `<div class="reasons"><h4>Почему показываем</h4><ul>${item.reasons.map((r) => `<li>${esc(r)}</li>`).join('')}</ul></div>`
            : '<p class="muted small">Новая анкета: данных для объяснения пока мало</p>'}
        </div>
        <div class="deck-card__actions">
          <button class="btn btn--round btn--skip" id="skip" title="Пропустить (←)" aria-label="Пропустить">✕</button>
          <button class="btn btn--round btn--like" id="like" title="Нравится (→)" aria-label="Нравится">♥</button>
        </div>
        <p class="muted small center">В подборке ещё ${discover.queue.length - 1}</p>
      </article>`;
    setAvatar($('#card-photo'), p.photoUrl, p.displayName);
    if (discover.queue[1]) Api.imageUrl(discover.queue[1].profile.photoUrl);
    $('#skip').onclick = () => react(p, 'SKIP');
    $('#like').onclick = () => react(p, 'LIKE');
  }

  async function react(profile, type) {
    const card = $('.deck-card');
    if (!card || card.dataset.busy) return;
    card.dataset.busy = '1';
    card.classList.add(type === 'LIKE' ? 'deck-card--like' : 'deck-card--skip');
    try {
      const result = await Api.post('/api/reactions', { targetProfileId: profile.id, type });
      discover.queue.shift();
      if (result.matched) showMatchModal(result.match);
      await showNextCard();
    } catch (e) {
      delete card.dataset.busy;
      card.classList.remove('deck-card--like', 'deck-card--skip');
      if (e instanceof Api.ApiError && e.status === 404) { discover.queue.shift(); toast('Анкета больше недоступна'); await showNextCard(); }
      else handleError(e);
    }
  }

  function showMatchModal(match) {
    const p = match.profile;
    showModal(`<div class="match-modal"><div class="match-modal__hearts">♥</div><h2>Это матч!</h2>
      <p>Вы понравились друг другу с <strong>${esc(p.displayName)}</strong>. Теперь можно написать.</p>
      <div class="contact-box"><span class="muted small">Контакт</span><strong>${esc(match.contact)}</strong><button class="btn btn--small btn--ghost" id="copy-contact">Копировать</button></div>
      <div class="modal__actions"><button class="btn btn--ghost" data-close>Продолжить</button><a class="btn btn--primary" href="#/matches">К матчам</a></div></div>`);
    $('#copy-contact').onclick = () => copyText(match.contact);
  }

  document.addEventListener('keydown', (event) => {
    if (currentPath() !== '/discover' || !discover.current || !$('#modal').hidden) return;
    if (event.target.matches('input, textarea, select')) return;
    if (event.key === 'ArrowLeft') react(discover.current, 'SKIP');
    if (event.key === 'ArrowRight') react(discover.current, 'LIKE');
  });

  // ---------- матчи ----------
  async function renderMatches() {
    const matches = await Api.get('/api/matches');
    app().innerHTML = `
      <section class="page">
        <header class="page__head"><h1>Матчи</h1><p class="muted">Взаимные симпатии. Контакты видны только здесь.</p></header>
        ${matches.length ? `<div class="grid-cards">${matches.map(matchCard).join('')}</div>` : `
          <div class="empty card"><div class="empty__icon">💌</div><h2>Матчей пока нет</h2>
          <p class="muted">Лайкайте анкеты в разделе «Знакомства»: взаимный лайк появится здесь вместе с контактом.</p>
          <a class="btn btn--primary" href="#/discover">К знакомствам</a></div>`}
      </section>`;
    matches.forEach((m) => setAvatar($(`#match-photo-${m.id}`), m.profile.photoUrl, m.profile.displayName));
    $$('[data-copy]').forEach((b) => (b.onclick = () => copyText(b.dataset.copy)));
    $$('[data-unmatch]').forEach((b) => (b.onclick = async () => {
      if (!(await confirmDialog('Разорвать матч?', 'Анкета исчезнет из матчей, и вы больше не увидите контакт.', 'Разорвать'))) return;
      try { await Api.del('/api/matches/' + b.dataset.unmatch); toast('Матч разорван'); renderMatches(); }
      catch (e) { handleError(e); }
    }));
  }

  function matchCard(m) {
    const p = m.profile;
    return `<article class="card match-card">
      <div class="avatar avatar--md" id="match-photo-${m.id}"><span>${esc(initial(p.displayName))}</span></div>
      <div class="match-card__body">
        <h3>${esc(p.displayName)}, ${p.age}</h3>
        <p class="muted small">${esc(p.city)} · матч ${formatDate(m.matchedAt)}</p>
        <div class="chips chips--static">${p.interests.slice(0, 4).map((i) => `<span class="chip chip--static">${esc(i.name)}</span>`).join('')}</div>
        <div class="contact-box"><span class="muted small">Контакт</span><strong>${esc(m.contact)}</strong><button class="btn btn--small btn--ghost" data-copy="${esc(m.contact)}">Копировать</button></div>
      </div>
      <button class="btn btn--ghost btn--small btn--danger-text" data-unmatch="${m.id}">Разорвать матч</button>
    </article>`;
  }

  // ---------- админ ----------
  const admin = { tab: 'stats', page: 0, query: '' };
  const ADMIN_TABS = [['stats', 'Статистика'], ['users', 'Пользователи'], ['interests', 'Интересы'], ['settings', 'Настройки']];

  async function renderAdmin() {
    app().innerHTML = `
      <section class="page">
        <header class="page__head"><h1>Администрирование</h1><p class="muted">Пользователи, справочник интересов, алгоритм по умолчанию и показатели сервиса.</p></header>
        <div class="tabs">${ADMIN_TABS.map(([key, label]) => `<button class="tab ${admin.tab === key ? 'tab--on' : ''}" data-tab="${key}">${label}</button>`).join('')}</div>
        <div id="admin-body"></div>
      </section>`;
    $$('.tab').forEach((tab) => (tab.onclick = () => {
      admin.tab = tab.dataset.tab;
      $$('.tab').forEach((t) => t.classList.toggle('tab--on', t === tab));
      renderAdminTab();
    }));
    await renderAdminTab();
  }

  async function renderAdminTab() {
    const body = $('#admin-body');
    body.innerHTML = '<div class="loading">Загрузка…</div>';
    const renderers = { stats: renderAdminStats, users: renderAdminUsers, interests: renderAdminInterests, settings: renderAdminSettings };
    try { await renderers[admin.tab](body); } catch (e) { handleError(e); body.innerHTML = ''; }
  }

  async function renderAdminStats(body) {
    const s = await Api.get('/api/admin/stats');
    const tile = (value, label) => `<div class="card tile"><div class="tile__value">${value}</div><div class="tile__label">${label}</div></div>`;
    const max = Math.max(1, ...s.topInterests.map((i) => i.profiles));
    body.innerHTML = `
      <div class="tiles">
        ${tile(s.users, `пользователей · активных ${s.activeUsers}, заблокированных ${s.blockedUsers}`)}
        ${tile(s.newUsersLast7Days, 'новых за 7 дней')}
        ${tile(s.profiles, `анкет · видимых ${s.visibleProfiles}, с фото ${s.profilesWithPhoto}`)}
        ${tile(s.likes, `лайков · пропусков ${s.skips}`)}
        ${tile(s.matches, 'матчей')}
        ${tile(s.interests, 'интересов в справочнике')}
      </div>
      <div class="card bar-list"><h3>Самые популярные интересы</h3>
        ${s.topInterests.length ? s.topInterests.map((i) => `<div class="bar"><span>${esc(i.name)}</span><div class="bar__track"><div class="bar__fill" style="width:${Math.round(i.profiles / max * 100)}%"></div></div><span class="muted">${i.profiles}</span></div>`).join('') : '<p class="muted">Анкет пока нет</p>'}
      </div>`;
  }

  async function renderAdminUsers(body) {
    const page = await Api.get(`/api/admin/users?page=${admin.page}&size=15&q=${encodeURIComponent(admin.query)}`);
    const badge = (u) => u.status === 'BLOCKED' ? '<span class="badge badge--blocked">Заблокирован</span>' : '<span class="badge badge--ok">Активен</span>';
    body.innerHTML = `
      <div class="toolbar"><input id="user-search" placeholder="Поиск по email" value="${esc(admin.query)}"><span class="muted small">Всего: ${page.totalElements}</span></div>
      <div class="table-wrap"><table>
        <thead><tr><th>ID</th><th>Email</th><th>Роль</th><th>Статус</th><th>Создан</th><th>Действия</th></tr></thead>
        <tbody>${page.content.map((u) => `<tr>
          <td>${u.id}</td><td>${esc(u.email)}</td>
          <td>${u.role === 'ADMIN' ? '<span class="badge badge--admin">ADMIN</span>' : 'USER'}</td>
          <td>${badge(u)}</td><td>${formatDate(u.createdAt)}</td>
          <td>${u.id === state.user.id ? '<span class="muted small">это вы</span>' : `
            ${u.status === 'BLOCKED'
              ? `<button class="btn btn--small btn--ghost" data-unblock="${u.id}">Разблокировать</button>`
              : `<button class="btn btn--small btn--ghost" data-block="${u.id}">Заблокировать</button>`}
            <button class="btn btn--small btn--ghost btn--danger-text" data-delete="${u.id}" data-email="${esc(u.email)}">Удалить</button>`}
          </td></tr>`).join('') || '<tr><td colspan="6" class="muted">Ничего не найдено</td></tr>'}
        </tbody></table></div>
      <div class="pager"><button class="btn btn--small btn--ghost" id="prev" ${page.page === 0 ? 'disabled' : ''}>← Назад</button><span class="muted small">Страница ${page.page + 1} из ${Math.max(1, page.totalPages)}</span><button class="btn btn--small btn--ghost" id="next" ${page.page + 1 >= page.totalPages ? 'disabled' : ''}>Вперёд →</button></div>`;

    let timer;
    $('#user-search').oninput = (event) => { clearTimeout(timer); timer = setTimeout(() => { admin.query = event.target.value.trim(); admin.page = 0; renderAdminTab(); }, 350); };
    $('#prev').onclick = () => { admin.page--; renderAdminTab(); };
    $('#next').onclick = () => { admin.page++; renderAdminTab(); };
    const act = async (fn, okMessage) => { try { await fn(); toast(okMessage, 'success'); renderAdminTab(); } catch (e) { handleError(e); } };
    $$('[data-block]').forEach((b) => (b.onclick = () => act(() => Api.post(`/api/admin/users/${b.dataset.block}/block`), 'Пользователь заблокирован')));
    $$('[data-unblock]').forEach((b) => (b.onclick = () => act(() => Api.post(`/api/admin/users/${b.dataset.unblock}/unblock`), 'Пользователь разблокирован')));
    $$('[data-delete]').forEach((b) => (b.onclick = async () => {
      if (await confirmDialog('Удалить пользователя?', `${b.dataset.email}: анкета, фото, лайки и матчи будут удалены.`, 'Удалить')) act(() => Api.del(`/api/admin/users/${b.dataset.delete}`), 'Пользователь удалён');
    }));
  }

  async function renderAdminInterests(body) {
    const interests = await Api.get('/api/interests');
    state.interests = interests;
    body.innerHTML = `
      <form class="card form inline-form" id="interest-add" style="margin-bottom:1rem">
        <input name="name" placeholder="Новый интерес" required maxlength="50">
        <select name="category">${options(CATEGORY, 'HOBBY')}</select>
        <button class="btn btn--primary btn--small" type="submit">Добавить</button>
        <span class="field-error" data-error-for="name"></span>
      </form>
      <div class="table-wrap"><table>
        <thead><tr><th>ID</th><th>Название</th><th>Категория</th><th>Действия</th></tr></thead>
        <tbody>${interests.map((i) => `<tr data-id="${i.id}">
          <td>${i.id}</td>
          <td><input value="${esc(i.name)}" maxlength="50" data-field="name"></td>
          <td><select data-field="category">${options(CATEGORY, i.category)}</select></td>
          <td><button class="btn btn--small btn--ghost" data-save="${i.id}">Сохранить</button><button class="btn btn--small btn--ghost btn--danger-text" data-remove="${i.id}" data-name="${esc(i.name)}">Удалить</button></td>
        </tr>`).join('')}</tbody></table></div>`;

    const refresh = async () => { state.interests = null; await renderAdminTab(); };
    $('#interest-add').onsubmit = async (event) => {
      event.preventDefault();
      const form = event.target;
      clearFieldErrors(form);
      try { await Api.post('/api/admin/interests', { name: form.name.value.trim(), category: form.category.value }); toast('Интерес добавлен', 'success'); await refresh(); }
      catch (e) { handleError(e, form); }
    };
    $$('[data-save]').forEach((b) => (b.onclick = async () => {
      const row = b.closest('tr');
      try {
        await Api.put(`/api/admin/interests/${b.dataset.save}`, { name: row.querySelector('[data-field=name]').value.trim(), category: row.querySelector('[data-field=category]').value });
        toast('Сохранено', 'success'); await refresh();
      } catch (e) { handleError(e); }
    }));
    $$('[data-remove]').forEach((b) => (b.onclick = async () => {
      if (!(await confirmDialog('Удалить интерес?', `«${b.dataset.name}» исчезнет из всех анкет.`, 'Удалить'))) return;
      try { await Api.del(`/api/admin/interests/${b.dataset.remove}`); toast('Интерес удалён'); await refresh(); } catch (e) { handleError(e); }
    }));
  }

  async function renderAdminSettings(body) {
    const [settings, strategies] = await Promise.all([Api.get('/api/admin/settings'), loadStrategies(true)]);
    body.innerHTML = `
      <div class="card settings-list">
        <h3>Алгоритм рекомендаций по умолчанию</h3>
        <p class="muted small">Используется, пока пользователь не выбрал другой в разделе «Знакомства».</p>
        ${strategies.map((s) => `<label class="strategy ${s.type === settings.defaultStrategy ? 'strategy--on' : ''}">
          <input type="radio" name="default-strategy" value="${s.type}" ${s.type === settings.defaultStrategy ? 'checked' : ''}>
          <span class="strategy__title">${esc(s.title)}</span><span class="strategy__desc">${esc(s.description)}</span></label>`).join('')}
      </div>`;
    $$('input[name=default-strategy]').forEach((radio) => (radio.onchange = async () => {
      try {
        await Api.put('/api/admin/settings', { defaultStrategy: radio.value });
        state.strategies = null;
        toast('Алгоритм по умолчанию изменён', 'success');
        renderAdminTab();
      } catch (e) { handleError(e); }
    }));
  }

  // ---------- запуск ----------
  document.addEventListener('DOMContentLoaded', async () => {
    await loadSession();
    window.addEventListener('hashchange', route);
    route();
  });

  return { navigate, state };
})();
