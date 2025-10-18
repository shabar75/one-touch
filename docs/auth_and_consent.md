# Authentication and Consent Gating

- Require stream ID + PIN/password + ephemeral session token.
- First message on control channel must be `type: "auth"` with payload `{token, stream_id, pin}`.
- Device verifies token (via signalling), validates PIN, then requires on-device consent.
- If the controller was remembered for X hours, consent auto-approves; otherwise `ack` with error `consent_required` is returned until user accepts.
