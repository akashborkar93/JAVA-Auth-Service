(() => {
  'use strict';

  const views = ['login', 'register', 'profile'];
  const messageBox = document.getElementById('message');

  function show(view) {
    views.forEach((v) => {
      document.getElementById('view-' + v).hidden = v !== view;
    });
  }

  function notify(text, type) {
    if (!text) {
      messageBox.hidden = true;
      return;
    }
    messageBox.textContent = text;
    messageBox.className = 'message ' + type;
    messageBox.hidden = false;
  }

  // The CSRF token is fetched fresh before each state-changing request because it is tied to the session.
  async function api(method, path, body) {
    const headers = { Accept: 'application/json' };
    if (method !== 'GET') {
      const csrf = await (await fetch('/api/auth/csrf', { credentials: 'same-origin' })).json();
      headers[csrf.headerName] = csrf.token;
    }
    if (body) {
      headers['Content-Type'] = 'application/json';
    }
    const response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body ? JSON.stringify(body) : undefined,
    });
    let data = null;
    if ((response.headers.get('Content-Type') || '').includes('json')) {
      data = await response.json();
    }
    return { ok: response.ok, status: response.status, data };
  }

  function errorText(result) {
    if (result.data && result.data.errors) {
      return Object.entries(result.data.errors)
        .map(([field, msg]) => field + ' ' + msg)
        .join('; ');
    }
    if (result.data && result.data.detail) {
      return result.data.detail;
    }
    return 'Something went wrong (HTTP ' + result.status + ').';
  }

  function showProfile(profile) {
    document.getElementById('profile-username').textContent = profile.username;
    document.getElementById('profile-role').textContent = profile.role;
    document.getElementById('profile-created').textContent = new Date(profile.createdAt).toLocaleString();
    show('profile');
  }

  function formData(form) {
    return Object.fromEntries(new FormData(form).entries());
  }

  async function submit(form, work) {
    const button = form.querySelector('button');
    button.disabled = true;
    notify(null);
    try {
      await work();
    } catch (e) {
      notify('Could not reach the server.', 'error');
    } finally {
      button.disabled = false;
    }
  }

  document.getElementById('form-register').addEventListener('submit', (event) => {
    event.preventDefault();
    const form = event.target;
    submit(form, async () => {
      const result = await api('POST', '/api/auth/register', formData(form));
      if (result.ok) {
        form.reset();
        show('login');
        notify('Account created. Please sign in.', 'ok');
      } else {
        notify(errorText(result), 'error');
      }
    });
  });

  document.getElementById('form-login').addEventListener('submit', (event) => {
    event.preventDefault();
    const form = event.target;
    submit(form, async () => {
      const result = await api('POST', '/api/auth/login', formData(form));
      form.reset();
      if (result.ok) {
        showProfile(result.data);
      } else {
        notify(errorText(result), 'error');
      }
    });
  });

  document.getElementById('btn-logout').addEventListener('click', async () => {
    await api('POST', '/api/auth/logout');
    show('login');
    notify('You have been signed out.', 'ok');
  });

  document.querySelectorAll('a[data-view]').forEach((link) => {
    link.addEventListener('click', (event) => {
      event.preventDefault();
      notify(null);
      show(link.dataset.view);
    });
  });

  // Restore the session on page load.
  api('GET', '/api/auth/me')
    .then((result) => (result.ok ? showProfile(result.data) : show('login')))
    .catch(() => show('login'));
})();
