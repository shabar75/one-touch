# Privacy & Policy Notes for OneTouch Remote Control

## Short privacy policy text (app-level)
OneTouch streams your device screen and, when you enable the Remote Control feature, allows an authorized controller to interact with your device using an AccessibilityService. Remote control is disabled by default and requires your explicit consent each session. Sensitive actions (placing calls, sending SMS, payments, exporting contacts) require visible confirmations on your device. OneTouch records an encrypted local audit log of remote actions (timestamp, controller ID, action type). You can view or delete this log at any time. OneTouch does not send analytics or telemetry without your explicit opt‑in.

## Play Store cautions and disclosures
- AccessibilityService disclosure: clearly describe remote control capabilities, purpose, and user benefit in Play listing and in-app onboarding.
- Sensitive actions: require visible, per‑action confirmation; prefer safe intents (ACTION_DIAL, ACTION_SENDTO) over background APIs.
- Permissions: request only when needed; explain why; allow feature use without CALL/SMS if not granted.
- Foreground service: display a persistent notification during remote sessions; provide an obvious Stop action.
- No auto-enabling of AccessibilityService; no hidden behavior.

## F-Droid notes
- No proprietary dependencies; WSS fallback should use open libraries; WebRTC is acceptable.
- Provide complete source and build reproducibility.
