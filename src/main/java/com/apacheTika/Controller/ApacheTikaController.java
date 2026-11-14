package com.apacheTika.Controller;

import com.apacheTika.dto.DocumentParseResponse;
import com.apacheTika.service.DocumentParsingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class ApacheTikaController {

    private final DocumentParsingService parsingService;

    public ApacheTikaController(DocumentParsingService parsingService) {
        this.parsingService = parsingService;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentParseResponse parse(@RequestPart("file") MultipartFile file) {
        return parsingService.parse(file);
    }
}
