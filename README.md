# Camera Music App

Android uchun kamera + musiqa bilan sinxron video yozish ilovasi.

## Funksiyalar
1. Camera2 API orqali qurilma qo'llab-quvvatlaydigan **eng yuqori video ruxsat**dan foydalanish.
2. Chap burchakdagi tugma orqali **qurilmadagi istalgan audio/qo'shiqni** tanlash.
3. O'rtadagi **Play** tugmasi bosilganda tanlangan musiqa ijro etiladi va **shu bilan bir vaqtda** video yozish boshlanadi.
4. Video va audio (mikrofon) **bir vaqtda, sinxron real vaqtda** yoziladi (Camera2 + MediaRecorder, bitta capture session).
5. Musiqaning **ovoz to'lqini gorizontal tarzda, real vaqtda** ekranda ko'rsatiladi (`Visualizer` API + custom `WaveformView`).
6. Pastki o'ng burchakdagi tugma orqali **old/orqa kamera** almashtiriladi.
7. `MediaRecorder.AudioSource.UNPROCESSED` orqali **AI shovqin bostirish (NS/AGC/AEC) o'chirilib, xom (raw) audio** yoziladi.

## Loyiha tuzilishi
```
CameraMusicApp/
  app/src/main/java/com/example/cameramusicapp/
    MainActivity.kt              - barcha funksiyalarni bog'laydi
    camera/CameraController.kt   - Camera2 preview, video yozish, kamera almashtirish
    audio/MusicVisualizerHelper.kt - musiqa ijrosi + real vaqt waveform ma'lumoti
    ui/WaveformView.kt            - gorizontal ovoz to'lqinini chizuvchi custom View
  app/src/main/res/layout/activity_main.xml - ekran interfeysi
  .github/workflows/build.yml    - Android Studio'siz bulutda APK yasash
```

## APK ni qanday olish mumkin (Android Studio shart emas)

### 1-usul: GitHub orqali (eng oson, kompyuterga hech narsa o'rnatmasdan)
1. Ushbu papkani (`CameraMusicApp`) yangi GitHub repositoriyasiga yuklang (github.com da "New repository" → fayllarni drag-and-drop qilib yuklash mumkin, kod yozish shart emas).
2. Repositoriyaga kirib **Actions** bo'limini oching — "Build APK" workflow avtomatik ishga tushadi (agar tushmasa, "Run workflow" tugmasini bosing).
3. Bir necha daqiqadan so'ng **Actions → oxirgi run → Artifacts** bo'limidan `CameraMusicApp-debug-apk` faylini yuklab oling — bu tayyor `.apk` fayl bo'ladi.
4. Shu apk faylni telefoningizga o'tkazib, o'rnating (noma'lum manbalardan o'rnatishga ruxsat berish kerak bo'lishi mumkin).

### 2-usul: Android Studio bilan (agar keyinchalik kompyuterda bo'lsa)
1. Android Studio'ni oching → "Open" → `CameraMusicApp` papkasini tanlang.
2. Gradle sinxronlanishini kuting.
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
4. Tayyor `.apk` fayl `app/build/outputs/apk/debug/` papkasida paydo bo'ladi.

### 3-usul: Onlayn IDE (Studio o'rnatmasdan, brauzerdan)
Gitpod yoki GitHub Codespaces kabi bepul onlayn muhitlarga shu papkani ochib, terminalda:
```
./gradlew assembleDebug
```
buyrug'ini ishga tushiring — natija xuddi shu `app/build/outputs/apk/debug/app-debug.apk` bo'ladi.

## Muhim eslatmalar
- `minSdkVersion` 26 (Android 8.0+) qilib belgilangan, chunki adaptiv ikonka va zamonaviy kamera API talab qiladi.
- `UNPROCESSED` audio manbasi barcha qurilmalarda qo'llab-quvvatlanmasligi mumkin; bunday holda kod avtomatik `CAMCORDER` manbasiga o'tadi (u ham deyarli xom signal beradi).
- Ilova hozircha ishlaydigan, tuzilishi to'g'ri loyiha sifatida yozilgan; haqiqiy qurilmada sinovdan o'tkazib, kerak bo'lsa nozik sozlashlar (masalan aniq bitrate, UI ranglari) kiritishingiz mumkin.
