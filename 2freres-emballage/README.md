# 2 Frères Emballage — تطبيق الكتالوج + بوت تيليغرام

كلشي مجاني: Google Sheets (قاعدة البيانات) + Google Drive (الصور) + Apps Script (الـAPI والبوت). ما تحتاجش سيرفر.

## 1) البوت
1. في تيليغرام: @BotFather ← `/newbot` ← خوذ الـ TOKEN.
2. @userinfobot ← يعطيك الـ ID تاعك (رقم).

## 2) الباك-أند (Apps Script)
1. دير Google Sheet جديد ← Extensions ← Apps Script.
2. امسح الكود اللي كاين، لصق `apps-script/Code.gs`.
3. في `CONFIG` حط: `BOT_TOKEN` و `ADMIN_IDS` (الـID تاعك، وتقدر تزيد ID تاع مول الحانوت).
4. اختار الفونكسيون `setup` ← Run ← اقبل الصلاحيات.
5. Deploy ← New deployment ← Web app:
   - Execute as: **Me**
   - Who has access: **Anyone**
   ← انسخ الرابط (يكمل بـ `/exec`).
6. لصقو في `WEB_APP_URL` ← Save ← Deploy ← Manage deployments ← ✏️ ← Version: **New version** ← Deploy.
7. Run ← `setWebhook`. حل الرابط في المتصفح: لازم يبان JSON.

> ⚠️ كل مرة تبدل الكود: Manage deployments ← ✏️ ← New version (باش يبقى نفس الرابط).

## 3) التطبيق (Android Studio)
1. Open ← فولدر `android`.
2. بدل `Config.java`: `API_URL` (نفس رابط `/exec`) و `WHATSAPP_NUMBER` (مثال `213555123456`).
3. Build ← Build APK(s).

## أوامر البوت
| الأمر | الوظيفة |
|---|---|
| `/add` | زيد منتوج (صورة ← اسم ← سعر ← فئة ← وصف) |
| `/list` | قائمة المنتوجات مع الـID |
| `/price ID PRIX` | بدل السعر |
| `/promo ID PRIX` | دير عرض (السعر القديم يتشطب في التطبيق) |
| `/unpromo ID` | نحي العرض |
| `/delete ID` | امسح المنتوج (والصورة) |
| `/cancel` | ألغي |

تقدر تبدل حتى مباشرة في الـ Google Sheet (الاسم، الوصف، الفئة...).

## واش فيه التطبيق
شبكة منتوجات بالصور • بحث • فلترة بالفئات + 🔥 Promos • صفحة تفاصيل • زر "Commander via WhatsApp" برسالة جاهزة فيها اسم المنتوج والرقم والسعر • يخدم بلا انترنت (آخر نسخة محفوظة) • سحب للتحديث.

## 4) بناء الـAPK أوتوماتيك (GitHub Actions) — بلا Android Studio
1. في GitHub: Settings ← Secrets and variables ← Actions ← **Variables** ← زيد:
   - `API_URL` = رابط `/exec`
   - `WHATSAPP_NUMBER` = مثال `213555123456`
2. Actions ← **Build Android APK (2 Frères Emballage)** ← Run workflow (ولا يخدم وحدو كي تبدل حاجة في `android/`).
3. كي يكمل: افتح الـ run ← Artifacts ← حمّل `2freres-emballage-apk` ← فيه `app-debug.apk` تقدر تنصبو في التيليفون.

> بناء محلي: `cd 2freres-emballage/android && ./gradlew assembleDebug`
