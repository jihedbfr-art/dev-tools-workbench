package com.jihedailabs.devtools.dialecttester;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CliMain {

    public static void main(String[] args) {
        // The report carries status marks and, when an engine answers in the JVM's locale, accented
        // error text. On a console whose default charset is not UTF-8 — the Windows default — both
        // arrive as question marks, which reads as a broken tool rather than a broken query.
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

        if (args.length == 0 || flags.containsKey("help")) {
            usage();
            System.exit(args.length == 0 ? 2 : 0);
            return;
        }

        Path script = Path.of(require(flags, "script"));
        if (!Files.isRegularFile(script)) {
            System.err.println("No such SQL file: " + script);
            System.exit(2);
            return;
        }

        String postgresUrl = require(flags, "postgres-url");
        String user = flags.getOrDefault("user", "postgres");
        String password = flags.getOrDefault("password", "postgres");
        // A fresh in-memory database per run: the point is to replay the script from nothing, and
        // a reused H2 would carry the previous run's tables into this one.
        String h2Url = flags.getOrDefault("h2-url",
                "jdbc:h2:mem:portability-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");

        List<SqlScript.Statement> statements;
        try {
            statements = SqlScript.parse(script);
        } catch (Exception e) {
            System.err.println("Could not read " + script + ": " + e.getMessage());
            System.exit(2);
            return;
        }

        if (statements.isEmpty()) {
            System.err.println("No statements found in " + script);
            System.exit(2);
            return;
        }

        registerDrivers();

        try (DatabaseSession h2 = new DatabaseSession("H2",
                     DriverManager.getConnection(h2Url, "sa", ""));
             DatabaseSession postgres = new DatabaseSession("PostgreSQL",
                     DriverManager.getConnection(postgresUrl, user, password))) {

            PortabilityReport report =
                    new DialectComparator("H2", "PostgreSQL").compare(statements, h2, postgres);
            System.out.print(report.render());
            System.exit(report.isPortable() ? 0 : 1);

        } catch (SQLException e) {
            // Failing to connect is not a portability finding, and reporting it as one would be a
            // green build that proved nothing.
            System.err.println("Could not connect: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Loads both drivers by name instead of relying on JDBC service discovery.
     *
     * <p>{@code DriverManager} finds drivers through {@code META-INF/services/java.sql.Driver}, and
     * the assembly plugin's jar-with-dependencies descriptor overwrites that file rather than
     * merging the copies from each dependency — so the shaded jar reaches at most one driver, and
     * the run dies on "No suitable driver found" against a database that was there all along.
     * Naming the classes sidesteps the packaging entirely.
     */
    private static void registerDrivers() {
        for (String driver : List.of("org.h2.Driver", "org.postgresql.Driver")) {
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException e) {
                System.err.println("Driver missing from the classpath: " + driver);
                System.exit(2);
            }
        }
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
            System.err.println("Missing required option --" + name);
            usage();
            System.exit(2);
        }
        return value;
    }

    private static void usage() {
        System.err.println("""
                Usage: java -jar db-dialect-portability-tester.jar --script=<file.sql> --postgres-url=<jdbc-url> [options]

                  --script=<file>        Required. SQL file replayed against both engines, in order.
                  --postgres-url=<url>   Required. e.g. jdbc:postgresql://localhost:5432/app
                  --user=<name>          PostgreSQL user (default: postgres)
                  --password=<secret>    PostgreSQL password (default: postgres)
                  --h2-url=<url>         Override the in-memory H2 URL, e.g. to add a compatibility
                                         mode: "...;MODE=PostgreSQL". Comparing against H2 in
                                         PostgreSQL mode answers a different question from
                                         comparing against default H2 — both are worth running.

                Exit codes: 0 no behavioural divergence, 1 divergences found, 2 could not run.
                """);
    }
}
