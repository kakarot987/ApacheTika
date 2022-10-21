package com.apacheTika.Controller;

import com.apacheTika.TikaConfig.ApacheTikaUtility;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/fileContent")
public class ApacheTikaController {

    @Autowired
    ApacheTikaUtility apacheTikaUtility;

    @GetMapping("/getContent")
    public List<String> getFileContent(@RequestParam("file") MultipartFile file) throws IOException, TikaException, SAXException {
        List<String> string = new ArrayList<>();
        string.add(apacheTikaUtility.parseExample(file.getInputStream()));
        string.add(apacheTikaUtility.parseToString(file.getInputStream()));
        string.add(apacheTikaUtility.parseBodyToHTML(file.getInputStream()));
        string.add(apacheTikaUtility.parseToHTML(file.getInputStream()));
        string.add(apacheTikaUtility.parseToPlainText(file.getInputStream()));
        string.add(apacheTikaUtility.parseToPlainTextChunks(file.getInputStream()).toString());
        return string;
    }
}
