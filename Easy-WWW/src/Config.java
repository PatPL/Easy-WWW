import java.io.*;
import java.util.HashMap;
import java.util.Map;

enum ConfigEntry {
    address ("address", "127.0.0.1",
        "// Address used by server to listen to incoming requests" + System.lineSeparator () +
            "//   127.0.0.1 - loopback, your device only" + System.lineSeparator () +
            "//   [Your local IP], e.g. 192.168.1.2 - listens to outside connections"
    ),
    port ("port", "8866",
        "// TCP port which the server will use" + System.lineSeparator () +
            "//   80 - HTTP"
    ),
    defaultRoot ("defaultRoot", "./",
        "// RELATIVE path to the default served directory. Valid examples:" + System.lineSeparator () +
            "//   defaultRoot = ./html" + System.lineSeparator () +
            "//   defaultRoot = html" + System.lineSeparator () +
            "//   defaultRoot = ./html/" + System.lineSeparator () +
            "//   defaultRoot = html/" + System.lineSeparator () +
            "//   defaultRoot = ./" + System.lineSeparator () +
            "//   defaultRoot = ." + System.lineSeparator () +
            "//   defaultRoot = ../html" + System.lineSeparator () +
            "//   defaultRoot = ../../../my_website/html"
    ),
    subdomainRoot ("subdomainRoot", ":",
        "// Optional additional roots tied to subdomains." + System.lineSeparator () +
            "// Multiple entries are allowed." + System.lineSeparator () +
            "// [hostname] - hostname which your DNS resolves to the address set in [address]" + System.lineSeparator () +
            "// for example, [hostname] = localhost, for [address] = 127.0.0.1" + System.lineSeparator () +
            "// Site has to be accessed using [hostname] for this matching to work. Directly, using IP won't work." + System.lineSeparator () +
            "// Examples (where [hostname] = localhost):" + System.lineSeparator () +
            "//   subdomainRoot:images = ./img - images.localhost uses ./img root path, regardless of [defaultRoot]" + System.lineSeparator () +
            "//   subdomainRoot:other = ../../my_other_website/html - other.localhost uses ../../my_other_website/html root path" + System.lineSeparator () +
            "//   subdomainRoot:images.other = ../../my_other_website/img - you can combine the subdomains" + System.lineSeparator () +
            "//   subdomainRoot:more:colons:are:allowed = ./examples - The config handles additional colons (more:colons:are:allowed.localhost)"
    ),
    redirectToMatchedSubdomain ("redirectToMatchedSubdomain", "true",
        "// Should the server redirect the browser precisely to the subdomain it chose to match, if there's a mismatch" + System.lineSeparator () +
            "// Example 1 (no subdomains set): " + System.lineSeparator () +
            "//   [hostname] (no redirection)" + System.lineSeparator () +
            "//   www.[hostname] -> [hostname]" + System.lineSeparator () +
            "//   aaa.bbb.[hostname] -> [hostname]" + System.lineSeparator () +
            "// Example 2 (all examples from [subdomainRoot] are set):" + System.lineSeparator () +
            "//   [hostname] (no redirection)" + System.lineSeparator () +
            "//   images.[hostname] (no redirection)" + System.lineSeparator () +
            "//   images.aaa.bbb.[hostname] -> [hostname]" + System.lineSeparator () +
            "//   aaa.bbb.images.[hostname] -> images.[hostname]" + System.lineSeparator () +
            "//   images.other.[hostname] (no redirection)" + System.lineSeparator () +
            "//   other.images.[hostname] -> images.[hostname]" + System.lineSeparator () +
            "//   images.aaa.bbb.other.[hostname] -> other.[hostname]"
    ),
    hostname ("hostname", "localhost",
        "// Valid domain name, that resolves to [address]" + System.lineSeparator () +
            "// Can be ignored, if not specified otherwise below:" + System.lineSeparator () +
            "// * It has to be set correctly, if [redirectToMatchedSubdomain] = true" + System.lineSeparator () +
            "// * It has to be set correctly, if any [subdomainRoot] is set"
    ),
    openInBrowser ("openInBrowser", "true",
        "// Should the program attempt to open the website root in default browser on server start"
    );
    
    public final String key;
    public final String defaultValue;
    public final String comment;
    
    ConfigEntry (String key, String defaultValue, String comment) {
        this.key = key.replaceAll (":", "");
        this.defaultValue = defaultValue;
        this.comment = comment;
    }
    
    public static HashMap<String, String> defaultHashMap () {
        HashMap<String, String> output = new HashMap<String, String> ();
        
        for (ConfigEntry i : ConfigEntry.values ()) {
            output.put (i.key, i.defaultValue);
        }
        
        return output;
    }
    
}

public class Config {
    private static final String configPath = "Easy-WWW.cfg";
    private static final Map<String, String> defaultConfig = ConfigEntry.defaultHashMap ();
    private static final Map<String, String> config = new HashMap<String, String> ();
    
    private static boolean configLoaded = false;
    
    public static void setBoolean (ConfigEntry key, boolean value) {
        setString (key, Boolean.toString (value));
    }
    
    public static boolean getBoolean (ConfigEntry key) {
        try {
            return Boolean.parseBoolean (getString (key));
        } catch (NumberFormatException e) {
            e.printStackTrace ();
            return false;
        }
    }
    
    public static void setInteger (ConfigEntry key, int value) {
        setString (key, Integer.toString (value));
    }
    
    public static int getInteger (ConfigEntry key) {
        try {
            return Integer.parseInt (getString (key));
        } catch (NumberFormatException e) {
            e.printStackTrace ();
            return -1;
        }
    }
    
    public static void setString (ConfigEntry key, String value) {
        config.put (key.key, value);
        saveConfig ();
    }
    
    public static String getString (ConfigEntry key) {
        loadConfig ();
        return config.get (key.key);
    }
    
    public static void setMap (ConfigEntry key, Map<String, String> value) {
        StringBuilder serializedMap = new StringBuilder ();
        serializedMap.append (":");
        
        for (Map.Entry<String, String> i : value.entrySet ()) {
            serializedMap.append (i.getKey ().replaceAll (":", "%3A"));
            serializedMap.append (":");
            serializedMap.append (i.getValue ().replaceAll (":", "%3A"));
            serializedMap.append (":");
        }
        
        setString (key, serializedMap.toString ());
    }
    
    public static Map<String, String> getMap (ConfigEntry key) {
        String[] parts = getString (key).split (":");
        // Serialized maps start and end with ":"
        // split() includes an empty string as the array's first element
        if (parts.length % 2 != 1) {
            System.out.println ("[ERROR] bad map");
        }
        
        HashMap<String, String> output = new HashMap<String, String> ();
        
        for (int i = 1; i < parts.length; i += 2) {
            output.put (
                parts[i].replaceAll ("%3A", ":"),
                parts[i + 1].replaceAll ("%3A", ":")
            );
        }
        
        return output;
    }
    
    private static void loadConfig () {
        if (configLoaded) {
            return;
        }
        
        config.clear ();
        config.putAll (defaultConfig);
        
        File file = new File (configPath);
        if (!file.exists ()) {
            saveConfig ();
            return;
        }
        
        FileReader reader = null;
        try {
            reader = new FileReader (file);
        } catch (FileNotFoundException e) {
            e.printStackTrace ();
            return;
        }
        
        BufferedReader bufferedReader = new BufferedReader (reader);
        
        String line;
        while (true) {
            try {
                line = bufferedReader.readLine ();
            } catch (IOException e) {
                e.printStackTrace ();
                break;
            }
            
            if (line == null) {
                break;
            }
            
            line = line.strip ();
            
            if (line.length () == 0) {
                continue;
            }
            
            if (line.startsWith ("//") || line.startsWith ("#")) {
                continue;
            }
            
            String[] parts = line.split ("=", 2);
            
            if (parts.length != 2) {
                System.out.printf ("Invalid config entry (no value): %s\n", line);
                continue;
            }
            
            String key = parts[0].trim ();
            String value = parts[1].trim ();
            
            if (key.length () == 0 || value.length () == 0) {
                System.out.printf ("Invalid config entry (empty key/value): %s\n", line);
                continue;
            }
            
            if (key.contains (":")) {
                // It's a map
                String[] keyParts = key.split (":", 2);
                String mainKey = keyParts[0].trim ();
                String mapKey = keyParts[1].trim ();
                
                config.put (mainKey, String.format (
                    "%s%s:%s:",
                    config.getOrDefault (mainKey, ":"),
                    mapKey.replaceAll (":", "%3A"),
                    value.replaceAll (":", "%3A")
                ));
            } else {
                // It's a regular value
                config.put (key, value);
            }
            
        }
        
        configLoaded = true;
        saveConfig ();
    }
    
    private static void saveConfig () {
        File file = new File (configPath);
        FileWriter writer = null;
        try {
            writer = new FileWriter (file);
        } catch (IOException e) {
            e.printStackTrace ();
            return;
        }
        
        for (ConfigEntry key : ConfigEntry.values ()) {
            try {
                String value = config.get (key.key);
                
                writer.write (key.comment);
                writer.write (System.lineSeparator ());
                if (value.startsWith (":")) {
                    // It's a serialized map
                    Map<String, String> parsed = getMap (key);
                    for (var i : parsed.entrySet ()) {
                        writer.write (key.key);
                        writer.write (":");
                        writer.write (i.getKey ());
                        writer.write (" = ");
                        writer.write (i.getValue ());
                        writer.write (System.lineSeparator ());
                    }
                } else {
                    // It's a regular value
                    writer.write (key.key);
                    writer.write (" = ");
                    writer.write (value);
                    writer.write (System.lineSeparator ());
                }
                writer.write (System.lineSeparator ());
                
            } catch (IOException e) {
                e.printStackTrace ();
            }
        }
        
        try {
            writer.flush ();
            writer.close ();
        } catch (IOException e) {
            e.printStackTrace ();
        }
        
    }
    
}
