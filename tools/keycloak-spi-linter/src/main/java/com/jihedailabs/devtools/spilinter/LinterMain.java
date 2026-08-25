package com.jihedailabs.devtools.spilinter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LinterMain {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar keycloak-spi-linter.jar <path-to-classes-or-jar> [additional-classpath-dirs...]");
            System.exit(1);
        }

        List<URL> urls = new ArrayList<>();
        for (String arg : args) {
            try {
                File file = new File(arg);
                if (file.exists()) {
                    urls.add(file.toURI().toURL());
                    if (file.isDirectory()) {
                        // Also add jars inside if it's a dependency dir
                        File[] jars = file.listFiles((d, name) -> name.endsWith(".jar"));
                        if (jars != null) {
                            for (File jar : jars) {
                                urls.add(jar.toURI().toURL());
                            }
                        }
                    }
                }
            } catch (MalformedURLException e) {
                System.err.println("Invalid path: " + arg);
            }
        }

        Path targetPath = Paths.get(args[0]);
        if (!Files.exists(targetPath)) {
            System.err.println("Error: target path does not exist: " + args[0]);
            System.exit(1);
        }

        try (URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[0]), LinterMain.class.getClassLoader())) {
            SpiValidator validator = new SpiValidator(classLoader);
            boolean success = validator.validate(targetPath);
            if (!success) {
                System.exit(1);
            } else {
                System.out.println("✅ All Keycloak SPI configurations are valid.");
                System.exit(0);
            }
        } catch (IOException e) {
            System.err.println("Failed to close classloader: " + e.getMessage());
            System.exit(1);
        }
    }
}
