import enums.Status;
import enums.StatusType;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Response {
    
    private int statusCode;
    private String statusMessage;
    private Map<String, String> headers;
    private String body;
    private byte[] binaryBody;
    
    /**
     * Returns a new Webserver.Response object with empty body and default [Status.NotImplemented_501] status
     */
    public Response () {
        setStatus (Status.NotImplemented_501);
        headers = new HashMap<String, String> ();
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
                
                setStatus (Status.NotImplemented_501);
                System.out.println ("Path bodies not yet implemented");
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
        output.append ("\r\n");
        
        // Body
        if (body != null) {
            // Text body
            output.append (body);
        }
        
        return output.toString ().strip ();
    }

//	// https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types/Complete_list_of_MIME_types
//	private String getMimeFromExtension(String extension) {
//		return switch(extension) {
//			case ".aac" -> "audio/aac";
//			case ".abw" -> "application/x-abiword";
//			case ".arc" -> "application/x-freearc";
//			case ".avi" -> "video/x-msvideo";
//			case ".azw" -> "application/vnd.amazon.ebook";
//			case ".bin" -> "application/octet-stream";
//			case ".bmp" -> "image/bmp";
//			case ".bz" -> "application/x-bzip";
//			case ".bz2" -> "application/x-bzip2";
//			case ".csh" -> "application/x-csh";
//			case ".css" -> "text/css";
//			case ".csv" -> "text/csv";
//			case ".doc" -> "application/msword";
//			case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
//			case ".eot" -> "application/vnd.ms-fontobject";
//			case ".epub" -> "application/epub+zip";
//			case ".gz" -> "application/gzip";
//			case ".gif" -> "image/gif";
//			case ".htm" -> "text/html";
//			case ".html" -> "text/html";
//			case ".ico" -> "image/vnd.microsoft.icon";
//			case ".ics" -> "text/calendar";
//			case ".jar" -> "application/java-archive";
//			case ".jpg" -> "image/jpeg";
//			case ".jpeg" -> "image/jpeg";
//			case ".js" -> "text/javascript";
//			case ".json" -> "application/json";
//			case ".jsonld" -> "application/ld+json";
//			case ".mid" -> "audio/midi audio/x-midi";
//			case ".midi" -> "audio/midi audio/x-midi";
//			case ".mjs" -> "text/javascript";
//			case ".mp3" -> "audio/mpeg";
//			case ".mpeg" -> "audio/mpeg";
//			case ".mpkg" -> "application/vnd.apple.installer+xml";
//			case ".odp" -> "application/vnd.oasis.opendocument.presentation";
//			case ".ods" -> "application/vnd.oasis.opendocument.spreadsheet";
//			case ".odt" -> "application/vnd.oasis.opendocument.text";
//			case ".oga" -> "audio/ogg";
//			case ".ogv" -> "video/ogg";
//			case ".ogx" -> "application/ogg";
//			case ".opus" -> "audio/opus";
//			case ".otf" -> "font/otf";
//			case ".png" -> "image/png";
//			case ".pdf" -> "application/pdf";
//			case ".php" -> "application/php";
//			case ".ppt" -> "application/vnd.ms-powerpoint";
//			case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
//			case ".rar" -> "application/x-rar-compressed";
//			case ".rtf" -> "application/rtf";
//			case ".sh" -> "application/x-sh";
//			case ".svg" -> "image/svg+xml";
//			case ".swf" -> "application/x-shockwave-flash";
//			case ".tar" -> "application/x-tar";
//			case ".tif" -> "image/tiff";
//			case ".tiff" -> "image/tiff";
//			case ".ts" -> "video/mp2t";
//			case ".ttf" -> "font/ttf";
//			case ".txt" -> "text/plain";
//			case ".vsd" -> "application/vnd.visio";
//			case ".wav" -> "audio/wav";
//			case ".weba" -> "audio/webm";
//			case ".webm" -> "video/webm";
//			case ".webp" -> "image/webp";
//			case ".woff" -> "font/woff";
//			case ".woff2" -> "font/woff2";
//			case ".xhtml" -> "application/xhtml+xml";
//			case ".xls" -> "application/vnd.ms-excel";
//			case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
//			case ".xml" -> "application/xml"; // or "text/xml"; idk
//			case ".xul" -> "application/vnd.mozilla.xul+xml";
//			case ".zip" -> "application/zip";
//			case ".3gp" -> "video/3gpp"; // or "audio/3gpp"; idk
//			case ".3g2" -> "video/3gpp2"; // or "audio/3gpp2"; idk
//			case ".7z" -> "application/x-7z-compressed";
//			default -> "application/octet-stream";
//		};
//	}
}
