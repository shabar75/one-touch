# Testing Checklist for OneTouch Remote Control Prototype

## Unit tests (suggestions)
- ControlChannelManager: parse messages, seq/ack handling, controller conflict
- Accessibility bridge: gesture dispatch returns success/failure paths
- AuditLogger: writes encrypted entries; handle IO failures gracefully

## Integration tests
- DataChannel/WSS loopback: send a tap message, observe dispatchGesture invoked
- Permission gating: CALL/SMS flows fall back to DIAL/SENDTO without runtime grants
- Foreground notification shown during session; session stop removes it

## Manual test steps
1. Install debug build on a test device (Android 9+).
2. Open OneTouch, start screen sharing (MediaProjection) and enable AccessibilityService in settings.
3. From remote client (test harness), open WebRTC DataChannel or WSS to device, send message:
```json
{"seq":1,"type":"gesture","timestamp":1715700000123,"client_id":"c1","payload":{"kind":"tap","points":[{"x":200,"y":400,"id":0}],"durationMs":60}}
```
4. Verify a tap occurs at coordinates.
5. Send a `text` message and verify focused field receives text.
6. Trigger `call_request` and confirm the dialer opens; ensure no call is placed without user tapping the call button.
7. Verify persistent notification is visible and Stop action works.
8. Disable AccessibilityService and retry: verify graceful error (no crash) and user guidance to enable service.
9. Simulate network loss: drop DataChannel; verify timeout and session ends.
10. Attempt second controller connection: verify conflict rejection unless user explicitly allows multi‑controller.
