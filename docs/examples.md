## Minimal JSON schema and examples

Schema fields:
- seq: integer sequence number
- type: gesture | text | node_action | global_action | call_request | ack | error
- timestamp: client-sent timestamp (ms)
- client_id: controller identifier
- payload: message-specific data
- mac: optional HMAC or null when using only DTLS

Example messages:

Tap gesture
```json
{"seq":1,"type":"gesture","timestamp":1715700000123,"client_id":"controller-uuid","payload":{"kind":"tap","points":[{"x":180,"y":420,"id":0}],"durationMs":60},"mac":null}
```

Set text
```json
{"seq":2,"type":"text","timestamp":1715700000456,"client_id":"controller-uuid","payload":{"text":"Hello world","focused":true},"mac":null}
```

Call request
```json
{"seq":3,"type":"call_request","timestamp":1715700000789,"client_id":"controller-uuid","payload":{"phone":"+15551234567"},"mac":null}
```
