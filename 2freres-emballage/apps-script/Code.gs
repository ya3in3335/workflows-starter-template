/**
 * 2 Frères Emballage — Backend gratuit (Google Sheets + Drive + Telegram)
 * - doGet  : API JSON lue par l'application Android
 * - doPost : Webhook du bot Telegram (panneau admin avec boutons)
 */
const CONFIG = {
  BOT_TOKEN: 'COLLER_LE_TOKEN_DU_BOT_ICI',
  ADMIN_IDS: [123456789],            // ID(s) Telegram des admins
  WEB_APP_URL: 'COLLER_URL_WEB_APP_ICI',
  STORE_NAME: '2 Frères Emballage',
  CURRENCY: 'DA'
};

const SHEET = 'Products';
const HEADERS = ['id', 'name', 'price', 'oldPrice', 'category', 'description', 'image', 'fileId', 'isOffer', 'createdAt', 'hidden'];
const PAGE_SIZE = 8;

const BTN = {
  ADD: '➕ زيد منتوج',
  LIST: '📦 المنتوجات',
  PROMOS: '🔥 العروض',
  HELP: '❓ مساعدة',
  CANCEL: '❌ إلغاء',
  SKIP: '⏭️ تخطي'
};

const MENU = {
  keyboard: [[{ text: BTN.ADD }, { text: BTN.LIST }], [{ text: BTN.PROMOS }, { text: BTN.HELP }]],
  resize_keyboard: true,
  is_persistent: true
};
const CANCEL_KB = { keyboard: [[{ text: BTN.CANCEL }]], resize_keyboard: true };
const SKIP_KB = { keyboard: [[{ text: BTN.SKIP }], [{ text: BTN.CANCEL }]], resize_keyboard: true };

const HELP = [
  '🛠️ لوحة التحكم — ' + CONFIG.STORE_NAME,
  '',
  '➕ زيد منتوج: صورة ← الاسم ← السعر ← الفئة ← الوصف',
  '📸 ولا ابعث صورة ديراكت، والإضافة تبدا وحدها',
  '📦 المنتوجات: اختار منتوج باش تبدّل السعر، العرض، الاسم، الصورة… ولا تخبّيه ولا تمسحو',
  '🔥 العروض: المنتوجات اللي فيهم عرض',
  '',
  '❌ إلغاء: يحبس أي عملية'
].join('\n');

const ASK = {
  name: '✏️ اكتب الاسم الجديد:',
  price: '💰 اكتب السعر الجديد (مثال: 1500):',
  promo: '🔥 اكتب سعر العرض (لازم يكون أقل من السعر الحالي):',
  category: '🏷️ اختار الفئة ولا اكتب وحدة جديدة:',
  description: '📝 اكتب الوصف الجديد (ولا ⏭️ تخطي باش تمسحو):',
  image: '🖼️ ابعث الصورة الجديدة:'
};

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
    allowed_updates: ['message', 'callback_query']
  })));
}

/* ===================== API POUR L'APPLICATION ===================== */

function doGet() {
  const products = readAll().filter(p => !p.hidden).reverse().map(p => ({
    id: p.id,
    name: p.name,
    price: p.price,
    oldPrice: p.oldPrice,
    category: p.category,
    description: p.description,
    image: p.image,
    isOffer: p.isOffer
  }));
  const out = {
    store: CONFIG.STORE_NAME,
    currency: CONFIG.CURRENCY,
    updatedAt: new Date().toISOString(),
    products: products                     // plus récents en premier
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
      fileId: String(r[7]),
      isOffer: bool_(r[8]),
      hidden: bool_(r[10])
    }));
}

/* ===================== BOT TELEGRAM ===================== */

function doPost(e) {
  let chatId = null;
  try {
    const update = JSON.parse(e.postData.contents);
    // Anti-doublon (Telegram peut renvoyer la même mise à jour)
    const cache = CacheService.getScriptCache();
    const key = 'u' + update.update_id;
    if (cache.get(key)) return ok_();
    cache.put(key, '1', 21600);
    if (update.message) {
      chatId = update.message.chat.id;
      handleMessage(update.message);
    } else if (update.callback_query) {
      chatId = update.callback_query.message.chat.id;
      handleCallback(update.callback_query);
    }
  } catch (err) {
    console.error(err);
    if (chatId) send(chatId, '⚠️ خطأ: ' + err.message, MENU);
  }
  return ok_();
}

function ok_() { return ContentService.createTextOutput('ok'); }

function isAdmin_(userId) {
  return CONFIG.ADMIN_IDS.map(String).indexOf(String(userId)) !== -1;
}

function handleMessage(msg) {
  const chatId = msg.chat.id;
  if (!isAdmin_(msg.from.id)) {
    return send(chatId, '⛔ ممنوع.\nالـ ID تاعك: ' + msg.from.id);
  }
  const text = (msg.text || '').trim();
  const cmd = text.toLowerCase().split(/\s+/)[0].split('@')[0];

  if (text === BTN.CANCEL || cmd === '/cancel') {
    clearState(chatId);
    return send(chatId, '❌ تلغات.', MENU);
  }
  if (text === BTN.HELP || cmd === '/start' || cmd === '/help' || cmd === '/menu') {
    clearState(chatId);
    return send(chatId, HELP, MENU);
  }
  if (text === BTN.ADD || cmd === '/add') {
    setState(chatId, { step: 'photo' });
    return send(chatId, '📸 ابعث صورة المنتوج.', CANCEL_KB);
  }
  if (text === BTN.LIST || cmd === '/list') {
    clearState(chatId);
    return sendList(chatId, 0, false);
  }
  if (text === BTN.PROMOS) {
    clearState(chatId);
    return sendList(chatId, 0, true);
  }
  if (cmd.startsWith('/')) return legacyCommand(chatId, text);

  const state = getState(chatId);
  if (state && state.step === 'edit') return applyEdit(chatId, msg, state, text);
  if (state) return wizard(chatId, msg, state, text);

  // Photo envoyée sans rien : on démarre l'ajout directement
  if (photoId_(msg)) return wizard(chatId, msg, { step: 'photo' }, text);
  send(chatId, HELP, MENU);
}

/* ---------- Ajout d'un produit (étape par étape) ---------- */

function wizard(chatId, msg, st, text) {
  switch (st.step) {
    case 'photo': {
      const fid = photoId_(msg);
      if (!fid) return send(chatId, '📸 لازم تبعث صورة.', CANCEL_KB);
      send(chatId, '⏳ راني نحفظ الصورة…');
      const img = savePhoto(fid);
      st.image = img.url; st.fileId = img.id; st.step = 'name';
      setState(chatId, st);
      return send(chatId, '✏️ واش هو اسم المنتوج؟', CANCEL_KB);
    }
    case 'name': {
      if (!text) return send(chatId, '✏️ اكتب اسم المنتوج.', CANCEL_KB);
      st.name = text; st.step = 'price';
      setState(chatId, st);
      return send(chatId, '💰 السعر بالـ' + CONFIG.CURRENCY + '؟ (مثال: 1500)', CANCEL_KB);
    }
    case 'price': {
      const p = num(text);
      if (!p) return send(chatId, '⚠️ السعر ماشي صحيح. اكتب رقم برك، مثال: 1500', CANCEL_KB);
      st.price = p; st.step = 'category';
      setState(chatId, st);
      return send(chatId, '🏷️ الفئة؟ اختار ولا اكتب وحدة جديدة:', categoryKeyboard_());
    }
    case 'category': {
      if (!text) return send(chatId, '🏷️ اكتب الفئة.', categoryKeyboard_());
      st.category = text; st.step = 'description';
      setState(chatId, st);
      return send(chatId, '📝 الوصف؟ (ولا اضغط ⏭️ تخطي)', SKIP_KB);
    }
    case 'description': {
      st.description = (text === BTN.SKIP || text === '/skip') ? '' : text;
      const id = addProduct(st);
      clearState(chatId);
      send(chatId, '✅ المنتوج تزاد (رقم ' + id + '). راهو يبان في التطبيق.', MENU);
      return sendCard(chatId, id);
    }
  }
  clearState(chatId);
  send(chatId, HELP, MENU);
}

/* ---------- Boutons sous les messages ---------- */

function handleCallback(q) {
  const chatId = q.message.chat.id;
  tg('answerCallbackQuery', { callback_query_id: q.id });
  if (!isAdmin_(q.from.id)) return;

  const parts = String(q.data || '').split(':');
  const action = parts[0];
  const id = Number(parts[1]);
  switch (action) {
    case 'l':  return sendList(chatId, id, parts[2] === '1', q.message.message_id);
    case 'p':  return sendCard(chatId, id);
    case 'e': {
      const field = parts[2];
      if (!ASK[field] || findRow_(id) < 0) return send(chatId, '⚠️ المنتوج ما كانش.', MENU);
      setState(chatId, { step: 'edit', id: id, field: field });
      const kb = field === 'category' ? categoryKeyboard_() : field === 'description' ? SKIP_KB : CANCEL_KB;
      return send(chatId, ASK[field], kb);
    }
    case 'up': {
      unPromo(id);
      send(chatId, '✅ العرض تنحّى.', MENU);
      return sendCard(chatId, id);
    }
    case 'h': {
      const hidden = toggleHidden(id);
      if (hidden === null) return send(chatId, '⚠️ المنتوج ما كانش.', MENU);
      send(chatId, hidden ? '🙈 المنتوج تخبّى من التطبيق.' : '👁️ المنتوج رجع يبان في التطبيق.', MENU);
      return sendCard(chatId, id);
    }
    case 'd':
      return send(chatId, '🗑️ متأكد تحب تمسح المنتوج رقم ' + id + '؟', deleteConfirmKb_(id));
    case 'dy': {
      const name = deleteProduct(id);
      return send(chatId, name === null ? '⚠️ المنتوج ما كانش.' : '🗑️ تمسح: ' + name, MENU);
    }
  }
}

function applyEdit(chatId, msg, st, text) {
  const row = findRow_(st.id);
  if (row < 0) { clearState(chatId); return send(chatId, '⚠️ المنتوج ما كانش.', MENU); }
  const sh = sheet_();
  const kb = st.field === 'category' ? categoryKeyboard_() : CANCEL_KB;

  switch (st.field) {
    case 'image': {
      const fid = photoId_(msg);
      if (!fid) return send(chatId, '🖼️ لازم تبعث صورة.', CANCEL_KB);
      send(chatId, '⏳ راني نحفظ الصورة…');
      const img = savePhoto(fid);
      trashFile_(sh.getRange(row, col_('fileId')).getValue());
      sh.getRange(row, col_('image')).setValue(img.url);
      sh.getRange(row, col_('fileId')).setValue(img.id);
      break;
    }
    case 'price': {
      const p = num(text);
      if (!p) return send(chatId, '⚠️ اكتب رقم برك، مثال: 1500', CANCEL_KB);
      sh.getRange(row, col_('price')).setValue(p);
      break;
    }
    case 'promo': {
      const p = num(text);
      if (!p) return send(chatId, '⚠️ اكتب رقم برك، مثال: 1200', CANCEL_KB);
      const price = Number(sh.getRange(row, col_('price')).getValue());
      const old = Number(sh.getRange(row, col_('oldPrice')).getValue()) || price;
      if (p >= old) return send(chatId, '⚠️ سعر العرض لازم يكون أقل من ' + fmt(old), CANCEL_KB);
      sh.getRange(row, col_('price')).setValue(p);
      sh.getRange(row, col_('oldPrice')).setValue(old);
      sh.getRange(row, col_('isOffer')).setValue(true);
      break;
    }
    case 'name':
    case 'category': {
      if (!text) return send(chatId, ASK[st.field], kb);
      sh.getRange(row, col_(st.field)).setValue(text);
      break;
    }
    case 'description': {
      sh.getRange(row, col_('description')).setValue((text === BTN.SKIP || text === '/skip') ? '' : text);
      break;
    }
    default:
      clearState(chatId);
      return send(chatId, HELP, MENU);
  }
  clearState(chatId);
  send(chatId, '✅ تبدّل.', MENU);
  sendCard(chatId, st.id);
}

/* ---------- Affichage ---------- */

function sendList(chatId, page, promosOnly, editMessageId) {
  const items = readAll().reverse().filter(p => !promosOnly || p.isOffer);
  if (!items.length) {
    return send(chatId, promosOnly ? '🔥 ما كاين حتى عرض درك.\nحلّ منتوج من 📦 المنتوجات واضغط 🔥 دير عرض.'
                                  : '📭 ما كاين حتى منتوج. اضغط ➕ زيد منتوج.', MENU);
  }
  const pages = Math.ceil(items.length / PAGE_SIZE);
  page = Math.max(0, Math.min(page || 0, pages - 1));
  const rows = items.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE).map(p => [{
    text: (p.hidden ? '🙈 ' : '') + (p.isOffer ? '🔥 ' : '') + p.name + ' — ' + fmt(p.price),
    callback_data: 'p:' + p.id
  }]);
  const flag = promosOnly ? '1' : '0';
  const nav = [];
  if (page > 0) nav.push({ text: '◀️ اللي قبل', callback_data: 'l:' + (page - 1) + ':' + flag });
  if (page < pages - 1) nav.push({ text: 'اللي من بعد ▶️', callback_data: 'l:' + (page + 1) + ':' + flag });
  if (nav.length) rows.push(nav);

  const text = (promosOnly ? '🔥 العروض' : '📦 المنتوجات') + ' (' + items.length + ')' +
    (pages > 1 ? ' — صفحة ' + (page + 1) + '/' + pages : '') + '\nاختار منتوج:';
  const markup = { inline_keyboard: rows };
  if (editMessageId) {
    const r = tg('editMessageText', { chat_id: chatId, message_id: editMessageId, text: text, reply_markup: markup });
    if (r.ok) return r;
  }
  return send(chatId, text, markup);
}

function sendCard(chatId, id) {
  const p = readAll().filter(x => x.id === id)[0];
  if (!p) return send(chatId, '⚠️ المنتوج ما كانش.', MENU);

  const lines = ['#' + p.id + ' • ' + p.name];
  lines.push(p.isOffer && p.oldPrice > p.price
    ? '🔥 ' + fmt(p.price) + '  (قبل: ' + fmt(p.oldPrice) + ')'
    : '💰 ' + fmt(p.price));
  if (p.category) lines.push('🏷️ ' + p.category);
  if (p.description) lines.push('📝 ' + p.description.slice(0, 600));
  if (p.hidden) lines.push('🙈 مخبّي من التطبيق');
  const caption = lines.join('\n');

  const kb = { inline_keyboard: [
    [{ text: '💰 السعر', callback_data: 'e:' + id + ':price' },
     p.isOffer ? { text: '❌ نحّي العرض', callback_data: 'up:' + id }
               : { text: '🔥 دير عرض', callback_data: 'e:' + id + ':promo' }],
    [{ text: '✏️ الاسم', callback_data: 'e:' + id + ':name' },
     { text: '🏷️ الفئة', callback_data: 'e:' + id + ':category' }],
    [{ text: '📝 الوصف', callback_data: 'e:' + id + ':description' },
     { text: '🖼️ الصورة', callback_data: 'e:' + id + ':image' }],
    [{ text: p.hidden ? '👁️ ورّيه' : '🙈 خبّيه', callback_data: 'h:' + id },
     { text: '🗑️ امسح', callback_data: 'd:' + id }]
  ] };

  const r = sendPhoto_(chatId, p, caption, kb);
  if (r && r.ok) return r;
  return send(chatId, caption, kb);
}

/* ===================== OPÉRATIONS ===================== */

function addProduct(p) {
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const props = PropertiesService.getScriptProperties();
    const id = Number(props.getProperty('NEXT_ID') || '1');
    props.setProperty('NEXT_ID', String(id + 1));
    sheet_().appendRow([id, p.name, p.price, '', p.category, p.description || '', p.image, p.fileId, false, new Date(), false]);
    return id;
  } finally {
    lock.releaseLock();
  }
}

function deleteProduct(id) {
  const row = findRow_(id);
  if (row < 0) return null;
  const sh = sheet_();
  const name = sh.getRange(row, col_('name')).getValue();
  trashFile_(sh.getRange(row, col_('fileId')).getValue());
  sh.deleteRow(row);
  return name;
}

function unPromo(id) {
  const row = findRow_(id);
  if (row < 0) return;
  const sh = sheet_();
  const old = Number(sh.getRange(row, col_('oldPrice')).getValue());
  if (old) sh.getRange(row, col_('price')).setValue(old);
  sh.getRange(row, col_('oldPrice')).setValue('');
  sh.getRange(row, col_('isOffer')).setValue(false);
}

function toggleHidden(id) {
  const row = findRow_(id);
  if (row < 0) return null;
  const sh = sheet_();
  sh.getRange(1, col_('hidden')).setValue('hidden');
  const hidden = !bool_(sh.getRange(row, col_('hidden')).getValue());
  sh.getRange(row, col_('hidden')).setValue(hidden);
  return hidden;
}

/* Anciennes commandes texte (toujours utilisables) : /price ID PRIX, /promo ID PRIX, /unpromo ID, /delete ID */
function legacyCommand(chatId, text) {
  const parts = text.split(/\s+/);
  const cmd = parts[0].toLowerCase().split('@')[0];
  const id = Number(parts[1]);
  if (findRow_(id) < 0) return send(chatId, HELP, MENU);
  switch (cmd) {
    case '/price':
    case '/promo': {
      const st = { step: 'edit', id: id, field: cmd === '/price' ? 'price' : 'promo' };
      setState(chatId, st);
      return applyEdit(chatId, {}, st, parts[2] || '');
    }
    case '/unpromo':
      unPromo(id);
      return sendCard(chatId, id);
    case '/delete':
      return send(chatId, '🗑️ متأكد تحب تمسح المنتوج رقم ' + id + '؟', deleteConfirmKb_(id));
    default:
      return send(chatId, HELP, MENU);
  }
}

function savePhoto(fileId) {
  const info = tg('getFile', { file_id: fileId });
  if (!info.ok) throw new Error('ما قدرتش نجيب الصورة (لازم تكون أقل من 20MB)');
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
function bool_(v) { return v === true || String(v).toUpperCase() === 'TRUE'; }

function findRow_(id) {
  if (!id) return -1;
  const sh = sheet_();
  const last = sh.getLastRow();
  if (last < 2) return -1;
  const ids = sh.getRange(2, 1, last - 1, 1).getValues();
  for (let i = 0; i < ids.length; i++) if (Number(ids[i][0]) === id) return i + 2;
  return -1;
}

/** Photo envoyée comme image, ou comme fichier image. */
function photoId_(msg) {
  if (msg.photo && msg.photo.length) return msg.photo[msg.photo.length - 1].file_id;
  if (msg.document && String(msg.document.mime_type || '').indexOf('image/') === 0) return msg.document.file_id;
  return null;
}

function deleteConfirmKb_(id) {
  return { inline_keyboard: [[
    { text: '✅ إيه، امسحو', callback_data: 'dy:' + id },
    { text: '↩️ لا', callback_data: 'p:' + id }
  ]] };
}

function trashFile_(fileId) {
  if (!fileId) return;
  try { DriveApp.getFileById(String(fileId)).setTrashed(true); } catch (e) {}
}

function categoryKeyboard_() {
  const cats = [...new Set(readAll().map(p => p.category).filter(Boolean))];
  const rows = [];
  for (let i = 0; i < cats.length; i += 2) rows.push(cats.slice(i, i + 2).map(c => ({ text: c })));
  rows.push([{ text: BTN.CANCEL }]);
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

/** Envoie la photo du produit depuis Drive (fiable même si le lien public est lent). */
function sendPhoto_(chatId, p, caption, markup) {
  try {
    const payload = { chat_id: String(chatId), caption: caption, reply_markup: JSON.stringify(markup) };
    if (p.fileId) payload.photo = DriveApp.getFileById(p.fileId).getBlob();
    else if (p.image) payload.photo = p.image;
    else return null;
    const res = UrlFetchApp.fetch('https://api.telegram.org/bot' + CONFIG.BOT_TOKEN + '/sendPhoto', {
      method: 'post',
      payload: payload,
      muteHttpExceptions: true
    });
    return JSON.parse(res.getContentText());
  } catch (e) {
    return null;
  }
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
