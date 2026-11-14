# Apache Tika — Improvement Roadmap

> **Verdict:** Keep as a learning project  
> **Improvement priority:** Low overall; security fixes are **P0 before processing untrusted files**  
> **Portfolio role:** Document processing / Java integration  
> **GitHub profile:** Keep public after addressing vulnerable dependencies; **do not pin**  
> **Progress:** **0/10** tasks complete

## Goal

Turn the existing Apache Tika parsing demo into a small, predictable **file-to-text and metadata extraction API**. Keep the scope deliberately modest: this is a single-service integration project, **not** a high-level system-design showcase.

The repository currently demonstrates six Apache Tika approaches: `parseExample()`, `parseToString()`, `parseToPlainText()`, `parseToHTML()`, `parseBodyToHTML()`, and `parseToPlainTextChunks()`. These are real API integrations, but they are primarily alternative representations of the same uploaded document, not six separate product features.

### Current limitations

- `GET /api/fileContent/getContent` accepts `MultipartFile`, and invokes all six parsing methods on every request.
- The controller repeatedly calls `file.getInputStream()` and does not explicitly manage the resulting streams.
- Multipart configuration allows **200 MB per file** and **215 MB per request**, without corresponding parser resource safeguards.
- `BodyContentHandler()` is used with its default extracted-text limit (approximately **100,000 characters**), which can fail on larger documents.
- Custom chunking uses a **100 × 1024 × 1024 character** threshold and repeated string concatenation.
- Extracted document content is printed to standard output, and parsing failures have no structured API error handling.
- There is only a basic Spring Boot context test; no README or CI workflow.
- `build.gradle` currently uses **Apache Tika 2.5.0**, **Spring Boot 2.7.4**, and **Java 8**.

## Improvement tracker — 0/10

### P0 — Fix before accepting untrusted uploads

- [ ] **1. Upgrade vulnerable Apache Tika dependencies.** Replace the pinned `tika-core:2.5.0` and `tika-parsers:2.5.0` with a maintained, patched, compatible set; check the effective transitive dependency tree and relevant security advisories, including **CVE-2025-66516**. Verify that the project still builds and parses representative files.
- [ ] **2. Replace GET with a multipart POST upload endpoint.** Introduce `POST /api/documents/parse` with `multipart/form-data` and an explicit `file` part. Return a typed JSON response instead of an unlabeled `List<String>`.
- [ ] **3. Bound upload and parser resource usage.** Lower the 200 MB file limit to a documented, conservative default; reject empty and unsupported files; cap extracted content and metadata; configure reasonable parsing deadlines and processing isolation for untrusted input. Return `413` for oversized uploads, and ensure large documents fail in a controlled way rather than exhausting memory.

### P1 — Make the API correct and maintainable

- [ ] **4. Avoid six parsing passes per request.** Extract requested text and metadata in one parsing pass where possible. Keep alternative Tika output modes as isolated examples or optional, explicitly selected formats.
- [ ] **5. Add structured success and error responses.** Define a response DTO containing `fileName`, `detectedType`, `text`, and `metadata`. Add centralized exception handling with appropriate `400`, `413`, `415`, `422`, and `500` responses; do not expose stack traces or private content to clients.
- [ ] **6. Own input streams and eliminate document-content logging.** Use try-with-resources or a single clearly owned stream, and remove `System.out.println` of extracted document text. Log only safe operational details.
- [ ] **7. Fix chunking efficiency and memory bounds.** Replace repeated string concatenation with `StringBuilder` or a bounded streaming writer; use small, documented chunk sizes and explicit maximum output limits. Avoid collecting unbounded chunks in memory.
- [ ] **8. Add representative parsing tests.** Cover PDF, DOCX, TXT, empty uploads, malformed input, unsupported formats, oversized content, and API error status codes. Include a document exceeding the default `BodyContentHandler` text limit to verify deliberate behavior.

### P2 — Improve developer experience

- [ ] **9. Add a concise README.** Explain the learning-project scope, prerequisites, supported file types, local setup, example `curl` upload, sample JSON response, and security limitations. Include small, non-sensitive test fixtures.
- [ ] **10. Add CI and update the Java/Spring Boot baseline.** Run Gradle build and tests on GitHub Actions. Upgrade the legacy Spring Boot / Java baseline to supported versions after checking compatibility with the selected Tika release.

## Target API contract

**Request**

```http
POST /api/documents/parse
Content-Type: multipart/form-data
```

Field: `file` (uploaded document)

**Example success response**

```json
{
  "fileName": "example.pdf",
  "detectedType": "application/pdf",
  "text": "Extracted document text...",
  "metadata": {
    "title": "Example document",
    "author": "Example author"
  }
}
```

**Minimum acceptance criteria:** one predictable response per upload; detected MIME type and metadata; bounded text extraction; safely managed streams; no document-body logs; informative errors; tests for valid, empty, malformed, and over-limit files.

## Security and large-document notes

- **Dependency security:** The existing Tika 2.5.0 pin requires immediate review because of the reported critical XXE advisory **CVE-2025-66516**. Upgrade and verify the effective resolved versions **before exposing the endpoint to untrusted users**.
- **Parsing risks:** File-size limits alone do not control decompression, nested documents, XML/entity processing, embedded resources, parsing time, or extracted text size. Production-facing deployments need supported-format policies, hard limits, isolated processing, and authentication as appropriate.
- **Text truncation:** A default `BodyContentHandler()` is not an unlimited text sink. Define whether excessive extracted text produces a documented truncation result or a controlled error, and test that policy.

## Scope and portfolio decision

| Question | Decision |
| --- | --- |
| Keep public? | Yes, after remediating the vulnerable dependency |
| Improve before November 20, 2026? | Only essential security and API fixes |
| HLD/system-design project? | No — small single-service integration |
| Specialized Java skill? | Yes — file parsing and document extraction |
| Pin to GitHub profile? | **No** |
| Archive? | Not necessary |

**Suggested execution order:** security dependency update → safe POST endpoint and resource limits → single-pass parsing with typed responses → stream/error handling → tests → documentation and CI.

This roadmap tracks proposed work; **none of the implementation changes above have been completed merely by creating this file**.
