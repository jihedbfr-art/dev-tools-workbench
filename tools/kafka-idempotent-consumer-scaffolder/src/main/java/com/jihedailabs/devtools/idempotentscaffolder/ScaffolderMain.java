package com.jihedailabs.devtools.idempotentscaffolder;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScaffolderMain {

    public static void main(String[] args) {
        // The report uses status marks, and an engine or a library may answer in the JVM's locale.
        // On a console whose default charset is not UTF-8 - the Windows default - both arrive as
        // question marks, which reads as a broken tool rather than a broken input.
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

        Map<String, String> flags;
        try {
            flags = parseFlags(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            usage();
            System.exit(2);
            return;
        }

        if (flags.containsKey("help") || args.length == 0) {
            usage();
            System.exit(args.length == 0 ? 2 : 0);
            return;
        }

        ScaffoldOptions options;
        try {
            options = new ScaffoldOptions(
                    require(flags, "package"),
                    flags.getOrDefault("class-name", "ProcessedEventRepository"),
                    flags.getOrDefault("table", "processed_events"),
                    flags.getOrDefault("event-id-column", "event_id"),
                    Dialect.parse(flags.getOrDefault("dialect", "postgres")),
                    flags.getOrDefault("migration-version", "V1"));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        List<GeneratedFile> generated = Scaffolder.generate(options);
        boolean dryRun = flags.containsKey("dry-run");
        boolean force = flags.containsKey("force");
        Path out = Path.of(flags.getOrDefault("out", "generated"));

        if (dryRun) {
            for (GeneratedFile file : generated) {
                System.out.println("--- " + out.resolve(file.path()) + " ---");
                System.out.println(file.content());
            }
            return;
        }

        try {
            for (GeneratedFile file : generated) {
                Path target = out.resolve(file.path());
                if (Files.exists(target) && !force) {
                    System.err.println("Refusing to overwrite " + target
                            + " — pass --force if that is what you want.");
                    System.exit(1);
                    return;
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content(), StandardCharsets.UTF_8);
                System.out.println("Wrote " + target);
            }
        } catch (IOException e) {
            System.err.println("Could not write generated files: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println();
        System.out.println("Next: annotate the listener that calls " + options.className()
                + ".markProcessed(...) with @Transactional, so the guard and the business work "
                + "commit together. Nothing else in the generated code can enforce that.");
    }

    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument '" + arg
                        + "' — every option is a --flag or --flag=value.");
            }
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            if (eq < 0) {
                flags.put(body, "");
            } else {
                flags.put(body.substring(0, eq), body.substring(eq + 1));
            }
        }
        return flags;
    }

    private static String require(Map<String, String> flags, String name) {
        String value = flags.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + name);
        }
        return value;
    }

    private static void usage() {
        System.err.println("""
                Usage: java -jar kafka-idempotent-consumer-scaffolder.jar --package=<java.package> [options]

                  --package=<pkg>            Required. Java package for the generated repository.
                  --class-name=<Name>        Default: ProcessedEventRepository
                  --table=<name>             Default: processed_events (lowercase, unquoted)
                  --event-id-column=<name>   Default: event_id
                  --dialect=postgres|h2      Default: postgres
                  --migration-version=<Vn>   Default: V1 (Flyway prefix)
                  --out=<dir>                Default: ./generated
                  --dry-run                  Print the files instead of writing them
                  --force                    Overwrite existing files
                """);
    }
}
