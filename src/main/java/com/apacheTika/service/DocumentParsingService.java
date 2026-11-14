package com.apacheTika.service;

import com.apacheTika.dto.DocumentParseResponse;
import com.apacheTika.error.DocumentException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

@Service
public class DocumentParsingService {

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "text/plain",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final int MAX_METADATA_ENTRIES = 32;
    private static final int MAX_METADATA_VALUE_LENGTH = 256;

    // Backpressure caps simultaneous work. Cancellation cannot stop all native/parser work.
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2),
            task -> {
                Thread thread = new Thread(task, "tika-parser-worker");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy()
    );

    private final long maxFileBytes;
    private final int maxTextChars;
    private final long timeoutMillis;
    private final Tika detector = new Tika();

    public DocumentParsingService(
            @Value("${app.parsing.max-file-bytes:10485760}") long maxFileBytes,
            @Value("${app.parsing.max-text-chars:1000000}") int maxTextChars,
            @Value("${app.parsing.timeout-millis:15000}") long timeoutMillis) {
        if (maxFileBytes <= 0 || maxTextChars <= 0 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("Parsing limits must be positive.");
        }
        this.maxFileBytes = maxFileBytes;
        this.maxTextChars = maxTextChars;
        this.timeoutMillis = timeoutMillis;
    }

    public DocumentParseResponse parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Upload a non-empty document.");
        }
        if (file.getSize() > maxFileBytes) {
            throw new DocumentException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Uploaded file exceeds the size limit.");
        }

        Path temp = null;
        try {
            temp = Files.createTempFile("tika-upload-", ".bin");
            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > maxFileBytes) {
                        throw new DocumentException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Uploaded file exceeds the size limit.");
                    }
                    out.write(buffer, 0, read);
                }
                if (total == 0) {
                    throw new DocumentException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Upload a non-empty document.");
                }
            }

            String detectedType = detector.detect(temp.toFile());
            if (!SUPPORTED_MIME_TYPES.contains(detectedType)) {
                throw new DocumentException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_FILE_TYPE", "Only PDF, DOCX, and TXT documents are supported.");
            }
            String fileName = safeFileName(file.getOriginalFilename());
            final Path document = temp;
            Future<DocumentParseResponse> job;
            try {
                job = workers.submit(() -> parseOnce(document, fileName, detectedType));
            } catch (RejectedExecutionException e) {
                throw new DocumentException(HttpStatus.SERVICE_UNAVAILABLE, "BUSY", "Document parser is busy; retry later.");
            }
            try {
                return job.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                job.cancel(true);
                throw new DocumentException(HttpStatus.UNPROCESSABLE_ENTITY, "PARSE_TIMEOUT", "Document parsing exceeded the time limit.");
            } catch (InterruptedException e) {
                job.cancel(true);
                Thread.currentThread().interrupt();
                throw new DocumentException(HttpStatus.SERVICE_UNAVAILABLE, "INTERRUPTED", "Document processing was interrupted.");
            } catch (ExecutionException e) {
                if (e.getCause() instanceof DocumentException documentException) {
                    throw documentException;
                }
                throw new DocumentException(HttpStatus.UNPROCESSABLE_ENTITY, "PARSE_ERROR", "The document could not be parsed.");
            }
        } catch (IOException e) {
            throw new DocumentException(HttpStatus.UNPROCESSABLE_ENTITY, "READ_ERROR", "Unable to read the uploaded document.");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Container runtime should clean up remaining transient files.
                }
            }
        }
    }

    private DocumentParseResponse parseOnce(Path path, String fileName, String detectedType) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler text = new BodyContentHandler(maxTextChars);
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata embeddedMetadata) {
                return false;
            }

            @Override
            public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata embeddedMetadata,
                                      boolean outputHtml) {
                // Do not parse embedded attachments.
            }
        });

        try (InputStream in = Files.newInputStream(path)) {
            parser.parse(in, text, metadata, context);
        } catch (IOException | TikaException | SAXException e) {
            throw new DocumentException(HttpStatus.UNPROCESSABLE_ENTITY, "PARSE_ERROR",
                    "Document parsing failed or extracted text exceeded the configured limit.");
        }

        Map<String, String> safeMetadata = new LinkedHashMap<>();
        Arrays.stream(metadata.names()).sorted().limit(MAX_METADATA_ENTRIES).forEach(name -> {
            String value = metadata.get(name);
            if (value != null) {
                safeMetadata.put(name.length() > 128 ? name.substring(0, 128) : name,
                        value.substring(0, Math.min(value.length(), MAX_METADATA_VALUE_LENGTH)));
            }
        });
        return new DocumentParseResponse(fileName, detectedType, text.toString(), safeMetadata);
    }

    private static String safeFileName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "upload";
        }
        String name = rawName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            return "upload";
        }
        return name.substring(0, Math.min(name.length(), 255));
    }

    public void shutdownWorkersForTests() {
        workers.shutdownNow();
    }

    @PreDestroy
    void shutdownWorkers() {
        workers.shutdownNow();
    }
}
