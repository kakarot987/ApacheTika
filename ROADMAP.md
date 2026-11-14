# Apache Tika — Improvement Roadmap

> **Verdict:** Keep as a learning project  
> **Priority:** Low overall; security work is mandatory before untrusted uploads  
> **Portfolio role:** Document processing / Java integration  
> **Profile:** Do not pin  
> **Implementation progress:** 9/10 improvements implemented and tested; hard isolation remains

## Implementation status

- [x] **P0 — Replace vulnerable Tika 2.5.0** with patched Tika 3.3.3 (verify transitive dependency scan in CI or locally).
- [x] **P0 — Replace GET upload with POST multipart/form-data** at `/api/documents/parse`.
- [ ] **P0 — Enforce fully isolated resource limits.** Added 10 MB uploads, 1M character extraction cap, bounded workers and cooperative timeout; **hard CPU/memory/time isolation is not yet guaranteed** and is needed before untrusted deployment.
- [x] **P1 — Eliminate six parsing passes per request**; a single `AutoDetectParser` invocation now extracts text and metadata.
- [x] **P1 — Structured DTO and errors** with HTTP 400, 413, 415, 422, 503 and fallback 500.
- [x] **P1 — Close streams and remove raw document logging**; handlers use bounded input/output and no content console prints.
- [x] **P1 — Eliminate inefficient custom string chunking**; legacy example utility is removed in favor of bounded `BodyContentHandler`.
- [x] **P1 — Add representative parsing tests**; MockMvc covers TXT, empty, unsupported, upload and text limits, and generated fixtures cover valid PDF, DOCX and malformed PDF.
- [x] **P2 — Add README** with local setup, sample curl/JSON, safety notes and Docker example.
- [x] **P2 — Add CI and modernize Java / Spring** to Java 17, Spring Boot 3.5.8 and Gradle 8.14.3.

## API

```http
POST /api/documents/parse
Content-Type: multipart/form-data
```

Field: `file`. Response: `{ "fileName": "...", "detectedType": "...", "text": "...", "metadata": {} }`.

## Verification required

- [x] GitHub Actions Gradle compilation and tests passed on the implementation commit.\n- [ ] Review the resolved dependency graph with a vulnerability scanner and ensure ongoing dependency updates.
- [x] Added valid PDF, valid DOCX, malformed PDF, and extraction limit tests.\n- [ ] Consider a dedicated malformed DOCX test and stronger timeout/integration tests.
- [ ] Add hard-kill worker-process sandboxing or deploy within restrictive container quotas before exposing untrusted upload functionality.

**Portfolio:** Keep public with security caveats, keep as a learning project, and **do not pin**. This is not a high-level system design project.
