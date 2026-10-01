/**
 * 2 Frères Emballage — Backend gratuit (Google Sheets + Drive + Telegram)
 * - doGet  : API JSON lue par l'application Android
 * - doPost : Webhook du bot Telegram (panneau admin)
 */
const CONFIG = {
  BOT_TOKEN: 'COLLER_LE_TOKEN_DU_BOT_ICI',
  ADMIN_IDS: [123456789],            // ID(s) Telegram des admins
  WEB_APP_URL: 'COLLER_URL_WEB_APP_ICI',
  STORE_NAME: '2 Frères Emballage',
  CURRENCY: 'DA'
};

const SHEET = 'Products';
const HEADERS = ['id', 'name', 'price', 'oldPrice', 'category', 'description', 'image', 'fileId', 'isOffer', 'createdAt'];

const HELP = [
  '🛠️ Panneau admin — ' + CONFIG.STORE_NAME,
  '',
  '/add — ajouter un produit',
  '/list — liste des produits',
  '/price ID PRIX — changer le prix',
  '/promo ID PRIX — mettre en promo (nouveau prix)',
  '/unpromo ID — retirer la promo',
  '/delete ID — supprimer un produit',
  '/cancel — annuler l\'opération en cours',
  '',
  'Exemple : /promo 3 1200'
].join('\n');

/* ===================== À EXÉCUTER UNE FOIS ===================== */

function setup() {
  const ss = SpreadsheetApp.getActive();
  const sh = ss.getSheetByName(SHEET) || ss.insertSheet(SHEET);
  sh.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]).setFontWeight('bold');
  sh.setFrozenRows(1);
  const props = PropertiesService.getScriptProperties();
  if (!props.getProperty('FOLDER_ID')) {
    props.setProperty('FOLDER_ID', DriveApp.createFolder('2FE_Images').getId());
  }
  if (!props.getProperty('NEXT_ID')) props.setProperty('NEXT_ID', '1');
  Logger.log('✅ Setup terminé');
}

function setWebhook() {
  Logger.log(JSON.stringify(tg('setWebhook', {
    url: CONFIG.WEB_APP_URL,
    drop_pending_updates: true,
    allowed_updates: ['message']
  })));
}

/* ===================== API POUR L'APPLICATION ===================== */

function doGet() {
  const out = {
    store: CONFIG.STORE_NAME,
    currency: CONFIG.CURRENCY,
    updatedAt: new Date().toISOString(),
    products: readAll().reverse()          // plus récents en premier
  };
  return ContentService.createTextOutput(JSON.stringify(out))
    .setMimeType(ContentService.MimeType.JSON);
}

function readAll() {
  const sh = sheet_();
  const last = sh.getLastRow();
  if (last < 2) return [];
  return sh.getRange(2, 1, last - 1, HEADERS.length).getValues()
    .filter(r => r[0] !== '')
    .map(r => ({
      id: Number(r[0]),
      name: String(r[1]),
      price: Number(r[2]) || 0,
      oldPrice: Number(r[3]) || 0,
      category: String(r[4]).trim(),
      description: String(r[5]),
      image: String(r[6]),
      isOffer: r[8] === true || String(r[8]).toUpperCase() === 'TRUE'
    }));
}

/* ===================== BOT TELEGRAM ===================== */

function doPost(e) {
  let update = null;
  try {
    update = JSON.parse(e.postData.contents);
    // Anti-doublon (Telegram peut renvoyer la même mise à jour)
    const cache = CacheService.getScriptCache();
    const key = 'u' + update.update_id;
    if (cache.get(key)) return ok_();
    cache.put(key, '1', 21600);
    if (update.message) handleMessage(update.message);
  } catch (err) {
    console.error(err);
    if (update && update.message) send(update.message.chat.id, '⚠️ Erreur : ' + err.message);
  }
  return ok_();
}

function ok_() { return ContentService.createTextOutput('ok'); }

function handleMessage(msg) {
  const chatId = msg.chat.id;
  if (CONFIG.ADMIN_IDS.map(String).indexOf(String(msg.from.id)) === -1) {
    return send(chatId, '⛔ Accès refusé.\nVotre ID Telegram : ' + msg.from.id);
  }
  const text = (msg.text || '').trim();
  const state = getState(chatId);

  if (text === '/skip' && state) return wizard(chatId, msg, state, '/skip');
  if (text.startsWith('/')) return command(chatId, text);
  if (!state) return send(chatId, HELP);
  wizard(chatId, msg, state, text);
}

function command(chatId, text) {
  const parts = text.split(/\s+/);
  const cmd = parts[0].toLowerCase().split('@')[0];
  const id = Number(parts[1]);
  switch (cmd) {
    case '/add':
      setState(chatId, { step: 'photo' });
      return send(chatId, '📸 Envoyez la photo du produit.', { remove_keyboard: true });
    case '/cancel':
      clearState(chatId);
      return send(chatId, '❌ Annulé.', { remove_keyboard: true });
    case '/list':    return listProducts(chatId);
    case '/delete':  return deleteProduct(chatId, id);
    case '/price':   return setPrice(chatId, id, num(parts[2]));
    case '/promo':   return setPromo(chatId, id, num(parts[2]));
    case '/unpromo': return unPromo(chatId, id);
    default:         return send(chatId, HELP);
  }
}

function wizard(chatId, msg, st, text) {
  switch (st.step) {
    case 'photo': {
      if (!msg.photo) return send(chatId, '📸 Il faut une photo (envoyée comme image, pas comme fichier).');
      send(chatId, '⏳ Enregistrement de la photo...');
      const img = savePhoto(msg.photo[msg.photo.length - 1].file_id);
      st.image = img.url; st.fileId = img.id; st.step = 'name';
      setState(chatId, st);
      return send(chatId, '✏️ Nom du produit ?');
    }
    case 'name': {
      if (!text) return send(chatId, '✏️ Nom du produit ?');
      st.name = text; st.step = 'price';
      setState(chatId, st);
      return send(chatId, '💰 Prix (' + CONFIG.CURRENCY + ') ?');
    }
    case 'price': {
      const p = num(text);
      if (!p) return send(chatId, '⚠️ Prix invalide. Exemple : 1500');
      st.price = p; st.step = 'category';
      setState(chatId, st);
      return send(chatId, '🏷️ Catégorie ? (choisissez ou écrivez une nouvelle)', categoryKeyboard_());
    }
    case 'category': {
      if (!text) return send(chatId, '🏷️ Catégorie ?');
      st.category = text; st.step = 'description';
      setState(chatId, st);
      return send(chatId, '📝 Description ? (ou /skip)', { remove_keyboard: true });
    }
    case 'description': {
      st.description = text === '/skip' ? '' : text;
      const id = addProduct(st);
      clearState(chatId);
      return send(chatId, '✅ Produit ajouté (ID ' + id + ')\n' + st.name + ' — ' + fmt(st.price) +
        '\n\nPour le mettre en promo : /promo ' + id + ' NOUVEAU_PRIX');
    }
  }
}

/* ===================== OPÉRATIONS ===================== */

function addProduct(p) {
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const props = PropertiesService.getScriptProperties();
    const id = Number(props.getProperty('NEXT_ID') || '1');
    props.setProperty('NEXT_ID', String(id + 1));
    sheet_().appendRow([id, p.name, p.price, '', p.category, p.description || '', p.image, p.fileId, false, new Date()]);
    return id;
  } finally {
    lock.releaseLock();
  }
}

function listProducts(chatId) {
  const items = readAll();
  if (!items.length) return send(chatId, '📭 Aucun produit. Tapez /add pour commencer.');
  let chunk = '';
  items.forEach(p => {
    const line = '#' + p.id + ' • ' + p.name + ' — ' + fmt(p.price) +
      (p.isOffer ? ' 🔥' : '') + (p.category ? ' [' + p.category + ']' : '');
    if ((chunk + line).length > 3800) { send(chatId, chunk); chunk = ''; }
    chunk += line + '\n';
  });
  if (chunk) send(chatId, chunk);
}

function deleteProduct(chatId, id) {
  const row = findRow_(id);
  if (row < 0) return send(chatId, '⚠️ ID introuvable. Voir /list');
  const sh = sheet_();
  const name = sh.getRange(row, col_('name')).getValue();
  const fileId = sh.getRange(row, col_('fileId')).getValue();
  sh.deleteRow(row);
  if (fileId) { try { DriveApp.getFileById(fileId).setTrashed(true); } catch (e) {} }
  send(chatId, '🗑️ Supprimé : ' + name);
}

function setPrice(chatId, id, price) {
  const row = findRow_(id);
  if (row < 0) return send(chatId, '⚠️ ID introuvable. Voir /list');
  if (!price) return send(chatId, '⚠️ Usage : /price ID PRIX');
  sheet_().getRange(row, col_('price')).setValue(price);
  send(chatId, '💰 Nouveau prix : ' + fmt(price));
}

function setPromo(chatId, id, newPrice) {
  const row = findRow_(id);
  if (row < 0) return send(chatId, '⚠️ ID introuvable. Voir /list');
  if (!newPrice) return send(chatId, '⚠️ Usage : /promo ID NOUVEAU_PRIX');
  const sh = sheet_();
  const price = Number(sh.getRange(row, col_('price')).getValue());
  const old = Number(sh.getRange(row, col_('oldPrice')).getValue()) || price;
  sh.getRange(row, col_('price')).setValue(newPrice);
  sh.getRange(row, col_('oldPrice')).setValue(old);
  sh.getRange(row, col_('isOffer')).setValue(true);
  send(chatId, '🔥 Promo activée : ' + fmt(old) + ' → ' + fmt(newPrice));
}

function unPromo(chatId, id) {
  const row = findRow_(id);
  if (row < 0) return send(chatId, '⚠️ ID introuvable. Voir /list');
  const sh = sheet_();
  const old = Number(sh.getRange(row, col_('oldPrice')).getValue());
  if (old) sh.getRange(row, col_('price')).setValue(old);
  sh.getRange(row, col_('oldPrice')).setValue('');
  sh.getRange(row, col_('isOffer')).setValue(false);
  send(chatId, '✅ Promo retirée.');
}

function savePhoto(fileId) {
  const info = tg('getFile', { file_id: fileId });
  if (!info.ok) throw new Error('Impossible de récupérer la photo');
  const url = 'https://api.telegram.org/file/bot' + CONFIG.BOT_TOKEN + '/' + info.result.file_path;
  const blob = UrlFetchApp.fetch(url).getBlob().setName('p_' + Date.now() + '.jpg');
  const folder = DriveApp.getFolderById(PropertiesService.getScriptProperties().getProperty('FOLDER_ID'));
  const file = folder.createFile(blob);
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  return { id: file.getId(), url: 'https://lh3.googleusercontent.com/d/' + file.getId() };
}

/* ===================== OUTILS ===================== */

function sheet_() { return SpreadsheetApp.getActive().getSheetByName(SHEET); }
function col_(name) { return HEADERS.indexOf(name) + 1; }

function findRow_(id) {
  if (!id) return -1;
  const sh = sheet_();
  const last = sh.getLastRow();
  if (last < 2) return -1;
  const ids = sh.getRange(2, 1, last - 1, 1).getValues();
  for (let i = 0; i < ids.length; i++) if (Number(ids[i][0]) === id) return i + 2;
  return -1;
}

function categoryKeyboard_() {
  const cats = [...new Set(readAll().map(p => p.category).filter(Boolean))];
  if (!cats.length) return { remove_keyboard: true };
  const rows = [];
  for (let i = 0; i < cats.length; i += 2) rows.push(cats.slice(i, i + 2).map(c => ({ text: c })));
  return { keyboard: rows, resize_keyboard: true, one_time_keyboard: true };
}

function num(t) { return Number(String(t || '').replace(',', '.').replace(/[^\d.]/g, '')) || 0; }
function fmt(n) { return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ' ' + CONFIG.CURRENCY; }

function getState(chatId) {
  const v = PropertiesService.getScriptProperties().getProperty('st_' + chatId);
  return v ? JSON.parse(v) : null;
}
function setState(chatId, s) { PropertiesService.getScriptProperties().setProperty('st_' + chatId, JSON.stringify(s)); }
function clearState(chatId) { PropertiesService.getScriptProperties().deleteProperty('st_' + chatId); }

function send(chatId, text, markup) {
  const payload = { chat_id: chatId, text: text };
  if (markup) payload.reply_markup = markup;
  return tg('sendMessage', payload);
}

function tg(method, payload) {
  const res = UrlFetchApp.fetch('https://api.telegram.org/bot' + CONFIG.BOT_TOKEN + '/' + method, {
    method: 'post',
    contentType: 'application/json',
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });
  return JSON.parse(res.getContentText());
}
