/**
 * Тонкая обёртка над fetch для REST API Matchly.
 * - подставляет JWT из localStorage;
 * - превращает ответы application/problem+json в исключение ApiError с полями status, detail и errors;
 * - загружает защищённые изображения (фото анкет) через fetch с токеном и отдаёт blob-URL.
 */
const Api = (() => {
  const TOKEN_KEY = 'matchly.token';

  class ApiError extends Error {
    constructor(problem, status) {
      super((problem && (problem.detail || problem.title)) || ('Ошибка ' + status));
      this.status = status;
      this.problem = problem || {};
      this.errors = (problem && problem.errors) || [];
    }
  }

  const getToken = () => localStorage.getItem(TOKEN_KEY);
  const setToken = (token) => token ? localStorage.setItem(TOKEN_KEY, token) : localStorage.removeItem(TOKEN_KEY);

  async function request(method, url, body) {
    const headers = {};
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;
    let payload = body;
    if (body !== undefined && body !== null && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
    const response = await fetch(url, { method, headers, body: payload });
    if (response.status === 204) return null;
    const contentType = response.headers.get('content-type') || '';
    const data = contentType.includes('json') ? await response.json().catch(() => null) : null;
    if (!response.ok) {
      // токен есть, но сервер его не принимает: аккаунт удалён, заблокирован или токен истёк
      if (response.status === 401 && token && !url.startsWith('/api/auth/login')) {
        setToken(null);
        window.dispatchEvent(new CustomEvent('matchly:session-expired'));
      }
      throw new ApiError(data, response.status);
    }
    return data;
  }

  const imageCache = new Map();

  /** Возвращает blob-URL защищённого изображения (или null, если фото нет). */
  async function imageUrl(url) {
    if (!url) return null;
    if (imageCache.has(url)) return imageCache.get(url);
    try {
      const response = await fetch(url, { headers: { Authorization: 'Bearer ' + getToken() } });
      if (!response.ok) return null;
      const objectUrl = URL.createObjectURL(await response.blob());
      imageCache.set(url, objectUrl);
      return objectUrl;
    } catch (e) {
      return null;
    }
  }

  return {
    get: (url) => request('GET', url),
    post: (url, body) => request('POST', url, body),
    put: (url, body) => request('PUT', url, body),
    del: (url, body) => request('DELETE', url, body),
    getToken,
    setToken,
    imageUrl,
    ApiError,
  };
})();
