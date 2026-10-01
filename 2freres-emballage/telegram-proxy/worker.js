/**
 * 2 Frères Emballage — Cloudflare Worker (gratuit)
 *
 * 1) Relais Telegram → Apps Script : Apps Script répond 302 aux POST, ce qui bloque
 *    la file du bot. On répond 200 à Telegram et on transmet en arrière-plan.
 * 2) API de l'application : comptes (pseudo + mot de passe), avis/notes, « j'aime ».
 *    Base de données : Cloudflare D1 (binding DB, voir schema.sql).
 * 3) Modération des avis depuis Telegram (répondre / supprimer / bloquer).
 *
 * Variables : APPS_SCRIPT_URL, ADMIN_IDS ("id1,id2")  — Secrets : TG_SECRET, BOT_TOKEN
 */

const PBKDF2_ITERATIONS = 20000;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    try {
      if (url.pathname.startsWith('/api/')) return await api(request, env, ctx, url);
      if (request.method === 'POST') return await telegram(request, env, ctx);
      return new Response('ok');
    } catch (e) {
      return json({ error: 'server', message: String(e && e.message || e) }, 500);
    }
  }
};

/* ===================== Telegram ===================== */

async function telegram(request, env, ctx) {
  if (request.headers.get('X-Telegram-Bot-Api-Secret-Token') !== env.TG_SECRET) {
    return new Response('forbidden', { status: 403 });
  }
  const body = await request.text();
  let update = null;
  try { update = JSON.parse(body); } catch (e) {}

  if (update && await handleReviewAdmin(update, env)) return new Response('ok');

  ctx.waitUntil(fetch(env.APPS_SCRIPT_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body,
    redirect: 'manual'
  }));
  return new Response('ok');
}

function isAdmin(env, id) {
  return String(env.ADMIN_IDS || '').split(',').map(s => s.trim()).includes(String(id));
}

async function tg(env, method, payload) {
  const r = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  return r.json().catch(() => ({}));
}

/** Boutons sous les avis (rv…) et réponse de l'admin. Retourne true si traité ici. */
async function handleReviewAdmin(update, env) {
  const q = update.callback_query;
  if (q && /^rv[drbu]:/.test(q.data || '')) {
    await tg(env, 'answerCallbackQuery', { callback_query_id: q.id });
    if (!isAdmin(env, q.from.id)) return true;
    const chatId = q.message.chat.id;
    const [action, raw] = q.data.split(':');
    const id = Number(raw);
    if (action === 'rvd') {
      await env.DB.prepare('DELETE FROM reviews WHERE id = ?').bind(id).run();
      await tg(env, 'sendMessage', { chat_id: chatId, text: '🗑️ التعليق تمسح.' });
    } else if (action === 'rvr') {
      await env.DB.prepare('INSERT OR REPLACE INTO admin_state (chat_id, review_id) VALUES (?, ?)')
        .bind(String(chatId), id).run();
      await tg(env, 'sendMessage', {
        chat_id: chatId,
        text: '✍️ اكتب الردّ تاع المحل على هاذ التعليق (ولا /cancel):',
        reply_markup: { force_reply: true }
      });
    } else if (action === 'rvb') {
      await env.DB.prepare('UPDATE users SET banned = 1 WHERE id = ?').bind(id).run();
      await env.DB.prepare('DELETE FROM sessions WHERE user_id = ?').bind(id).run();
      await tg(env, 'sendMessage', { chat_id: chatId, text: '🚫 المستخدم تبلوكا. ما يقدرش يكتب تعليقات.',
        reply_markup: { inline_keyboard: [[{ text: '↩️ نحّي البلوك', callback_data: 'rvu:' + id }]] } });
    } else if (action === 'rvu') {
      await env.DB.prepare('UPDATE users SET banned = 0 WHERE id = ?').bind(id).run();
      await tg(env, 'sendMessage', { chat_id: chatId, text: '✅ البلوك تنحّى.' });
    }
    return true;
  }

  const m = update.message;
  if (m && m.text && isAdmin(env, m.from.id)) {
    const chatId = String(m.chat.id);
    const st = await env.DB.prepare('SELECT review_id FROM admin_state WHERE chat_id = ?').bind(chatId).first();
    if (!st) return false;
    await env.DB.prepare('DELETE FROM admin_state WHERE chat_id = ?').bind(chatId).run();
    const text = m.text.trim();
    if (text === '/cancel' || text === '❌ إلغاء') {
      await tg(env, 'sendMessage', { chat_id: chatId, text: '❌ تلغى الردّ.' });
      return true;
    }
    await env.DB.prepare('UPDATE reviews SET reply = ? WHERE id = ?').bind(text.slice(0, 1000), st.review_id).run();
    await tg(env, 'sendMessage', { chat_id: chatId, text: '✅ الردّ تحط، يبان تحت التعليق في التطبيق.' });
    return true;
  }
  return false;
}

async function notifyAdmins(env, text, keyboard) {
  const ids = String(env.ADMIN_IDS || '').split(',').map(s => s.trim()).filter(Boolean);
  await Promise.all(ids.map(id => tg(env, 'sendMessage', { chat_id: id, text, reply_markup: keyboard })));
}

/* ===================== API ===================== */

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS'
    }
  });
}

const err = (code, status) => json({ error: code }, status);

async function api(request, env, ctx, url) {
  if (request.method === 'OPTIONS') return json({});
  const path = url.pathname.replace(/\/+$/, '');
  const method = request.method;
  const body = method === 'POST' ? await request.json().catch(() => ({})) : {};

  if (path === '/api/register' && method === 'POST') return register(env, body);
  if (path === '/api/login' && method === 'POST') return login(env, body);
  if (path === '/api/ratings' && method === 'GET') return ratings(env);
  if (path === '/api/reviews' && method === 'GET') {
    const user = await auth(request, env);
    return listReviews(env, Number(url.searchParams.get('product')), user);
  }

  const user = await auth(request, env);
  if (!user) return err('auth', 401);

  if (path === '/api/me' && method === 'GET') return json({ user: publicUser(user) });
  if (path === '/api/logout' && method === 'POST') {
    await env.DB.prepare('DELETE FROM sessions WHERE token = ?').bind(user.token).run();
    return json({ ok: true });
  }
  if (path === '/api/reviews' && method === 'POST') return saveReview(env, ctx, user, body);

  let m = path.match(/^\/api\/reviews\/(\d+)\/like$/);
  if (m && method === 'POST') return toggleLike(env, user, Number(m[1]));
  m = path.match(/^\/api\/reviews\/(\d+)$/);
  if (m && method === 'DELETE') {
    const r = await env.DB.prepare('DELETE FROM reviews WHERE id = ? AND user_id = ?').bind(Number(m[1]), user.id).run();
    return r.meta.changes ? json({ ok: true }) : err('not_found', 404);
  }
  return err('not_found', 404);
}

/* ---------- Comptes ---------- */

const USERNAME_RE = /^[A-Za-z0-9_.؀-ۿ]{3,20}$/;

function publicUser(u) { return { id: u.id, username: u.username }; }

function hex(buf) { return [...new Uint8Array(buf)].map(b => b.toString(16).padStart(2, '0')).join(''); }

async function hashPassword(password, saltHex) {
  const salt = new Uint8Array(saltHex.match(/../g).map(h => parseInt(h, 16)));
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: PBKDF2_ITERATIONS }, key, 256);
  return hex(bits);
}

function randomHex(bytes) { return hex(crypto.getRandomValues(new Uint8Array(bytes))); }

async function newSession(env, user) {
  const token = randomHex(32);
  await env.DB.prepare('INSERT INTO sessions (token, user_id, created_at) VALUES (?, ?, ?)').bind(token, user.id, Date.now()).run();
  return json({ token, user: publicUser(user) });
}

async function register(env, body) {
  const username = String(body.username || '').trim();
  const password = String(body.password || '');
  if (!USERNAME_RE.test(username)) return err('bad_username', 400);
  if (password.length < 6 || password.length > 100) return err('bad_password', 400);
  const exists = await env.DB.prepare('SELECT id FROM users WHERE username = ?').bind(username).first();
  if (exists) return err('username_taken', 409);
  const salt = randomHex(16);
  const hash = await hashPassword(password, salt);
  const r = await env.DB.prepare('INSERT INTO users (username, pass_hash, salt, created_at) VALUES (?, ?, ?, ?)')
    .bind(username, hash, salt, Date.now()).run();
  return newSession(env, { id: r.meta.last_row_id, username });
}

async function login(env, body) {
  const username = String(body.username || '').trim();
  const password = String(body.password || '');
  const u = await env.DB.prepare('SELECT * FROM users WHERE username = ?').bind(username).first();
  if (!u || await hashPassword(password, u.salt) !== u.pass_hash) return err('bad_credentials', 401);
  if (u.banned) return err('banned', 403);
  return newSession(env, u);
}

async function auth(request, env) {
  const h = request.headers.get('Authorization') || '';
  const token = h.startsWith('Bearer ') ? h.slice(7).trim() : '';
  if (!token) return null;
  const u = await env.DB.prepare(
    'SELECT u.id, u.username, u.banned FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?'
  ).bind(token).first();
  return u ? { ...u, token } : null;
}

/* ---------- Avis ---------- */

async function ratings(env) {
  const { results } = await env.DB.prepare(
    'SELECT product_id, AVG(rating) AS avg, COUNT(*) AS count FROM reviews GROUP BY product_id'
  ).all();
  const out = {};
  for (const r of results) out[r.product_id] = { avg: Math.round(r.avg * 10) / 10, count: r.count };
  return json({ ratings: out });
}

async function listReviews(env, productId, user) {
  if (!productId) return err('bad_product', 400);
  const uid = user ? user.id : 0;
  const { results } = await env.DB.prepare(
    `SELECT r.id, r.rating, r.text, r.reply, r.created_at, r.user_id, u.username,
            (SELECT COUNT(*) FROM review_likes l WHERE l.review_id = r.id) AS likes,
            EXISTS(SELECT 1 FROM review_likes l WHERE l.review_id = r.id AND l.user_id = ?) AS liked
       FROM reviews r JOIN users u ON u.id = r.user_id
      WHERE r.product_id = ?
      ORDER BY (r.user_id = ?) DESC, likes DESC, r.created_at DESC
      LIMIT 200`
  ).bind(uid, productId, uid).all();
  let sum = 0;
  const dist = [0, 0, 0, 0, 0];
  const reviews = results.map(r => {
    sum += r.rating;
    dist[r.rating - 1]++;
    return {
      id: r.id, username: r.username, rating: r.rating, text: r.text, reply: r.reply || '',
      createdAt: r.created_at, likes: r.likes, likedByMe: !!r.liked, mine: r.user_id === uid
    };
  });
  const count = reviews.length;
  return json({
    summary: { avg: count ? Math.round(sum / count * 10) / 10 : 0, count, dist },
    reviews,
    me: user ? publicUser(user) : null
  });
}

async function saveReview(env, ctx, user, body) {
  if (user.banned) return err('banned', 403);
  const productId = Number(body.productId);
  const rating = Number(body.rating);
  const text = String(body.text || '').trim().slice(0, 1000);
  if (!productId) return err('bad_product', 400);
  if (!(rating >= 1 && rating <= 5)) return err('bad_rating', 400);
  const now = Date.now();
  const prev = await env.DB.prepare('SELECT id FROM reviews WHERE product_id = ? AND user_id = ?').bind(productId, user.id).first();
  let id;
  if (prev) {
    id = prev.id;
    await env.DB.prepare('UPDATE reviews SET rating = ?, text = ?, updated_at = ? WHERE id = ?').bind(rating, text, now, id).run();
  } else {
    const r = await env.DB.prepare(
      'INSERT INTO reviews (product_id, user_id, rating, text, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)'
    ).bind(productId, user.id, rating, text, now, now).run();
    id = r.meta.last_row_id;
  }
  const stars = '⭐'.repeat(Math.round(rating));
  const name = String(body.productName || ('#' + productId)).slice(0, 100);
  ctx.waitUntil(notifyAdmins(env,
    (prev ? '✏️ تعليق تبدّل' : '💬 تعليق جديد') + ' على: ' + name + '\n' + stars + ' — 👤 ' + user.username +
    (text ? '\n\n' + text : ''),
    { inline_keyboard: [
      [{ text: '↩️ ردّ', callback_data: 'rvr:' + id }, { text: '🗑️ امسح', callback_data: 'rvd:' + id }],
      [{ text: '🚫 بلوكي ' + user.username, callback_data: 'rvb:' + user.id }]
    ] }).catch(() => {}));
  return json({ ok: true, id });
}

async function toggleLike(env, user, reviewId) {
  const exists = await env.DB.prepare('SELECT 1 FROM review_likes WHERE review_id = ? AND user_id = ?').bind(reviewId, user.id).first();
  if (exists) {
    await env.DB.prepare('DELETE FROM review_likes WHERE review_id = ? AND user_id = ?').bind(reviewId, user.id).run();
  } else {
    const r = await env.DB.prepare('SELECT 1 FROM reviews WHERE id = ?').bind(reviewId).first();
    if (!r) return err('not_found', 404);
    await env.DB.prepare('INSERT INTO review_likes (review_id, user_id) VALUES (?, ?)').bind(reviewId, user.id).run();
  }
  const c = await env.DB.prepare('SELECT COUNT(*) AS n FROM review_likes WHERE review_id = ?').bind(reviewId).first();
  return json({ liked: !exists, likes: c.n });
}
