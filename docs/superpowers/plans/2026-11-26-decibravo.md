# DeciBravo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Застосунок Android, яким суддя міряє оплески після кожного учасника змагання й отримує один бал (сила × тривалість), таблицю місць і калібрування формули.

**Architecture:** Один модуль `app`. Підрахунок (`scoring`) і зберігання (`data`) — чистий Kotlin без Android-залежностей, покритий JVM-тестами. Запис звуку (`audio`) — тонка обгортка над `AudioRecord`, що видає кадри по 100 мс. Екрани на Jetpack Compose; кожен екран має ViewModel, який лише з'єднує `Store`, `MeasureSession` і джерело кадрів.

**Tech Stack:** Kotlin 2.4.21, Android Gradle Plugin 9.5.3, Jetpack Compose (BOM 2026.11.01), navigation-compose 2.10.2, kotlinx-serialization-json 1.13.0, kotlinx-coroutines 1.11.0, JUnit 4.13.2. Версії перевірено 26.11.2026 за індексами Google Maven і Maven Central.

**Spec:** `docs/superpowers/specs/2026-11-26-decibravo-design.md`. Виконавець читає і план, і специфікацію; номери розділів нижче («спека 8.3») посилаються на неї.

## Global Constraints

- `applicationId` і пакет: `app.decibravo`. `minSdk 26`, `compileSdk 36`, `targetSdk 36`. JDK 17.
- Лише портретна орієнтація, лише темна тема.
- Жодного доступу до мережі: дозволу `INTERNET` у маніфесті немає.
- Єдиний дозвіл: `android.permission.RECORD_AUDIO`.
- Усі тексти інтерфейсу українською, у `res/values/strings.xml` (це типові ресурси, тож українська показується за будь-якої мови системи). У текстах лише «змагання», «учасник», «замір», «бал», «таблиця»; нічого про конкретний захід.
- Кольори: тло `#000000`, текст `#FFFFFF`, другорядний `#A8A8A8`, лінії `#333333`, акцент `#FFD60A`, попередження `#FF9F0A`. Без градієнтів, тіней, карток.
- Цифри балів, часу й місць — Barlow Condensed Bold із `res/font`, рівноширокі цифри. Решта — системний шрифт.
- Головні кнопки 64–96 dp заввишки на всю ширину; будь-яка зона дотику ≥ 48 dp.
- Звук: `AudioRecord`, моно, 48 000 Гц, 16 біт PCM; кадр — 4800 відліків (100 мс) без перекриття.
- Числа: десяткова кома; тривалість з одним знаком («9,1 с»); бал і сила — цілі, заокруглення до найближчого; місця — за незаокругленим балом.
- Дані — у `context.getExternalFilesDir(null)`; запис файлів атомарний (тимчасовий файл, потім перейменування).
- Пакети `scoring` і `data` не імпортують нічого з `android.*` / `androidx.*`.
- Тека ще не є git-репозиторієм: Task 1 створює локальний репозиторій; далі коміт після кожного завдання. Віддаленого репозиторію немає, нічого не пушити. Кожне повідомлення коміту закінчується рядком атрибуції, який задає сесія виконавця.
- Перевірки «на емуляторі» можна робити й на підключеному телефоні; якщо жодного пристрою немає, виконавець каже про це прямо й не позначає крок виконаним.
- Команди збірки запускати з кореня проєкту: `./gradlew :app:testDebugUnitTest` (тести), `./gradlew :app:assembleDebug` (APK у `app/build/outputs/apk/debug/app-debug.apk`).

## Review Focus

Умови, яких специфікація прямо не розписує, але які найімовірніше трапляться судді. Кожна має перевірку в завданні-власнику.

1. **Дозвіл на мікрофон відхилено або відкликано в налаштуваннях.** Очікування: екран із поясненням і кнопкою до налаштувань, без падіння. → Task 7, крок 7.
2. **Застосунок пішов у тло посеред заміру** (дзвінок, блокування екрана, кнопка «додому»). Очікування: замір скасовано, мікрофон звільнено, жодних файлів не лишилось. → Task 6 (`cancel_leavesNoPending`), Task 10, крок 6.
3. **Мікрофон зайнятий або запис обірвався з помилкою.** Очікування: замір скасовано з повідомленням, застосунок працює далі. → Task 6 (`sourceError_endsInErrorState`).
4. **Пошкоджений або недописаний файл даних** (сів акумулятор під час запису). Очікування: пошкоджене змагання пропущено, решта відкривається; тимчасові файли ігноруються. → Task 5 (`corruptCompetition_isSkipped`).
5. **Довгі назви, бал із чотирьох і більше цифр, десятки учасників.** Очікування: назви обрізаються трьома крапками, бал зменшує шрифт і не виходить за екран, списки гортаються. → Task 4 (`scoreScale_*`), Task 9, крок 5.

---

## Розкладка файлів

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, .gitignore
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml, values/themes.xml, font/barlow_condensed_bold.ttf
app/src/main/java/app/decibravo/
  DeciBravoApp.kt            Application: тримає Store і фабрику джерела кадрів
  MainActivity.kt            єдина Activity, NavHost
  scoring/Frame.kt           Frame, сталі кадру
  scoring/Params.kt          Calibration, MeasureParams
  scoring/Levels.kt          рівень кадру, фон, дБFS
  scoring/ScoreEngine.kt     автомат заміру, Result, replay
  scoring/CalibrationMath.kt підбір опорного рівня
  scoring/Ranking.kt         місця
  scoring/Format.kt          показ чисел, текст таблиці, масштаб шрифту балу
  data/Models.kt             серіалізовані моделі
  data/CompetitionRules.kt   блокування, розбір списку учасників
  data/Wav.kt                запис WAV
  data/Store.kt              файли на диску
  audio/FrameChunker.kt      нарізання PCM на кадри
  audio/FrameSource.kt       інтерфейс джерела кадрів, AudioChunk
  audio/Recorder.kt          AudioRecord
  audio/MeasureSession.kt    один замір: автомат + кадри + WAV + стовпчики
  ui/theme/Theme.kt          кольори, шрифти
  ui/common/Components.kt    кнопки, степер, велике число, графік рівня
  ui/common/MicPermission.kt запит дозволу
  ui/Nav.kt                  маршрути
  ui/competitions/…          список змагань
  ui/edit/…                  нове змагання / налаштування
  ui/floor/…                 вимір фону
  ui/table/…                 таблиця
  ui/measure/…               замір і результат
  ui/calibration/…           калібрування й перевірка мікрофона
app/src/test/java/app/decibravo/…   JVM-тести поруч із тим, що перевіряють
README.md                    як зібрати, встановити, забрати дані
```

---

### Task 1: Каркас проєкту, який збирається

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `.gitignore`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/java/app/decibravo/DeciBravoApp.kt`, `app/src/main/java/app/decibravo/MainActivity.kt`
- Test: `app/src/test/java/app/decibravo/SmokeTest.kt`

**Interfaces:**
- Produces: модуль `:app`, що збирається; `class DeciBravoApp : Application`; `class MainActivity : ComponentActivity`.

- [ ] **Step 1: `git init` і `.gitignore`** (`.gradle/`, `build/`, `local.properties`, `.idea/`, `*.iml`, `.DS_Store`).

- [ ] **Step 2: Gradle-обгортка.** `gradle wrapper --gradle-version 9.9.1` (локально встановлено Gradle 9.9.1). Якщо AGP 9.5.3 у кроці 5 вимагатиме іншої версії Gradle, поставити ту, яку назве його повідомлення про помилку.

- [ ] **Step 3: `gradle/libs.versions.toml`** з версіями: `agp = "9.5.3"`, `kotlin = "2.4.21"`, `composeBom = "2026.11.01"`, `activityCompose = "1.13.0"`, `navigationCompose = "2.10.2"`, `lifecycle = "2.11.1"`, `coreKtx = "1.19.1"`, `serializationJson = "1.13.0"`, `coroutines = "1.11.0"`, `junit = "4.13.2"`. Плагіни: `com.android.application`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`. AGP 9 має вбудовану підтримку Kotlin, тож `org.jetbrains.kotlin.android` не підключати; якщо збірка скаже інакше, діяти за її повідомленням.

- [ ] **Step 4: `app/build.gradle.kts` і маніфест.** `namespace` та `applicationId` `app.decibravo`; `minSdk 26`, `compileSdk 36`, `targetSdk 36`; `versionCode 1`, `versionName "0.1"`; Compose увімкнено; залежності з каталогу; `testImplementation` junit і `kotlinx-coroutines-test`. У маніфесті: `uses-permission RECORD_AUDIO`; `application android:name=".DeciBravoApp"`, `android:label="DeciBravo"`; `MainActivity` з `android:screenOrientation="portrait"` і `exported="true"` з фільтром LAUNCHER. `themes.xml`: тема без панелі дій, чорне тло вікна.

- [ ] **Step 4a: Іконка застосунку.** Готове лого лежить у `logo/android/res/` (адаптивна іконка: `drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_monochrome.xml`, `mipmap-anydpi-v26/ic_launcher.xml`, `mipmap-anydpi-v26/ic_launcher_round.xml`, `values/ic_launcher_background.xml`). Скопіювати ці п'ять файлів без змін у відповідні теки `app/src/main/res/` і вказати в маніфесті `android:icon="@mipmap/ic_launcher"` та `android:roundIcon="@mipmap/ic_launcher_round"`. Теку `logo/` не змінювати.

- [ ] **Step 5: `MainActivity`** показує чорний екран із білим текстом `DeciBravo` (тимчасово). `SmokeTest` з одним тестом `assertEquals(4, 2 + 2)`.

- [ ] **Step 6: Перевірка.**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`; існує `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 7: Commit** — `chore: scaffold Android project`.

---

### Task 2: Кадр, параметри, рівень і фон

**Files:**
- Create: `scoring/Frame.kt`, `scoring/Params.kt`, `scoring/Levels.kt`
- Test: `app/src/test/java/app/decibravo/scoring/LevelsTest.kt`

**Interfaces:**
- Produces:

```kotlin
// Frame.kt
const val SAMPLE_RATE = 48_000
const val FRAME_SAMPLES = 4_800
const val FRAME_SEC = 0.1
const val CLIP_SAMPLE = 32_700          // |відлік| ≥ цього = перевантажений
@Serializable data class Frame(val rms: Double, val clipped: Int)

// Params.kt
@Serializable data class Calibration(
    val refDbfs: Double = -30.0, val gamma: Double = 1.0, val thresholdDb: Double = 6.0)
data class MeasureParams(
    val maxDurationSec: Int, val quietEndSec: Double,
    val floorRms: Double, val calibration: Calibration)

// Levels.kt
const val MIN_FLOOR_RMS = 1e-4          // −80 дБFS
fun frameOf(samples: ShortArray, offset: Int = 0, length: Int = samples.size): Frame
fun dbfs(rms: Double): Double           // 20·log10(rms); для rms ≤ 0 повертає -120.0
fun fromDb(db: Double): Double          // 10^(db/20)
fun floorOf(frames: List<Frame>): Double        // медіана rms, не менша за MIN_FLOOR_RMS
fun floorIsNoisy(frames: List<Frame>): Boolean  // найбільший rms перевищує медіану більш ніж на 12 дБ
fun isLoud(frame: Frame, p: MeasureParams): Boolean   // rms ≥ floorRms · fromDb(thresholdDb)
fun contribution(frame: Frame, p: MeasureParams): Double  // спека 8.2; для негучного кадру 0.0
```

`rms` рахується з відліків, поділених на 32768.0. Медіана парної кількості — середнє двох центральних.

- [ ] **Step 1: Написати тести, що падають**

```kotlin
private val cal = Calibration()                 // −30 дБFS, gamma 1, поріг 6 дБ
private val p = MeasureParams(10, 2.0, floorRms = 0.001, calibration = cal)
private val REF = fromDb(-30.0)

@Test fun frameOf_fullScaleSquare() {
    val f = frameOf(ShortArray(4800) { if (it % 2 == 0) 32767 else -32767 })
    assertEquals(32767 / 32768.0, f.rms, 1e-9); assertEquals(4800, f.clipped)
}
@Test fun frameOf_silence() { val f = frameOf(ShortArray(4800)); assertEquals(0.0, f.rms, 0.0); assertEquals(0, f.clipped) }
@Test fun frameOf_countsOnlyNearFullScale() {
    val s = ShortArray(4800) { 1000 }; s[0] = 32700; s[1] = -32700; s[2] = 32699
    assertEquals(2, frameOf(s).clipped)
}
@Test fun dbfs_values() { assertEquals(-20.0, dbfs(0.1), 1e-9); assertEquals(-120.0, dbfs(0.0), 0.0) }
@Test fun floorOf_isMedian() { assertEquals(0.002, floorOf(listOf(0.001, 0.002, 0.9).map { Frame(it, 0) }), 1e-12) }
@Test fun floorOf_clampsToMinimum() { assertEquals(1e-4, floorOf(List(30) { Frame(0.0, 0) }), 0.0) }
@Test fun floorIsNoisy_detectsSpike() {
    assertFalse(floorIsNoisy(List(30) { Frame(0.001, 0) }))
    assertTrue(floorIsNoisy(List(29) { Frame(0.001, 0) } + Frame(0.005, 0)))   // +14 дБ
}
@Test fun isLoud_atThreshold() {
    assertTrue(isLoud(Frame(0.001 * fromDb(6.0), 0), p)); assertFalse(isLoud(Frame(0.0019, 0), p))
}
@Test fun contribution_atReference_isOnePointPerFrame() {
    val rms = sqrt(REF * REF + 0.001 * 0.001)
    assertEquals(1.0, contribution(Frame(rms, 0), p), 1e-9)
}
@Test fun contribution_gammaTwo_doubleLevel_isFourPoints() {
    val rms = sqrt(4 * REF * REF + 0.001 * 0.001)
    assertEquals(4.0, contribution(Frame(rms, 0), p.copy(calibration = cal.copy(gamma = 2.0))), 1e-9)
}
@Test fun contribution_quietFrame_isZero() { assertEquals(0.0, contribution(Frame(0.0015, 0), p), 0.0) }
```

- [ ] **Step 2: Запустити й побачити падіння.** Run: `./gradlew :app:testDebugUnitTest --tests '*LevelsTest'` · Expected: FAIL (не компілюється).
- [ ] **Step 3: Реалізувати три файли за сигнатурами вище.**
- [ ] **Step 4: Запустити.** Expected: PASS, 11 тестів.
- [ ] **Step 5: Commit** — `feat: frame level, floor and per-frame contribution`.

---

### Task 3: Автомат заміру й перерахунок

**Files:**
- Create: `scoring/ScoreEngine.kt`
- Test: `app/src/test/java/app/decibravo/scoring/ScoreEngineTest.kt`

**Interfaces:**
- Consumes: `Frame`, `MeasureParams`, `isLoud`, `contribution`, `FRAME_SEC` (Task 2).
- Produces:

```kotlin
const val ONSET_FRAMES = 3
const val CLIP_LIMIT = 10
enum class Phase { WAITING, APPLAUSE, ENDED }
enum class EndReason { LIMIT, SILENCE, STOP, NO_APPLAUSE }
@Serializable data class Result(
    val score: Double, val durationSec: Double, val strength: Double,
    val reason: EndReason, val overloaded: Boolean)
data class Snapshot(
    val phase: Phase, val score: Double,
    val applauseFrames: Int,      // кадрів від початку оплесків включно з поточним; 0 в очікуванні
    val result: Result?)          // не null лише у фазі ENDED
class ScoreEngine(private val params: MeasureParams) {
    val snapshot: Snapshot
    fun feed(frame: Frame): Snapshot   // після ENDED кадри ігноруються
    fun stop(): Snapshot
}
fun replay(frames: List<Frame>, params: MeasureParams): Result   // кінець ряду без умови = stop()
```

Правила (спека 8.3, 8.3.1, 8.4), усе в цілих кадрах: `maxFrames = maxDurationSec · 10`, `quietFrames = round(quietEndSec · 10)`.
- WAITING → APPLAUSE на третьому гучному кадрі поспіль; ці три кадри йдуть у бал, `applauseFrames` стає 3.
- Якщо після кадру номер `maxFrames` від старту фаза все ще WAITING → ENDED, `NO_APPLAUSE` (навіть якщо йде незавершена серія з одного-двох гучних).
- В APPLAUSE кожен гучний кадр додає `contribution`. ENDED/`LIMIT`, коли `applauseFrames == maxFrames`; ENDED/`SILENCE`, коли поспіль `quietFrames` негучних.
- `durationSec = (індекс останнього гучного − індекс початку оплесків + 1) · 0.1`; `strength = score / durationSec`, за нульової тривалості 0.
- `overloaded` = сума `clipped` усіх поданих кадрів ≥ `CLIP_LIMIT`.
- `stop()` у WAITING → `NO_APPLAUSE`; в APPLAUSE → `STOP`.

- [ ] **Step 1: Написати тести, що падають**

```kotlin
private const val FLOOR = 0.001
private val REF = fromDb(-30.0)
private val LOUD = Frame(sqrt(REF * REF + FLOOR * FLOOR), 0)   // рівно 1 бал за кадр
private val QUIET = Frame(FLOOR, 0)
private val p = MeasureParams(10, 2.0, FLOOR, Calibration())
private fun seq(vararg parts: Pair<Int, Frame>) = parts.flatMap { (n, f) -> List(n) { f } }
private fun live(frames: List<Frame>, params: MeasureParams = p): Pair<Result?, Int> {
    val e = ScoreEngine(params); var used = 0
    for (f in frames) { used++; if (e.feed(f).phase == Phase.ENDED) break }
    return e.snapshot.result to used
}
private fun assertResult(r: Result, score: Double, dur: Double, reason: EndReason) {
    assertEquals(score, r.score, 1e-6); assertEquals(dur, r.durationSec, 1e-9); assertEquals(reason, r.reason)
}

@Test fun applauseThenSilence() {
    val (r, used) = live(seq(50 to LOUD, 30 to QUIET))
    assertResult(r!!, 50.0, 5.0, EndReason.SILENCE); assertEquals(10.0, r.strength, 1e-6); assertEquals(70, used)
}
@Test fun silenceBeforeApplause_changesNothing() {
    assertResult(live(seq(30 to QUIET, 50 to LOUD, 30 to QUIET)).first!!, 50.0, 5.0, EndReason.SILENCE)
}
@Test fun oneOrTwoLoudFramesBeforeApplause_areIgnored() {
    val r = live(seq(5 to QUIET, 2 to LOUD, 5 to QUIET, 50 to LOUD, 30 to QUIET)).first!!
    assertResult(r, 50.0, 5.0, EndReason.SILENCE)
}
@Test fun onsetOnThirdLoudFrame_countsAllThree() {
    val e = ScoreEngine(p)
    assertEquals(Phase.WAITING, e.feed(LOUD).phase); assertEquals(Phase.WAITING, e.feed(LOUD).phase)
    val s = e.feed(LOUD)
    assertEquals(Phase.APPLAUSE, s.phase); assertEquals(3, s.applauseFrames); assertEquals(3.0, s.score, 1e-6)
}
@Test fun pauseShorterThanQuietEnd_doesNotEnd() {
    assertResult(live(seq(20 to LOUD, 19 to QUIET, 20 to LOUD, 30 to QUIET)).first!!, 40.0, 5.9, EndReason.SILENCE)
}
@Test fun singleLoudFrameDuringApplause_counts() {
    assertResult(live(seq(20 to LOUD, 10 to QUIET, 1 to LOUD, 30 to QUIET)).first!!, 21.0, 3.1, EndReason.SILENCE)
}
@Test fun limit_endsExactlyAtMaxDuration() {
    val (r, used) = live(seq(150 to LOUD))
    assertResult(r!!, 100.0, 10.0, EndReason.LIMIT); assertEquals(100, used)
}
@Test fun limit_countsFromOnsetNotFromStart() {
    val (r, used) = live(seq(40 to QUIET, 150 to LOUD))
    assertResult(r!!, 100.0, 10.0, EndReason.LIMIT); assertEquals(140, used)
}
@Test fun noApplause_endsAfterMaxDuration() {
    val (r, used) = live(seq(200 to QUIET))
    assertResult(r!!, 0.0, 0.0, EndReason.NO_APPLAUSE); assertEquals(0.0, r.strength, 0.0); assertEquals(100, used)
}
@Test fun noApplause_evenWithPendingOnsetAtTimeout() {
    assertEquals(EndReason.NO_APPLAUSE, live(seq(98 to QUIET, 50 to LOUD)).first!!.reason)
}
@Test fun stop_duringApplause() {
    val e = ScoreEngine(p); repeat(30) { e.feed(LOUD) }
    assertResult(e.stop().result!!, 30.0, 3.0, EndReason.STOP)
}
@Test fun stop_whileWaiting_isNoApplause() { assertEquals(EndReason.NO_APPLAUSE, ScoreEngine(p).stop().result!!.reason) }
@Test fun feedAfterEnd_isIgnored() {
    val e = ScoreEngine(p); repeat(30) { e.feed(LOUD) }; val r = e.stop().result
    assertEquals(r, e.feed(LOUD).result)
}
@Test fun replay_ofStoppedSeries_isStop() { assertResult(replay(seq(30 to LOUD), p), 30.0, 3.0, EndReason.STOP) }
@Test fun replay_equalsLive_forSilenceAndLimit() {
    for (frames in listOf(seq(10 to QUIET, 50 to LOUD, 30 to QUIET), seq(150 to LOUD))) {
        val (r, used) = live(frames); assertEquals(r, replay(frames.take(used), p))
    }
}
@Test fun sameFrames_sameResult() { val f = seq(7 to QUIET, 33 to LOUD, 25 to QUIET); assertEquals(replay(f, p), replay(f, p)) }
@Test fun twiceAsLong_twiceTheScore() {
    assertEquals(2 * replay(seq(20 to LOUD), p).score, replay(seq(40 to LOUD), p).score, 1e-6)
}
@Test fun louder_scoresMore() {
    val louder = Frame(sqrt(4 * REF * REF + FLOOR * FLOOR), 0)
    assertEquals(60.0, replay(seq(30 to louder), p).score, 1e-6)
}
@Test fun scoreEqualsStrengthTimesDuration() {
    val r = replay(seq(20 to LOUD, 5 to QUIET, 20 to LOUD), p); assertEquals(r.score, r.strength * r.durationSec, 1e-9)
}
@Test fun refAndGamma_keepDurationAndReason() {
    val f = seq(10 to QUIET, 50 to LOUD, 20 to QUIET); val a = replay(f, p)
    val b = replay(f, p.copy(calibration = Calibration(refDbfs = -20.0, gamma = 1.6)))
    assertEquals(a.durationSec, b.durationSec, 0.0); assertEquals(a.reason, b.reason); assertNotEquals(a.score, b.score, 1e-3)
}
@Test fun overloaded_fromTenClippedSamples() {
    assertTrue(replay(seq(30 to LOUD) + Frame(LOUD.rms, 10), p).overloaded)
    assertFalse(replay(seq(30 to LOUD) + Frame(LOUD.rms, 9), p).overloaded)
}
@Test fun quietEndHalfSeconds() {
    val (r, used) = live(seq(20 to LOUD, 40 to QUIET), p.copy(quietEndSec = 1.5))
    assertEquals(EndReason.SILENCE, r!!.reason); assertEquals(35, used)
}
```

- [ ] **Step 2: Запустити й побачити падіння.** Run: `./gradlew :app:testDebugUnitTest --tests '*ScoreEngineTest'` · Expected: FAIL.
- [ ] **Step 3: Реалізувати `ScoreEngine` і `replay`.**
- [ ] **Step 4: Запустити.** Expected: PASS, 22 тести.
- [ ] **Step 5: Commit** — `feat: measurement state machine and replay`.

---

### Task 4: Калібрування, місця, показ чисел

**Files:**
- Create: `scoring/CalibrationMath.kt`, `scoring/Ranking.kt`, `scoring/Format.kt`
- Test: `scoring/CalibrationMathTest.kt`, `scoring/RankingTest.kt`, `scoring/FormatTest.kt`

**Interfaces:**
- Consumes: `replay`, `Result`, `MeasureParams`, `Calibration`.
- Produces:

```kotlin
// CalibrationMath.kt — спека 9.6 «швидке встановлення масштабу»
// newRefDbfs = refDbfs + (20 / gamma) · log10(score / target); null, якщо бал 0, target ≤ 0 або результат поза −60…0
fun refForTargetScore(frames: List<Frame>, params: MeasureParams, targetScore: Double): Double?

// Ranking.kt
data class Entry(val participantId: String, val score: Double, val durationSec: Double, val acceptedAt: Long)
data class Ranked(val place: Int, val entry: Entry)
fun rank(entries: List<Entry>): List<Ranked>     // бал ↓, тривалість ↓, acceptedAt ↑; місця 1, 2, 3… без пропусків і повторів
fun placeIfAccepted(score: Double, durationSec: Double, others: List<Entry>): Int   // місце нового результату серед others

// Format.kt
fun fmtScore(score: Double): String        // "262"
fun fmtSeconds(sec: Double): String        // "9,1"
fun fmtStrength(strength: Double): String  // "29"
fun fmtPlace(place: Int): String           // "1-ше", "2-ге", "3-тє", "4-те", "7-ме", "8-ме", інакше "N-те"
data class TableRow(val place: Int?, val name: String, val result: Result?)
fun tableText(competitionName: String, rows: List<TableRow>): String
fun scoreScale(text: String): Float        // 1f для ≤3 знаків; 3f / довжина для довших
```

`tableText`: перший рядок — назва змагання; далі `1. Три царі — 314 (9,8 с, сила 32)` для рядків із результатом; для рядків без результату `— Чорти`.

- [ ] **Step 1: Написати тести, що падають**

```kotlin
// CalibrationMathTest (LOUD, QUIET, p, seq — як у Task 3)
@Test fun refForTarget_hitsTarget() {
    val f = seq(50 to LOUD); val ref = refForTargetScore(f, p, 100.0)!!
    assertEquals(100.0, replay(f, p.copy(calibration = p.calibration.copy(refDbfs = ref))).score, 1e-6)
}
@Test fun refForTarget_respectsGamma() {
    val pg = p.copy(calibration = Calibration(gamma = 1.6)); val f = seq(50 to LOUD)
    val ref = refForTargetScore(f, pg, 123.0)!!
    assertEquals(123.0, replay(f, pg.copy(calibration = pg.calibration.copy(refDbfs = ref))).score, 1e-6)
}
@Test fun refForTarget_zeroScore_isNull() { assertNull(refForTargetScore(seq(50 to QUIET), p, 100.0)) }
@Test fun refForTarget_outOfRange_isNull() { assertNull(refForTargetScore(seq(50 to LOUD), p, 1e9)) }

// RankingTest
@Test fun rank_byScoreThenDurationThenTime() {
    val r = rank(listOf(Entry("a", 100.0, 5.0, 3), Entry("b", 200.0, 4.0, 2), Entry("c", 100.0, 6.0, 4), Entry("d", 100.0, 6.0, 1)))
    assertEquals(listOf("b", "d", "c", "a"), r.map { it.entry.participantId }); assertEquals(listOf(1, 2, 3, 4), r.map { it.place })
}
@Test fun rank_usesUnroundedScore() { assertEquals("y", rank(listOf(Entry("x", 261.6, 5.0, 1), Entry("y", 262.4, 5.0, 2)))[0].entry.participantId) }
@Test fun placeIfAccepted_between() { assertEquals(2, placeIfAccepted(150.0, 5.0, listOf(Entry("a", 100.0, 5.0, 1), Entry("b", 200.0, 4.0, 2)))) }
@Test fun placeIfAccepted_empty_isFirst() { assertEquals(1, placeIfAccepted(0.0, 0.0, emptyList())) }

// FormatTest
@Test fun numbers() {
    assertEquals("262", fmtScore(261.5)); assertEquals("0", fmtScore(0.0))
    assertEquals("9,1", fmtSeconds(9.12)); assertEquals("10,0", fmtSeconds(10.0)); assertEquals("29", fmtStrength(28.8))
}
@Test fun places() { assertEquals(listOf("1-ше", "2-ге", "3-тє", "4-те", "7-ме", "8-ме", "12-те"), listOf(1, 2, 3, 4, 7, 8, 12).map(::fmtPlace)) }
@Test fun tableText_format() {
    val r = Result(313.6, 9.8, 32.0, EndReason.LIMIT, false)
    assertEquals("Коляда 2026\n1. Три царі — 314 (9,8 с, сила 32)\n— Чорти",
        tableText("Коляда 2026", listOf(TableRow(1, "Три царі", r), TableRow(null, "Чорти", null))))
}
@Test fun scoreScale_shrinksLongNumbers() {
    assertEquals(1f, scoreScale("262"), 0f); assertEquals(0.75f, scoreScale("1240"), 1e-6f); assertEquals(0.6f, scoreScale("12400"), 1e-6f)
}
```

- [ ] **Step 2: Запустити й побачити падіння.** Run: `./gradlew :app:testDebugUnitTest --tests '*CalibrationMathTest' --tests '*RankingTest' --tests '*FormatTest'` · Expected: FAIL.
- [ ] **Step 3: Реалізувати три файли.**
- [ ] **Step 4: Запустити.** Expected: PASS, 12 тестів.
- [ ] **Step 5: Commit** — `feat: calibration math, ranking and number formatting`.

---

### Task 5: Моделі, правила змагання, WAV і сховище

**Files:**
- Create: `data/Models.kt`, `data/CompetitionRules.kt`, `data/Wav.kt`, `data/Store.kt`
- Test: `data/CompetitionRulesTest.kt`, `data/WavTest.kt`, `data/StoreTest.kt`

**Interfaces:**
- Consumes: `Frame`, `Calibration`, `Result`, `MeasureParams`, `replay`.
- Produces:

```kotlin
// Models.kt
@Serializable data class Participant(val id: String, val name: String, val measurementId: String? = null)
@Serializable data class Competition(
    val id: String, val name: String, val createdAt: Long,
    val maxDurationSec: Int = 10, val quietEndSec: Double = 2.0,
    val floorRms: Double? = null, val floorMeasuredAt: Long? = null,
    val frozenCalibration: Calibration? = null,
    val participants: List<Participant> = emptyList())
@Serializable data class Measurement(
    val id: String, val participantId: String, val acceptedAt: Long,
    val audioSource: String, val frames: List<Frame>, val result: Result, val wavFile: String)
class PendingMeasurement(val frames: List<Frame>, val result: Result, val audioSource: String, val wavTemp: File)

// CompetitionRules.kt
const val MAX_DURATION_MIN = 3; const val MAX_DURATION_MAX = 60
const val QUIET_END_MIN = 1.0; const val QUIET_END_MAX = 5.0; const val QUIET_END_STEP = 0.5
fun Competition.isLocked(): Boolean                       // є хоч один учасник із measurementId
fun Competition.acceptedCount(): Int
fun Competition.effectiveCalibration(device: Calibration): Calibration   // frozenCalibration ?: device
fun Competition.measureParams(device: Calibration): MeasureParams?       // null, якщо фон не виміряно
fun parseParticipantNames(text: String): List<String>     // по рядку; обрізати пробіли; пропустити порожні; повтори лишити

// Wav.kt — 48 кГц, моно, 16 біт, little-endian, заголовок 44 байти
class WavWriter(file: File) : Closeable { fun write(pcm: ShortArray, length: Int = pcm.size); override fun close() }

// Store.kt
class Store(private val root: File, private val now: () -> Long = System::currentTimeMillis) {
    fun loadCalibration(): Calibration                    // типове, якщо файла немає або він пошкоджений
    fun saveCalibration(c: Calibration)
    fun listCompetitions(): List<Competition>             // найновіше перше; пошкоджені пропущено
    fun loadCompetition(id: String): Competition?
    fun createCompetition(name: String, names: List<String>, maxDurationSec: Int, quietEndSec: Double): Competition
    fun saveCompetition(c: Competition)                   // кидає IllegalStateException, якщо змагання заблоковане, а змінено maxDurationSec, quietEndSec або floorRms
    fun deleteCompetition(id: String)
    fun addParticipants(competitionId: String, names: List<String>): Competition
    fun renameParticipant(competitionId: String, participantId: String, name: String): Competition
    fun removeParticipant(competitionId: String, participantId: String): Competition   // разом із його заміром
    fun loadMeasurement(competitionId: String, measurementId: String): Measurement?
    fun accept(competitionId: String, participantId: String, pending: PendingMeasurement): Competition
    fun deleteMeasurement(competitionId: String, participantId: String): Competition
    fun recalculate(competitionId: String): Competition   // спека 9.6: лише refDbfs і gamma з калібрування телефона
    fun newTempWav(): File                                // у root/tmp
}
```

Розкладка на диску: `root/calibration.json`; `root/competitions/<id>/competition.json`; `root/competitions/<id>/measurements/<mid>.json` і `<mid>.wav`; `root/tmp/`. Ідентифікатори — `UUID.randomUUID().toString()`. Запис JSON: у `<name>.tmp`, потім `renameTo`. `accept`: переносить `wavTemp` на місце, пише замір, вилучає файли попереднього заміру учасника, ставить `frozenCalibration = loadCalibration()`, якщо вона була `null`. `deleteMeasurement` і `removeParticipant`: якщо зарахованих не лишилось, `frozenCalibration = null`. `recalculate`: `frozenCalibration = frozen.copy(refDbfs = device.refDbfs, gamma = device.gamma)`, потім для кожного заміру `result = replay(frames, params)` і перезапис; для змагання без зарахованих замірів нічого не робить. Конструктор `Store` чистить `root/tmp`.

- [ ] **Step 1: Написати тести, що падають**

```kotlin
// CompetitionRulesTest
@Test fun parseNames() { assertEquals(listOf("Три царі", "Ангели", "Ангели"), parseParticipantNames("  Три царі \n\n Ангели\r\nАнгели\n  ")) }
@Test fun lockAndCalibration() {
    val open = Competition("c", "n", 0, participants = listOf(Participant("p", "A")))
    assertFalse(open.isLocked()); assertEquals(Calibration(gamma = 1.3), open.effectiveCalibration(Calibration(gamma = 1.3)))
    val locked = open.copy(frozenCalibration = Calibration(gamma = 2.0), participants = listOf(Participant("p", "A", "m")))
    assertTrue(locked.isLocked()); assertEquals(2.0, locked.effectiveCalibration(Calibration()).gamma, 0.0)
}
@Test fun measureParams_needsFloor() {
    assertNull(Competition("c", "n", 0).measureParams(Calibration()))
    assertEquals(0.002, Competition("c", "n", 0, floorRms = 0.002).measureParams(Calibration())!!.floorRms, 0.0)
}

// WavTest
@Test fun header_andData() {
    val f = File.createTempFile("t", ".wav"); WavWriter(f).use { it.write(ShortArray(4800) { 1 }); it.write(ShortArray(4800) { -1 }) }
    val b = f.readBytes(); assertEquals(44 + 19200, b.size); assertEquals("RIFF", String(b, 0, 4)); assertEquals("WAVE", String(b, 8, 4))
    val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(36 + 19200, bb.getInt(4)); assertEquals(1, bb.getShort(22).toInt()); assertEquals(48000, bb.getInt(24))
    assertEquals(16, bb.getShort(34).toInt()); assertEquals(19200, bb.getInt(40)); assertEquals(1, bb.getShort(44).toInt())
}

// StoreTest — @get:Rule val tmp = TemporaryFolder(); helper pending(score): PendingMeasurement з frames = seq(30 to LOUD), справжнім тимчасовим wav
@Test fun createAndReload() {
    val c = Store(tmp.root).createCompetition("Проба", listOf("A", "B"), 10, 2.0)
    val again = Store(tmp.root).loadCompetition(c.id)!!
    assertEquals("Проба", again.name); assertEquals(listOf("A", "B"), again.participants.map { it.name })
}
@Test fun accept_freezesCalibration_andPersists() {
    val s = Store(tmp.root); s.saveCalibration(Calibration(gamma = 1.4)); val c = s.createCompetition("n", listOf("A"), 10, 2.0)
    val after = s.accept(c.id, c.participants[0].id, pending())
    assertEquals(1.4, after.frozenCalibration!!.gamma, 0.0); assertTrue(after.isLocked())
    val m = Store(tmp.root).loadMeasurement(c.id, after.participants[0].measurementId!!)!!
    assertEquals(30, m.frames.size); assertTrue(File(tmp.root, "competitions/${c.id}/measurements/${m.wavFile}").exists())
}
@Test fun accept_again_replacesOldFiles() {
    val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); val pid = c.participants[0].id
    val first = s.accept(c.id, pid, pending()).participants[0].measurementId!!; s.accept(c.id, pid, pending())
    val dir = File(tmp.root, "competitions/${c.id}/measurements")
    assertEquals(2, dir.listFiles()!!.size); assertFalse(File(dir, "$first.json").exists())
}
@Test fun deletingLastMeasurement_unlocksAndClearsCalibration() {
    val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); val pid = c.participants[0].id
    s.accept(c.id, pid, pending()); val after = s.deleteMeasurement(c.id, pid)
    assertFalse(after.isLocked()); assertNull(after.frozenCalibration)
    assertEquals(0, File(tmp.root, "competitions/${c.id}/measurements").listFiles()!!.size)
}
@Test(expected = IllegalStateException::class) fun lockedCompetition_rejectsSettingsChange() {
    val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0)
    s.saveCompetition(s.accept(c.id, c.participants[0].id, pending()).copy(maxDurationSec = 20))
}
@Test fun lockedCompetition_allowsRenameAndNewParticipants() {
    val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); s.accept(c.id, c.participants[0].id, pending())
    s.saveCompetition(s.loadCompetition(c.id)!!.copy(name = "нова")); assertEquals(2, s.addParticipants(c.id, listOf("B")).participants.size)
}
@Test fun recalculate_takesOnlyRefAndGamma() {
    val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); val pid = c.participants[0].id
    s.saveCompetition(c.copy(floorRms = 0.001)); val before = s.accept(c.id, pid, pending())
    s.saveCalibration(Calibration(refDbfs = -24.0, gamma = 1.0, thresholdDb = 12.0)); val after = s.recalculate(c.id)
    assertEquals(-24.0, after.frozenCalibration!!.refDbfs, 0.0); assertEquals(6.0, after.frozenCalibration!!.thresholdDb, 0.0)
    val m = s.loadMeasurement(c.id, after.participants[0].measurementId!!)!!
    assertEquals(3.0, m.result.durationSec, 1e-9); assertTrue(m.result.score < 30.0)
}
@Test fun corruptCompetition_isSkipped() {
    val s = Store(tmp.root); val good = s.createCompetition("добре", emptyList(), 10, 2.0); val bad = s.createCompetition("зле", emptyList(), 10, 2.0)
    File(tmp.root, "competitions/${bad.id}/competition.json").writeText("{ \"id\": ")
    File(tmp.root, "competitions/${good.id}/competition.json.tmp").writeText("сміття")
    assertEquals(listOf(good.id), Store(tmp.root).listCompetitions().map { it.id }); assertEquals(Calibration(), Store(tmp.root).loadCalibration())
}
@Test fun deleteCompetition_removesDirectory() {
    val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A"), 10, 2.0); s.deleteCompetition(c.id)
    assertFalse(File(tmp.root, "competitions/${c.id}").exists()); assertTrue(s.listCompetitions().isEmpty())
}
@Test fun removeParticipant_dropsItsMeasurement() {
    val s = Store(tmp.root); val c = s.createCompetition("n", listOf("A", "B"), 10, 2.0); s.accept(c.id, c.participants[0].id, pending())
    val after = s.removeParticipant(c.id, c.participants[0].id); assertEquals(listOf("B"), after.participants.map { it.name }); assertFalse(after.isLocked())
}
```

- [ ] **Step 2: Запустити й побачити падіння.** Run: `./gradlew :app:testDebugUnitTest --tests 'app.decibravo.data.*'` · Expected: FAIL.
- [ ] **Step 3: Реалізувати чотири файли.** JSON через `kotlinx.serialization` з `ignoreUnknownKeys = true`.
- [ ] **Step 4: Запустити.** Expected: PASS, 14 тестів.
- [ ] **Step 5: Commit** — `feat: models, competition rules, WAV writer and file store`.

---

### Task 6: Нарізання на кадри й сеанс заміру

**Files:**
- Create: `audio/FrameSource.kt`, `audio/FrameChunker.kt`, `audio/MeasureSession.kt`
- Test: `audio/FrameChunkerTest.kt`, `audio/MeasureSessionTest.kt`

**Interfaces:**
- Consumes: `frameOf`, `ScoreEngine`, `Snapshot`, `Result`, `MeasureParams`, `WavWriter`, `PendingMeasurement`, `dbfs`.
- Produces:

```kotlin
// FrameSource.kt
class AudioChunk(val frame: Frame, val pcm: ShortArray)      // pcm.size == FRAME_SAMPLES
interface FrameSource {
    val sourceName: String                 // "UNPROCESSED" або "VOICE_RECOGNITION"
    fun chunks(): Flow<AudioChunk>         // холодний потік; запис іде, поки його збирають; помилка = виняток у потоці
}

// FrameChunker.kt — збирає довільні порції PCM у повні кадри
class FrameChunker { fun push(buffer: ShortArray, length: Int): List<AudioChunk> }

// MeasureSession.kt — чиста логіка одного заміру, без потоків
const val BAR_COUNT = 50
const val BAR_RANGE_DB = 40.0
sealed interface MeasureState {
    data object Ready : MeasureState
    data class Running(val waiting: Boolean, val score: Double, val elapsedSec: Double, val bars: List<Float>) : MeasureState
    data class Done(val result: Result) : MeasureState
    data class Failed(val cause: Throwable) : MeasureState
}
class MeasureSession(private val params: MeasureParams, private val sourceName: String, private val wavTemp: File) {
    val state: MeasureState
    fun start(): MeasureState                 // Ready → Running(waiting = true)
    fun onChunk(chunk: AudioChunk): MeasureState
    fun stop(): MeasureState
    fun fail(cause: Throwable): MeasureState  // вилучає wavTemp
    fun cancel()                              // вилучає wavTemp; state → Ready
    fun pending(): PendingMeasurement?        // не null лише в Done
}
```

`bars`: рівно `BAR_COUNT` значень 0…1 лише в стані оплесків (в очікуванні список порожній). Стовпчик `i` охоплює кадри оплесків `[i·k, (i+1)·k)`, де `k = maxFrames / BAR_COUNT` (дробове; кадр належить стовпчику `floor(n / k)`); значення — найбільше з `((dbfs(rms) − dbfs(floorRms)) / BAR_RANGE_DB)` обмежене 0…1; ще не досягнуті стовпчики `-1f` («порожній»). WAV пишеться з першого кадру після `start()` до кінця заміру; у `Done` файл закрито.

- [ ] **Step 1: Написати тести, що падають**

```kotlin
// FrameChunkerTest
@Test fun oddSizedPushes_makeWholeFrames() {
    val c = FrameChunker(); val buf = ShortArray(1000) { 16384 }; var out = 0
    repeat(12) { out += c.push(buf, 1000).size }            // 12 000 відліків = 2 повні кадри
    assertEquals(2, out); assertEquals(1, c.push(buf, 1000).size + c.push(buf, 1000).size + c.push(buf, 400).size)
}
@Test fun chunk_hasFrameAndPcm() {
    val ch = FrameChunker().push(ShortArray(4800) { 16384 }, 4800).single()
    assertEquals(0.5, ch.frame.rms, 1e-9); assertEquals(4800, ch.pcm.size)
}
@Test fun lengthParameter_isRespected() { assertTrue(FrameChunker().push(ShortArray(9600), 4799).isEmpty()) }

// MeasureSessionTest — fun chunk(rms: Double) = AudioChunk(Frame(rms, 0), ShortArray(FRAME_SAMPLES)); LOUD_RMS = LOUD.rms, FLOOR, p як у Task 3
@Test fun fullRun_endsInDone_withPendingAndWav() {
    val wav = tmp.newFile("m.wav"); val s = MeasureSession(p, "UNPROCESSED", wav); s.start()
    repeat(50) { s.onChunk(chunk(LOUD_RMS)) }; repeat(20) { s.onChunk(chunk(FLOOR)) }
    val done = s.state as MeasureState.Done; assertEquals(5.0, done.result.durationSec, 1e-9)
    val pm = s.pending()!!; assertEquals(70, pm.frames.size); assertEquals("UNPROCESSED", pm.audioSource)
    assertEquals(44 + 70 * 9600, wav.length().toInt())
}
@Test fun waitingThenRunning() {
    val s = MeasureSession(p, "x", tmp.newFile()); assertTrue((s.start() as MeasureState.Running).waiting)
    s.onChunk(chunk(LOUD_RMS)); s.onChunk(chunk(LOUD_RMS)); val r = s.onChunk(chunk(LOUD_RMS)) as MeasureState.Running
    assertFalse(r.waiting); assertEquals(0.3, r.elapsedSec, 1e-9); assertEquals(BAR_COUNT, r.bars.size)
    assertTrue(r.bars[0] > 0f); assertEquals(-1f, r.bars[49], 0f)
}
@Test fun stop_givesDone() {
    val s = MeasureSession(p, "x", tmp.newFile()); s.start(); repeat(30) { s.onChunk(chunk(LOUD_RMS)) }
    assertEquals(EndReason.STOP, (s.stop() as MeasureState.Done).result.reason)
}
@Test fun cancel_leavesNoPending() {
    val wav = tmp.newFile(); val s = MeasureSession(p, "x", wav); s.start(); repeat(30) { s.onChunk(chunk(LOUD_RMS)) }; s.cancel()
    assertNull(s.pending()); assertFalse(wav.exists()); assertEquals(MeasureState.Ready, s.state)
}
@Test fun sourceError_endsInErrorState() {
    val wav = tmp.newFile(); val s = MeasureSession(p, "x", wav); s.start(); s.onChunk(chunk(LOUD_RMS))
    assertTrue(s.fail(IllegalStateException("mic")) is MeasureState.Failed); assertNull(s.pending()); assertFalse(wav.exists())
}
@Test fun chunksAfterDone_areIgnored() {
    val s = MeasureSession(p, "x", tmp.newFile()); s.start(); repeat(30) { s.onChunk(chunk(LOUD_RMS)) }; val d = s.stop()
    assertEquals(d, s.onChunk(chunk(LOUD_RMS))); assertEquals(30, s.pending()!!.frames.size)
}
```

- [ ] **Step 2: Запустити й побачити падіння.** Run: `./gradlew :app:testDebugUnitTest --tests 'app.decibravo.audio.*'` · Expected: FAIL.
- [ ] **Step 3: Реалізувати три файли.**
- [ ] **Step 4: Запустити.** Expected: PASS, 9 тестів.
- [ ] **Step 5: Commit** — `feat: frame chunker and measurement session`.

---

### Task 7: Запис звуку, тема, перевірка мікрофона — перша збірка для власника

**Files:**
- Create: `audio/Recorder.kt`, `ui/theme/Theme.kt`, `ui/common/Components.kt`, `ui/common/MicPermission.kt`, `ui/Nav.kt`, `ui/calibration/CalibrationScreen.kt`, `ui/calibration/CalibrationViewModel.kt`, `app/src/main/res/font/barlow_condensed_bold.ttf`, `app/src/main/assets/licenses/OFL-BarlowCondensed.txt`
- Modify: `DeciBravoApp.kt`, `MainActivity.kt`, `res/values/strings.xml`

**Interfaces:**
- Consumes: `FrameSource`, `AudioChunk`, `FrameChunker`, `Store`, `floorOf`, `floorIsNoisy`, `dbfs`.
- Produces:

```kotlin
// Recorder.kt
class Recorder(private val context: Context) : FrameSource   // chunks() працює на Dispatchers.IO
// DeciBravoApp.kt
class DeciBravoApp : Application() { val store: Store; fun frameSource(): FrameSource }
// Theme.kt
object Palette { val Bg; val Text; val Secondary; val Line; val Accent; val Warning }   // значення з Global Constraints
val DigitFont: FontFamily            // Barlow Condensed Bold
@Composable fun DeciBravoTheme(content: @Composable () -> Unit)
// Components.kt
@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 68.dp, enabled: Boolean = true)   // жовта, чорний текст
@Composable fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 64.dp, enabled: Boolean = true)   // біла рамка
@Composable fun Stepper(valueText: String, onMinus: () -> Unit, onPlus: () -> Unit, minusLabel: String, plusLabel: String, enabled: Boolean = true)
@Composable fun ScoreNumber(text: String, color: Color = Palette.Text, modifier: Modifier = Modifier)   // на всю ширину, множиться на scoreScale(text)
@Composable fun LevelBars(bars: List<Float>, modifier: Modifier = Modifier)   // -1f = сіра крапка, 0…1 = жовтий стовпчик
@Composable fun TopBar(title: String, onBack: (() -> Unit)?, action: (@Composable () -> Unit)? = null)
// MicPermission.kt
@Composable fun WithMicPermission(content: @Composable () -> Unit)   // запит; при відмові — пояснення й кнопка до налаштувань застосунку
// Nav.kt
object Routes { const val COMPETITIONS = "competitions"; const val CALIBRATION = "calibration"
    const val NEW = "competition/new"; fun table(id: String) = "competition/$id"; fun edit(id: String) = "competition/$id/edit"
    fun floor(id: String, thenParticipant: String? = null): String
    fun measure(id: String, participantId: String) = "competition/$id/measure/$participantId"
    fun result(id: String, participantId: String) = "competition/$id/result/$participantId" }
```

`Recorder` (спека 7.1): джерело `UNPROCESSED`, якщо `AudioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"`, інакше `VOICE_RECOGNITION`; `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`, 48 000 Гц; буфер не менший за `getMinBufferSize` і за 2 кадри. Після створення: якщо `AutomaticGainControl.isAvailable()` / `NoiseSuppressor.isAvailable()`, створити для `audioSessionId` і `enabled = false`. Якщо стан не `STATE_INITIALIZED` або `read` повертає від'ємне — виняток у потоці. При скасуванні збирання: `stop()` і `release()`.

У цьому завданні екран калібрування має лише блок **«Перевірка мікрофона»** (решта — Task 11):
- рядок «Джерело звуку: без обробки» або «Джерело звуку: розпізнавання мови (без обробки не підтримується)»;
- кнопка «Слухати» / «Зупинити»; поки слухає — поточний рівень у дБFS великими цифрами, найбільший рівень за останні 5 секунд, `LevelBars` з останніх 50 кадрів (шкала −80…0 дБFS), екран не гасне;
- кнопка «Виміряти фон (3 с)»: відлік, потім «Фон: −52 дБFS»; якщо `floorIsNoisy` — «Були гучні сплески; переміряйте в тиші».

`MainActivity` тимчасово відкриває екран калібрування як стартовий (у Task 8 стартовим стане список змагань).

- [ ] **Step 1: Шрифт.** Покласти `BarlowCondensed-Bold.ttf` і `OFL.txt` з `https://github.com/google/fonts/tree/main/ofl/barlowcondensed` у шляхи вище. Завантаження файла з мережі потребує дозволу власника в сесії виконання; спитати перед цим кроком.
- [ ] **Step 2: `Theme.kt`, `Components.kt`, `Nav.kt`.**
- [ ] **Step 3: `Recorder.kt`, `DeciBravoApp` (`store = Store(getExternalFilesDir(null)!!)`).**
- [ ] **Step 4: `MicPermission.kt`, `CalibrationViewModel`, `CalibrationScreen` (блок перевірки мікрофона).**
- [ ] **Step 5: Зібрати й запустити тести.** Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug` · Expected: `BUILD SUCCESSFUL`, усі попередні тести PASS.
- [ ] **Step 6: Перевірка на емуляторі** (`Medium_Phone_API_36`, мікрофон хоста). Run: `./gradlew :app:installDebug`, відкрити застосунок. Expected: після дозволу «Слухати» показує рівень, який помітно росте від оплесків біля мікрофона; «Виміряти фон» дає число; після «Зупинити» індикатор мікрофона в системі зникає.
- [ ] **Step 7: Перевірка відмови в дозволі.** Відхилити дозвіл; потім відкликати його в налаштуваннях і повернутися. Expected: екран із поясненням і кнопкою «Відкрити налаштування», без падіння; після надання дозволу блок працює.
- [ ] **Step 8: Commit** — `feat: audio recorder, theme and microphone check`.
- [ ] **Step 9: Віха для власника.** Передати `app/build/outputs/apk/debug/app-debug.apk` із проханням: встановити на телефон судді, натиснути «Слухати», плеснути тихо й гучно з однакової відстані та повідомити обидва рівні й рядок «Джерело звуку». Різниця має бути помітною (орієнтир: понад 6 дБ). Далі йти за планом, не чекаючи; якщо різниці немає, повідомити власника, що потрібен інший телефон (спека, розділ 3).

---

### Task 8: Список змагань, створення й налаштування

**Files:**
- Create: `ui/competitions/CompetitionListScreen.kt`, `ui/competitions/CompetitionListViewModel.kt`, `ui/edit/CompetitionEditScreen.kt`, `ui/edit/CompetitionEditViewModel.kt`
- Modify: `MainActivity.kt` (NavHost, стартовий маршрут `Routes.COMPETITIONS`), `res/values/strings.xml`
- Test: `app/src/test/java/app/decibravo/ui/edit/EditFormTest.kt`

**Interfaces:**
- Consumes: `Store`, `Competition`, `isLocked`, `acceptedCount`, `parseParticipantNames`, сталі меж із `CompetitionRules.kt`, компоненти Task 7.
- Produces:

```kotlin
// CompetitionEditViewModel.kt — чиста форма, яку тестуємо без Android
data class EditForm(val name: String = "", val participantsText: String = "", val maxDurationSec: Int = 10, val quietEndSec: Double = 2.0) {
    fun plusMax(): EditForm; fun minusMax(): EditForm          // крок 1, межі 3…60
    fun plusQuiet(): EditForm; fun minusQuiet(): EditForm      // крок 0,5, межі 1…5
    val canSave: Boolean                                       // назва не порожня після trim
}
fun summaryLine(c: Competition): String   // "5 із 9 замірів" або "Ще не почато · 14 учасників"
```

Екрани (спека 9.2, 9.3):
- **Список:** заголовок «Змагання»; кнопка-іконка «Калібрування» (`contentDescription`); рядки з назвою і `summaryLine`; порожній стан «Змагань ще немає. Створіть перше.»; унизу `PrimaryButton("Нове змагання")`.
- **Нове змагання** (`Routes.NEW`): назва; багаторядкове поле «Учасники · N» з підказкою «По одному в рядку. Можна вставити весь список одразу.»; два степери «Найдовший замір» (`10 с`) і «Тиша закінчує замір» (`2 с`, `1,5 с`); рядок «Фон» зі станом і кнопкою «Виміряти» (спершу зберігає змагання, потім веде на `Routes.floor(id)`); `PrimaryButton("Створити")` веде на `Routes.table(id)`.
- **Налаштування** (`Routes.edit(id)`), той самий екран: кнопка «Зберегти». Якщо `isLocked()`: степери й «Виміряти» вимкнені, під ними «Заблоковано, бо вже є зараховані заміри»; замість текстового поля — список учасників із діями «перейменувати», «вилучити» (підтвердження, якщо в учасника є замір) і «Додати учасників» (діалог із тим самим багаторядковим полем). Якщо не заблоковано — текстове поле, заповнене поточними назвами; збереження заміняє список. Унизу «Перерахувати за поточним калібруванням» (з'явиться в Task 11) і «Вилучити змагання» (підтвердження; повертає до списку).

- [ ] **Step 1: Написати тести, що падають**

```kotlin
@Test fun steppers_respectBounds() {
    assertEquals(3, EditForm(maxDurationSec = 3).minusMax().maxDurationSec); assertEquals(60, EditForm(maxDurationSec = 60).plusMax().maxDurationSec)
    assertEquals(1.0, EditForm(quietEndSec = 1.0).minusQuiet().quietEndSec, 0.0); assertEquals(5.0, EditForm(quietEndSec = 5.0).plusQuiet().quietEndSec, 0.0)
    assertEquals(2.5, EditForm().plusQuiet().quietEndSec, 0.0); assertEquals(11, EditForm().plusMax().maxDurationSec)
}
@Test fun canSave_needsName() { assertFalse(EditForm(name = "  ").canSave); assertTrue(EditForm(name = "Проба").canSave) }
@Test fun summaryLine_texts() {
    val ps = List(14) { Participant("$it", "п$it") }
    assertEquals("Ще не почато · 14 учасників", summaryLine(Competition("c", "n", 0, participants = ps)))
    assertEquals("1 із 14 замірів", summaryLine(Competition("c", "n", 0, participants = ps.mapIndexed { i, p -> if (i == 0) p.copy(measurementId = "m") else p })))
}
```

- [ ] **Step 2: Запустити й побачити падіння.** Run: `./gradlew :app:testDebugUnitTest --tests '*EditFormTest'` · Expected: FAIL.
- [ ] **Step 3: Реалізувати `EditForm`, `summaryLine`, обидва ViewModel і екрани; підключити маршрути в NavHost.** Для маршрутів `Routes.table`, `Routes.floor`, `Routes.measure`, `Routes.result` зареєструвати тимчасові порожні екрани з `TopBar` і назвою маршруту, щоб переходи не падали; їх заміняють Task 9 і Task 10.
- [ ] **Step 4: Запустити тести й зібрати.** Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug` · Expected: PASS, `BUILD SUCCESSFUL`.
- [ ] **Step 5: Перевірка на емуляторі.** Створити змагання, вставивши 9 назв одним текстом із порожніми рядками; закрити застосунок зі списку недавніх; відкрити знову. Expected: змагання в списку з «Ще не почато · 9 учасників»; у налаштуваннях назви на місці.
- [ ] **Step 6: Commit** — `feat: competition list, creation and settings`.

---

### Task 9: Вимір фону й таблиця

**Files:**
- Create: `ui/floor/FloorScreen.kt`, `ui/floor/FloorViewModel.kt`, `ui/table/TableScreen.kt`, `ui/table/TableViewModel.kt`
- Modify: `MainActivity.kt` (маршрути), `res/values/strings.xml`
- Test: `app/src/test/java/app/decibravo/ui/table/TableRowsTest.kt`

**Interfaces:**
- Consumes: `Store`, `FrameSource`, `floorOf`, `floorIsNoisy`, `rank`, `Entry`, `TableRow`, `tableText`, `fmt*`, `Routes`.
- Produces:

```kotlin
// TableViewModel.kt
data class TableUi(val title: String, val subtitle: String, val ranked: List<RankedRow>, val waiting: List<WaitingRow>)
data class RankedRow(val participantId: String, val place: Int, val name: String, val detail: String, val score: String, val overloaded: Boolean)
data class WaitingRow(val participantId: String, val name: String)
fun buildTable(c: Competition, measurements: Map<String, Measurement>): TableUi   // ключ — participantId
fun copyText(c: Competition, measurements: Map<String, Measurement>): String
```

- **Вимір фону** (`Routes.floor(id, thenParticipant)`), у `WithMicPermission`: підказка «Поставте телефон на місце й зробіть тишу»; `PrimaryButton("Виміряти фон")`; 3-секундний відлік (30 кадрів), екран не гасне; далі «Фон: −52 дБFS» і, якщо `floorIsNoisy`, «Були гучні сплески; переміряйте в тиші»; кнопки «Залишити» (зберігає `floorRms`, `floorMeasuredAt`; веде на `Routes.measure(id, thenParticipant)`, якщо учасника задано, інакше назад) і «Переміряти».
- **Таблиця** (`Routes.table(id)`, спека 9.4): `TopBar` з назвою, «назад» до списку, іконка налаштувань; `subtitle` = «5 із 9 замірів · найдовший замір 10 с»; рядки `ranked` (місце шрифтом цифр, перше місце жовте; назва в один рядок із трьома крапками; `detail` = «9,8 с · сила 32»; бал шрифтом цифр; знак перевантаження з `contentDescription` «Мікрофон перевантажився під час заміру»); підзаголовок «Ще не виступали» (лише якщо такі є); рядки `waiting` із жовтою іконкою старту; унизу `OutlineButton("Скопіювати таблицю")` → буфер обміну і коротке повідомлення «Скопійовано». Увесь вміст гортається.
- Дотик до `WaitingRow`: якщо `floorRms == null` → `Routes.floor(id, participantId)`, інакше → `Routes.measure(id, participantId)`. Дотик до `RankedRow` → `Routes.result(id, participantId)`.

- [ ] **Step 1: Написати тести, що падають**

```kotlin
// helper m(pid, score, dur, at, overloaded = false): Measurement з Result(score, dur, score / dur, LIMIT, overloaded)
@Test fun buildTable_ordersAndFormats() {
    val c = Competition("c", "Коляда 2026", 0, participants = listOf(Participant("a", "Ангели", "ma"), Participant("b", "Чорти"), Participant("t", "Три царі", "mt")))
    val ui = buildTable(c, mapOf("a" to m("a", 187.2, 7.1, 2, overloaded = true), "t" to m("t", 313.6, 9.8, 1)))
    assertEquals("2 із 3 замірів · найдовший замір 10 с", ui.subtitle)
    assertEquals(listOf("Три царі", "Ангели"), ui.ranked.map { it.name }); assertEquals(listOf(1, 2), ui.ranked.map { it.place })
    assertEquals("314", ui.ranked[0].score); assertEquals("9,8 с · сила 32", ui.ranked[0].detail); assertTrue(ui.ranked[1].overloaded)
    assertEquals(listOf("Чорти"), ui.waiting.map { it.name })
}
@Test fun copyText_listsRankedThenWaiting() {
    val c = Competition("c", "Коляда 2026", 0, participants = listOf(Participant("b", "Чорти"), Participant("t", "Три царі", "mt")))
    assertEquals("Коляда 2026\n1. Три царі — 314 (9,8 с, сила 32)\n— Чорти", copyText(c, mapOf("t" to m("t", 313.6, 9.8, 1))))
}
```

- [ ] **Step 2: Запустити й побачити падіння.** Run: `./gradlew :app:testDebugUnitTest --tests '*TableRowsTest'` · Expected: FAIL.
- [ ] **Step 3: Реалізувати `buildTable`, `copyText`, обидва ViewModel і екрани; підключити маршрути.**
- [ ] **Step 4: Запустити тести й зібрати.** Expected: PASS, `BUILD SUCCESSFUL`.
- [ ] **Step 5: Перевірка на емуляторі.** Створити змагання з учасником із назвою на 60 знаків і ще 40 учасниками; виміряти фон. Expected: довга назва обрізана трьома крапками в один рядок; список гортається до останнього учасника й кнопки «Скопіювати таблицю»; після виміру фону в налаштуваннях «Виміряно о ГГ:ХХ».
- [ ] **Step 6: Commit** — `feat: floor measurement and standings table`.

---

### Task 10: Замір і результат

**Files:**
- Create: `ui/measure/MeasureScreen.kt`, `ui/measure/MeasureViewModel.kt`, `ui/measure/ResultScreen.kt`
- Modify: `MainActivity.kt` (маршрути), `res/values/strings.xml`
- Test: `app/src/test/java/app/decibravo/ui/measure/MeasureTextsTest.kt`

**Interfaces:**
- Consumes: `MeasureSession`, `MeasureState`, `FrameSource`, `Store.accept`, `Store.deleteMeasurement`, `Store.newTempWav`, `measureParams`, `placeIfAccepted`, `fmt*`, `ScoreNumber`, `LevelBars`.
- Produces:

```kotlin
fun endReasonText(r: EndReason, quietEndSec: Double): String
// LIMIT → "Закінчено: досягнуто найдовшого заміру"; SILENCE → "Закінчено: 2 с тиші" (число з налаштувань, "1,5 с");
// STOP → "Закінчено: зупинено вручну"; NO_APPLAUSE → "Оплесків не було"
fun readyHint(maxDurationSec: Int, quietEndSec: Double): String
// "Натисніть «Старт», щойно учасник закінчив. Замір зупиниться сам: через 10 с або після 2 с тиші."
fun placeText(place: Int): String   // "зараз 3-тє місце"
```

**Екран заміру** (`Routes.measure`, спека 9.5), у `WithMicPermission`; спільне: назва учасника вгорі, `ScoreNumber` у центрі, підпис «бал».
- `Ready`: бал «0» кольором `#757575`; `readyHint`; `PrimaryButton("Старт", height = 96.dp)`; «назад» до таблиці.
- `Running(waiting = true)`: бал «0» білий; замість часу «Чекаю на оплески»; `OutlineButton("Стоп", height = 96.dp)`.
- `Running(waiting = false)`: `fmtScore(score)`; `LevelBars(bars)`; «6,4 с із 10»; «Стоп».
- `Done`: бал; два числа «тривалість» і «сила»; `endReasonText`; угорі праворуч жовтим `placeText(placeIfAccepted(…, інші зараховані без цього учасника))`; якщо `overloaded` — «Мікрофон перевантажився; результат може бути заниженим» кольором попередження; `PrimaryButton("Зарахувати", height = 72.dp)`, `OutlineButton("Переміряти")`.
- `Failed`: «Не вдалося записати звук. Можливо, мікрофон зайнятий іншим застосунком.» і кнопка «Назад до таблиці».

Поведінка ViewModel:
- «Старт»: `session.start()`, збирати `frameSource.chunks()` у `viewModelScope` і подавати в `session.onChunk`; виняток потоку → `session.fail`. Після `Done` або `Failed` збирання скасувати (мікрофон звільняється).
- «Зарахувати»: `store.accept(...)`, один раз (кнопка вимикається після першого дотику), потім `popBackStack` до таблиці.
- «Переміряти»: `session.cancel()`, новий сеанс у стані `Ready`.
- Системне «назад»: у `Running` — `cancel()` і до таблиці; у `Done` — діалог «Відкинути цей замір?»; у `Ready` — до таблиці.
- Екран не гасне, поки він відкритий (`FLAG_KEEP_SCREEN_ON`).
- Коли Activity переходить у `ON_STOP` під час `Running`: `session.cancel()`, збирання скасовано, стан `Ready`.

**Екран результату** (`Routes.result`) для учасника із зарахованим заміром: той самий вигляд, що `Done`, з поточним місцем («1-ше місце» без «зараз»); кнопки `OutlineButton("Переміряти")` → `Routes.measure` і «Вилучити замір» (підтвердження «Вилучити замір учасника …?») → `store.deleteMeasurement`, назад до таблиці.

- [ ] **Step 1: Написати тести, що падають**

```kotlin
@Test fun endReasonTexts() {
    assertEquals("Закінчено: 2 с тиші", endReasonText(EndReason.SILENCE, 2.0)); assertEquals("Закінчено: 1,5 с тиші", endReasonText(EndReason.SILENCE, 1.5))
    assertEquals("Закінчено: досягнуто найдовшого заміру", endReasonText(EndReason.LIMIT, 2.0))
    assertEquals("Закінчено: зупинено вручну", endReasonText(EndReason.STOP, 2.0)); assertEquals("Оплесків не було", endReasonText(EndReason.NO_APPLAUSE, 2.0))
}
@Test fun hintAndPlace() {
    assertEquals("Натисніть «Старт», щойно учасник закінчив. Замір зупиниться сам: через 10 с або після 2 с тиші.", readyHint(10, 2.0))
    assertEquals("зараз 3-тє місце", placeText(3))
}
```

- [ ] **Step 2: Запустити й побачити падіння.** Run: `./gradlew :app:testDebugUnitTest --tests '*MeasureTextsTest'` · Expected: FAIL.
- [ ] **Step 3: Реалізувати тексти, ViewModel і обидва екрани; підключити маршрути.**
- [ ] **Step 4: Запустити тести й зібрати.** Expected: PASS, `BUILD SUCCESSFUL`.
- [ ] **Step 5: Перевірка на емуляторі: повний шлях.** Натиснути «Старт», почекати 2 с мовчки, плескати 4 с, замовкнути. Expected: спершу «Чекаю на оплески», потім бал росте, замір сам закінчується через 2 с тиші з тривалістю близько 4 с; «Зарахувати» повертає до таблиці з учасником на першому місці; після перезапуску застосунку результат на місці; у `Android/data/app.decibravo/files/competitions/<id>/measurements/` є `.json` і `.wav`.
- [ ] **Step 6: Перевірка скасування.** Під час заміру натиснути «додому», повернутися. Expected: екран у стані «готовий», індикатор мікрофона в системі зник, у `files/tmp` немає файлів. Повторити із системним «назад»: повернення до таблиці, учасник без результату.
- [ ] **Step 7: Перевірка заміни й вилучення.** Переміряти учасника й відкинути новий замір: старий результат лишився. Переміряти й зарахувати: результат замінено, у теці один `.json` і один `.wav`. Вилучити єдиний замір: налаштування змагання знову відкриті.
- [ ] **Step 8: Commit** — `feat: measurement and result screens`.

---

### Task 11: Калібрування й перерахунок

**Files:**
- Modify: `ui/calibration/CalibrationScreen.kt`, `ui/calibration/CalibrationViewModel.kt`, `ui/edit/CompetitionEditScreen.kt`, `ui/edit/CompetitionEditViewModel.kt`, `res/values/strings.xml`
- Test: `app/src/test/java/app/decibravo/ui/calibration/CalibrationPreviewTest.kt`

**Interfaces:**
- Consumes: `Calibration`, `replay`, `refForTargetScore`, `rank`, `Store.loadCalibration/saveCalibration/recalculate/listCompetitions/loadMeasurement`.
- Produces:

```kotlin
data class PreviewRow(val participantId: String, val name: String, val place: Int, val duration: String, val strength: String, val score: String)
// перераховує зараховані заміри зразка з калібруванням draft; фон, найдовший замір і тишу бере зі зразка
fun preview(sample: Competition, measurements: Map<String, Measurement>, draft: Calibration): List<PreviewRow>
```

Екран калібрування (спека 9.6) під блоком перевірки мікрофона з Task 7:
- три регулятори: «Опорний рівень» (−60…0 дБFS, показ з одним знаком), «Вага гучності» (0,5…2,0, крок 0,1), «Поріг гучності» (3…20 дБ, крок 1);
- вибір змагання-зразка зі списку тих, що мають зараховані заміри; якщо таких немає — «Щоб калібрувати, зробіть пробні заміри в будь-якому змаганні»;
- список `preview(...)`, що оновлюється під час руху регуляторів; під ним примітка «Перегляд зі зміненим порогом наближений»;
- дотик до рядка → діалог «Скільки балів має дати цей замір?» з числовим полем → `refForTargetScore`; при `null` — «Не вдається: бал цього заміру нульовий або потрібний рівень поза межами»;
- `PrimaryButton("Зберегти")` → `store.saveCalibration(draft)`; «Скинути» → `Calibration()` у чернетку.

Налаштування змагання: дія «Перерахувати за поточним калібруванням» (видима, коли `isLocked()`), підтвердження «Бали всіх учасників буде перераховано з поточним опорним рівнем і вагою гучності. Тривалість не зміниться.» → `store.recalculate(id)`.

- [ ] **Step 1: Написати тести, що падають**

```kotlin
// два заміри в зразку: A = seq(50 to LOUD), B = seq(30 to DOUBLE) (рівень 2·ref), фон 0.001
@Test fun preview_withDefaults() {
    val rows = preview(sample, ms, Calibration())
    assertEquals(listOf("B", "A"), rows.map { it.name }); assertEquals(listOf("60", "50"), rows.map { it.score }); assertEquals("3,0", rows[0].duration)
}
@Test fun preview_gammaChangesOrder() {
    val rows = preview(sample, ms, Calibration(gamma = 0.5))     // B: 30·√2 ≈ 42, A: 50
    assertEquals(listOf("A", "B"), rows.map { it.name }); assertEquals(listOf(1, 2), rows.map { it.place })
}
@Test fun preview_ignoresFrozenCalibrationOfSample() {
    assertEquals(preview(sample, ms, Calibration(refDbfs = -20.0)), preview(sample.copy(frozenCalibration = Calibration(refDbfs = -40.0)), ms, Calibration(refDbfs = -20.0)))
}
```

- [ ] **Step 2: Запустити й побачити падіння.** Run: `./gradlew :app:testDebugUnitTest --tests '*CalibrationPreviewTest'` · Expected: FAIL.
- [ ] **Step 3: Реалізувати `preview`, доповнити ViewModel і екран, додати дію перерахунку.**
- [ ] **Step 4: Запустити всі тести й зібрати.** Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug` · Expected: PASS, `BUILD SUCCESSFUL`.
- [ ] **Step 5: Перевірка на емуляторі.** У пробному змаганні зробити тихий і гучний заміри; у калібруванні встановити «гучний замір = 100 балів», зберегти; створити нове змагання й поміряти. Expected: бали в перегляді міняються під час руху регуляторів; гучний зразок показує 100; у пробному змаганні бали лишились старими, доки не натиснуто «Перерахувати», після нього гучний замір має 100, тривалості ті самі.
- [ ] **Step 6: Commit** — `feat: calibration screen and recalculation`.

---

### Task 12: Вигляд за мокапом, інструкція, фінальна збірка

**Files:**
- Create: `README.md`, `app/src/main/res/drawable/ic_mark.xml`
- Modify: екрани з Task 7–11 за потреби

**Interfaces:**
- Consumes: усе попереднє.
- Produces: `app-debug.apk` для телефона судді; `README.md`.

- [ ] **Step 1: Звірити кожен екран зі специфікацією 9.1–9.6.** Чорне тло, один жовтий акцент, без тіней і карток; цифри шрифтом Barlow Condensed; кнопки «Старт» і «Стоп» 96 dp; три цифри балу займають майже всю ширину екрана; `ScoreNumber("1240")` і `ScoreNumber("12400")` не виходять за екран; усі іконки-кнопки мають `contentDescription`; зони дотику ≥ 48 dp. Виправити розбіжності.
- [ ] **Step 2: Лого на першому екрані.** Іконку застосунку вже взято з `logo/android/res/` у Task 1; перевірити, що вона показується на робочому столі емулятора. На екрані списку змагань ліворуч від заголовка «Змагання» показати знак «db» заввишки 32 dp: перенести `logo/decibravo-mark-color.svg` у векторний ресурс `res/drawable/ic_mark.xml` (ті самі контури й кольори).
- [ ] **Step 3: `README.md` українською:** що це; як зібрати (`./gradlew :app:assembleDebug`); як встановити APK на телефон (дозволити встановлення з невідомих джерел; або `adb install -r`); що збірка підписана налагоджувальним ключем цього Mac, тож оновлення треба збирати на ньому ж, інакше доведеться вилучити застосунок разом із даними; де лежать дані й записи (`Android/data/app.decibravo/files/`) і як забрати їх кабелем; коротка інструкція судді (телефон на штативі в одній точці, не поруч з окремими людьми; мікрофон прикрити від вітру; «Старт» після того, як учасник замовк; фон міряти на місці в тиші).
- [ ] **Step 4: Повний прогін.** Run: `./gradlew clean :app:testDebugUnitTest :app:assembleDebug` · Expected: `BUILD SUCCESSFUL`, усі тести PASS.
- [ ] **Step 5: Commit** — `chore: visual pass, app icon and README`.
- [ ] **Step 6: Передати власникові APK і перелік перевірок на телефоні судді** (спека, розділ 11): тихі й гучні оплески дають помітно різну силу; результати на місці після перезавантаження телефона; цифру видно з кількох кроків надворі; кнопки натискаються в рукавичках; проба з кількома людьми на відстані, як на матчі, з підбором калібрування й перевіркою на перевантаження.
