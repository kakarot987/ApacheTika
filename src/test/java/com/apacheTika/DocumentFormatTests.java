package com.apacheTika;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apacheTika.dto.DocumentParseResponse;
import com.apacheTika.error.DocumentException;
import com.apacheTika.service.DocumentParsingService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentFormatTests {

    private final DocumentParsingService parser = new DocumentParsingService(10 * 1024 * 1024, 1000000, 15000);

    @AfterEach
    void tearDown() {
        parser.shutdownWorkersForTests();
    }

    @Test
    void extractsPdfTextAndMetadata() throws IOException {
        MockMultipartFile upload = new MockMultipartFile("file", "sample.pdf", "application/pdf", pdf());
        DocumentParseResponse result = parser.parse(upload);
        assertEquals("application/pdf", result.detectedType());
        assertTrue(result.text().contains("Hello from PDF"), result.text());
    }

    @Test
    void extractsDocxText() throws IOException {
        MockMultipartFile upload = new MockMultipartFile("file", "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx());
        DocumentParseResponse result = parser.parse(upload);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", result.detectedType());
        assertTrue(result.text().contains("Hello from DOCX"), result.text());
    }

    @Test
    void rejectsMalformedDocumentWithoutLeakingInternalDetails() {
        byte[] truncated = "%PDF-1.4\nnot a valid cross reference or document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile upload = new MockMultipartFile("file", "broken.pdf", "application/pdf", truncated);
        assertThrows(DocumentException.class, () -> parser.parse(upload));
    }

    private static byte[] pdf() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
        List<Integer> positions = new ArrayList<>();
        positions.add(0);
        writeObject(out, positions, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        writeObject(out, positions, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        writeObject(out, positions, 3,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>");
        writeObject(out, positions, 4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        String stream = "BT /F1 12 Tf 72 700 Td (Hello from PDF) Tj ET\n";
        writeObject(out, positions, 5, "<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length
                + " >>\nstream\n" + stream + "endstream");
        int xref = out.size();
        out.write("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
        for (int i = 1; i <= 5; i++) {
            out.write(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", positions.get(i))
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        out.write(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static void writeObject(ByteArrayOutputStream out, List<Integer> positions, int id, String value)
            throws IOException {
        positions.add(out.size());
        out.write((id + " 0 obj\n" + value + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
    }

    private static byte[] docx() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                    "</Types>");
            entry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                    "</Relationships>");
            entry(zip, "word/document.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                    "<w:body><w:p><w:r><w:t>Hello from DOCX</w:t></w:r></w:p></w:body></w:document>");
        }
        return out.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, String data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
