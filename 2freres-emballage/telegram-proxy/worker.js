/**
 * 2 Frères Emballage — Cloudflare Worker (gratuit)
 *
 * 1) Relais Telegram → Apps Script : Apps Script répond 302 aux POST, ce qui bloque
 *    la file du bot. On répond 200 à Telegram et on transmet en arrière-plan.
 * 2) API de l'application : catalogue, comptes (pseudo + mot de passe), avis/notes,
 *    « j'aime », notifications (générales ou pour un seul client), messages de support.
 * 3bis) API de l'application admin (/api/admin/*) : produits + photos, notifications,
 *    support, avis, utilisateurs, statistiques.
 *    Base de données : Cloudflare D1 (binding DB, voir schema.sql + migrations_*.sql).
 * 3) Modération des avis depuis Telegram (répondre / supprimer / bloquer).
 *
 * 4) Connexion Firebase (/api/firebase-login) : e-mail vérifié ou Google ; voir migrations_003_firebase.sql.
 * 6) Notifications push instantanées (FCM) : secret FCM_SERVICE_ACCOUNT, table push_tokens (migrations_004_push.sql).
 * 5) Connexion admin (/api/admin/google-login) : Google uniquement, e-mail autorisé dans users.email
 *    (role = 'admin'). Les admins ne peuvent plus se connecter par mot de passe.
 *
 * Variables : APPS_SCRIPT_URL, ADMIN_IDS ("id1,id2"), FIREBASE_PROJECT_ID (optionnel)
 * Secrets : TG_SECRET, BOT_TOKEN
 */

const PBKDF2_ITERATIONS = 20000;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    try {
      if (url.pathname.startsWith('/api/')) return await api(request, env, ctx, url);
      if (url.pathname.startsWith('/img/')) return await image(env, url.pathname.slice(5));
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
  if (path === '/api/admin/images' && method === 'POST') {
    const admin = await auth(request, env);
    if (!admin || admin.role !== 'admin') return err('forbidden', 403);
    return uploadImage(request, env, url);
  }
  const body = method === 'POST' ? await request.json().catch(() => ({})) : {};

  if (path === '/api/register' && method === 'POST') return register(env, body);
  if (path === '/api/login' && method === 'POST') return login(env, body);
  if (path === '/api/firebase-login' && method === 'POST') return firebaseLogin(env, body);
  if (path === '/api/admin/google-login' && method === 'POST') return adminGoogleLogin(env, body);
  if (path === '/api/push/register' && method === 'POST') return pushRegister(env, body, await auth(request, env));
  if (path === '/api/ratings' && method === 'GET') return ratings(env);
  if (path === '/api/catalog' && method === 'GET') return catalog(env, url, await auth(request, env));
  if (path === '/api/reviews' && method === 'GET') {
    const user = await auth(request, env);
    return listReviews(env, Number(url.searchParams.get('product')), user);
  }

  const user = await auth(request, env);
  if (!user) return err('auth', 401);

  ctx.waitUntil(env.DB.prepare('UPDATE users SET last_seen = ? WHERE id = ?').bind(Date.now(), user.id).run().catch(() => {}));
  if (path.startsWith('/api/admin/')) {
    if (user.role !== 'admin') return err('forbidden', 403);
    return adminApi(env, ctx, url, path, method, body, user);
  }

  if (path === '/api/me' && method === 'GET') return json({ user: publicUser(user) });
  if (path === '/api/me/password' && method === 'POST') return changePassword(env, user, body);
  if (path === '/api/support' && method === 'GET') return supportThread(env, user.id, 'user');
  if (path === '/api/support' && method === 'POST') return supportSend(env, ctx, user, body);
  if (path === '/api/support/unread' && method === 'GET') {
    const r = await env.DB.prepare('SELECT COUNT(*) AS n FROM messages WHERE user_id = ? AND from_admin = 1 AND read_by_user = 0').bind(user.id).first();
    return json({ unread: r.n });
  }
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

function publicUser(u) { return { id: u.id, username: u.username, role: u.role || 'user' }; }

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
  return newSession(env, { id: r.meta.last_row_id, username, role: 'user' });
}

async function login(env, body) {
  const username = String(body.username || '').trim();
  const password = String(body.password || '');
  const u = await env.DB.prepare('SELECT * FROM users WHERE username = ?').bind(username).first();
  if (!u || await hashPassword(password, u.salt) !== u.pass_hash) return err('bad_credentials', 401);
  if (u.banned) return err('banned', 403);
  if (u.role === 'admin') return err('use_google', 403);   // les admins se connectent avec Google
  return newSession(env, u);
}

/* ---------- Connexion Firebase (e-mail vérifié ou Google) ----------
 * L'application envoie l'ID token Firebase ; on vérifie sa signature avec les clés publiques
 * de Google, puis on ouvre une session comme pour un compte classique.
 * Variable optionnelle : FIREBASE_PROJECT_ID (sinon la valeur ci-dessous). */
const FIREBASE_PROJECT_ID = 'sample-firebase-ai-app-2c019';
const FIREBASE_JWKS = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const GOOGLE_JWKS = 'https://www.googleapis.com/oauth2/v3/certs';
const jwksCache = {};

function b64url(s) {
  s = s.replace(/-/g, '+').replace(/_/g, '/');
  while (s.length % 4) s += '=';
  return Uint8Array.from(atob(s), c => c.charCodeAt(0));
}

async function jwksKey(url, kid) {
  let c = jwksCache[url];
  if (!c || Date.now() > c.until) {
    const r = await fetch(url);
    if (!r.ok) throw new Error('jwks');
    const maxAge = Number((/max-age=(\d+)/.exec(r.headers.get('Cache-Control') || '') || [])[1] || 3600);
    c = jwksCache[url] = { keys: (await r.json()).keys || [], until: Date.now() + maxAge * 1000 };
  }
  return c.keys.find(k => k.kid === kid);
}

/** Vérifie la signature RS256 d'un JWT avec les clés publiques (JWKS) et renvoie ses claims. */
async function verifyJwt(token, jwksUrl) {
  const parts = String(token).split('.');
  if (parts.length !== 3) throw new Error('format');
  const dec = new TextDecoder();
  const header = JSON.parse(dec.decode(b64url(parts[0])));
  const claims = JSON.parse(dec.decode(b64url(parts[1])));
  if (header.alg !== 'RS256' || !header.kid) throw new Error('alg');
  const jwk = await jwksKey(jwksUrl, header.kid);
  if (!jwk) throw new Error('kid');
  const key = await crypto.subtle.importKey('jwk', { kty: jwk.kty, n: jwk.n, e: jwk.e, alg: 'RS256', ext: true },
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
  const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, b64url(parts[2]),
    new TextEncoder().encode(parts[0] + '.' + parts[1]));
  if (!ok) throw new Error('signature');
  const now = Math.floor(Date.now() / 1000);
  if (!(claims.exp > now) || !(claims.iat <= now + 60)) throw new Error('time');
  return claims;
}

async function verifyFirebaseToken(env, token) {
  const projectId = env.FIREBASE_PROJECT_ID || FIREBASE_PROJECT_ID;
  const claims = await verifyJwt(token, FIREBASE_JWKS);
  const now = Math.floor(Date.now() / 1000);
  if (claims.aud !== projectId || claims.iss !== 'https://securetoken.google.com/' + projectId) throw new Error('aud');
  if (!(claims.auth_time <= now + 60)) throw new Error('time');
  if (!claims.sub || typeof claims.sub !== 'string' || claims.sub.length > 128) throw new Error('sub');
  return claims;
}

/** Pseudo public à partir du nom affiché (jamais de l'adresse e-mail). */
function usernameFrom(name) {
  let base = String(name || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .replace(/\s+/g, '_').replace(/[^A-Za-z0-9_.\u0600-\u06FF]/g, '').slice(0, 15);
  if (base.length < 3) base = 'client';
  return base;
}

async function firebaseLogin(env, body) {
  let c;
  try { c = await verifyFirebaseToken(env, body.idToken); } catch (e) { return err('bad_token', 401); }
  const provider = c.firebase && c.firebase.sign_in_provider;
  if (provider !== 'google.com' && provider !== 'password') return err('bad_provider', 400);
  if (provider === 'password' && c.email_verified !== true) return err('email_not_verified', 403);

  let u = await env.DB.prepare('SELECT * FROM users WHERE firebase_uid = ?').bind(c.sub).first();
  if (!u) {
    const base = usernameFrom(body.displayName || c.name);
    let username = base;
    for (let i = 0; i < 8; i++) {
      const taken = await env.DB.prepare('SELECT id FROM users WHERE username = ?').bind(username).first();
      if (!taken) break;
      username = base + '_' + (100 + Math.floor(Math.random() * 9900));
    }
    // Pas de mot de passe local : ce compte ne peut se connecter que par Firebase.
    const r = await env.DB.prepare(
      'INSERT INTO users (username, pass_hash, salt, created_at, firebase_uid, email) VALUES (?, ?, ?, ?, ?, ?)'
    ).bind(username, '!firebase', randomHex(16), Date.now(), c.sub, String(c.email || '').slice(0, 200)).run();
    u = { id: r.meta.last_row_id, username, role: 'user', banned: 0 };
  } else if (c.email && c.email !== u.email) {
    await env.DB.prepare('UPDATE users SET email = ? WHERE id = ?').bind(String(c.email).slice(0, 200), u.id).run();
  }
  if (u.banned) return err('banned', 403);
  return newSession(env, u);
}

/* ---------- Connexion admin : Google uniquement ----------
 * L'application admin envoie le Google ID token (Credential Manager). On vérifie la signature
 * (clés Google), l'audience (client Web OAuth du projet), l'e-mail vérifié, puis on cherche
 * un compte admin avec cette adresse (colonne users.email). Aucun mot de passe.
 * Variable optionnelle : GOOGLE_WEB_CLIENT_ID. */
const GOOGLE_WEB_CLIENT_ID = '956106850630-19isn3en66ig2h9sickrvsvno3q207o5.apps.googleusercontent.com';

async function adminGoogleLogin(env, body) {
  let c;
  try { c = await verifyJwt(body.idToken, GOOGLE_JWKS); } catch (e) { return err('bad_token', 401); }
  if (c.aud !== (env.GOOGLE_WEB_CLIENT_ID || GOOGLE_WEB_CLIENT_ID)) return err('bad_token', 401);
  if (c.iss !== 'https://accounts.google.com' && c.iss !== 'accounts.google.com') return err('bad_token', 401);
  if (c.email_verified !== true || !c.email) return err('bad_token', 401);
  const u = await env.DB.prepare("SELECT * FROM users WHERE role = 'admin' AND lower(email) = lower(?)")
    .bind(String(c.email)).first();
  if (!u) return err('not_admin', 403);
  if (u.banned) return err('banned', 403);
  return newSession(env, u);
}

async function auth(request, env) {
  const h = request.headers.get('Authorization') || '';
  const token = h.startsWith('Bearer ') ? h.slice(7).trim() : '';
  if (!token) return null;
  const u = await env.DB.prepare(
    'SELECT u.id, u.username, u.banned, u.role FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?'
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
  ctx.waitUntil(pushToAdmins(env, { type: 'review', id: String(id), productId: String(productId),
    title: stars + ' ' + user.username + ' — ' + name, body: text.slice(0, 300) }).catch(() => {}));
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

async function changePassword(env, user, body) {
  if (user.role === 'admin') return err('use_google', 403);
  const u = await env.DB.prepare('SELECT * FROM users WHERE id = ?').bind(user.id).first();
  if (await hashPassword(String(body.oldPassword || ''), u.salt) !== u.pass_hash) return err('bad_credentials', 401);
  const pw = String(body.newPassword || '');
  if (pw.length < 6 || pw.length > 100) return err('bad_password', 400);
  const salt = randomHex(16);
  await env.DB.prepare('UPDATE users SET pass_hash = ?, salt = ? WHERE id = ?').bind(await hashPassword(pw, salt), salt, user.id).run();
  await env.DB.prepare('DELETE FROM sessions WHERE user_id = ? AND token != ?').bind(user.id, user.token).run();
  return json({ ok: true });
}

/* ===================== Catalogue (application client) ===================== */

const STORE = { name: '2 Frères Emballage', currency: 'DA' };

function imageUrl(url, img) {
  if (!img) return '';
  return /^https?:/.test(img) ? img : url.origin + '/img/' + img;
}

function productOut(url, p) {
  return {
    id: p.id, name: p.name, price: p.price, oldPrice: p.old_price, category: p.category,
    description: p.description, image: imageUrl(url, p.image), isOffer: !!p.is_offer, hidden: !!p.hidden,
    createdAt: p.created_at, updatedAt: p.updated_at
  };
}

function notifOut(url, n) {
  return {
    id: n.id, title: n.title, body: n.body, productId: n.product_id, image: imageUrl(url, n.image),
    link: n.link, userId: n.user_id, createdAt: new Date(n.created_at).toISOString()
  };
}

async function catalog(env, url, user) {
  const [products, notifs] = await Promise.all([
    env.DB.prepare('SELECT * FROM products WHERE hidden = 0 ORDER BY id DESC').all(),
    env.DB.prepare('SELECT * FROM notifications WHERE user_id IS NULL OR user_id = ? ORDER BY id DESC LIMIT 40')
      .bind(user ? user.id : -1).all()
  ]);
  return json({
    store: STORE.name, currency: STORE.currency, updatedAt: new Date().toISOString(),
    products: products.results.map(p => productOut(url, p)),
    notifications: notifs.results.map(n => notifOut(url, n))
  });
}

async function image(env, id) {
  if (!/^[a-f0-9]{8,64}$/.test(id)) return new Response('not found', { status: 404 });
  const r = await env.DB.prepare('SELECT mime, data FROM images WHERE id = ?').bind(id).first();
  if (!r) return new Response('not found', { status: 404 });
  return new Response(new Uint8Array(r.data), {
    headers: { 'Content-Type': r.mime, 'Cache-Control': 'public, max-age=31536000, immutable', 'Access-Control-Allow-Origin': '*' }
  });
}

async function uploadImage(request, env, url) {
  const mime = (request.headers.get('Content-Type') || 'image/jpeg').split(';')[0];
  if (!/^image\/(jpeg|png|webp)$/.test(mime)) return err('bad_image', 400);
  const buf = await request.arrayBuffer();
  if (!buf.byteLength || buf.byteLength > 1800000) return err('image_too_big', 413);
  const id = randomHex(16);
  await env.DB.prepare('INSERT INTO images (id, mime, data, created_at) VALUES (?, ?, ?, ?)').bind(id, mime, buf, Date.now()).run();
  return json({ id, url: url.origin + '/img/' + id });
}

async function deleteImageIfLocal(env, img) {
  if (img && /^[a-f0-9]{8,64}$/.test(img)) await env.DB.prepare('DELETE FROM images WHERE id = ?').bind(img).run();
}

/** Accepte un id d'image ou une URL /img/<id> renvoyée par l'upload. */
function imageRef(v) {
  const s = String(v || '').trim();
  const m = s.match(/\/img\/([a-f0-9]{8,64})$/);
  return m ? m[1] : s;
}

/* ===================== Push (Firebase Cloud Messaging) =====================
 * Secret FCM_SERVICE_ACCOUNT = contenu JSON de la clé « compte de service » Firebase.
 * Sans ce secret, rien n'est envoyé (les applications gardent leur vérification périodique). */
const PUBLIC_ORIGIN = 'https://flat-violet-af44.yacincianai.workers.dev';
let fcmAuth = { token: null, until: 0 };

function pemToDer(pem) {
  const b = atob(String(pem).replace(/-----[^-]+-----/g, '').replace(/\s+/g, ''));
  return Uint8Array.from(b, c => c.charCodeAt(0));
}
const b64urlStr = s => btoa(s).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
const b64urlBuf = buf => b64urlStr(String.fromCharCode(...new Uint8Array(buf)));

/** Jeton OAuth Google à partir du compte de service (JWT signé RS256), mis en cache ~55 min. */
async function googleAccessToken(env, scope) {
  const sa = JSON.parse(env.FCM_SERVICE_ACCOUNT);
  const now = Math.floor(Date.now() / 1000);
  const head = b64urlStr(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claim = b64urlStr(JSON.stringify({ iss: sa.client_email, scope, aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600 }));
  const key = await crypto.subtle.importKey('pkcs8', pemToDer(sa.private_key), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(head + '.' + claim));
  const r = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=' + head + '.' + claim + '.' + b64urlBuf(sig)
  });
  const j = await r.json();
  if (!j.access_token) throw new Error('oauth: ' + JSON.stringify(j).slice(0, 200));
  return { token: j.access_token, project: sa.project_id, expiresIn: j.expires_in || 3600 };
}

async function fcmSend(env, target) {
  if (!env.FCM_SERVICE_ACCOUNT) return { skipped: true };
  if (!fcmAuth.token || Date.now() > fcmAuth.until) {
    const t = await googleAccessToken(env, 'https://www.googleapis.com/auth/firebase.messaging');
    fcmAuth = { token: t.token, project: t.project, until: Date.now() + (t.expiresIn - 300) * 1000 };
  }
  const r = await fetch('https://fcm.googleapis.com/v1/projects/' + fcmAuth.project + '/messages:send', {
    method: 'POST',
    headers: { 'Authorization': 'Bearer ' + fcmAuth.token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ message: { ...target, android: { priority: 'high' } } })
  });
  if (r.ok) return { ok: true };
  const e = await r.json().catch(() => ({}));
  const code = JSON.stringify(e);
  return { ok: false, gone: r.status === 404 || /UNREGISTERED|INVALID_ARGUMENT.*token/i.test(code), status: r.status };
}

/** Données uniquement (chaînes) : l'application affiche elle-même la notification et ouvre le bon écran. */
function fcmData(data) {
  const out = {};
  for (const [k, v] of Object.entries(data)) if (v !== undefined && v !== null) out[k] = String(v);
  return out;
}

async function pushToTokens(env, rows, data) {
  await Promise.all(rows.map(async ({ token }) => {
    const r = await fcmSend(env, { token, data: fcmData(data) }).catch(() => ({}));
    if (r.gone) await env.DB.prepare('DELETE FROM push_tokens WHERE token = ?').bind(token).run();
  }));
}

async function pushToTopic(env, topic, data) { return fcmSend(env, { topic, data: fcmData(data) }); }

async function pushToUser(env, userId, data) {
  const { results } = await env.DB.prepare("SELECT token FROM push_tokens WHERE user_id = ? AND app = 'client'").bind(userId).all();
  return pushToTokens(env, results, data);
}

async function pushToAdmins(env, data) {
  const { results } = await env.DB.prepare(
    "SELECT t.token FROM push_tokens t JOIN users u ON u.id = t.user_id WHERE t.app = 'admin' AND u.role = 'admin'").all();
  return pushToTokens(env, results, data);
}

/** L'application envoie son jeton FCM (avec ou sans compte connecté). */
async function pushRegister(env, body, user) {
  const token = String(body.token || '');
  if (!/^[\w:\-.]{20,4096}$/.test(token)) return err('bad_token', 400);
  const app = body.app === 'admin' && user && user.role === 'admin' ? 'admin' : 'client';
  await env.DB.prepare(
    'INSERT INTO push_tokens (token, user_id, app, updated_at) VALUES (?, ?, ?, ?) ' +
    'ON CONFLICT(token) DO UPDATE SET user_id = excluded.user_id, app = excluded.app, updated_at = excluded.updated_at'
  ).bind(token, user ? user.id : null, app, Date.now()).run();
  return json({ ok: true, app, push: !!env.FCM_SERVICE_ACCOUNT });
}

/* ===================== Notifications & support ===================== */

async function createNotif(env, n) {
  const title = String(n.title || '').slice(0, 120), body = String(n.body || '').slice(0, 1000);
  const img = imageRef(n.image), link = String(n.link || ''), productId = Number(n.productId) || 0;
  const userId = n.userId ? Number(n.userId) : null;
  const r = await env.DB.prepare(
    'INSERT INTO notifications (title, body, product_id, image, link, user_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
  ).bind(title, body, productId, img, link, userId, Date.now()).run();
  const id = r.meta.last_row_id;
  // Push instantané (FCM) : à tout le monde (sujet « all ») ou au seul client concerné
  const data = { type: 'notif', id: String(id), title, body, productId: String(productId), link,
    image: img ? (/^https?:/.test(img) ? img : PUBLIC_ORIGIN + '/img/' + img) : '' };
  await (userId ? pushToUser(env, userId, data) : pushToTopic(env, 'all', data)).catch(() => {});
  return id;
}

async function supportThread(env, userId, side) {
  const { results } = await env.DB.prepare(
    'SELECT id, from_admin, text, created_at FROM messages WHERE user_id = ? ORDER BY id ASC LIMIT 500'
  ).bind(userId).all();
  await env.DB.prepare(side === 'user'
    ? 'UPDATE messages SET read_by_user = 1 WHERE user_id = ? AND from_admin = 1 AND read_by_user = 0'
    : 'UPDATE messages SET read_by_admin = 1 WHERE user_id = ? AND from_admin = 0 AND read_by_admin = 0').bind(userId).run();
  return json({ messages: results.map(m => ({ id: m.id, fromAdmin: !!m.from_admin, text: m.text, createdAt: m.created_at })) });
}

async function supportSend(env, ctx, user, body) {
  if (user.banned) return err('banned', 403);
  const text = String(body.text || '').trim().slice(0, 2000);
  if (!text) return err('empty', 400);
  await env.DB.prepare('INSERT INTO messages (user_id, from_admin, text, created_at, read_by_user) VALUES (?, 0, ?, ?, 1)')
    .bind(user.id, text, Date.now()).run();
  ctx.waitUntil(pushToAdmins(env, { type: 'support', userId: String(user.id), username: user.username,
    title: '💬 ' + user.username, body: text.slice(0, 300) }).catch(() => {}));
  return supportThread(env, user.id, 'user');
}

/* ===================== API admin ===================== */

async function adminApi(env, ctx, url, path, method, body, admin) {
  const DB = env.DB;
  let m;

  if (path === '/api/admin/stats' && method === 'GET') {
    const q = sql => DB.prepare(sql).first();
    const [p, h, u, r, a, msg, n, lastMsg, lastRev] = await Promise.all([
      q('SELECT COUNT(*) AS n FROM products'),
      q('SELECT COUNT(*) AS n FROM products WHERE is_offer = 1 AND hidden = 0'),
      q("SELECT COUNT(*) AS n FROM users WHERE role != 'admin'"),
      q('SELECT COUNT(*) AS n FROM reviews'),
      q('SELECT AVG(rating) AS v FROM reviews'),
      q('SELECT COUNT(*) AS n FROM messages WHERE from_admin = 0 AND read_by_admin = 0'),
      q('SELECT COUNT(*) AS n FROM notifications'),
      q('SELECT MAX(id) AS id FROM messages WHERE from_admin = 0'),
      q('SELECT MAX(id) AS id FROM reviews')
    ]);
    return json({
      products: p.n, promos: h.n, users: u.n, reviews: r.n, avgRating: a.v ? Math.round(a.v * 10) / 10 : 0,
      unreadMessages: msg.n, notifications: n.n, lastMessageId: lastMsg.id || 0, lastReviewId: lastRev.id || 0
    });
  }

  /* ---- Produits ---- */
  if (path === '/api/admin/products' && method === 'GET') {
    const { results } = await DB.prepare('SELECT * FROM products ORDER BY id DESC').all();
    return json({ products: results.map(p => productOut(url, p)) });
  }
  if (path === '/api/admin/products' && method === 'POST') {
    const name = String(body.name || '').trim().slice(0, 150);
    const price = Number(body.price);
    if (!name) return err('bad_name', 400);
    if (!(price > 0)) return err('bad_price', 400);
    const offer = !!body.isOffer;
    const oldPrice = offer ? Number(body.oldPrice) || 0 : 0;
    if (offer && !(oldPrice > price)) return err('bad_promo', 400);
    let id = Number(body.id) || 0;
    const prev = id ? await DB.prepare('SELECT image, is_offer FROM products WHERE id = ?').bind(id).first() : null;
    if (id && !prev) return err('not_found', 404);
    // image absente de la requête = on garde la photo actuelle
    const img = body.image === undefined || body.image === null ? (prev ? prev.image : '') : imageRef(body.image);
    const fields = [name, price, oldPrice, String(body.category || '').trim().slice(0, 60),
      String(body.description || '').trim().slice(0, 3000), img, offer ? 1 : 0, body.hidden ? 1 : 0];
    const now = Date.now();
    let wasOffer = false;
    if (id) {
      wasOffer = !!prev.is_offer;
      await DB.prepare('UPDATE products SET name=?, price=?, old_price=?, category=?, description=?, image=?, is_offer=?, hidden=?, updated_at=? WHERE id=?')
        .bind(...fields, now, id).run();
      if (prev.image && prev.image !== fields[5]) await deleteImageIfLocal(env, prev.image);
    } else {
      const r = await DB.prepare('INSERT INTO products (name, price, old_price, category, description, image, is_offer, hidden, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)')
        .bind(...fields, now, now).run();
      id = r.meta.last_row_id;
    }
    if (body.notify && !body.hidden) {
      const fmt = v => String(Math.round(v * 100) / 100).replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ' ' + STORE.currency;
      if (offer && (!wasOffer || !Number(body.id))) {
        await createNotif(env, { title: '🔥 Promo', body: name + ' : ' + fmt(price) + ' au lieu de ' + fmt(oldPrice), productId: id, image: fields[5] });
      } else if (!Number(body.id)) {
        await createNotif(env, { title: '🆕 Nouveau produit', body: name + ' — ' + fmt(price), productId: id, image: fields[5] });
      } else {
        await createNotif(env, { title: '✨ ' + STORE.name, body: name + ' — ' + fmt(price), productId: id, image: fields[5] });
      }
    }
    const p = await DB.prepare('SELECT * FROM products WHERE id = ?').bind(id).first();
    return json({ product: productOut(url, p) });
  }
  if ((m = path.match(/^\/api\/admin\/products\/(\d+)$/)) && method === 'DELETE') {
    const p = await DB.prepare('SELECT image FROM products WHERE id = ?').bind(Number(m[1])).first();
    if (!p) return err('not_found', 404);
    await DB.prepare('DELETE FROM products WHERE id = ?').bind(Number(m[1])).run();
    await DB.prepare('DELETE FROM reviews WHERE product_id = ?').bind(Number(m[1])).run();
    await deleteImageIfLocal(env, p.image);
    return json({ ok: true });
  }

  /* ---- IA : reconnaître quel produit est sur une photo ---- */
  if (path === '/api/admin/ai/match' && method === 'POST') return aiMatch(env, body);

  /* ---- Notifications ---- */
  if (path === '/api/admin/notifications' && method === 'GET') {
    const { results } = await DB.prepare(
      'SELECT n.*, u.username FROM notifications n LEFT JOIN users u ON u.id = n.user_id ORDER BY n.id DESC LIMIT 200').all();
    return json({ notifications: results.map(n => ({ ...notifOut(url, n), username: n.username || null })) });
  }
  if (path === '/api/admin/notifications' && method === 'POST') {
    if (!String(body.body || '').trim()) return err('empty', 400);
    const id = await createNotif(env, {
      title: String(body.title || '').trim() || STORE.name, body: String(body.body).trim(),
      productId: body.productId, image: body.image, link: body.link, userId: body.userId
    });
    return json({ ok: true, id });
  }
  if ((m = path.match(/^\/api\/admin\/notifications\/(\d+)$/)) && method === 'DELETE') {
    await DB.prepare('DELETE FROM notifications WHERE id = ?').bind(Number(m[1])).run();
    return json({ ok: true });
  }

  /* ---- Support ---- */
  if (path === '/api/admin/conversations' && method === 'GET') {
    const { results } = await DB.prepare(
      `SELECT u.id AS user_id, u.username, u.banned,
              (SELECT text FROM messages WHERE user_id = u.id ORDER BY id DESC LIMIT 1) AS last_text,
              (SELECT from_admin FROM messages WHERE user_id = u.id ORDER BY id DESC LIMIT 1) AS last_from_admin,
              (SELECT MAX(created_at) FROM messages WHERE user_id = u.id) AS last_at,
              (SELECT COUNT(*) FROM messages WHERE user_id = u.id AND from_admin = 0 AND read_by_admin = 0) AS unread
         FROM users u WHERE EXISTS (SELECT 1 FROM messages WHERE user_id = u.id)
        ORDER BY last_at DESC`).all();
    return json({ conversations: results.map(c => ({
      userId: c.user_id, username: c.username, banned: !!c.banned, lastText: c.last_text,
      lastFromAdmin: !!c.last_from_admin, lastAt: c.last_at, unread: c.unread })) });
  }
  if ((m = path.match(/^\/api\/admin\/support\/(\d+)$/))) {
    const uid = Number(m[1]);
    if (method === 'GET') return supportThread(env, uid, 'admin');
    if (method === 'POST') {
      const text = String(body.text || '').trim().slice(0, 2000);
      if (!text) return err('empty', 400);
      const exists = await DB.prepare('SELECT id FROM users WHERE id = ?').bind(uid).first();
      if (!exists) return err('not_found', 404);
      await DB.prepare('INSERT INTO messages (user_id, from_admin, text, created_at, read_by_admin) VALUES (?, 1, ?, ?, 1)')
        .bind(uid, text, Date.now()).run();
      if (body.notify !== false) {
        await createNotif(env, { title: '💬 Message de ' + STORE.name, body: text, link: 'support', userId: uid });
      }
      return supportThread(env, uid, 'admin');
    }
  }

  /* ---- Avis ---- */
  if (path === '/api/admin/reviews' && method === 'GET') {
    const { results } = await DB.prepare(
      `SELECT r.*, u.username, p.name AS product_name FROM reviews r
         JOIN users u ON u.id = r.user_id LEFT JOIN products p ON p.id = r.product_id
        ORDER BY r.id DESC LIMIT 300`).all();
    return json({ reviews: results.map(r => ({
      id: r.id, productId: r.product_id, productName: r.product_name || ('#' + r.product_id), userId: r.user_id,
      username: r.username, rating: r.rating, text: r.text, reply: r.reply || '', createdAt: r.created_at })) });
  }
  if ((m = path.match(/^\/api\/admin\/reviews\/(\d+)\/reply$/)) && method === 'POST') {
    const id = Number(m[1]);
    const reply = String(body.reply || '').trim().slice(0, 1000);
    const r = await DB.prepare('SELECT user_id, product_id FROM reviews WHERE id = ?').bind(id).first();
    if (!r) return err('not_found', 404);
    await DB.prepare('UPDATE reviews SET reply = ? WHERE id = ?').bind(reply || null, id).run();
    if (reply) await createNotif(env, { title: '💬 ' + STORE.name + ' a répondu à votre avis', body: reply, productId: r.product_id, userId: r.user_id });
    return json({ ok: true });
  }
  if ((m = path.match(/^\/api\/admin\/reviews\/(\d+)$/)) && method === 'DELETE') {
    await DB.prepare('DELETE FROM reviews WHERE id = ?').bind(Number(m[1])).run();
    return json({ ok: true });
  }

  /* ---- Utilisateurs ---- */
  if (path === '/api/admin/users' && method === 'GET') {
    const { results } = await DB.prepare(
      `SELECT u.id, u.username, u.banned, u.role, u.created_at, u.last_seen,
              (SELECT COUNT(*) FROM reviews WHERE user_id = u.id) AS reviews,
              (SELECT COUNT(*) FROM messages WHERE user_id = u.id) AS messages
         FROM users u ORDER BY u.id DESC`).all();
    return json({ users: results.map(u => ({
      id: u.id, username: u.username, banned: !!u.banned, role: u.role, createdAt: u.created_at,
      lastSeen: u.last_seen || 0, reviews: u.reviews, messages: u.messages })) });
  }
  if ((m = path.match(/^\/api\/admin\/users\/(\d+)\/ban$/)) && method === 'POST') {
    const id = Number(m[1]);
    if (id === admin.id) return err('self', 400);
    await DB.prepare("UPDATE users SET banned = ? WHERE id = ? AND role != 'admin'").bind(body.banned ? 1 : 0, id).run();
    if (body.banned) await DB.prepare('DELETE FROM sessions WHERE user_id = ?').bind(id).run();
    return json({ ok: true });
  }

  return err('not_found', 404);
}

/* ===================== IA (Cloudflare Workers AI, gratuit) ===================== */

const VISION_MODEL = '@cf/meta/llama-3.2-11b-vision-instruct';

async function runVision(env, input) {
  try {
    return await env.AI.run(VISION_MODEL, input);
  } catch (e) {
    // Ce modèle demande d'accepter sa licence une seule fois.
    if (/agree/i.test(String(e && e.message || e))) {
      await env.AI.run(VISION_MODEL, { prompt: 'agree' }).catch(() => {});
      return env.AI.run(VISION_MODEL, input);
    }
    throw e;
  }
}

/** { imageId, names: ["Boîte pizza", ...] } → { index } (−1 si aucun ne correspond) */
async function aiMatch(env, body) {
  const names = Array.isArray(body.names) ? body.names.map(n => String(n).slice(0, 120)).slice(0, 80) : [];
  const id = imageRef(body.imageId);
  if (!names.length || !/^[a-f0-9]{8,64}$/.test(id)) return err('bad_request', 400);
  const img = await env.DB.prepare('SELECT data FROM images WHERE id = ?').bind(id).first();
  if (!img) return err('not_found', 404);
  const list = names.map((n, i) => (i + 1) + '. ' + n).join('\n');
  const out = await runVision(env, {
    image: [...new Uint8Array(img.data)],
    messages: [
      { role: 'system', content: 'You match product photos of a packaging and gift shop to product names. Reply with the number only.' },
      { role: 'user', content: 'Which product from this list is shown in the photo?\n' + list +
        '\nAnswer with only the number of the best matching product (or 0 if none match).' }
    ],
    max_tokens: 8,
    temperature: 0
  });
  const text = String((out && (out.response || out.description)) || '');
  const n = parseInt((text.match(/\d+/) || ['0'])[0], 10);
  return json({ index: n >= 1 && n <= names.length ? n - 1 : -1, raw: text.slice(0, 40) });
}
