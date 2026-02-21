# AGENT.md — Smart Notification Manager (Android)

## Project Overview

An Android app that intelligently filters notifications during focus mode. It uses a hybrid rule-based + on-device ML pipeline to classify incoming notifications as important or not, deduplicates cross-app notifications for the same event, and presents a grouped digest when focus mode ends.

**Target device constraint:** 4GB RAM.

---

## Architecture

### Core Pipeline

```
Notification arrives (via NotificationListenerService)
       │
       ▼
┌──────────────────┐
│  Dedup / Cluster  │  ← Embedding cosine similarity + time window
│                    │    Reuses embeddings from classifier
└───────┬──────────┘
        │
  Duplicate? ──Yes──▶ Inherit priority from existing event cluster
        │
       No (new event)
        │
        ▼
┌──────────────────┐
│  Rule Engine      │  ← Fast heuristics (handles ~60-70% of notifications)
│  (Stage 1)        │    Contacts, keywords, app allowlist, categories
└───────┬──────────┘
        │
   Confident? ──Yes──▶ Allow / Suppress
        │
       No (ambiguous)
        ▼
┌──────────────────┐
│  ML Classifier    │  ← USE Lite embedding + structured features
│  (Stage 2)        │    TFLite classification head
└───────┬──────────┘
        │
        ▼
  Priority Score → Allow / Suppress / Silent delivery
```

### Hot Path vs Cold Path

- **Hot path** (every notification during focus mode): Rule engine → ML classifier → dedup. Must be <100ms, <30MB RAM.
- **Cold path** (on focus mode end): Template-based digest generation. Pure Kotlin, no model inference.

---

## Tech Stack

| Layer                    | Technology                                      |
| ------------------------ | ----------------------------------------------- |
| Language                 | Kotlin                                          |
| UI                       | Jetpack Compose + Material 3                    |
| DI                       | Hilt                                            |
| Notification interception| NotificationListenerService                     |
| Focus mode               | Foreground Service + Quick Settings Tile         |
| Background reliability   | WorkManager watchdog                            |
| Local DB                 | Room                                            |
| Preferences              | DataStore                                       |
| Text embeddings          | TFLite + Universal Sentence Encoder Lite (~20MB)|
| Classification head      | TFLite custom model (~5MB)                      |
| Contact resolution       | ContactsContract API                            |
| Deduplication            | Cosine similarity on shared embeddings          |
| Summarization / Digest   | Template-based grouping engine (pure Kotlin)    |
---

## Key Data Models

```kotlin
data class NotificationRecord(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val embedding: FloatArray,
    val timestamp: Long,
    val isContact: Boolean,
    val isGroupChat: Boolean,
    val hasMention: Boolean,
    val notificationCategory: String?,
    val priorityScore: Float?,
    val eventClusterId: String
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

## ML Model Details

### Embedding Model
- **Model:** Universal Sentence Encoder Lite (TFLite)
- **Size:** ~20MB
- **Output:** 512-dimensional float vector
- **Latency:** ~30-50ms on mid-range device
- **Usage:** Shared across classification and deduplication

### Classification Head
- **Architecture:** Small feedforward network (512 + N structured features → 64 → 3 classes)
- **Input features:**
  - 512-dim text embedding from USE Lite
  - `isContact` (bool)
  - `isGroupChat` (bool)
  - `hasMention` (bool)
  - `appType` (categorical: messaging, email, social, productivity, other)
  - `notificationCategory` (from Android system)
  - `hourOfDay` (int, normalized)
- **Output:** 3-class softmax (high / medium / low priority)
- **Size:** ~5MB
- **Latency:** <10ms
- **Training:** Trained on synthetic labeled data, exported to TFLite

### Deduplication
- **Method:** Cosine similarity on USE Lite embeddings
- **Threshold:** 0.85 (tunable)
- **Time window:** 10 minutes
- **Buffer size:** Last 30 notifications

### Memory Budget

| Component              | RAM     |
| ---------------------- | ------- |
| USE Lite               | ~20MB   |
| Classification head    | ~5MB    |
| Sliding window buffer  | ~1MB    |
| Digest engine          | ~0      |
| **Total ML footprint** | **~26MB** |

---

## Rule Engine Heuristics (Stage 1)

These rules are evaluated before ML inference. If a rule produces a confident result, ML is skipped.

| Rule                                       | Result         |
| ------------------------------------------ | -------------- |
| Sender in phone contacts                   | → High priority |
| App in user's "always allow" list          | → Pass through  |
| Category is ALARM or CALL                  | → Always allow  |
| Group chat with no @mention of user        | → Suppress      |
| Known bot / automated notification pattern | → Suppress      |
| Keywords: urgent, emergency, deadline      | → High priority |
| Promotional / transactional email pattern  | → Suppress      |

---

## Digest Generation (Template-Based)

When focus mode ends, generate a digest from suppressed notifications:

1. Group by `eventClusterId` (deduped events)
2. Sort by `priorityScore` descending
3. Split into tiers: "might be important" vs "low priority"
4. Format important ones with sender + app + preview text
5. Collapse low-priority into count: "and {X} other notifications"
6. Display cross-app duplicates as: "{event} (LinkedIn, Gmail)"

No model inference. Pure Kotlin string formatting.

---

## Build Priority

### P0 — Core MVP (build first)
- [ ] `SmartNotificationListener` — intercept and log all notifications
- [ ] `FocusModeService` + `FocusModeTile` — toggle focus mode
- [ ] Room DB setup — store notification history
- [ ] Basic Compose UI — focus mode toggle + notification list
- [ ] `RuleEngine` — contact matching, keyword detection, app allowlist
- [ ] Allow/suppress logic wired into listener
- [ ] `OnboardingScreen` — guide user to enable notification access

### P1 — Impressive Demo (build second)
- [ ] `EmbeddingService` — load USE Lite, compute embeddings
- [ ] `MLClassifier` — TFLite classification head for ambiguous notifications
- [ ] `ClassifierPipeline` — orchestrate rules → ML fallback
- [ ] `DedupEngine` + `SlidingWindowBuffer` — cross-app dedup using shared embeddings
- [ ] `DigestGenerator` — template-based grouped summary on focus mode end
- [ ] `DigestScreen` — UI to review suppressed notifications

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

<!-- Contact resolution -->
<uses-permission android:name="android.permission.READ_CONTACTS" />

<!-- Quick Settings tile -->
<!-- No extra permission needed, just declare the TileService -->
```

---

## Synthetic Training Data

Training data should be generated before the hackathon. Target: ~2000-3000 labeled examples.

**Categories to cover:**
- Direct messages from known contacts (high priority)
- @mentions in group chats (high priority)
- Urgent keywords in any context (high priority)
- Group chat general messages (low priority)
- Bot / automated notifications (low priority)
- Promotional emails (low priority)
- Calendar reminders (medium priority)
- Transactional alerts — delivery, payment (medium priority)
- Social media activity — likes, follows (low priority)

**Format:** CSV with columns: `text`, `app_type`, `is_contact`, `is_group`, `has_mention`, `category`, `label`

**Label values:** `high`, `medium`, `low`

---

## Coding Conventions

- Follow Kotlin coding conventions and Android best practices
- Use coroutines and Flow for async operations
- All TFLite inference runs on `Dispatchers.Default`, never on main thread
- Room operations use suspend functions
- ViewModels expose StateFlow to Compose UI
- Use sealed classes for UI state (Loading, Success, Error)
- Keep services lightweight — delegate logic to injected repository/engine classes
- Write unit tests for RuleEngine and DedupEngine (deterministic, no Android dependencies)

---

## Key Gotchas

1. **NotificationListenerService** requires the user to manually enable it in Settings > Notifications > Notification access. The onboarding flow must guide them there.
2. The service can be killed by the system. The WorkManager watchdog should check and prompt the user to re-enable if needed.
3. Some apps (e.g., WhatsApp) use a single notification key and update it. Check `sbn.key` to distinguish updates from new notifications.
4. On Android 13+, `POST_NOTIFICATIONS` requires runtime permission for your own digest notification.
5. TFLite interpreter should be initialized once and reused, not created per inference call.
6. USE Lite input has a max sequence length — truncate long notification text before encoding.
