# DocFlow CRDT Protocol

DocFlow uses the unmodified Hocuspocus/Yjs binary sync and awareness protocol. It does not define a new CRDT algorithm or wrap Yjs structs in a custom binary format.

Application metadata is mapped as follows:

| Field | Source / storage |
| --- | --- |
| `protocolVersion` | `1`, returned by the Java access API and stored with checkpoints/updates |
| `documentId` | Hocuspocus document name and MySQL `doc_id` |
| `clientId` | Authenticated connection/socket ID stored with each increment; Yjs also carries its own internal client clock IDs |
| `updateId` | SHA-256 of `documentId + payload`, unique per document in MySQL |
| `payload` | Unmodified Yjs `Uint8Array` update |

Connection sequence:

1. The browser opens a Hocuspocus provider with the document ID and JWT.
2. Hocuspocus calls the Java `/api/v1/docs/{id}/crdt/access` endpoint.
3. Java authenticates the JWT, validates document access, and returns read/write scope.
4. Hocuspocus loads the last checkpoint and applies remaining increments in sequence order.
5. Yjs exchanges state vectors and only sends missing binary updates.
6. Each update is bounded by `CRDT_MAX_UPDATE_BYTES`, deduplicated by `updateId`, persisted, and broadcast through the official Redis extension.
7. Debounced checkpoint storage writes the full Yjs state transactionally. Covered increments are deleted only after the checkpoint write succeeds.

Yjs updates are commutative, associative, and idempotent. Delivery order therefore does not affect convergence, and applying the same payload more than once does not duplicate content.
