import enums.Status;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;

public class WebServer {
    
    private final ServerSocket server;
    private final Thread listenerThread;
    private boolean running = false;
    private String defaultWebsiteRoot = null;
    
    public interface ServerHandler {
        /**
         * Interprets the request and builds an appropriate response.
         *
         * @param req Parsed HTTP request received from a client
         * @param res Webserver.Response object that will be sent back to the client
         * @return Whether the handler has fully handled the request. If true, [res] will be sent. If false, [handleRequest ()] will try to find next handler
         */
        boolean handle (Request req, Response res);
    }
    
    private final SortedMap<String, ServerHandler> handlers = new TreeMap<String, ServerHandler> ((String a, String b) -> {
        // https://docs.oracle.com/javase/7/docs/api/java/util/TreeMap.html
        // (...) but a sorted map performs all key comparisons using its compareTo (or compare) method,
        // so two keys that are deemed equal by this method are, from the standpoint of the sorted map, equal.
        //
        // Sort by string length descending first, then alphabetically descending in case of a tie.
        // Example:
        //   "/abcdef"
        //   "/xyz"
        //   "/abc"
        //   "/f"
        if (b.length () != a.length ()) {
            return b.length () - a.length ();
        } else {
            return b.compareTo (a);
        }
    });
    
    /**
     * Returns a server object. Hosts at 'localhost:[port]'
     *
     * @param port The port which will be scanned for incoming connections
     * @throws IOException Thrown when unable to open a socket. Is the [port] already in use?
     */
    public WebServer (String address, int port) throws IOException {
        // From docs of 'new ServerSocket (int, int)'
        // backlog – requested maximum length of the queue of incoming connections.
        final int backlogSize = 16;
        server = new ServerSocket (port, backlogSize, InetAddress.getByName (address));
        
        listenerThread = new Thread (this::listen);
    }
    
    /**
     * @return Server address. Format: "x.x.x.x:p"
     */
    public String getAddress () {
        return String.format ("%s:%s", server.getInetAddress ().getHostAddress (), server.getLocalPort ());
    }
    
    /**
     * Starts the server.
     */
    public void start () {
        running = true;
        listenerThread.start ();
    }
    
    /**
     * Attempts to stop the server
     *
     * @return Whether the server was successfully stopped
     */
    public boolean stop () {
        try {
            // Will cause 'server.accept ();' to interrupt, therefore stopping 'listen ()'
            server.close ();
            running = false;
            return true;
        } catch (IOException e) {
            System.out.printf ("Error at server.close ():\n%s\n", e);
            return false;
        }
    }
    
    /**
     * Adds a new handler
     *
     * @param URI     Requests with URI that start with [URI] may be handled by this handler
     * @param handler The handler
     * @return Whether the handler was successfully added
     */
    public boolean addHandler (String URI, ServerHandler handler) {
        // TreeMap is not thread safe
        if (running) {
            System.out.println ("Stop the server before editing handlers");
            return false;
        }
        
        if (handlers.containsKey (URI)) {
            return false;
        }
        
        handlers.put (URI, handler);
        return true;
    }
    
    /**
     * Attempts to remove a handler matched by [URI]
     *
     * @param URI URI of the handler meant to be removed
     * @return Whether the handler was succesfully removed
     */
    public boolean removeHandler (String URI) {
        // TreeMap is not thread safe
        if (running) {
            System.out.println ("Stop the server before editing handlers");
            return false;
        }
        
        if (!handlers.containsKey (URI)) {
            return false;
        }
        
        handlers.remove (URI);
        return true;
    }
    
    // Synchronous. Will lock, and run until 'server.close ()' is called
    private void listen () {
        while (true) {
            Socket client;
            try {
                client = server.accept ();
            } catch (IOException e) {
                // Most likely caused by 'stop ()' call
                // System.out.printf ("Error at server.accept ():\n%s\n", e);
                return;
            }
            
            new Thread (() -> {
                try {
                    handleRequest (client);
                } catch (IOException e) {
                    System.out.printf ("Error at handleRequest ():\n%s\n", e);
                }
            }).start ();
        }
    }
    
    // Parses incoming request, sends back a response and closes the connection
    private void handleRequest (Socket client) throws IOException {
        BufferedReader input = new BufferedReader (new InputStreamReader (client.getInputStream (), StandardCharsets.UTF_8));
        
        // Sometimes a client establishes connection before sending any data
        // Maximum wait time in seconds
        final double waitTime = 10;
        // Retries to read data after [d] seconds
        double waitInterval = 0.02;
        // Current wait time in seconds
        double waitedFor = 0;
        while (waitedFor <= waitTime && !input.ready ()) {
            try {
                Thread.sleep ((long) (waitInterval * 1000));
                waitedFor += waitInterval;
                waitInterval *= 1.5;
            } catch (InterruptedException e) {
                return;
            }
        }
        
        if (!input.ready ()) {
            // No data after [waitTime] seconds. Close the connection.
            client.close ();
            return;
        }
        
        StringBuilder data = new StringBuilder ();
        while (input.ready ()) {
            data.append (input.readLine ());
            data.append ("\n");
        }
        client.shutdownInput ();
        
        Request req;
        Response res = new Response ();
        try {
            req = new Request (data.toString ());
        } catch (Exception e) {
            System.out.printf ("Encountered an error while parsing a request:\n%s\n", e);
            
            res.setStatus (Status.BadRequest_400);
            res.setBody ("<h1>BAD REQUEST</h1>", Response.BodyType.HTML);
            client.getOutputStream ().write (res.toByteArray ());
            client.close ();
            return;
        }
        
        for (String URI : handlers.keySet ()) {
            if (req.URI.startsWith (URI)) {
                if (handlers.get (URI).handle (req, res)) {
                    break;
                }
            }
        }
        
        String host = req.headers.getOrDefault ("Host", "");
        String domain = Config.getString (ConfigEntry.hostname);
        if (host.equals (domain)) {
            // Before anything else, check if by any chance the browser is
            // already asking for the main domain
            if (defaultWebsiteRoot != null) {
                res.websiteRoot = defaultWebsiteRoot;
                res.setBody (req.URI, Response.BodyType.Path);
            }
        } else if (host.endsWith (Config.getString (ConfigEntry.hostname))) {
            // Site accessed by hostname. Continue matching.
            Map<String, String> subdomains = Config.getMap (ConfigEntry.subdomainRoot);
            
            int lastDotIndex = -1;
            List<Integer> dotIndices = new ArrayList<Integer> ();
            dotIndices.add (-1);
            
            while ((lastDotIndex = host.indexOf (".", lastDotIndex + 1)) >= 0) {
                if (lastDotIndex >= 0) {
                    dotIndices.add (lastDotIndex);
                }
            }
            
            int domainStartIndex = host.length () - domain.length ();
            String bestSubdomainMatch = "";
            String bestSubdomainMatchRoot = defaultWebsiteRoot;
            outerLoop:
            for (int i = 0; i < dotIndices.size (); ++i) {
                int startIndex = dotIndices.get (i) + 1;
                if (startIndex >= domainStartIndex) {
                    // substring now only contains the domain
                    break;
                }
                
                String subdomainCandidate = host.substring (startIndex, domainStartIndex);
                for (Map.Entry<String, String> subdomain : subdomains.entrySet ()) {
                    if (subdomainCandidate.equals (subdomain.getKey () + ".")) {
                        bestSubdomainMatch = subdomain.getKey () + ".";
                        bestSubdomainMatchRoot = subdomain.getValue ();
                        break outerLoop;
                    }
                }
            }
            
            if (
                !Config.getBoolean (ConfigEntry.redirectToMatchedSubdomain) ||
                    String.format ("%s%s", bestSubdomainMatch, domain).equals (host)
            ) {
                // Correct host
                if (bestSubdomainMatchRoot != null) {
                    res.websiteRoot = bestSubdomainMatchRoot;
                    res.setBody (req.URI, Response.BodyType.Path);
                }
            } else {
                // Incorrect host. Redirect the browser
                res.setHeader ("Location", String.format ("//%s%s%s", bestSubdomainMatch, domain, req.URI));
                res.setStatus (Status.SeeOther_303);
            }
            
        } else {
            // Site accessed in other way. No subdomain matching
            if (defaultWebsiteRoot != null) {
                res.websiteRoot = defaultWebsiteRoot;
                res.setBody (req.URI, Response.BodyType.Path);
            }
        }
        
        res.setHeader ("Requested-URI", req.URI);
        res.setHeader ("Server", "Easy-WWW");
//        System.out.printf (
//            // https://en.wikipedia.org/wiki/ANSI_escape_code
//            "|>\033[93m%s\n\033[91m<<<<<<<< INBOUND\033[0m\n%s\n\033[32m>>>>>>>> OUTBOUND\033[0m\n%s\n\033[96m****************\033[0m\n\n",
//            LocalDateTime.now ().format (DateTimeFormatter.ISO_LOCAL_DATE_TIME),
//            Utility.leftPad (req.toString (), "\033[91m| \033[0m"),
//            Utility.leftPad (res.toString (), "\033[32m| \033[0m")
//        );
        
        client.getOutputStream ().write (res.toByteArray ());
        client.shutdownOutput ();
        client.close ();
    }
    
    public static void main (String[] args) throws URISyntaxException, IOException {
        WebServer s;
        try {
            s = new WebServer (Config.getString (ConfigEntry.address), Config.getInteger (ConfigEntry.port));
        } catch (IOException e) {
            System.out.printf ("Couldn't start the server.\n%s\n", e);
            return;
        }
        
        s.defaultWebsiteRoot = Config.getString (ConfigEntry.defaultRoot);
        
        s.start ();
        System.out.printf ("Started server at %s\n", s.getAddress ());
        
        if (Config.getBoolean (ConfigEntry.openInBrowser) && Desktop.isDesktopSupported ()) {
            if (Desktop.getDesktop ().isSupported (Desktop.Action.BROWSE)) {
                Desktop.getDesktop ().browse (new URI (String.format ("http://%s", s.getAddress ())));
            } else {
                System.out.println ("'openInBrowser' is enabled, but your desktop doesn't support that");
            }
        }
        
    }
    
}
