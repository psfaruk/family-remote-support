#!/usr/bin/env python3
# W3 string injection — 11 new keys x15 locales, inserted before </resources>.
import re, pathlib

KEYS = ["pair_create_btn","pair_waiting_hint","pair_expired_new","pair_cancelled_new",
        "pair_paired_with","pair_copied","scan_screen_t","scan_manual_hint",
        "scan_wizard_btn","perm_route_intro"]

BLOCKS = {
 "values": [
  "Create the QR code",
  "On the other phone open Add Device → Scan, point the camera at this QR — or type the code below.",
  "The 5 minutes are up — create a new code.",
  "Code cancelled — you can create a new one.",
  "%1$s is now linked ✓ — every session still asks for approval first.",
  "Code copied",
  "Scan or type the code",
  "Type or paste the 32-character code shown on the other phone.",
  "Next: permission setup",
  "Reliable help needs a few permissions on this phone. The wizard explains each one and opens the right settings page — you can run it any time.",
 ],
 "values-bn": [
  "QR কোড তৈরি করুন",
  "অন্য ফোনে ডিভাইস যোগ → স্ক্যান খুলে এই QR-এর দিকে ক্যামেরা ধরুন — অথবা নিচের কোডটি লিখুন।",
  "৫ মিনিট পেরিয়ে গেছে — নতুন কোড তৈরি করুন।",
  "কোড বাতিল হয়েছে — চাইলে নতুন কোড তৈরি করতে পারেন।",
  "%1$s এখন যুক্ত ✓ — প্রতিটি সেশনের জন্য আগে অনুমতি চাওয়া হবে।",
  "কোড কপি হয়েছে",
  "কোড স্ক্যান বা লিখুন",
  "অন্য ফোনে দেখানো ৩২-অক্ষরের কোডটি লিখুন বা পেস্ট করুন।",
  "পরের ধাপ: পারমিশন সেটআপ",
  "নির্ভরযোগ্য সাহায্যের জন্য এই ফোনে কিছু পারমিশন দরকার। উইজার্ড প্রতিটি ধাপ বুঝিয়ে সঠিক সেটিংস-পাতা খুলে দেয় — যেকোনো সময় চালাতে পারেন।",
 ],
 "values-ar": [
  "إنشاء رمز QR",
  "على الهاتف الآخر: افتح إضافة جهاز ← مسح، ثم وجّه الكاميرا نحو رمز QR هذا — أو اكتب الرمز أدناه.",
  "انتهت الخمس دقائق — أنشئ رمزًا جديدًا.",
  "تم إلغاء الرمز — يمكنك إنشاء رمز جديد.",
  "تم الربط مع %1$s ✓ — كل جلسة تظل تطلب الموافقة أولًا.",
  "تم نسخ الرمز",
  "امسح الرمز أو اكتبه",
  "اكتب أو الصق رمز 32 حرفًا الظاهر على الهاتف الآخر.",
  "التالي: إعداد الأذونات",
  "المساعدة الموثوقة تحتاج بعض الأذونات على هذا الهاتف. يشرح المعالج كل خطوة ويفتح صفحة الإعدادات الصحيحة — يمكنك تشغيله في أي وقت.",
 ],
 "values-de": [
  "QR-Code erstellen",
  "Öffnen Sie auf dem anderen Telefon „Gerät hinzufügen“ → Scannen und richten Sie die Kamera auf diesen QR-Code — oder tippen Sie den Code unten ein.",
  "Die 5 Minuten sind um — erstellen Sie einen neuen Code.",
  "Code abgebrochen — Sie können einen neuen erstellen.",
  "%1$s ist jetzt verknüpft ✓ — jede Sitzung fragt weiterhin zuerst um Erlaubnis.",
  "Code kopiert",
  "Code scannen oder eingeben",
  "Tippen Sie den 32-stelligen Code vom anderen Telefon ein oder fügen Sie ihn ein.",
  "Weiter: Berechtigungs-Setup",
  "Zuverlässige Hilfe braucht einige Berechtigungen auf diesem Telefon. Der Assistent erklärt jeden Schritt und öffnet die richtige Einstellungsseite — jederzeit ausführbar.",
 ],
 "values-es": [
  "Crear el código QR",
  "En el otro teléfono abra Añadir dispositivo → Escanear y apunte la cámara a este QR — o escriba el código de abajo.",
  "Los 5 minutos terminaron — cree un código nuevo.",
  "Código cancelado — puede crear uno nuevo.",
  "%1$s ya está vinculado ✓ — cada sesión sigue pidiendo permiso primero.",
  "Código copiado",
  "Escanear o escribir el código",
  "Escriba o pegue el código de 32 caracteres mostrado en el otro teléfono.",
  "Siguiente: configuración de permisos",
  "La ayuda confiable necesita algunos permisos en este teléfono. El asistente explica cada paso y abre la página de ajustes correcta — puede ejecutarlo en cualquier momento.",
 ],
 "values-fr": [
  "Créer le code QR",
  "Sur l\\'autre téléphone, ouvrez Ajouter un appareil → Scanner et pointez la caméra vers ce QR — ou saisissez le code ci-dessous.",
  "Les 5 minutes sont écoulées — créez un nouveau code.",
  "Code annulé — vous pouvez en créer un nouveau.",
  "%1$s est maintenant associé ✓ — chaque session demande toujours d\\'abord l\\'autorisation.",
  "Code copié",
  "Scanner ou saisir le code",
  "Saisissez ou collez le code de 32 caractères affiché sur l\\'autre téléphone.",
  "Suivant : configuration des autorisations",
  "Une aide fiable nécessite quelques autorisations sur ce téléphone. L\\'assistant explique chaque étape et ouvre la bonne page de réglages — utilisable à tout moment.",
 ],
 "values-hi": [
  "QR कोड बनाएँ",
  "दूसरे फ़ोन पर डिवाइस जोड़ें → स्कैन खोलकर इस QR पर कैमरा लगाएँ — या नीचे दिया कोड टाइप करें।",
  "5 मिनट पूरे हो गए — नया कोड बनाएँ।",
  "कोड रद्द हुआ — आप नया कोड बना सकते हैं।",
  "%1$s अब जुड़ गया ✓ — हर सेशन से पहले अनुमति पूछी जाएगी।",
  "कोड कॉपी हुआ",
  "कोड स्कैन या टाइप करें",
  "दूसरे फ़ोन पर दिखा 32-अक्षरों का कोड टाइप या पेस्ट करें।",
  "अगला: अनुमति सेटअप",
  "भरोसेमंद मदद के लिए इस फ़ोन पर कुछ अनुमतियाँ चाहिए। विज़ार्ड हर कदम समझाता है और सही सेटिंग्स-पेज खोलता है — कभी भी चला सकते हैं।",
 ],
 "values-id": [
  "Buat kode QR",
  "Di ponsel lain buka Tambah Perangkat → Pindai, arahkan kamera ke QR ini — atau ketik kode di bawah.",
  "5 menit sudah habis — buat kode baru.",
  "Kode dibatalkan — Anda dapat membuat yang baru.",
  "%1$s kini tertaut ✓ — setiap sesi tetap meminta izin lebih dulu.",
  "Kode disalin",
  "Pindai atau ketik kode",
  "Ketik atau tempel kode 32 karakter yang tampil di ponsel lain.",
  "Lanjut: penyiapan izin",
  "Bantuan yang andal memerlukan beberapa izin di ponsel ini. Wizard menjelaskan tiap langkah dan membuka halaman pengaturan yang tepat — bisa dijalankan kapan saja.",
 ],
 "values-ja": [
  "QRコードを作成",
  "相手の携帯で「デバイス追加 → スキャン」を開き、このQRにカメラを向けてください。下のコードを入力しても構いません。",
  "5分が経過しました — 新しいコードを作成してください。",
  "コードを取消しました — 新しく作成できます。",
  "%1$s と連携しました ✓ — 毎回のセッション前に許可を求めます。",
  "コードをコピーしました",
  "コードをスキャンまたは入力",
  "相手の携帯に表示された32桁のコードを入力・貼り付けしてください。",
  "次へ: 権限の設定",
  "確実なサポートにはいくつかの権限が必要です。ウィザードが各ステップを説明し、正しい設定画面を開きます。いつでも実行できます。",
 ],
 "values-ne": [
  "QR कोड बनाउनुहोस्",
  "अर्को फोनमा डिभाइस थप्नुहोस् → स्क्यान खोलेर यो QR मा क्यामेरा राख्नुहोस् — वा तलको कोड टाइप गर्नुहोस्।",
  "५ मिनेट बित्यो — नयाँ कोड बनाउनुहोस्।",
  "कोड रद्द भयो — नयाँ कोड बनाउन सक्नुहुन्छ।",
  "%1$s अब जोडियो ✓ — हरेक सेसनअघि अनुमति सोधिनेछ।",
  "कोड प्रतिलिपि भयो",
  "कोड स्क्यान वा टाइप गर्नुहोस्",
  "अर्को फोनमा देखाइएको ३२-अक्षरको कोड टाइप वा पेस्ट गर्नुहोस्।",
  "अर्को: अनुमति सेटअप",
  "भरपर्दो सहयोगका लागि यो फोनमा केही अनुमति चाहिन्छ। विजार्डले हरेक चरण बुझाउँछ र सही सेटिङ-पृष्ठ खोल्छ — जुनसुकै बेला चलाउन सकिन्छ।",
 ],
 "values-pt": [
  "Criar o código QR",
  "No outro telefone abra Adicionar dispositivo → Escanear e aponte a câmera para este QR — ou digite o código abaixo.",
  "Os 5 minutos acabaram — crie um novo código.",
  "Código cancelado — você pode criar um novo.",
  "%1$s agora está vinculado ✓ — cada sessão continua pedindo aprovação primeiro.",
  "Código copiado",
  "Escanear ou digitar o código",
  "Digite ou cole o código de 32 caracteres mostrado no outro telefone.",
  "Próximo: configuração de permissões",
  "Ajuda confiável precisa de algumas permissões neste telefone. O assistente explica cada etapa e abre a página certa de ajustes — pode executá-lo a qualquer momento.",
 ],
 "values-ru": [
  "Создать QR-код",
  "На другом телефоне откройте «Добавить устройство» → «Сканировать» и наведите камеру на этот QR — или введите код ниже.",
  "5 минут истекли — создайте новый код.",
  "Код отменён — можно создать новый.",
  "%1$s теперь связан ✓ — каждый сеанс по-прежнему сначала запрашивает разрешение.",
  "Код скопирован",
  "Отсканируйте или введите код",
  "Введите или вставьте 32-символьный код, показанный на другом телефоне.",
  "Далее: настройка разрешений",
  "Надёжной помощи нужны некоторые разрешения на этом телефоне. Мастер объясняет каждый шаг и открывает нужную страницу настроек — запускать можно в любое время.",
 ],
 "values-tr": [
  "QR kodu oluştur",
  "Diğer telefonda Cihaz Ekle → Tara\\'yı açıp kamerayı bu QR\\'ya doğrultun — ya da aşağıdaki kodu yazın.",
  "5 dakika doldu — yeni bir kod oluşturun.",
  "Kod iptal edildi — yenisini oluşturabilirsiniz.",
  "%1$s artık bağlı ✓ — her oturum yine önce izin ister.",
  "Kod kopyalandı",
  "Kodu tara veya yaz",
  "Diğer telefonda gösterilen 32 karakterlik kodu yazın veya yapıştırın.",
  "Sıradaki: izin kurulumu",
  "Güvenilir yardım bu telefonda birkaç izin gerektirir. Sihirbaz her adımı açıklar ve doğru ayar sayfasını açar — istediğiniz zaman çalıştırabilirsiniz.",
 ],
 "values-ur": [
  "QR کوڈ بنائیں",
  "دوسرے فون پر ڈیوائس شامل کریں ← اسکین کھول کر اس QR پر کیمرہ رکھیں — یا نیچے دیا کوڈ ٹائپ کریں۔",
  "۵ منٹ ختم ہو گئے — نیا کوڈ بنائیں۔",
  "کوڈ منسوخ ہو گیا — آپ نیا بنا سکتے ہیں۔",
  "%1$s اب جڑ گیا ✓ — ہر سیشن سے پہلے اجازت پوچھی جائے گی۔",
  "کوڈ کاپی ہو گیا",
  "کوڈ اسکین یا ٹائپ کریں",
  "دوسرے فون پر دکھایا گیا ۳۲ حروف کا کوڈ ٹائپ یا پیسٹ کریں۔",
  "اگلا: اجازت کی ترتیب",
  "قابلِ اعتماد مدد کے لیے اس فون پر کچھ اجازتوں کی ضرورت ہے۔ وزرڈ ہر مرحلہ سمجھاتا ہے اور درست ترتیبات کا صفحہ کھولتا ہے — کبھی بھی چلا سکتے ہیں۔",
 ],
 "values-zh": [
  "生成二维码",
  "在另一部手机上打开「添加设备 → 扫描」，将相机对准此二维码 — 或在下方输入配对码。",
  "5 分钟已过 — 请生成新的配对码。",
  "配对码已取消 — 您可以重新生成。",
  "已与 %1$s 关联 ✓ — 每次会话仍会先请求批准。",
  "配对码已复制",
  "扫描或输入配对码",
  "输入或粘贴另一部手机上显示的 32 位配对码。",
  "下一步：权限设置",
  "可靠的协助需要本手机上的几项权限。向导会逐步说明并打开正确的设置页面 — 可随时运行。",
 ],
}

res = pathlib.Path("app/src/main/res")
marker = "<!-- W3 (v0.51.0) — Add-Device subtree: pairing FSM, QR TTL, scan/manual, wizard shortcut -->"
count = 0
for folder, texts in BLOCKS.items():
    f = res / folder / "strings.xml"
    xml = f.read_text(encoding="utf-8")
    assert len(texts) == len(KEYS), folder
    if marker in xml:
        print(f"SKIP {folder} (already injected)")
        continue
    lines = [f"    {marker}"] if folder == "values" else []
    if folder != "values":
        lines = [f"    {marker}"]
    block = "\n".join([""] + [
        f'    <string name="{k}">{t}</string>' for k, t in zip(KEYS, texts)
    ])
    assert "</resources>" in xml
    xml = xml.replace("</resources>", block + "\n</resources>")
    f.write_text(xml, encoding="utf-8")
    count += 1
    print(f"OK   {folder}")
print(f"\n{count} locale files updated, {len(KEYS)} keys each")
