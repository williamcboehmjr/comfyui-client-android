# Changelog

All notable changes to **ComfyUI Client** are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.2.1] - 2026-06-05

### 🐛 Bug Fixes
- **Resilient Connection Checks**: Added a 3-attempt retry loop with `500ms` delay in `pollLocalServer` to prevent transient network issues or stale sockets in OkHttp's connection pool from prematurely firing the TRIGGERcmd wake sequence.
- Added `Connection: close` header to ping requests to bypass stale pooled connections.
- Set `retryOnConnectionFailure(true)` on the connection ping client.

---

## [1.2.0] - 2026-06-05

### 🏗️ Architecture Refactor
- **Modular Screen Architecture**: Completely decoupled the monolithic `Screens.kt` file into individual, focused screen files under `ui/screens/` — each screen (Prompt, Progress, Result, Settings, Gallery, Copilot, ServerWake, ZoomableImage) is now its own Kotlin file for far better maintainability and readability.
- **`GenerationRepository`**: Introduced a dedicated repository class that owns all generation logic and runs on a `SupervisorJob`-backed `repositoryScope` so generation state survives Activity pauses, rotations, and backgrounding.
- **`WorkflowTransformer`**: Extracted prompt injection into its own utility class. Recursively walks the ComfyUI API-format graph to inject the (optionally enhanced) text prompt into `CLIPTextEncode` or `PrimitiveNode` nodes, removing the dependency on brittle index assumptions.
- **`CopilotChatManager`**: Moved all Gemini vision-chat state and history into a dedicated manager class, decoupling it completely from the ViewModel.
- **`MediaSaver`**: Extracted local media-save logic (including `MediaStore` operations) into a dedicated utility class.

### ✨ New Features
- **Collapsible Enhanced-Prompt Card** (Progress Screen): When the Gemini prompt enhancer produces a long enhanced prompt, the card on the Progress Screen now starts collapsed to a tappable 3-line preview. Tapping it smoothly animates open to show the full text, getting it out of the way so you can watch generation progress without scrolling.
- **TRIGGERcmd Server Wake Integration**: Added an optional server-wake step that fires a TRIGGERcmd command (via the TRIGGERcmd cloud relay) when the local ComfyUI host is unreachable on connection. Polls until the server is back online before proceeding to generation.

### 🐛 Bug Fixes
- **Double Percentage in Notifications** (`35% 35%` → `35%`): Sanitized all progress-string paths. The notification builder, queue components, and status-text updates now strip any existing `" (N%)"` suffix before appending the current percentage, preventing the duplicate counter.
- **Background WebSocket Disconnection**: Generation state is now held in `GenerationRepository` (not the Activity/ViewModel) so that backgrounding the app no longer drops the active WebSocket listener or loses progress.
- **Prompt Enhancer Not Applying**: Fixed a race condition where the enhanced prompt was computed but the workflow injection happened before the coroutine resolved. `WorkflowTransformer` now receives the final prompt string synchronously at injection time.

### 🎨 UI/UX Polish
- Dynamic **AI Provider status card** on the Prompt Screen now correctly reflects whichever provider is active (Gemini, ChatGPT, Claude, Grok, or Local/Custom), including per-provider key validation states.
- Smooth `animateContentSize()` transition on the collapsible prompt card.

---

## [1.1.1] - 2026-06-04

### Added
- TRIGGERcmd integration: auto-wake trigger on local host connection failure.
- Background status polling with "Server Ready" push notification once ComfyUI is back online.

### Fixed
- Various stability improvements to the WebSocket reconnect path.

---

## [1.0.8] - 2026-06-01

### Added
- **Gallery Screen**: Browse and manage all past generation outputs in an OLED-optimized grid view.
- **Full-screen zoom viewer** (`ZoomableImage`): Pinch-to-zoom and pan support on any generated image.
- Gallery detail bottom sheet: copy prompt, share, save to device, or reload into the prompter.

### Fixed
- Image download and MediaStore write path on Android 10+.

---

## [1.0.7] - 2026-05-30

### Added
- **Floating Queue FAB**: Live generation count badge with bottom sheet showing queue depth.
- **Queue controls**: "Clear Queue" (removes pending jobs, keeps active) and "Stop All" (interrupts current generation and empties queue).
- Animated queue item list inside the bottom sheet.

---

## [1.0.6] - 2026-05-28

### Added
- **Cloud Host Integrations**: Orchestration adapters for RunPod Serverless, Fal.ai, and ComfyDeploy with smart local fallback logic.
- **EncryptedSharedPreferences**: All API keys (Gemini, ChatGPT, Claude, Grok, cloud provider keys) stored with AES-256 GCM encryption inside the private app sandbox.
- Host-type dropdown in Settings with animated, provider-specific configuration panels.

### Fixed
- Positional input alignment for custom nodes — correctly maps connection slots vs. widget inputs using `/object_info`.

---

## [1.0.5] - 2026-05-28

### Added
- Dangling node recovery: automatically cleans up links pointing to UI-only nodes and maps default values from `/object_info`.
- Request-rate-limiting cooldown (5-second generator lock) to prevent duplicate queue fires.

---

## [1.0.4] - 2026-05-28

### Added
- Multimodal Vision Refiner chat (Gemini Flash): attach gallery images, dictate prompts, and review suggestions.
- Transient in-RAM conversation history — no data leaves the device.

---

## [1.0.3] - 2026-05-28

### Added
- Multi-LLM Prompt Enhancer: Gemini, ChatGPT, Claude, Grok, and local/custom OpenAI-compatible endpoints.
- "Fetch Models" button to dynamically query `/models` on local LLM servers.
- Per-provider masked API key inputs in Settings.

---

## [1.0.2] - 2026-05-28

### Added
- Universal Graph-to-API engine: converts UI-format ComfyUI JSON (nodes + links) into server-ready API-format payloads.
- Native aspect-ratio-aware resolution injection into latent/image generator nodes.
- Seed control card on Prompt Screen (random / fixed seed toggle).

---

## [1.0.1] - 2026-05-28

### Fixed
- WebSocket event subscription stabilization.
- Initial progress-bar rendering on the Progress Screen.

---

## [1.0.0] - 2026-05-28

### Initial Release
- Core ComfyUI REST + WebSocket client.
- Real-time generation progress with percentage and status text.
- Basic prompt screen with aspect ratio, megapixel, and model selectors.
- Secure local connection via IP address.
