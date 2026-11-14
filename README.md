# Apache Tika document extraction API

A **learning-focused** Spring Boot + Apache Tika service that extracts text and bounded metadata from PDF, DOCX, and TXT uploads. This is a small document-processing integration project, not a production-hardened document-ingestion platform.

## Stack

- Java 17, Spring Boot 3.5.8, Gradle 8.14.3
- Apache Tika 3.3.3 (standard parsers)
- JUnit 5 / Spring MockMvc; GitHub Actions CI

## Run

```bash
./gradlew clean test bootRun
```

Windows: use `gradlew.bat`. A Java 17 JDK is required.

## API

```bash
curl --fail-with-body -X POST http://localhost:8080/api/documents/parse \
  -F "file=@example.pdf"
```

Response (`application/json`):

```json
{
  "fileName": "example.pdf",
  "detectedType": "application/pdf",
  "text": "Extracted document text...",
  "metadata": {
    "dc:title": "Example document"
  }
}
```

Metadata keys come from the source document/Tika and are not guaranteed to exist or be identical across formats. They are limited to 32 keys and 256 characters per value.

Errors have a consistent shape:

```json
{"code": "UNSUPPORTED_FILE_TYPE", "message": "Only PDF, DOCX, and TXT documents are supported."}
```

Expected statuses: `400` empty/missing/invalid multipart; `413` over-size; `415` unsupported format; `422` parse error or deadline; `503` busy; `500` unexpected error. There is no authentication in this demo.

## Limits and safety

- Max multipart file **10 MB**, request **11 MB**; content copied with an additional 10 MB streaming cap.
- Content type is **detected from the file**, not trusted from the upload's declared MIME type.
- Only PDF, DOCX, and plain text are accepted.
- Up to **1,000,000 extracted characters**; going over the cap returns a controlled `422` instead of silently truncating.
- Bounded metadata and a capped parser worker queue (two workers, two queued jobs).
- Nominal parsing deadline **15 seconds**. Java futures cancellation is **cooperative**, so a stuck parser may continue running in the background. The limit is **not a hard kill**.
- Nested/embedded document extraction is disabled. Document bodies are not logged.
- Temporary upload data is cleaned up on normal completion; canceled non-cooperative parsers may outlive their temporary-file lifecycle.

For **untrusted Internet-facing files**, do **not** rely on these in-process controls alone. Add authentication/rate limiting and run the service or parser workers inside an isolated container with strict CPU, RAM, process, disk and network limits, read-only filesystem, a bounded writable temporary filesystem, and a hard external request/job deadline. Do not treat this demo as a secure file-processing sandbox.

Example development container build after `./gradlew bootJar`:

```bash
docker build -t apache-tika-demo .
docker run --rm -p 8080:8080 --memory=512m --cpus=1 \
  --read-only --tmpfs /tmp:rw,nosuid,noexec,size=64m apache-tika-demo
```

## Tests and further work

`./gradlew test` runs Spring context and HTTP API tests, including text extraction, empty input, unsupported binary formats, over-limit upload and extracted-text limits. Add dedicated valid PDF/DOCX, malformed document and hard-isolation integration tests before offering the endpoint to untrusted users. CI runs the Gradle build and tests on pushes and pull requests.

See [ROADMAP.md](ROADMAP.md) for the change tracker and remaining verification work.
