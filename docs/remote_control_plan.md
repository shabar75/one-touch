# OneTouch Remote Control Feature – Design and Implementation Plan

## Overview
Enable an authorized remote controller to operate the device while its screen is shared via existing MediaProjection. Keep the current streaming architecture and add a separate, secure control channel for low‑latency input commands.

## Architecture
```mermaid
flowchart LR
  RC[Remote Controller (Browser/Web app)]
  subgraph Phone
    APP[OneTouch App]
    ACC[AccessibilityService]
    WRTC[WebRTC DataChannel]
    WSS[WSS Fallback]
    AUDIT[Encrypted Audit Log]
    NOTIF[Foreground Notification]
  end
  SIG[Signalling Server (screenstream.io)]

  RC <---> SIG
  APP <---> SIG
  RC <-- DTLS/SRTP --> WRTC
  RC <-- TLS/WSS --> WSS
  WRTC --> APP
  WSS --> APP
  APP <--> ACC
  APP --> AUDIT
  APP --> NOTIF
```

### Sequence of operations (Global/WebRTC)
1. User starts sharing; app obtains MediaProjection (existing) and shows persistent session notification.
2. App registers a control channel offering over WebRTC (DataChannel "control").
3. Remote controller authenticates using stream ID + user PIN and presents ephemeral token.
4. WebRTC handshake via signalling; DTLS establishes keys. App derives/receives a per‑session symmetric key for optional E2E encryption of control payloads.
5. App prompts per‑session acceptance on device; controller may be remembered for X hours (encrypted, expiring).
6. Remote sends input messages over DataChannel; app ACKs and executes via AccessibilityService.
7. Sensitive actions (CALL/SMS/payments/export) require a second explicit device‑side confirmation dialog.
8. Actions are recorded in a local encrypted audit log.
9. Session ends on user stop, timeout, or inactivity.

### Sequence of operations (Local/RTSP or Local/MJPEG)
- Keep media path unchanged.
- Add an embedded secure WebSocket (wss) endpoint for control (separate from MJPEG/RTSP). Same auth and message schema. TLS cert: app‑generated self‑signed for LAN or user‑provided; pin during session.

## Exact Android APIs used
- AccessibilityService for input injection:
  - Gestures: `dispatchGesture(gesture, callback, handler)` with `GestureDescription`
  - Text entry and node actions: `AccessibilityNodeInfo.performAction(ACTION_SET_TEXT / ACTION_PASTE / ACTION_CLICK)`, `ACTION_FOCUS`
  - Global actions: `performGlobalAction(GLOBAL_ACTION_BACK|HOME|RECENTS|NOTIFICATIONS)`
- Permissions/Intents for sensitive actions:
  - Calls: prefer `Intent.ACTION_DIAL`; if and only if user confirms and `CALL_PHONE` granted, `Intent.ACTION_CALL`
  - SMS: prefer `Intent.ACTION_SENDTO` (`smsto:`)
  - As a stricter path: `SmsManager.sendTextMessage()` only after explicit per‑action confirmation + granted `SEND_SMS` and clear user awareness
- Foreground notification: `NotificationCompat` with `startForeground()` in a service used for the session
- Encryption: Jetpack Security Crypto (`MasterKey`, `EncryptedFile` or SQLCipher/Room w/ Encrypted support) for audit log

## Manifest changes (snippets)
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" /> <!-- if audio share enabled -->
<uses-permission android:name="android.permission.CALL_PHONE" /> <!-- gated by UX & runtime -->
<uses-permission android:name="android.permission.SEND_SMS" /> <!-- gated by UX & runtime -->

<application>
  <service
    android:name=".RemoteControlAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false"
    android:foregroundServiceType="mediaProjection|connectedDevice">
    <intent-filter>
      <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
      android:name="android.accessibilityservice"
      android:resource="@xml/accessibility_service_config" />
  </service>

  <service
    android:name=".ControlSessionService"
    android:exported="false"
    android:foregroundServiceType="connectedDevice" />
</application>
```

Accessibility config (`res/xml/accessibility_service_config.xml`):
```xml
<accessibility-service
  xmlns:android="http://schemas.android.com/apk/res/android"
  android:accessibilityEventTypes="typeAllMask"
  android:accessibilityFeedbackType="feedbackGeneric"
  android:notificationTimeout="0"
  android:canPerformGestures="true"
  android:description="@string/remote_control_service_desc"
  android:settingsActivity=".OnboardingActivity"/>
```

## Required permissions and justifications
- FOREGROUND_SERVICE: session service + persistent notification during remote control
- BIND_ACCESSIBILITY_SERVICE: bind the service for gestures, global actions, text
- RECORD_AUDIO: only if audio sharing is enabled (already used by MediaProjection)
- CALL_PHONE: only if user explicitly confirms per call; otherwise use ACTION_DIAL
- SEND_SMS: only if explicitly granted + per‑action confirmation; otherwise use ACTION_SENDTO

Runtime checks and safe flows (examples are in code skeletons).

## Control protocol and message schema
- Transport: WebRTC DataChannel (Global) or WSS (Local/RTSP fallback)
- Message: compact JSON
```json
{
  "seq": 1234,
  "type": "gesture|text|node_action|global_action|call_request|ack|error",
  "timestamp": 1715700000123,
  "client_id": "controller-uuid",
  "payload": {"...": "..."},
  "mac": "optional-hmac-or-null"
}
```
- ACKs: `type: "ack"`, payload `{ "ack_seq": <seq>, "status": "ok|error", "error": "optional" }`
- Reliability: sender keeps a short retransmit queue; if no ACK in 300ms, retry up to N times; de‑duplicate via `seq`
- Optional E2E encryption: per‑session key K negotiated via WebRTC (DTLS exporter) or via signalling; encrypt payload with AEAD (AES‑GCM), include `mac` or send `payload_enc`

Payload examples:
- gesture: `{ "kind":"tap|swipe|long_press|multi_touch", "points":[{"x":120,"y":300,"id":0}], "durationMs":80 }`
- text: `{ "text":"hello", "focused":true }`
- node_action: `{ "action":"click|paste|set_text", "node_path":"id/resource/...", "args":{}}`
- global_action: `{ "action":"back|home|recents|notifications" }`
- call_request: `{ "phone":"+1234567" }`

See also `docs/control_protocol.json` for concrete examples.

## Signalling and authentication
- Reuse existing signalling (screenstream.io) for WebRTC SDP exchange.
- Authentication requirements:
  - Stream ID + user PIN/password
  - Ephemeral session token issued by the app (short‑lived, single‑use)
  - Optionally derive per‑session encryption key from DTLS exporter or ECDH over signalling
- Trade‑offs: E2E encrypting control payloads improves privacy but complicates debugging and requires key rotation; DataChannel already uses DTLS. Default on for Global; optional for Local if WSS with trusted TLS.

## Security and UX safeguards
- Explicit onboarding explaining capabilities and risks; manual enable of AccessibilityService
- Per‑session acceptance dialog on device; optional "remember controller for X hours" (encrypted token)
- Second confirmation for sensitive actions (CALL/SMS/payments/export)
- Always‑visible Stop action: ongoing notification + in‑app button
- Single active controller by default; optional admin setting for multi‑controller with explicit opt‑in
- Automatic timeout and inactivity revocation (e.g., 10 minutes)
- Encrypted local audit log; user can view/export/delete

## Telephony and SMS handling
- Calls: prefer `ACTION_DIAL`; `ACTION_CALL` only after user approves a device dialog and `CALL_PHONE` is granted; otherwise show dialer
- SMS: prefer `ACTION_SENDTO` with `smsto:`; avoid background send. `SmsManager.sendTextMessage()` only with explicit permission + on‑device confirmation; background SMS likely triggers Play review and can be policy‑sensitive

## Rate limiting and performance
- Coalesce pointer move events; cap gesture frequency (e.g., ≤60 events/sec)
- Bundle multi‑touch into a single `dispatchGesture`
- Expected end‑to‑end latency: ≥0.5–1s typical; measure via timestamp echo and logs
- CPU/bandwidth: each controller adds DataChannel traffic; keep messages compact

## Logging, audit, privacy
- Keep an encrypted local audit log: timestamp, controller id, action type, parameters hash
- Retention: 30 days default; configurable; user can view/export/delete
- Telemetry: none by default; explicit opt‑in if ever added; document clearly

## Play Store / F-Droid cautions
- AccessibilityService must be user‑enabled; do not auto‑enable
- Prominently disclose remote control capabilities and limitations
- Sensitive actions require visible, per‑action confirmation on device
- Use intents (DIAL/SENDTO) by default; avoid background CALL/SMS unless strictly justified and consented
- Persistent foreground notification during sessions
- No hidden behavior, no obfuscation of sensitive API use; keep feature behind explicit opt‑in

## Deliverables in this prototype
- Minimal prototype code that opens a DataChannel or WSS and executes one tap via AccessibilityService
- Kotlin skeletons: DataChannel handler, AccessibilityService executor, Onboarding flow
- Manifest and Gradle snippets
- Privacy text and policy checklist in this repository

```note
This repository contains a \"prototype/OneTouchRemote\" module with code skeletons engineers can import into Android Studio.
```
