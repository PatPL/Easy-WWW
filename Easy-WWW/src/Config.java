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
    root ("root", "./",
        "// RELATIVE path to the served directory. Valid examples:" + System.lineSeparator () +
            "//   root = ./html" + System.lineSeparator () +
            "//   root = html" + System.lineSeparator () +
            "//   root = ./html/" + System.lineSeparator () +
            "//   root = html/" + System.lineSeparator () +
            "//   root = /." + System.lineSeparator () +
            "//   root = ." + System.lineSeparator () +
            "//   This value DOESN'T support absolute paths." + System.lineSeparator () +
            "//   Following example will point to the current directory" + System.lineSeparator () +
            "//   just like the two previous examples" + System.lineSeparator () +
            "//   root = /"
    ),
    openInBrowser ("openInBrowser", "true",
        "// Should the program attempt to open the website root in default browser on server start"
    );
    
    public final String key;
    public final String defaultValue;
    public final String comment;
    
    ConfigEntry (String key, String defaultValue, String comment) {
        this.key = key;
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
            
            config.put (key, value);
            
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
                writer.write (key.comment);
                writer.write (System.lineSeparator ());
                writer.write (key.key);
                writer.write (" = ");
                writer.write (config.get (key.key));
                writer.write (System.lineSeparator ());
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
