# StreamMind

**StreamMind is an AI-powered video player for Android.** It combines a Media3 video player with transcript-grounded retrieval, Gemini-powered video understanding, personalized discovery, learning tools, and accessibility support.

The project demonstrates how a mobile product can combine **Kotlin, Jetpack Compose, modular architecture, Room persistence, retrieval-augmented generation (RAG), multimodal AI, deterministic fake/live implementations, and quota-aware network behavior**.

> **Current status:** Portfolio project. Use `fakeDebug` for deterministic demos and interviews. Use `liveDebug` to exercise the Gemini integration with network access, a valid API key, and available model quota.

## Product capabilities

### Video library and playback

- Browse five Blender Open Movies: Big Buck Bunny, Elephants Dream, Sintel, Tears of Steel, and Spring.
- Play remote video streams with Media3 ExoPlayer.
- Use a fixed 16:9 player surface with chapter markers aligned to the player timeline.
- Seek from chapter markers, chapter rows, transcript-linked moments, and AI search results.
- Control playback speed from 0.5x through 2.0x.
- Save playback position per video in Room.
- Display a multi-item **Continue Watching** tray with progress indicators and remove actions.
- Display a rotating spotlight carousel and searchable, filterable library views.
- Navigate back to the library from the player with the device back action.

### AI video understanding

- Generate concise **AI Chapters** from timestamped transcript segments.
- Generate **Key Moments and Highlights** with titles, descriptions, categories, and seekable timestamps.
- Ask questions about a video using transcript-grounded **RAG**. The app retrieves relevant transcript chunks and streams an answer with timestamp context.
- Search transcript content semantically using Gemini embeddings, cosine similarity, lexical overlap, and relevance thresholds.
- Generate an overall **AI Quiz and Learning Mode** with multiple-choice questions, explanations, score tracking, restart behavior, and source-moment seeking.
- Generate an optional **Scene Quiz** for the selected transcript scene. Scene questions are explicitly requested and cached using a video-plus-timestamp key.
- Generate multimodal **Audio Scene Descriptions** by extracting a frame at the current playback position, sending it to Gemini vision, displaying the response, and reading it with Android Text-to-Speech.

### Personalization and accessibility

- Show a first-run **Personalize My Library** dialog for Animation, Sci-Fi, and Fantasy preferences.
- Persist selected preferences and rank **Recommended for You** using preferences and watch history.
- Keep recommendations hidden until the user selects a topic or accumulates qualifying watch history.
- Provide offline placeholders wherever a network-dependent feature cannot work without connectivity.
- Keep existing Room-backed AI and playback results usable when connectivity is temporarily lost.

## Technical architecture

The project uses a multi-module structure so that UI, feature behavior, local persistence, remote services, and fake/live AI implementations remain independently testable.

| Module | Responsibility |
|---|---|
| `app` | Application shell, navigation, library UI, personalization, Hilt wiring, and flavor selection |
| `feature:player` | Compose player, ExoPlayer state, controls, chapters, transcript, search, quizzes, and AI panels |
| `feature:transcript` | Asset-backed transcript loading and Room synchronization |
| `feature:ai-chapters` | Chapter, Key Moment, and quiz contracts, fake/live repositories, validation, caching, and request coordination |
| `feature:ai-qa` | Embeddings, cosine retrieval, semantic search, and streamed transcript-grounded answers |
| `feature:accessibility` | Frame extraction, Gemini scene descriptions, and Text-to-Speech playback |
| `data:data-local` | Room database, entities, DAOs, migrations, and flavor-isolated storage |
| `data:data-remote` | Retrofit Gemini APIs, embeddings, streaming API, API-key configuration, and remote DTOs |
| `core:core-common` | Shared coroutine dispatcher abstractions |

The primary data path is:

```text
Video catalog
    -> PlayerScreen / ExoPlayer
    -> Asset transcript repository
    -> Room transcript_segments
    -> Feature use case
        -> Fake repository in fakeDebug
        -> Gemini repository in liveDebug
    -> Room cache
    -> Compose panel and timestamp actions
```

## AI implementation details

### Fake and live build variants

The app uses a `network` product-flavor dimension:

- **`fakeDebug`** uses deterministic local repositories. It is suitable for UI development, interviews, demos, and offline use. It does not call Gemini or consume API quota.
- **`liveDebug`** uses Gemini for Chapters, Key Moments, quizzes, embeddings, transcript-grounded answers, and multimodal scene descriptions. It requires internet access, `GEMINI_API_KEY`, and available model quota.

Both variants use the same repository interfaces and Compose UI. Hilt selects the fake or live implementation, allowing the UI and feature contracts to be exercised without requiring live AI during normal development.

### Transcript-grounded retrieval

Curated timestamped transcript segments are stored in Room. In the live variant, missing transcript embeddings are generated with `gemini-embedding-001` using retrieval-document semantics. A user query is embedded using retrieval-query semantics and scored against stored vectors with cosine similarity.

Search ranking also uses lexical overlap. When a query contains direct transcript matches, zero-overlap results and weak vector matches are filtered out. This prevents broad semantic similarity from returning unrelated opening or setup scenes. When there is no direct lexical overlap, vector similarity remains available for synonym-based searches.

Ask-the-Video uses the selected transcript context as the source for a streamed Gemini answer. The UI displays timestamp context and can seek the player to the evidence-bearing segment.

### Chapters and Key Moments

Chapters use timestamped transcript content and return short JSON chapter labels. The player loads cached Chapters automatically. If no valid cache exists, the live build generates Chapters through Gemini; the fake build uses deterministic results and the curated chapter-title catalog.

Key Moments use the same transcript grounding pattern and return a title, description, kind, and timestamp. Cached moments are available when present. New live Key Moments generation is user-triggered through **Generate highlights**, preventing a video-open event from automatically consuming another Gemini request.

### Quota-aware generation coordination

Chapters, Key Moments, and Quiz use cases are protected by the shared `AiGenerationGate`:

- Cache checks and generation run inside a keyed single-flight lock.
- Concurrent callers for the same feature and video cannot issue duplicate Gemini requests.
- A global lock serializes live generation requests from these features.
- HTTP 500, 502, 503, and 504 errors use bounded retry behavior.
- HTTP 429 is not retried.
- HTTP 429 activates a short process-wide cooldown for further generation attempts.
- Previous cached rows are preserved until replacement succeeds, so quota errors do not erase usable results.

Overall Quiz and Scene Quiz are explicitly user-triggered. Scene Quiz uses a synthetic cache key containing the video ID and transcript start time, which prevents repeated generation for the same scene.

The gate is an in-memory process-level safeguard. A production multi-device service would additionally need server-side quotas, durable request status, authentication, and backend observability.

### Multimodal accessibility

Audio Scene Description extracts a frame at the current playback position with `MediaMetadataRetriever`, sends the frame with nearby transcript context to Gemini in `liveDebug`, displays the generated description, and reads it with Android Text-to-Speech. Replaying an existing description does not require another model call.

The fake build uses deterministic descriptions and does not analyze or upload a frame.

## Data and persistence

Room stores:

- Timestamped transcript segments and optional embedding vectors
- Generated Chapters
- Generated Key Moments
- Generated quiz questions
- Per-video playback progress

Transcript assets are shipped under `app/src/main/assets/transcripts/`. Asset synchronization replaces stale transcript rows when the source timing data changes. Fake and live variants use separate database names, preventing deterministic fake data from leaking into the live build.

Fresh-install handling clears restored playback progress and shows personalization onboarding once. Skipping onboarding is stored as a completed decision, so the dialog does not reappear every time the user returns from the player.

## Project setup

### Requirements

- Android Studio with a current Android SDK and Android Gradle Plugin support
- JDK 17
- Android API level 34 for compilation
- Minimum Android API level 26
- Internet access for remote video playback and `liveDebug`
- A Gemini API key only for `liveDebug`

### Configure the project

1. Clone or download the repository.
2. Open the project in Android Studio.
3. Copy `local.properties.example` to `local.properties`.
4. Set `sdk.dir` to the local Android SDK path.
5. Add `GEMINI_API_KEY` to `local.properties` only if using `liveDebug`.
6. Sync Gradle and choose a build variant from the Build Variants panel.

Example `local.properties`:

```properties
sdk.dir=/path/to/your/Android/Sdk
GEMINI_API_KEY=your-key-for-liveDebug-only
```

Do not commit `local.properties` or an API key. Direct mobile-to-Gemini access is intended only for a controlled portfolio demonstration. A production application should use an authenticated backend proxy with server-side key storage, user quotas, and abuse controls.

### Build and test

```bash
# Deterministic offline-capable demonstration build
./gradlew :app:assembleFakeDebug

# Live Gemini-backed build
./gradlew :app:assembleLiveDebug

# Focused unit tests currently in the repository
./gradlew :feature:ai-qa:test :feature:accessibility:test
```

The current tests cover vector mathematics and nearest-transcript-segment selection. Compose UI tests, Room integration tests, repository contract tests, and flavor-level end-to-end tests are recommended next.

## References

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Android Media3](https://developer.android.com/media/media3)
- [Room persistence library](https://developer.android.com/training/data-storage/room)
- [Gemini API](https://ai.google.dev/gemini-api/docs)
- [Hilt dependency injection](https://developer.android.com/training/dependency-injection/hilt-android)
- [Android Text-to-Speech](https://developer.android.com/guide/topics/text-to-speech)
