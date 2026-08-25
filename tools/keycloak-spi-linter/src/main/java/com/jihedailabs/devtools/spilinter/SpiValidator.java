package com.jihedailabs.devtools.spilinter;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpiValidator {

    private final ClassLoader classLoader;

    public SpiValidator(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public boolean validate(Path basePath) throws IOException {
        Path servicesDir;
        if (Files.isDirectory(basePath)) {
            servicesDir = basePath.resolve("META-INF/services");
        } else if (basePath.toString().endsWith(".jar")) {
            // For simplicity in this tool, we assume the user provides target/classes.
            // If they provide a jar, we'd need to use a ZipFileSystem.
            FileSystem fs = FileSystems.newFileSystem(basePath, (ClassLoader) null);
            servicesDir = fs.getPath("META-INF/services");
        } else {
            System.err.println("Unsupported target path type. Please provide a directory (e.g. target/classes) or a .jar file.");
            return false;
        }

        if (!Files.exists(servicesDir) || !Files.isDirectory(servicesDir)) {
            System.out.println("No META-INF/services directory found. Nothing to validate.");
            return true; // No SPIs is a valid state
        }

        boolean allValid = true;

        try (Stream<Path> paths = Files.walk(servicesDir, 1)) {
            List<Path> serviceFiles = paths.filter(Files::isRegularFile).collect(Collectors.toList());
            
            for (Path serviceFile : serviceFiles) {
                String spiInterfaceName = serviceFile.getFileName().toString();
                if (spiInterfaceName.contains(".")) {
                    boolean result = validateSpiFile(spiInterfaceName, serviceFile);
                    if (!result) {
                        allValid = false;
                    }
                }
            }
        }

        return allValid;
    }

    private boolean validateSpiFile(String spiInterfaceName, Path serviceFile) {
        boolean fileValid = true;
        System.out.println("Validating SPI: " + spiInterfaceName);

        List<String> classNames;
        try {
            classNames = Files.readAllLines(serviceFile).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("  ❌ Error reading file: " + e.getMessage());
            return false;
        }

        if (classNames.isEmpty()) {
            System.err.println("  ❌ File is empty.");
            return false;
        }

        Class<?> spiInterfaceClass = null;
        try {
            spiInterfaceClass = Class.forName(spiInterfaceName, false, classLoader);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            System.err.println("  ⚠️ Could not load SPI interface '" + spiInterfaceName + "'. Make sure Keycloak dependencies are on the classpath.");
            // We don't fail immediately, we try to load the impl classes anyway
        }

        Map<String, String> idToClassName = new HashMap<>();

        for (String className : classNames) {
            try {
                // Rule 1: Class exists
                Class<?> clazz = Class.forName(className, false, classLoader);
                
                // Rule 2: Implements interface
                if (spiInterfaceClass != null && !spiInterfaceClass.isAssignableFrom(clazz)) {
                    System.err.println("  ❌ Class '" + className + "' does not implement interface '" + spiInterfaceName + "'.");
                    fileValid = false;
                }

                // Rule 3: Has public no-arg constructor
                Constructor<?> constructor;
                try {
                    constructor = clazz.getConstructor();
                } catch (NoSuchMethodException e) {
                    System.err.println("  ❌ Class '" + className + "' is missing a public no-arg constructor.");
                    fileValid = false;
                    continue; // Can't instantiate without it
                }

                // Rule 4: getId() does not return null or empty
                Object instance = constructor.newInstance();
                Method getIdMethod;
                try {
                    getIdMethod = clazz.getMethod("getId");
                } catch (NoSuchMethodException e) {
                    System.err.println("  ❌ Class '" + className + "' does not have a public getId() method.");
                    fileValid = false;
                    continue;
                }

                Object idObj = getIdMethod.invoke(instance);
                if (idObj == null || !(idObj instanceof String) || ((String) idObj).trim().isEmpty()) {
                    System.err.println("  ❌ Class '" + className + "' getId() returned null or empty string.");
                    fileValid = false;
                    continue;
                }
                
                String id = (String) idObj;

                // Rule 5: No duplicate ID for the same SPI
                if (idToClassName.containsKey(id)) {
                    System.err.println("  ❌ Duplicate ID found for SPI '" + spiInterfaceName + "': ID '" + id + "' is used by both '" + idToClassName.get(id) + "' and '" + className + "'.");
                    fileValid = false;
                } else {
                    idToClassName.put(id, className);
                }

            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                System.err.println("  ❌ Class '" + className + "' could not be loaded: " + e.toString() + ". Are dependencies missing?");
                fileValid = false;
            } catch (Exception e) {
                System.err.println("  ❌ Error instantiating class '" + className + "': " + e.getMessage());
                fileValid = false;
            }
        }

        if (fileValid) {
            System.out.println("  ✅ OK (" + classNames.size() + " providers)");
        }
        return fileValid;
    }
}
