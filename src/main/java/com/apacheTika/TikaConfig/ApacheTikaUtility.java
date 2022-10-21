package com.apacheTika.TikaConfig;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class ApacheTikaUtility {

    //The Tika facade, provides a number of very quick
    // and easy ways to have your content parsed by Tika, and return the resulting plain text
    public String parseToString(InputStream file) throws IOException, TikaException, SAXException {
     Tika tika = new Tika();

        return "Parsing file into String: " + tika.parseToString(file);
    }

    //For more control, you can call the Tika Parsers directly. Most likely, you'll want to start out using the Auto-Detect Parser,
    // which automatically figures out what kind of content you have, then calls the appropriate parser for you.
    public String parseExample(InputStream file) throws IOException, SAXException, TikaException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
            parser.parse(file, handler, metadata);
            return "Parsing file using Auto-Detector: " + handler.toString();
        }

        //By using the BodyContentHandler, you can request that Tika
        // return only the content of the document's body as a plain-text string.
    public String parseToPlainText(InputStream file) throws IOException, SAXException, TikaException {
        BodyContentHandler handler = new BodyContentHandler();

        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
            parser.parse(file, handler, metadata);
            return "Parsing file into Plain Text: " + handler.toString();
    }

    //By using the ToXMLContentHandler, you can get the XHTML content of the whole document as a string.
    public String parseToHTML(InputStream file) throws IOException, SAXException, TikaException {
        ContentHandler handler = new ToXMLContentHandler();

        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
            parser.parse(file, handler, metadata);
            return "Parsing file into HTML: " + handler.toString();
    }

    //If you just want the body of the xhtml document, without the header,
    // you can chain together a BodyContentHandler and a ToXMLContentHandler as shown:

    public String parseBodyToHTML(InputStream file) throws IOException, SAXException, TikaException {
        ContentHandler handler = new BodyContentHandler(
                new ToXMLContentHandler());

        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
            parser.parse(file, handler, metadata);
            System.out.println("---------" +  handler.toString() );
            return "Parsing file's Body into HTML: " + handler.toString();
    }

    //Sometimes, you want to chunk the resulting text up, perhaps to output as you go minimising memory use,
    // perhaps to output to HDFS files,
    // or any other reason! With a small custom content handler, you can do that.
    public List<String> parseToPlainTextChunks(InputStream file) throws IOException, SAXException, TikaException {
        final List<String> chunks = new ArrayList<>();
        chunks.add("");
        ContentHandlerDecorator handler = new ContentHandlerDecorator() {
            final int MAXIMUM_TEXT_CHUNK_SIZE = 100 * 1024 * 1024;

            @Override
            public void characters(char[] ch, int start, int length) {
                String lastChunk = chunks.get(chunks.size() - 1);
                String thisStr = new String(ch, start, length);

                if (lastChunk.length() + length > MAXIMUM_TEXT_CHUNK_SIZE) {
                    chunks.add(thisStr);
                } else {
                    chunks.set(chunks.size() - 1, lastChunk + thisStr);
                }
            }
        };

        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
            parser.parse(file, handler, metadata);

        return chunks;
    }


}
