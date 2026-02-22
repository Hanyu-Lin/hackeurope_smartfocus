# AGENT.md — Smart Notification Manager (Android)

## Project Overview

An Android app that acts as an intelligent notification firewall. It intercepts all system notifications via `NotificationListenerService`, applies user-defined **Focus Modes** (each a named set of filter rules), sends ambiguous notifications to an AI classifier via a structured plaintext prompt built from the raw notification object, deduplicates cross-app notifications for the same event, and persists every intercepted notification for full-text search and audit.

**Core features:**

- **Focus Modes** — iOS-inspired named profiles (e.g., "Work", "Sleep", "Gym"), each with independent filter rules
- **Per-rule actions** — each rule can trigger an alarm sound, haptic buzz, or silent delivery
- **AI-based urgency detection** — structured plaintext prompt sent to AI classifier for notifications that pass rule filters; sensitivity toggle controls the threshold
- **Whitelist support** — app-level or contact-level pass-through rules
- **Cross-app bundling** — model-driven grouping using a dynamic `BundleMap` (k × 1024 latent vectors stored in Room); solo notifications pass through the original `StatusBarNotification` untouched; once a second related notification arrives the first is cancelled and both are re-posted as a single app-branded grouped notification updated in real time
- **Notification Search & Audit** — all intercepted notifications persisted and fully searchable by keyword, app, timestamp, or priority

**Supported apps (initial scope):** Phone calls, SMS, LinkedIn, Gmail, Instagram, Discord

**Target device constraint:** 4 GB RAM

---

## Architecture

### Core Pipeline

```
Notification arrives (NotificationListenerService)
        │
        ▼
┌─────────────────────┐
│  NotificationParser  │  ← Extract structured fields + capture all
│                      │    original sbn metadata for pass-through /
│                      │    cancellation. Produces ParsedNotification.
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐
│  Focus Mode Router   │  ← Any Focus Mode active?
└────────┬────────────┘
         │ No active mode
         │   → sbn passes through untouched (do NOT cancel)
         │   → Persist NotificationRecord (outcome = ALLOWED)
         │
         │ Active mode
         ▼
┌─────────────────────┐
│  Rule Engine         │  ← Evaluate ordered rules on ParsedNotification.
│  (Stage 1)           │    Each matched rule carries an Action config.
└────────┬────────────┘
         │
    Rule match?
         ├──ALLOW──▶ Execute RuleAction (buzz / alarm / silent).
         │           sbn passes through untouched.
         │           Persist (outcome = ALLOWED).
         │
         ├──SUPPRESS▶ cancelNotification(sbn.key).
         │            Persist (outcome = SUPPRESSED).
         │
        No match → send rawPrompt to AI model
         │
         ▼
┌──────────────────────────────────────────┐
│  AI Model                                 │
│  Input:  rawPrompt (plaintext string)     │
│  Output: priority  — Float 0–10           │
│          group     — k-dim sparse vector  │
│          latent    — FloatArray(1024)     │
└────────┬─────────────────────────────────┘
         │
         ▼
┌─────────────────────┐
│  BundleEngine        │  ← Resolve group vector against BundleMap.
│                      │    Assign to existing bundle or create new.
│                      │    Update BundleMap centroid (EMA).
└────────┬────────────┘
         │
   Bundle assigned?
         │
         ├──Existing bundle (size was 1 → now 2)
         │    → Cancel the previously passed-through sbn.
         │    → Cancel this sbn.
         │    → Post new grouped MessagingStyle notification.
         │    → Persist (outcome = BUNDLED).
         │
         ├──Existing bundle (size already ≥ 2)
         │    → Cancel this sbn.
         │    → Update existing grouped notification in-place.
         │    → Persist (outcome = BUNDLED).
         │
        New bundle (group all zeros)
         │    → Store latent as new BundleMap entry.
         │    → Check priority score:
         │
         ▼
   priority ≥ threshold?
         │
         ├──Yes → sbn passes through untouched.
         │         Persist (outcome = ALLOWED).
         │
        No  → cancelNotification(sbn.key).
               Persist (outcome = SUPPRESSED).
```

### Hot Path vs Cold Path

- **Hot path** (every notification during focus mode): Parse → Rule Engine → AI Model → Bundle Engine → deliver or cancel. The AI model call is the primary latency driver; rule-matched notifications never reach the model.
- **Cold path** (on focus mode end): Template-based digest of SUPPRESSED + BUNDLED notifications. Pure Kotlin string formatting, no inference.
- **Audit path** (always): Every intercepted notification is written to Room DB with its final outcome regardless of active mode or rule result.

---

## Tech Stack

| Layer                     | Technology                                                           |
| ------------------------- | -------------------------------------------------------------------- |
| Language                  | Kotlin                                                               |
| UI                        | Jetpack Compose + Material 3                                         |
| DI                        | Hilt                                                                 |
| Notification interception | NotificationListenerService                                          |
| Focus mode                | Foreground Service + Quick Settings Tile                             |
| Background reliability    | WorkManager watchdog                                                 |
| Local DB                  | Room                                                                 |
| Preferences               | DataStore                                                            |
| Notification parsing      | Custom `NotificationParser` (pure Kotlin)                            |
| AI model                  | External model — outputs `priority`, `group`, `latent`               |
| Bundle assignment         | `BundleEngine` — sparse `group` vector → `BundleMap` lookup          |
| Bundle centroid storage   | Room `BundleMapEntry` table (k × 1024 floats)                        |
| Bundled notification      | `NotificationManager` re-post with `MessagingStyle`, source app icon |
| Contact resolution        | ContactsContract API                                                 |
| Summarization / Digest    | Template-based grouping engine (pure Kotlin)                         |
| Search                    | Room FTS4 full-text search                                           |
| Audio alerts              | AudioManager + SoundPool                                             |
| Haptic alerts             | VibrationEffect API                                                  |

---

## Key Data Models

```kotlin
// A named focus profile — equivalent to iOS Focus Mode
data class FocusMode(
    val id: String,
    val name: String,                      // e.g. "Work", "Sleep", "Gym"
    val isActive: Boolean,
    val rules: List<FilterRule>,
    val priorityThreshold: Float           // 0–10, model priority score must meet or exceed this to pass
)

// A single filter rule within a FocusMode
data class FilterRule(
    val id: String,
    val focusModeId: String,
    val type: RuleType,                    // APP, KEYWORD, CONTACT, CATEGORY
    val value: String,                     // packageName / keyword / contactId / category string
    val effect: RuleEffect,                // ALLOW, SUPPRESS
    val action: RuleAction                 // NONE, BUZZ, ALARM, SILENT
)

enum class RuleType { APP, KEYWORD, CONTACT, CATEGORY }
enum class RuleEffect { ALLOW, SUPPRESS }
enum class RuleAction { NONE, BUZZ, ALARM, SILENT }

// The three outputs the AI model returns for each notification
data class ModelOutput(
    val priority: Float,                   // 0–10 urgency score
    val group: FloatArray,                 // k-dim sparse vector; 1.0 at matched bundle index, 0 elsewhere
    val latent: FloatArray                 // 1024-dim latent vector representing this notification's content
)

// One entry in the global BundleMap — persisted in Room
// k grows dynamically; each row is one bundle slot
@Entity(tableName = "bundle_map")
data class BundleMapEntry(
    @PrimaryKey val bundleIndex: Int,      // slot index i (0-based, monotonically assigned)
    val bundleId: String,                  // UUID for this bundle, FK referenced by NotificationRecord
    val centroid: FloatArray,              // 1024-dim EMA centroid, updated on each assignment
    val packageName: String,               // primary source app (first notification's package)
    val createdAt: Long,
    val updatedAt: Long
)

// A live bundle — tracks which notifications have been grouped and the posted Android notification ID
@Entity(tableName = "notification_bundle")
data class NotificationBundle(
    @PrimaryKey val bundleId: String,
    val bundleIndex: Int,                  // FK to BundleMapEntry.bundleIndex
    val packageName: String,
    val appLabel: String,
    val notificationIds: List<String>,     // ordered list of NotificationRecord.id values
    val postedNotificationId: Int,         // Android notification ID of our re-posted grouped notification
    val createdAt: Long,
    val updatedAt: Long
)

// Structured output of NotificationParser
data class ParsedNotification(
    val packageName: String,
    val appLabel: String,
    val category: String,
    val title: String,
    val text: String,
    val subText: String?,
    val isGroupConversation: Boolean,
    val conversationTitle: String?,
    val sender: String?,
    val timestamp: Long,
    val rawPrompt: String,

    // Original sbn metadata — needed to cancel and re-post faithfully
    val originalKey: String,               // sbn.key — use for cancelNotification(key)
    val originalTag: String?,              // sbn.tag
    val originalId: Int,                   // sbn.id
    val originalSmallIconResId: Int?,
    val originalLargeIconBitmap: Bitmap?,
    val originalColor: Int?,
    val originalContentIntent: PendingIntent?,
    val originalSortKey: String?
)

// A single intercepted notification (always persisted)
data class NotificationRecord(
    val id: String,                        // our internal UUID
    val packageName: String,
    val appLabel: String,
    val category: String,
    val title: String,
    val text: String,
    val rawPrompt: String,
    val timestamp: Long,
    val isContact: Boolean,
    val outcome: NotificationOutcome,
    val appliedRuleId: String?,
    val priorityScore: Float?,             // model priority output, null if resolved by rule engine
    val bundleId: String?                  // non-null when outcome = BUNDLED; FK to NotificationBundle
)

enum class NotificationOutcome { ALLOWED, SUPPRESSED, BUNDLED }
```

---

## Notification Parsing

### Overview

`NotificationParser` extracts a consistent `ParsedNotification` from the raw `android.app.Notification` object delivered by `NotificationListenerService`. Because each app structures its notification extras differently, the parser dispatches to a per-app strategy based on `packageName`. The final output is a `rawPrompt` — a structured **plaintext string** that serves as the sole input to the AI classifier.

### Android Notification Object — Key Fields

`NotificationListenerService` delivers a `StatusBarNotification` (`sbn`) with:

```kotlin
sbn.packageName                           // package of the posting app
sbn.notification.extras                   // Bundle — all notification content lives here
sbn.notification.category                 // Notification.CATEGORY_* constant (may be null)
sbn.notification.`when`                   // posting timestamp (millis)

// Standard extras (android.app.Notification):
extras.getString(Notification.EXTRA_TITLE)            // primary title line
extras.getString(Notification.EXTRA_TEXT)             // single-line body text
extras.getString(Notification.EXTRA_BIG_TEXT)         // expanded body (BigTextStyle)
extras.getString(Notification.EXTRA_SUB_TEXT)         // secondary descriptor line
extras.getString(Notification.EXTRA_SUMMARY_TEXT)     // summary (InboxStyle)
extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) // InboxStyle lines array
extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
extras.getString(Notification.EXTRA_CONVERSATION_TITLE)   // MessagingStyle group name
extras.getParcelableArray(Notification.EXTRA_MESSAGES)    // MessagingStyle message array
```

Each entry in `EXTRA_MESSAGES` is a `Bundle` with:

- `"sender_person"` → `Person` (has `.name`, `.uri`)
- `"text"` → `CharSequence`
- `"time"` → `Long`

Use `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)` as a higher-level alternative that handles API version differences automatically.

---

### Normalized Category Values

`ParsedNotification.category` is normalized to one of these strings regardless of source app:

| Value           | Meaning                                  |
| --------------- | ---------------------------------------- |
| `call`          | Incoming or missed phone call            |
| `message`       | Direct message or SMS                    |
| `email`         | Email new message or thread update       |
| `social`        | Social activity — like, follow, reaction |
| `mention`       | @mention or tag in a post or comment     |
| `group_message` | Group chat message                       |
| `system`        | System-level or service notification     |
| `other`         | Anything not matched above               |

---

### Per-App Extraction Strategies

#### Phone Calls — `com.android.phone` / `com.google.android.dialer`

Phone call notifications carry `Notification.CATEGORY_CALL` or `CATEGORY_MISSED_CALL`.

```kotlin
category = "call"
title     = extras.getString(EXTRA_TITLE) ?: ""        // e.g. "Incoming call" / "Missed call"
text      = extras.getString(EXTRA_TEXT) ?: ""         // caller display name or raw number
subText   = null
```

Resolve `text` against `ContactsContract` when it is a raw phone number so the AI classifier sees a name rather than digits.

---

#### SMS / MMS — `com.google.android.apps.messaging`, `com.android.mms`

SMS apps use `MessagingStyle` on API 24+ and fall back to `BigTextStyle` or plain style.

**MessagingStyle path (preferred):**

```kotlin
val style  = NotificationCompat.MessagingStyle
                 .extractMessagingStyleFromNotification(sbn.notification)
val lastMsg = style?.messages?.lastOrNull()

sender            = lastMsg?.person?.name ?: extras.getString(EXTRA_TITLE) ?: ""
text              = lastMsg?.text?.toString() ?: ""
isGroupConversation = style?.isGroupConversation ?: false
conversationTitle = style?.conversationTitle?.toString()  // group name if isGroup
category          = if (isGroupConversation) "group_message" else "message"
title             = conversationTitle ?: sender
```

**Fallback (BigText / plain):**

```kotlin
title    = extras.getString(EXTRA_TITLE) ?: ""
text     = extras.getString(EXTRA_BIG_TEXT) ?: extras.getString(EXTRA_TEXT) ?: ""
category = "message"
```

---

#### Gmail — `com.google.android.gm`

Gmail uses `BigTextStyle` for individual emails and `InboxStyle` for bundled inbox summaries.

**Single email (BigTextStyle):**

```kotlin
title    = extras.getString(EXTRA_TITLE) ?: ""           // sender name
text     = extras.getString(EXTRA_BIG_TEXT)
              ?: extras.getString(EXTRA_TEXT) ?: ""       // subject line or preview
subText  = extras.getString(EXTRA_SUB_TEXT)              // account address if present
category = "email"
```

**Bundled inbox (InboxStyle):**

```kotlin
val lines = extras.getCharSequenceArray(EXTRA_TEXT_LINES)
title     = extras.getString(EXTRA_TITLE) ?: ""           // e.g. "3 new messages"
text      = lines?.joinToString(" | ") ?: extras.getString(EXTRA_SUMMARY_TEXT) ?: ""
category  = "email"
```

Note: Gmail often formats `EXTRA_TEXT` as `"Sender Name – Subject preview"` (en dash, `\u2013`). Split on `–` if you want to separate sender from subject in the prompt.

---

#### LinkedIn — `com.linkedin.android`

LinkedIn uses `BigTextStyle`. It does not use `MessagingStyle`.

```kotlin
title    = extras.getString(EXTRA_TITLE) ?: ""   // e.g. "John Doe liked your post"
text     = extras.getString(EXTRA_BIG_TEXT)
              ?: extras.getString(EXTRA_TEXT) ?: ""
subText  = extras.getString(EXTRA_SUB_TEXT)
category = inferLinkedInCategory(title)
```

**Category inference:**

```kotlin
fun inferLinkedInCategory(title: String): String = when {
    title.contains("messaged you", ignoreCase = true)       -> "message"
    title.contains("sent you a message", ignoreCase = true) -> "message"
    title.contains("mentioned you", ignoreCase = true)      -> "mention"
    title.contains("commented", ignoreCase = true)          -> "mention"
    title.contains("liked", ignoreCase = true)              -> "social"
    title.contains("reacted", ignoreCase = true)            -> "social"
    title.contains("connection request", ignoreCase = true) -> "social"
    title.contains("accepted your", ignoreCase = true)      -> "social"
    else                                                    -> "other"
}
```

---

#### Instagram — `com.instagram.android`

Instagram uses `MessagingStyle` for DMs on recent builds and `BigTextStyle` for activity notifications.

**MessagingStyle (DMs on newer builds):**

```kotlin
val style  = NotificationCompat.MessagingStyle
                 .extractMessagingStyleFromNotification(sbn.notification)
if (style != null) {
    val lastMsg         = style.messages.lastOrNull()
    sender              = lastMsg?.person?.name ?: extras.getString(EXTRA_TITLE) ?: ""
    text                = lastMsg?.text?.toString() ?: ""
    isGroupConversation = style.isGroupConversation
    title               = style.conversationTitle?.toString() ?: sender
    category            = if (isGroupConversation) "group_message" else "message"
}
```

**BigTextStyle fallback (activity notifications):**

```kotlin
title    = extras.getString(EXTRA_TITLE) ?: ""   // username
text     = extras.getString(EXTRA_TEXT) ?: ""    // e.g. "liked your photo."
category = inferInstagramCategory(text)
```

**Category inference:**

```kotlin
fun inferInstagramCategory(text: String): String = when {
    text.contains("liked", ignoreCase = true)              -> "social"
    text.contains("started following", ignoreCase = true)  -> "social"
    text.contains("mentioned you", ignoreCase = true)      -> "mention"
    text.contains("commented", ignoreCase = true)          -> "mention"
    text.contains("sent you a message", ignoreCase = true) -> "message"
    text.contains("replied to your story", ignoreCase = true) -> "message"
    else                                                   -> "social"
}
```

---

#### Discord — `com.discord`

Discord uses `MessagingStyle` for both DMs and server channel messages.

```kotlin
val style  = NotificationCompat.MessagingStyle
                 .extractMessagingStyleFromNotification(sbn.notification)
if (style != null) {
    val lastMsg         = style.messages.lastOrNull()
    sender              = lastMsg?.person?.name ?: extras.getString(EXTRA_TITLE) ?: ""
    text                = lastMsg?.text?.toString() ?: ""
    isGroupConversation = style.isGroupConversation
    conversationTitle   = style.conversationTitle?.toString()  // e.g. "#general" or DM name
    title               = conversationTitle ?: sender
    category            = if (isGroupConversation) "group_message" else "message"
} else {
    // Fallback for grouped summary notifications
    title    = extras.getString(EXTRA_TITLE) ?: ""
    text     = extras.getString(EXTRA_TEXT) ?: ""
    category = "group_message"
}
```

Note: `isGroupConversation` is `true` for both server channels and group DMs. Server channels can be identified by a `#` prefix in `conversationTitle` — treat this as a heuristic, not a guarantee.

---

### Plaintext Prompt Format (`rawPrompt`)

After extraction, `NotificationParser` serializes the `ParsedNotification` into a single plaintext string. This is the **only input** to the AI classifier — no JSON, no structured objects.

```
app: <appLabel>
category: <category>
title: <title>
text: <text>
```

**Construction rules:**

- All values are single-line: strip `\n`, `\r`, collapse whitespace
- Truncate `text` to 200 characters before building the prompt
- If a field is null or blank, omit that line entirely (no `text: null`)
- Plain UTF-8, no brackets, no quotes

**Example outputs per app:**

```
app: Phone
category: call
title: Incoming call
text: Mom
```

```
app: Messages
category: message
title: Erik
text: Hey are you coming tonight?
```

```
app: Gmail
category: email
title: GitHub
text: [GitHub] Your pull request was merged into main
```

```
app: LinkedIn
category: mention
title: Sara Chen mentioned you in a comment
text: Great point! Really insightful perspective on the market shift.
```

```
app: Instagram
category: social
title: _johndoe
text: liked your photo.
```

```
app: Discord
category: group_message
title: #general
text: Linus: anyone up for a review session tonight?
```

---

### `NotificationParser` — Implementation Sketch

```kotlin
@Singleton
class NotificationParser @Inject constructor(
    private val context: Context
) {
    fun parse(sbn: StatusBarNotification): ParsedNotification {
        val pkg      = sbn.packageName
        val extras   = sbn.notification.extras
        val appLabel = resolveAppLabel(pkg)

        val parsed = when (pkg) {
            "com.android.phone",
            "com.google.android.dialer"         -> parseCall(extras, appLabel, pkg, sbn.notification)
            "com.google.android.apps.messaging",
            "com.android.mms"                   -> parseSms(extras, appLabel, pkg, sbn.notification)
            "com.google.android.gm"             -> parseGmail(extras, appLabel, pkg, sbn.notification)
            "com.linkedin.android"              -> parseLinkedIn(extras, appLabel, pkg, sbn.notification)
            "com.instagram.android"             -> parseInstagram(extras, appLabel, pkg, sbn.notification)
            "com.discord"                       -> parseDiscord(extras, appLabel, pkg, sbn.notification)
            else                                -> parseGeneric(extras, appLabel, pkg, sbn.notification)
        }

        return parsed.copy(rawPrompt = buildPrompt(parsed))
    }

    private fun buildPrompt(p: ParsedNotification): String = buildString {
        appendLine("app: ${p.appLabel}")
        appendLine("category: ${p.category}")
        if (p.title.isNotBlank()) appendLine("title: ${p.title.singleLine()}")
        if (p.text.isNotBlank())  appendLine("text: ${p.text.singleLine().take(200)}")
    }.trimEnd()

    private fun String.singleLine() =
        replace('\n', ' ').replace('\r', ' ').trim()

    private fun resolveAppLabel(pkg: String): String =
        runCatching {
            context.packageManager
                .getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0))
                .toString()
        }.getOrDefault(pkg)
}
```

---

## Focus Mode & Rule Engine

### Focus Mode Lifecycle

Focus modes are user-created profiles stored in Room. Only one mode can be active at a time. Activation/deactivation is available via:

- In-app toggle on the Home screen
- Quick Settings Tile
- Manual toggle (future: Shortcuts / Automations)

### Rule Types

| Rule Type  | Match Criteria                                                                |
| ---------- | ----------------------------------------------------------------------------- |
| `APP`      | Exact package name match (e.g., `com.discord`)                                |
| `KEYWORD`  | Case-insensitive substring match against `ParsedNotification.text` or `title` |
| `CONTACT`  | Resolved sender (`title` or `sender`) matches a device Contact                |
| `CATEGORY` | Matches `ParsedNotification.category` (e.g., `call`, `email`)                 |

### Rule Evaluation Order

1. `CATEGORY` rules — `call` and `system` evaluated first (always-allow candidates)
2. `CONTACT` rules — whitelist pass-through for known senders
3. `APP` rules — package-level allowlist / blocklist
4. `KEYWORD` rules — urgency keywords or spam patterns in title/text
5. No match → escalate to AI Classifier

### Rule Actions

Each rule carries a `RuleAction` that fires on an ALLOW outcome:

| Action   | Behavior                                                       |
| -------- | -------------------------------------------------------------- |
| `NONE`   | Deliver silently per system default                            |
| `BUZZ`   | Trigger `VibrationEffect.createOneShot` pattern                |
| `ALARM`  | Play audio via `SoundPool` using user-selected or default tone |
| `SILENT` | Force silent delivery regardless of system volume              |

Suppressed notifications always use `SILENT` regardless of action config.

### Built-in Heuristics (always active within Stage 1)

| Condition                                 | Default Effect |
| ----------------------------------------- | -------------- |
| Category is `call`                        | ALLOW          |
| Sender resolves to a device Contact       | ALLOW          |
| App in user's explicit allowlist          | ALLOW          |
| Category is `group_message`, no @mention  | SUPPRESS       |
| Category is `social` (likes, follows)     | SUPPRESS       |
| Keywords: urgent, emergency, deadline     | ALLOW          |
| Promotional / transactional email pattern | SUPPRESS       |

---

## AI Classifier Integration

The AI classifier receives `rawPrompt` and returns a priority label. The integration is intentionally thin:

- **Input:** `rawPrompt` plaintext string (4 lines max, ~300 chars max)
- **Output:** One of `high`, `medium`, `low` — or a normalized float score in `[0.0, 1.0]`
- **Threshold:** Controlled per Focus Mode by `sensitivityThreshold`
  - Higher sensitivity = lower pass threshold = more notifications get through
- **Interface:**

```kotlin
interface NotificationClassifier {
    suspend fun classify(prompt: String): ClassificationResult
}

data class ClassificationResult(
    val label: String,   // "high" | "medium" | "low"
    val score: Float     // 0.0–1.0
)
```

The concrete implementation (local rules, on-device LLM, remote API) is swappable without touching the pipeline.

---

## Bundle Engine

### Overview

`BundleEngine` is responsible for taking the AI model's three outputs (`priority`, `group`, `latent`) and deciding what to do with the incoming notification's `StatusBarNotification`. It owns the global `BundleMap` (persisted in Room) and the live grouped notification posted to the Android notification shade.

The model is owned by your teammates. From the app's perspective it is a black box callable as:

```kotlin
interface NotificationModel {
    suspend fun infer(prompt: String): ModelOutput
}

data class ModelOutput(
    val priority: Float,        // 0–10 urgency score
    val group: FloatArray,      // k-dim sparse vector; 1.0 at matched bundle index i, 0 elsewhere
    val latent: FloatArray      // 1024-dim content vector for this notification
)
```

---

### BundleMap

A global, dynamically-growing table of latent centroids. Each row represents one active bundle slot.

```
BundleMap:  index i  →  centroid[i]  (FloatArray of size 1024)
```

- Persisted in Room as `BundleMapEntry` (one row per slot)
- Global — shared across all Focus Modes
- `k` grows by 1 each time a new bundle is created; there is no fixed upper bound
- Centroid is updated on every new assignment using an exponential moving average:

```
bundleMap[i] = 0.6 * bundleMap[i] + 0.4 * latent
```

---

### Bundle Assignment Algorithm

```kotlin
suspend fun assign(parsed: ParsedNotification, output: ModelOutput): BundleDecision {
    val firstMatchIndex = output.group.indexOfFirst { it == 1.0f }

    return if (firstMatchIndex == -1) {
        // All zeros → new bundle
        val newIndex  = bundleMapDao.nextIndex()          // monotonically incrementing
        val newBundleId = UUID.randomUUID().toString()
        bundleMapDao.insert(BundleMapEntry(
            bundleIndex = newIndex,
            bundleId    = newBundleId,
            centroid    = output.latent,
            packageName = parsed.packageName,
            createdAt   = parsed.timestamp,
            updatedAt   = parsed.timestamp
        ))
        BundleDecision.NewBundle(newBundleId, output.priority)

    } else {
        // Match at firstMatchIndex → join existing bundle
        val entry = bundleMapDao.getByIndex(firstMatchIndex)

        // Update centroid with EMA
        val updated = FloatArray(1024) { i ->
            0.6f * entry.centroid[i] + 0.4f * output.latent[i]
        }
        bundleMapDao.updateCentroid(firstMatchIndex, updated, parsed.timestamp)

        BundleDecision.JoinBundle(entry.bundleId, output.priority)
    }
}

sealed class BundleDecision {
    data class NewBundle(val bundleId: String, val priority: Float) : BundleDecision()
    data class JoinBundle(val bundleId: String, val priority: Float) : BundleDecision()
}
```

---

### Naive Fallback (before model is integrated)

Until the real model is wired in, `BundleEngine` uses a stub that groups solely by `packageName`:

```kotlin
class NaiveNotificationModel : NotificationModel {
    // In-memory map: packageName → assigned bundle index
    private val packageBundleIndex = mutableMapOf<String, Int>()
    private var nextIndex = 0

    override suspend fun infer(prompt: String, packageName: String): ModelOutput {
        val index = packageBundleIndex.getOrPut(packageName) { nextIndex++ }
        val group = FloatArray(nextIndex) { i -> if (i == index) 1.0f else 0.0f }
        return ModelOutput(
            priority = 5.0f,                      // neutral — defer to threshold
            group    = group,
            latent   = FloatArray(1024) { 0f }    // zero vector; centroid update is a no-op
        )
    }
}
```

Swap `NaiveNotificationModel` for the real model implementation via Hilt binding without touching any other class.

---

### Real-Time Notification Update Flow

The key invariant: **a solo notification always shows as the original unmodified `sbn`**. Only once a second notification joins the same bundle do we cancel and replace.

```
Bundle size after assignment:
  = 1 (first notification, new bundle created)
      → sbn passes through untouched.
        Store sbn.key in NotificationBundle so we can cancel it later.

  = 2 (second notification joins, bundle was solo)
      → cancelNotification(firstNotif.originalKey)   ← cancel the one already in the shade
      → cancelNotification(thisNotif.originalKey)    ← cancel this incoming one too
      → postBundledNotification(bundle)              ← post grouped notification for the first time

  ≥ 3 (third+ notification joins an existing bundle)
      → cancelNotification(thisNotif.originalKey)    ← cancel just this new one
      → updateBundledNotification(bundle)            ← update existing grouped notification in-place
```

`NotificationBundle.notificationIds` tracks all member IDs in insertion order. The first entry's `originalKey` is stored separately as `NotificationBundle.soloSbnKey` so it can be cancelled when the bundle grows from 1 to 2.

```kotlin
@Entity(tableName = "notification_bundle")
data class NotificationBundle(
    @PrimaryKey val bundleId: String,
    val bundleIndex: Int,
    val packageName: String,
    val appLabel: String,
    val notificationIds: List<String>,     // all member NotificationRecord.id values, in order
    val soloSbnKey: String,               // originalKey of the first (solo) notification
    val postedNotificationId: Int,         // stable Android notif ID we post/update for this bundle
    val createdAt: Long,
    val updatedAt: Long
)
```

---

### Bundled Notification Delivery

The re-posted grouped notification uses `MessagingStyle` and is branded to look like the source app:

```kotlin
fun buildBundleNotification(
    bundle: NotificationBundle,
    members: List<ParsedNotification>
): Notification {
    val style = NotificationCompat.MessagingStyle(
        Person.Builder().setName(members.first().appLabel).build()
    ).apply {
        conversationTitle = "${members.first().appLabel} · ${members.size} notifications"
        isGroupConversation = true
        members.forEach { p ->
            addMessage(
                NotificationCompat.MessagingStyle.Message(
                    p.text, p.timestamp,
                    Person.Builder().setName(p.title).build()
                )
            )
        }
    }

    return NotificationCompat.Builder(context, "bundled_${bundle.packageName}")
        .setSmallIcon(members.first().originalSmallIconResId ?: R.drawable.ic_bundle_fallback)
        .setColor(members.first().originalColor ?: Color.TRANSPARENT)
        .setStyle(style)
        .setContentIntent(members.first().originalContentIntent)  // tap opens source app
        .setGroup("bundle_${bundle.bundleId}")
        .setGroupSummary(true)
        .setOnlyAlertOnce(true)    // silent updates after the first post
        .setAutoCancel(false)
        .build()
}
```

Call `NotificationManagerCompat.notify(bundle.postedNotificationId, notification)` for both the initial post (size = 2) and all subsequent updates (size ≥ 3). Android updates the existing row in the shade silently.

---

## Notification Search & Audit

All intercepted notifications are persisted to Room with FTS4 indexing, regardless of allow/suppress/bundle outcome.

Full-text search runs on `title`, `text`, and `rawPrompt` via Room FTS4. Attribute filters: app, outcome (ALLOWED / SUPPRESSED / BUNDLED), date range, priority score, `bundleId` (to view all notifications in a given bundle). Sort by timestamp (default) or priorityScore.

Default retention: 30 days (configurable in Settings). CSV export of audit log available in Settings.

---

## Digest Generation (Template-Based)

When focus mode ends, generate a digest from all non-allowed notifications:

1. Group by outcome: BUNDLED notifications group by `bundleId`; SUPPRESSED notifications are listed individually
2. Sort by `priorityScore` descending within each group
3. Split into tiers: "might be important" (high/medium score) vs "low priority"
4. For bundles: show `"{appLabel} · {count} notifications"` with the most recent message as preview
5. For cross-app bundles: show `"LinkedIn · Gmail · 3 notifications"`
6. Collapse low-priority into count: "and {X} other notifications"

No model inference. Pure Kotlin string formatting.

---

## Screen Inventory

| Screen                     | Purpose                                                            |
| -------------------------- | ------------------------------------------------------------------ |
| `HomeScreen`               | Active focus mode toggle, current mode name, shortcut to digest    |
| `FocusModeListScreen`      | List all focus modes, create / duplicate / delete                  |
| `FocusModeDetailScreen`    | Edit mode name, sensitivity slider, manage rules list              |
| `RuleEditorScreen`         | Create / edit a single rule: type, value, effect, action config    |
| `DigestScreen`             | Grouped digest of suppressed notifications from last focus session |
| `SearchScreen`             | Full-text + filtered search across full notification audit log     |
| `NotificationDetailScreen` | Full detail view of a single persisted notification + raw prompt   |
| `OnboardingScreen`         | Guide user to enable Notification Access in Settings               |
| `SettingsScreen`           | Retention period, default action sounds, export audit log          |

---

## Build Priority

### P0 — Core MVP (build first)

- [ ] `SmartNotificationListener` — intercept notifications, call parser, persist to Room; cancel `sbn` only when bundling or suppressing
- [ ] `NotificationParser` — per-app extraction for all 6 supported apps + generic fallback; capture all `original*` metadata fields including `originalKey`
- [ ] `FocusModeService` + `FocusModeTile` — toggle active focus mode
- [ ] Room DB setup — `FocusMode`, `FilterRule`, `NotificationRecord`, `BundleMapEntry`, `NotificationBundle`, FTS4 virtual table
- [ ] `RuleEngine` — evaluate ordered rule set against `ParsedNotification` fields
- [ ] `ActionDispatcher` — execute `RuleAction` (BUZZ / ALARM / SILENT)
- [ ] Basic Compose UI — `HomeScreen`, `FocusModeListScreen`, `FocusModeDetailScreen`, `RuleEditorScreen`
- [ ] `OnboardingScreen` — guide user to enable notification access

### P1 — Impressive Demo (build second)

- [ ] `NaiveNotificationModel` — stub implementation grouping by `packageName`; swap-ready via Hilt binding
- [ ] `BundleEngine` — `assign()` algorithm, EMA centroid update, `BundleDecision` sealed class
- [ ] `BundleNotificationPoster` — cancel solo sbn on bundle size 1→2, post/update app-branded `MessagingStyle` grouped notification
- [ ] `ClassifierPipeline` — orchestrate Rule Engine → Model infer → Bundle Engine → pass-through or cancel
- [ ] Wire real `NotificationModel` when teammates deliver it — no other code changes needed
- [ ] `DigestGenerator` — template-based summary of SUPPRESSED + BUNDLED outcomes on focus mode end
- [ ] `DigestScreen` + `SearchScreen` + `NotificationDetailScreen`
- [ ] `SettingsScreen` — retention config, priority threshold slider, sound picker, CSV export

---

## Android Permissions Required

```xml
<!-- Notification access — user must manually enable in Settings -->
<service
    android:name=".service.SmartNotificationListener"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>

<!-- Foreground service for focus mode -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Contact resolution for CONTACT-type rules -->
<uses-permission android:name="android.permission.READ_CONTACTS" />

<!-- Haptic feedback -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Quick Settings tile — no extra permission needed, just declare the TileService -->
```

---

## Coding Conventions

- Follow Kotlin coding conventions and Android best practices
- Use coroutines and Flow for all async operations
- AI model `infer()` calls run on `Dispatchers.IO` — never on main thread
- Room operations use `suspend` functions
- ViewModels expose `StateFlow` to Compose UI
- Use sealed classes for UI state: `Loading`, `Success`, `Error`
- Services are lightweight shells — delegate all logic to injected repository / engine classes
- `NotificationParser`, `RuleEngine`, `BundleEngine`, and `ActionDispatcher` must be unit-testable with no Android framework dependencies (accept plain data classes, not live Android objects)
- `NotificationModel` is an interface — `NaiveNotificationModel` and the real model are both swappable Hilt bindings; `BundleEngine` never references a concrete implementation
- `BundleNotificationPoster` is abstracted behind an interface so it can be faked in tests without posting real notifications
- `FocusMode` and `FilterRule` are pure data — no Android imports in model layer

---

## Key Gotchas

1. **Notification access requires manual opt-in.** `NotificationListenerService` is enabled by the user in Settings > Apps > Special app access > Notification access. Deep-link there via `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
2. **Service can be killed by the system.** A WorkManager watchdog should periodically verify the service is running and prompt the user to re-enable if not.
3. **Some apps update a single notification key rather than posting new ones.** Check `sbn.key` — updates should patch the existing `NotificationRecord` rather than insert a new one, and must not trigger the solo→bundle transition.
4. **Android 13+ requires runtime `POST_NOTIFICATIONS` permission** to post or update bundled notifications.
5. **`EXTRA_MESSAGES` parcelable API changed in API 33.** Prefer `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification()` which handles versioning internally.
6. **MessagingStyle is not guaranteed.** Always implement a BigText/plain fallback for every app — apps can downgrade their notification style between versions.
7. **Gmail sender vs. subject split.** `EXTRA_TEXT` often uses the format `"Sender \u2013 Subject"` (en dash). Split on `\u2013` to separate them; fall back to the raw string if the separator is absent.
8. **Discord server vs. DM detection.** `isGroupConversation = true` for both server channels and group DMs. A `#` prefix in `conversationTitle` suggests a server channel — treat as a heuristic only.
9. **`VibrationEffect` API differs across Android versions.** Use `VibrationEffect.createOneShot` for API 26+ with a graceful fallback for older targets.
10. **Room FTS4 must be kept in sync manually.** Use a DAO wrapper that writes to both `NotificationRecord` and its FTS shadow table in a single transaction.
11. **Priority threshold slider maps to `FocusMode.priorityThreshold` (0–10).** This controls the AI model's `priority` output, not the Rule Engine. Make the distinction clear in the UI.
12. **Active Focus Mode must survive process death.** Persist the active mode ID to DataStore, not just in-memory ViewModel state.
13. **`cancelNotification()` requires `sbn.key`.** You cannot reconstruct the key from package name + ID alone. Store `originalKey` on `ParsedNotification` and `NotificationBundle.soloSbnKey` at parse time.
14. **`BundleMapEntry.centroid` is a `FloatArray` of size 1024.** Room cannot store `FloatArray` natively — use a `@TypeConverter` that serializes it to a `ByteArray` or a comma-delimited `String`. `ByteArray` is preferred for compactness (4 KB per row).
15. **The solo→bundle transition must cancel the first notification before posting the grouped one.** If you post the grouped notification first, there will be a brief moment where both appear in the shade. Always cancel both original `sbn`s before calling `notify()`.
16. **`group` vector dimension grows with k.** When the model outputs a `group` vector, its size must match the current `k` (number of `BundleMapEntry` rows). Validate `group.size == bundleMapDao.count()` before scanning; mismatch means the model and DB are out of sync.
