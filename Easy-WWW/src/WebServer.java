import enums.Status;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.SortedMap;
import java.util.TreeMap;

public class WebServer {
    
    private final ServerSocket server;
    private final Thread listenerThread;
    private boolean running = false;
    private String websiteRoot = null;
    
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
        
        if (websiteRoot != null) {
            res.websiteRoot = websiteRoot;
            res.setBody (req.URI, Response.BodyType.Path);
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
        
        s.websiteRoot = Config.getString (ConfigEntry.root);
        
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
