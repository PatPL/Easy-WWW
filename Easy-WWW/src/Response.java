import enums.Status;
import enums.StatusType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Response {
    
    private int statusCode;
    private String statusMessage;
    private Map<String, String> headers;
    private Map<String, String> binaryHeaders;
    private String body;
    private byte[] binaryBody;
    public String websiteRoot = null;
    
    /**
     * Returns a new Webserver.Response object with empty body and default [Status.NotImplemented_501] status
     */
    public Response () {
        setStatus (Status.NotImplemented_501);
        headers = new HashMap<String, String> ();
        binaryHeaders = new HashMap<String, String> ();
        body = "";
        binaryBody = new byte[0];
    }
    
    // https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6
    public Response (String rawHTTP) throws Exception {
        if (rawHTTP.strip ().length () == 0) {
            throw new Exception ("Empty response");
        }
        
        // Line counter
        int i = 0;
        String[] lines = rawHTTP.split ("\n");
        
        // Ignore empty leading lines
        while (i < lines.length && lines[i].strip ().length () == 0) {
            ++i;
        }
        
        // Status-Line
        String[] statusLine = lines[i++].split (" ", 3);
        if (statusLine.length != 3) {
            throw new Exception (String.format ("Invalid Status-Line:\n%s\n", lines[i - 1]));
        } else {
            this.statusCode = Integer.parseInt (statusLine[1].strip ());
            this.statusMessage = statusLine[2].strip ();
        }
        
        // general-header
        // request-header
        // CRLF
        this.headers = new HashMap<String, String> ();
        while (i < lines.length && lines[i].strip ().length () != 0) {
            String[] header = lines[i].split (":", 2);
            if (header.length != 2 || header[0].strip ().length () == 0) {
                // Invalid header
                continue;
            }
            
            headers.put (header[0].strip (), header[1].strip ());
            
            ++i;
        }
        
        // lines[i] should be a CRLF here. Next line is the beginning of request's body
        ++i;
        
        StringBuilder bodyBuilder = new StringBuilder ();
        while (i < lines.length) {
            bodyBuilder.append (lines[i++].strip ());
            bodyBuilder.append ("\r\n");
        }
        this.body = bodyBuilder.toString ().strip ();
        
    }
    
    public int getStatusCode () {
        return statusCode;
    }
    
    public String getStatusMessage () {
        return statusMessage;
    }
    
    public StatusType getStatusType () {
        return Status.getStatusType (this.statusCode);
    }
    
    /**
     * Sets a predefinied status code and message
     *
     * @param newStatus
     */
    public void setStatus (Status newStatus) {
        setStatus (newStatus.code, newStatus.message);
    }
    
    /**
     * Sets a custom status code and message
     *
     * @param code
     * @param message
     */
    public void setStatus (int code, String message) {
        statusCode = code;
        statusMessage = message;
    }
    
    public String getHeader (String key) {
        return headers.getOrDefault (key, null);
    }
    
    /**
     * Sets, or adds a header with a given key
     *
     * @param key
     * @param value
     */
    public void setHeader (String key, String value) {
        headers.put (key, value);
    }
    
    /**
     * Removes a header with a given key
     *
     * @param key
     */
    public void removeHeader (String key) {
        headers.remove (key);
    }
    
    /**
     * Sets mime content type included in "Content-Type" header
     *
     * @param mime
     */
    public void setContentType (String mime) {
        setHeader ("Content-Type", String.format ("%s; charset=utf-8", mime));
    }

//	/**
//	 * Sets contentType based on the extension of the file pointed to by given path
//	 *
//	 * @param path
//	 */
//	public void setContentTypeByFileExtension(String path) {
//		setContentType(getMimeFromExtension(Utility.getExtensionFromPath(path)));
//	}
    
    public enum BodyType {
        Text,
        HTML,
        Path
    }
    
    public String getBody () {
        return body;
    }
    
    /**
     * Sets response body to a String value or an entire file
     *
     * @param value String body value, or a path to a file, if [type] is [BodyType.Path]
     * @param type
     */
    public void setBody (String value, BodyType type) {
        switch (type) {
            case Text:
                setContentType ("text/plain");
                body = value;
                break;
            case HTML:
                setContentType ("text/html");
                body = value;
                break;
            case Path:
                // Crucial. toByteArray will write the text body when available
                // It will only use binaryBody, if body == null
                body = null;
                
                if (value.equals ("")) {
                    value = ".";
                }
                
                Path path = Path.of (value);
                
                if (websiteRoot != null) {
                    path = Path.of (websiteRoot, path.toString ());
                }
                
                path = path.toAbsolutePath ().normalize ();
                String pathString = path.toString ();
                
                // While it will also falsely 'find' extensions if both the directory name has a dot,
                // and the file has no extension, in that case we still want no extension (default case)
                // Same thing when the entire path has no dots (The entire path will be passed = default case)
                
                setContentType (getMimeFromExtension (pathString.substring (pathString.lastIndexOf (".") + 1)));
                
                File file = new File (pathString);
                
                if (file.exists ()) {
                    if (file.isDirectory ()) {
                        if (new File (pathString + "/index.html").exists ()) {
                            setBody (value.endsWith ("/") ? value + "index.html" : value + "/index.html", BodyType.Path);
                            return;
                        }
                        
                        setStatus (Status.NotImplemented_501);
                        setBody (String.format ("Resource <b>%s</b> is a directory. No support for that yet", value), BodyType.HTML);
                    } else {
                        // It's a file
                        try {
                            binaryBody = Files.readAllBytes (path);
                            
                            binaryHeaders.put ("Content-Length", String.valueOf (binaryBody.length));
                            setStatus (Status.OK_200);
                        } catch (Exception e) {
                            setStatus (Status.InternalServerError_500);
                            setBody (String.format ("There was an error while fetching resource <b>%s</b>", value), BodyType.HTML);
                            e.printStackTrace ();
                        }
                    }
                } else {
                    setStatus (Status.NotFound_404);
                    setBody (String.format ("Resource at <b>%s</b> doesn't exist", value), BodyType.HTML);
                }
                
                break;
        }
    }
    
    // https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6
    
    /**
     * @return Built response in form of a UTF-8 byte array
     */
    public byte[] toByteArray () {
        String output = this.toString ();
        
        // Body
        if (body != null) {
            // Text body
            return output.getBytes (StandardCharsets.UTF_8);
        } else {
            // Binary body
            byte[] headerBytes = output.getBytes (StandardCharsets.UTF_8);
            byte[] responseBytes = new byte[headerBytes.length + binaryBody.length];
            System.arraycopy (headerBytes, 0, responseBytes, 0, headerBytes.length);
            System.arraycopy (binaryBody, 0, responseBytes, headerBytes.length, binaryBody.length);
            return responseBytes;
        }
        
    }
    
    // https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6
    
    /**
     * Builds an entire response (without binary body, is response uses binary body)
     *
     * @return Built response in form of a String
     */
    public String toString () {
        StringBuilder output = new StringBuilder ();
        
        // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
        output.append ("HTTP/1.1 ");
        output.append (statusCode);
        output.append (" ");
        if (statusMessage != null && statusMessage.length () > 0) {
            output.append (statusMessage);
        } else {
            output.append ("Status message not set");
        }
        output.append ("\r\n");
        
        // Headers
        for (Map.Entry<String, String> header : headers.entrySet ()) {
            output.append (String.format ("%s: %s\r\n", header.getKey (), header.getValue ()));
        }
        
        if (body == null) {
            // Binary headers, if binary body is being sent
            for (Map.Entry<String, String> header : binaryHeaders.entrySet ()) {
                output.append (String.format ("%s: %s\r\n", header.getKey (), header.getValue ()));
            }
        }
        
        output.append ("\r\n");
        
        // Body
        if (body != null) {
            // Text body
            output.append (body);
        }
        
        // Trailing whitespace required for binary bodies, also could be part of the body itself
        return output.toString ().stripLeading ();
    }
    
    private static final Map<String, String> mimeExtenstionMap = new HashMap<String, String> () {{
        put ("aac", "audio/aac");
        put ("abw", "application/x-abiword");
        put ("arc", "application/x-freearc");
        put ("avi", "video/x-msvideo");
        put ("azw", "application/vnd.amazon.ebook");
        put ("bin", "application/octet-stream");
        put ("bmp", "image/bmp");
        put ("bz", "application/x-bzip");
        put ("bz2", "application/x-bzip2");
        put ("csh", "application/x-csh");
        put ("css", "text/css");
        put ("csv", "text/csv");
        put ("doc", "application/msword");
        put ("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        put ("eot", "application/vnd.ms-fontobject");
        put ("epub", "application/epub+zip");
        put ("gz", "application/gzip");
        put ("gif", "image/gif");
        put ("htm", "text/html");
        put ("html", "text/html");
        put ("ico", "image/vnd.microsoft.icon");
        put ("ics", "text/calendar");
        put ("jar", "application/java-archive");
        put ("jpg", "image/jpeg");
        put ("jpeg", "image/jpeg");
        put ("js", "text/javascript");
        put ("json", "application/json");
        put ("jsonld", "application/ld+json");
        put ("mid", "audio/midi audio/x-midi");
        put ("midi", "audio/midi audio/x-midi");
        put ("mjs", "text/javascript");
        put ("mp3", "audio/mpeg");
        put ("mpeg", "audio/mpeg");
        put ("mpkg", "application/vnd.apple.installer+xml");
        put ("odp", "application/vnd.oasis.opendocument.presentation");
        put ("ods", "application/vnd.oasis.opendocument.spreadsheet");
        put ("odt", "application/vnd.oasis.opendocument.text");
        put ("oga", "audio/ogg");
        put ("ogv", "video/ogg");
        put ("ogx", "application/ogg");
        put ("opus", "audio/opus");
        put ("otf", "font/otf");
        put ("png", "image/png");
        put ("pdf", "application/pdf");
        put ("php", "application/php");
        put ("ppt", "application/vnd.ms-powerpoint");
        put ("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        put ("rar", "application/x-rar-compressed");
        put ("rtf", "application/rtf");
        put ("sh", "application/x-sh");
        put ("svg", "image/svg+xml");
        put ("swf", "application/x-shockwave-flash");
        put ("tar", "application/x-tar");
        put ("tif", "image/tiff");
        put ("tiff", "image/tiff");
        put ("ts", "video/mp2t");
        put ("ttf", "font/ttf");
        put ("txt", "text/plain");
        put ("vsd", "application/vnd.visio");
        put ("wav", "audio/wav");
        put ("weba", "audio/webm");
        put ("webm", "video/webm");
        put ("webp", "image/webp");
        put ("woff", "font/woff");
        put ("woff2", "font/woff2");
        put ("xhtml", "application/xhtml+xml");
        put ("xls", "application/vnd.ms-excel");
        put ("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        put ("xml", "application/xml"); // or "text/xml"; idk
        put ("xul", "application/vnd.mozilla.xul+xml");
        put ("zip", "application/zip");
        put ("3gp", "video/3gpp"); // or "audio/3gpp"; idk
        put ("3g2", "video/3gpp2"); // or "audio/3gpp2"; idk
        put ("7z", "application/x-7z-compressed");
    }};
    
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Complete_list_of_MIME_types
    private String getMimeFromExtension (String extension) {
        return mimeExtenstionMap.getOrDefault (extension, "application/octet-stream");
    }
}
