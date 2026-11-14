package com.apacheTika;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apacheTika.service.DocumentParsingService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DocumentParsingService parsingService;

    @Test
    void parsesTextOnceAndReturnsStructuredJson() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain",
                "Hello Apache Tika!".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/documents/parse").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName", is("notes.txt")))
                .andExpect(jsonPath("$.detectedType", is("text/plain")))
                .andExpect(jsonPath("$.text", containsString("Hello Apache Tika!")))
                .andExpect(jsonPath("$.metadata").isMap());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        mockMvc.perform(multipart("/api/documents/parse").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("EMPTY_FILE")));
    }

    @Test
    void rejectsUnsupportedBinaryRegardlessOfDeclaredMime() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.bin", "text/plain", new byte[]{0, 1, 2, 3, 0, 4});
        mockMvc.perform(multipart("/api/documents/parse").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("UNSUPPORTED_FILE_TYPE")));
    }

    @Test
    void rejectsFilesBeyondApplicationLimit() throws Exception {
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "large.txt", "text/plain", bytes);
        mockMvc.perform(multipart("/api/documents/parse").file(file))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void rejectsContentBeyondExtractionLimit() {
        DocumentParsingService smallLimit = new DocumentParsingService(1024 * 1024, 50, 15000);
        try {
            byte[] bytes = ("hello ".repeat(100)).getBytes(StandardCharsets.UTF_8);
            MultipartFile file = new MockMultipartFile("file", "long.txt", "text/plain", bytes);
            org.junit.jupiter.api.Assertions.assertThrows(
                    com.apacheTika.error.DocumentException.class, () -> smallLimit.parse(file));
        } finally {
            smallLimit.shutdownWorkersForTests();
        }
    }
}
