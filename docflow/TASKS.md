# DocFlow Follow-up Tasks

## Collaboration Editing

- [x] Keep revision-number conflict handling only for existing `LEGACY` documents.
- [x] Select Yjs with Tiptap Collaboration and the open-source Hocuspocus server.
- [x] Persist Yjs binary increments and compact them into MySQL checkpoints.
- [x] Use the Hocuspocus Redis extension for multi-node update and awareness propagation.
- [x] Authenticate every collaboration connection through the Java permission API.
- [x] Add CRDT convergence, duplicate, out-of-order, reconnect and recovery tests.
- [x] Retain bounded CRDT checkpoint history and allow authorized point-in-time restore.
- [x] Add browser live paginated editing, MathLive-backed MathML storage, and resumable attachment uploads.
- [x] Add per-document CSL-JSON reference storage with DOI lookup endpoints.

The project has permanently selected CRDT for new collaborative documents. OT will not be implemented and no OT API, model, transform, acknowledgement, retry, or test work remains planned. Existing documents stay `LEGACY`; new documents default to `CRDT`.
