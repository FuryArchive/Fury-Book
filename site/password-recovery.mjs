// Public Auth API only. Never use a service-role key or the app's stored session here.
const AUTH_URL = 'https://xmmekahkeervdvrywmys.supabase.co/auth/v1';
const PUBLISHABLE_KEY = 'sb_publishable_L4tEqZbiS_rCCY4juHbllw_4HVbe6UI';
const INVALID_LINK = 'Ссылка недействительна, истекла или уже использована. Запросите новое письмо.';
const EMAIL_SENT = 'Если аккаунт с таким email существует, на него отправлена ссылка для восстановления. Проверьте почту и папку «Спам».';

export function parseRecoveryHash(hash) {
  const params = new URLSearchParams(hash.replace(/^#/, ''));
  if (params.has('error') || params.has('error_code')) return { mode: 'invalid' };
  if (params.get('type') !== 'recovery') return { mode: 'request' };
  const token = params.get('access_token');
  return token ? { mode: 'reset', token } : { mode: 'invalid' };
}

export function validatePassword(password, confirmation) {
  if (password.length < 8) return 'Пароль должен содержать минимум 8 символов.';
  if (password !== confirmation) return 'Пароли не совпадают.';
  return null;
}

function apiError(code, status) {
  let message = 'Не удалось выполнить запрос. Попробуйте ещё раз.';
  if (status === 429 || code === 'over_email_send_rate_limit' || code === 'over_request_rate_limit') {
    message = 'Слишком много запросов. Подождите немного и попробуйте снова.';
  } else if (status === 401 || ['bad_jwt', 'session_not_found', 'session_expired', 'otp_expired', 'refresh_token_not_found'].includes(code)) {
    message = INVALID_LINK;
  } else if (code === 'same_password') {
    message = 'Новый пароль должен отличаться от старого.';
  } else if (code === 'weak_password') {
    message = 'Пароль недостаточно надёжен. Используйте более длинный пароль с буквами и цифрами.';
  } else if (code === 'email_address_not_authorized') {
    message = 'Сервис пока не может отправить письмо на этот адрес. Администратору нужно настроить отправку почты в Supabase.';
  } else if (code === 'reauthentication_needed' || code === 'reauthentication_not_valid') {
    message = 'Нужно повторно подтвердить доступ к почте. Запросите новую ссылку восстановления.';
  }
  return Object.assign(new Error(message), { code, status });
}

export function createRecoveryClient(fetchImpl = globalThis.fetch) {
  async function request(path, method, body, token) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      let response;
      try {
        response = await fetchImpl(AUTH_URL + path, {
          method,
          headers: {
            apikey: PUBLISHABLE_KEY,
            'Content-Type': 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          ...(body === undefined ? {} : { body: JSON.stringify(body) }),
          credentials: 'omit',
          cache: 'no-store',
          referrerPolicy: 'no-referrer',
          signal: controller.signal,
        });
      } catch {
        throw new Error('Не удалось связаться с Fury Cloud. Проверьте интернет-соединение и повторите попытку.');
      }
      let data = {};
      try { data = await response.json() || {}; } catch { /* Empty 204 responses are valid. */ }
      if (!response.ok) throw apiError(data.error_code || (typeof data.code === 'string' ? data.code : undefined), response.status);
      return data;
    } finally {
      clearTimeout(timeout);
    }
  }
  function requireToken(token) {
    if (!token) throw apiError('session_not_found', 401);
  }
  return {
    async requestReset(email, redirectTo) {
      const cleanEmail = email.trim();
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(cleanEmail)) throw new Error('Введите корректный email.');
      try {
        await request('/recover?redirect_to=' + encodeURIComponent(redirectTo), 'POST', { email: cleanEmail });
      } catch (error) {
        // Do not reveal whether this email has an account.
        if (error.code !== 'user_not_found') throw error;
      }
    },
    async verifyRecovery(token) {
      requireToken(token);
      const user = await request('/user', 'GET', undefined, token);
      if (!user.id || !user.email) throw apiError('session_not_found', 401);
      return user;
    },
    async updatePassword(token, password) {
      requireToken(token);
      const validation = validatePassword(password, password);
      if (validation) throw new Error(validation);
      return request('/user', 'PUT', { password }, token);
    },
    async closeRecovery(token) {
      requireToken(token);
      await request('/logout?scope=local', 'POST', undefined, token);
    },
  };
}

export async function mountRecovery(doc, location, history, client = createRecoveryClient()) {
  const link = parseRecoveryHash(location.hash);
  // Consume the fragment before making any network request. Keep the token in memory only.
  history.replaceState(null, '', location.pathname);
  let token = link.token || null;
  let busy = false;
  let cooldownUntil = 0;
  const byId = id => doc.getElementById(id);
  const requestForm = byId('request-form');
  const resetForm = byId('reset-form');
  const status = byId('status');
  const sendButton = byId('send-button');
  const retryButton = byId('retry-button');
  const appUrl = new URL('./app/', location.href).href;
  byId('back-link').href = appUrl;

  function message(text, isError = false) {
    status.textContent = text;
    status.className = isError ? 'message error' : 'message';
  }
  function updateSendButton() {
    const seconds = Math.max(0, Math.ceil((cooldownUntil - Date.now()) / 1000));
    sendButton.disabled = busy || seconds > 0;
    sendButton.textContent = busy ? 'Отправляем…' : seconds ? `Отправить повторно (${seconds})` : 'Отправить ссылку';
  }
  function setBusy(value) {
    busy = value;
    byId('request-fields').disabled = value;
    byId('reset-fields').disabled = value;
    retryButton.disabled = value;
    updateSendButton();
  }
  function showRequest(text = '') {
    token = null;
    resetForm.hidden = true;
    retryButton.hidden = true;
    requestForm.hidden = false;
    byId('heading').textContent = 'Восстановление пароля';
    byId('description').textContent = 'Введите почту, указанную при регистрации в Fury Book.';
    message(text, Boolean(text));
  }
  async function verifyLink() {
    setBusy(true);
    requestForm.hidden = true;
    resetForm.hidden = true;
    retryButton.hidden = true;
    message('Проверяем ссылку…');
    try {
      const user = await client.verifyRecovery(token);
      byId('heading').textContent = 'Новый пароль';
      byId('description').textContent = `Аккаунт: ${user.email}`;
      resetForm.hidden = false;
      message('');
      byId('password').focus();
    } catch (error) {
      if (error.status === 401 || error.message === INVALID_LINK) {
        showRequest(INVALID_LINK);
      } else {
        message(error.message, true);
        retryButton.hidden = false;
      }
    } finally {
      setBusy(false);
    }
  }
  retryButton.addEventListener('click', () => { if (!busy) void verifyLink(); });
  requestForm.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy || Date.now() < cooldownUntil) return;
    setBusy(true);
    message('');
    try {
      await client.requestReset(byId('email').value, appUrl);
      message(EMAIL_SENT);
      cooldownUntil = Date.now() + 60000;
      const interval = setInterval(() => {
        updateSendButton();
        if (Date.now() >= cooldownUntil) clearInterval(interval);
      }, 1000);
    } catch (error) {
      message(error.message, true);
    } finally {
      setBusy(false);
    }
  });
  resetForm.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy || !token) return;
    const password = byId('password').value;
    const validation = validatePassword(password, byId('confirmation').value);
    if (validation) return message(validation, true);
    setBusy(true);
    message('Сохраняем пароль…');
    let completedToken;
    try {
      await client.updatePassword(token, password);
      completedToken = token;
      token = null;
      byId('password').value = '';
      byId('confirmation').value = '';
      resetForm.hidden = true;
      byId('heading').textContent = 'Пароль изменён';
      byId('description').textContent = 'Теперь можно войти в Fury Book с новым паролем.';
      byId('back-link').textContent = 'Вернуться в Fury Book';
      message('Готово. Ваши персонажи и данные аккаунта сохранены.');
      byId('back-link').focus();
    } catch (error) {
      if (error.status === 401 || error.message === INVALID_LINK) showRequest(INVALID_LINK);
      else message(error.message, true);
    } finally {
      setBusy(false);
    }
    // A failed logout must never turn a successful password change into a false failure.
    if (completedToken) {
      try { await client.closeRecovery(completedToken); } catch { /* Session is not persisted. */ }
    }
  });
  if (link.mode === 'reset') await verifyLink();
  else showRequest(link.mode === 'invalid' ? INVALID_LINK : '');
}

if (typeof window !== 'undefined' && typeof document !== 'undefined') {
  void mountRecovery(document, window.location, window.history);
}
