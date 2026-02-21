# AGENT.md — Smart Notification Manager (Android)

## Project Overview

An Android app that intelligently filters notifications during focus mode. It uses a **rule-based engine first** (Phase 1), then adds an on-device ML pipeline (Phase 2) for ambiguous cases, with deduplication and a grouped digest when focus mode ends.

**Target device constraint:** 4GB RAM.

**Current focus:** Phase 1 (non-ML) only. ML components are documented for later.

---

## Tech Stack (Verified)

| Layer                    | Technology                          | Phase | Project status        |
| ------------------------ | ----------------------------------- | ----- | ---------------------- |
| Language                 | Kotlin                              | 1     | ✅ in project          |
| UI                       | Jetpack Compose + Material 3        | 1     | ✅ in project          |
| DI                       | Hilt                                | 1     | ⬜ to add              |
| Notification interception| NotificationListenerService         | 1     | ⬜ to add              |
| Focus mode               | Foreground Service + Quick Settings Tile | 1 | ⬜ to add              |
| Background reliability   | WorkManager watchdog                | 1     | ⬜ to add              |
| Local DB                 | Room                                | 1     | ⬜ to add              |
| Preferences              | DataStore                           | 1     | ⬜ to add              |
| Contact resolution       | ContactsContract API                | 1     | ⬜ to add              |
| Summarization / Digest   | Template-based (pure Kotlin)        | 1     | ⬜ to add              |
| Text embeddings          | TFLite + USE Lite (~20MB)           | 2     | Later                  |
| Classification head      | TFLite custom model (~5MB)          | 2     | Later                  |
| Deduplication            | Cosine similarity on embeddings     | 2     | Later                  |

Phase 1 = non-ML MVP. Phase 2 = ML classifier + dedup (build after P0/P1 demo works).

---

## Architecture

### Phase 1 pipeline (current)

```
Notification arrives (NotificationListenerService)
       │
       ▼
┌──────────────────┐
│  Rule Engine     │  Contacts, keywords, app allowlist, categories
└───────┬──────────┘
        │
        ▼
  Allow / Suppress → Store in Room → (on focus end) Template digest
```

### Full pipeline (Phase 2, later)

- **Dedup/Cluster** (before rules): embedding + time window; reuse embeddings for classifier.
- **Rule Engine** (Stage 1): same as Phase 1; confident → allow/suppress.
- **ML Classifier** (Stage 2): only for ambiguous; USE Lite + TFLite head.
- **Hot path:** &lt;100ms, &lt;30MB RAM. **Cold path:** digest = pure Kotlin, no inference.

---

## Key Data Models

Phase 1 can use a subset; Phase 2 extends with `embedding` and `priorityScore`.

```kotlin
data class NotificationRecord(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val isContact: Boolean,
    val isGroupChat: Boolean,
    val hasMention: Boolean,
    val notificationCategory: String?,
    val eventClusterId: String,
    // Phase 2:
    val embedding: FloatArray? = null,
    val priorityScore: Float? = null
)

data class EventCluster(
    val clusterId: String,
    val notifications: List<NotificationRecord>,
    val priorityScore: Float,
    val primaryApp: String,
    val apps: Set<String>
)
```

---

## Build Priority

### P0 — Core MVP (Phase 1, build first)

- [ ] `SmartNotificationListener` — intercept and log all notifications
- [ ] `FocusModeService` + `FocusModeTile` — toggle focus mode
- [ ] Room DB setup — store notification history
- [ ] Basic Compose UI — focus mode toggle + notification list
- [ ] `RuleEngine` — contact matching, keyword detection, app allowlist
- [ ] Allow/suppress logic wired into listener
- [ ] `OnboardingScreen` — guide user to enable notification access

### P1 — Impressive Demo (Phase 2, build second)

- [ ] `EmbeddingService` — load USE Lite, compute embeddings
- [ ] `MLClassifier` — TFLite classification head
- [ ] `ClassifierPipeline` — rules → ML fallback
- [ ] `DedupEngine` + `SlidingWindowBuffer` — cross-app dedup
- [ ] `DigestGenerator` — template-based grouped summary
- [ ] `DigestScreen` — UI to review suppressed notifications

---

## Rule Engine Heuristics (Phase 1)

Evaluated before any ML. Confident result → no ML.

| Rule                                       | Result          |
| ------------------------------------------ | --------------- |
| Sender in phone contacts                   | → High priority |
| App in user's "always allow" list          | → Pass through  |
| Category is ALARM or CALL                  | → Always allow  |
| Group chat with no @mention of user        | → Suppress      |
| Known bot / automated notification pattern | → Suppress      |
| Keywords: urgent, emergency, deadline      | → High priority |
| Promotional / transactional email pattern  | → Suppress      |

---

## Digest Generation (Phase 1 & 2)

When focus mode ends:

1. Group by `eventClusterId` (Phase 2: deduped events; Phase 1: by app/time or simple grouping).
2. Sort by priority (Phase 2: `priorityScore`; Phase 1: rule outcome or time).
3. Format with sender + app + preview; collapse low-priority into "and X other notifications."
4. No model inference; pure Kotlin.

---

## Android Permissions Required

```xml
<!-- Notification access — user must enable in Settings -->
<service
    android:name=".service.SmartNotificationListener"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

---

## Coding Conventions

- Kotlin conventions and Android best practices
- Coroutines and Flow for async; Room suspend functions
- ViewModels expose StateFlow to Compose; sealed classes for UI state (Loading, Success, Error)
- Services stay thin — delegate to injected repository/engine
- Unit tests for RuleEngine (and later DedupEngine); no Android deps in engine tests

---

## Key Gotchas

1. **NotificationListenerService** must be enabled by user in Settings → Notifications → Notification access. Onboarding must guide them.
2. Service can be killed; use a WorkManager watchdog to detect and prompt re-enable.
3. Some apps (e.g. WhatsApp) reuse one notification key; use `sbn.key` to tell updates from new notifications.
4. Android 13+: request `POST_NOTIFICATIONS` at runtime for your own digest notification.
5. *(Phase 2)* TFLite interpreter: init once, reuse; run on `Dispatchers.Default`. USE Lite has max sequence length — truncate long text.

---

## Phase 2 — ML (reference only for now)

### Embedding + classification

- **Embedding:** USE Lite, ~20MB, 512-dim, ~30–50ms.
- **Classifier:** 512 + structured features → 64 → 3 classes (high/medium/low), ~5MB, &lt;10ms.
- **Dedup:** cosine similarity on embeddings, threshold 0.85, 10-min window, last 30 notifications.

### Synthetic training data (when building Phase 2)

- Target ~2000–3000 labeled examples.
- Categories: DMs from contacts, @mentions, urgent keywords, group chat general, bot/promo, calendar, transactional, social.
- Format: CSV with `text`, `app_type`, `is_contact`, `is_group`, `has_mention`, `category`, `label` (`high`|`medium`|`low`).

### Memory budget (Phase 2)

| Component           | RAM   |
| ------------------- | ----- |
| USE Lite            | ~20MB |
| Classification head | ~5MB  |
| Sliding window      | ~1MB  |
| **Total ML**        | **~26MB** |
