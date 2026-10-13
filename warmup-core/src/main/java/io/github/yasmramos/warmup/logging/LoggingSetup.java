package io.github.yasmramos.warmup.logging;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingSetup {

    public LoggingSetup() {
    }
    
    public static void install() {
        var root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            root.removeHandler(handler);
        }
        
        var console = new ConsoleHandler();
        console.setFormatter(new SLF4JStyleFormatter());
        try {
            console.setEncoding("UTF-8");
        } catch (UnsupportedEncodingException ex) {
            System.getLogger(LoggingSetup.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        console.setLevel(Level.ALL);
        
        FileHandler file = null;
        try {
            file = new FileHandler("./logs/test-tailwindfx-test%g.log");
            file.setFormatter(new SLF4JStyleFormatter());
            file.setEncoding("UTF-8");
            file.setLevel(Level.ALL);
        } catch (IOException ex) {
            System.getLogger(LoggingSetup.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        
        root.addHandler(console);
        root.addHandler(file);
        root.setLevel(Level.INFO);
    }
}

