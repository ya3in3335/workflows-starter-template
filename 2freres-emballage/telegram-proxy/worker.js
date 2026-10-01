/**
 * 2 Frères Emballage — relais Telegram → Apps Script (Cloudflare Workers, gratuit)
 *
 * Telegram exige une réponse 200 ; Apps Script répond toujours 302 aux POST,
 * ce qui bloque la file des messages du bot. Ce Worker répond 200 tout de suite
 * à Telegram et transmet le message à Apps Script en arrière-plan.
 */
const APPS_SCRIPT_URL = 'COLLER_URL_WEB_APP_ICI';
const SECRET = 'COLLER_SECRET_ICI'; // même valeur que secret_token du webhook Telegram

export default {
  async fetch(request, env, ctx) {
    if (request.method !== 'POST') return new Response('ok');
    if (request.headers.get('X-Telegram-Bot-Api-Secret-Token') !== SECRET) {
      return new Response('forbidden', { status: 403 });
    }
    const body = await request.text();
    ctx.waitUntil(fetch(APPS_SCRIPT_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: body,
      redirect: 'manual'
    }));
    return new Response('ok');
  }
};
