package com.jihedailabs.devtools.outboxverifier;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CliMain {

    public static void main(String[] args) {
        // The report uses status marks, and an engine or a library may answer in the JVM's locale.
        // On a console whose default charset is not UTF-8 - the Windows default - both arrive as
        // question marks, which reads as a broken tool rather than a broken input.
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

        Map<String, String> options = parseOptions(args);

        String jdbcUrl = require(options, "jdbc-url");
        String table = require(options, "table");
        String idColumn = options.getOrDefault("id-column", "id");
        String createdAtColumn = options.getOrDefault("created-at-column", "created_at");
        String completedAtColumn = options.getOrDefault("completed-at-column", "published_at");
        String failedAtColumn = options.get("failed-at-column");
        String retryCountColumn = options.get("retry-count-column");

        Thresholds defaults = Thresholds.defaults();
        Thresholds thresholds = new Thresholds(
                Duration.ofMinutes(Long.parseLong(options.getOrDefault("stagnation-minutes",
                        String.valueOf(defaults.stagnationThreshold().toMinutes())))),
                Duration.ofHours(Long.parseLong(options.getOrDefault("bloat-hours",
                        String.valueOf(defaults.bloatThreshold().toHours())))),
                Integer.parseInt(options.getOrDefault("retry-threshold",
                        String.valueOf(defaults.retryLoopThreshold())))
        );

        OutboxColumns columns = new OutboxColumns(idColumn, createdAtColumn, completedAtColumn, failedAtColumn, retryCountColumn);

        String user = options.get("user");
        String password = options.get("password");

        try (Connection connection = (user != null)
                ? DriverManager.getConnection(jdbcUrl, user, password)
                : DriverManager.getConnection(jdbcUrl)) {

            OutboxAuditor auditor = new OutboxAuditor(connection, table, columns, thresholds);
            OutboxReport report = auditor.audit(Instant.now());
            printReport(report, thresholds);
            System.exit(report.isHealthy() ? 0 : 1);

        } catch (SQLException e) {
            System.err.println("Failed to connect or query the outbox table: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void printReport(OutboxReport report, Thresholds thresholds) {
        System.out.println("Outbox audit — " + report.totalRows() + " row(s)");
        System.out.println();

        printSignal("Stagnant (pending longer than " + thresholds.stagnationThreshold().toMinutes() + "m)",
                report.stagnantRowIds());
        printCount("Unpurged / bloated (completed longer than " + thresholds.bloatThreshold().toHours() + "h ago)",
                report.bloatedCount());
        printLatency(report);
        printCount("Failed", report.failedCount());
        printSignal("Retry loops (>= " + thresholds.retryLoopThreshold() + " attempts)", report.retryLoopRowIds());

        System.out.println();
        System.out.println(report.isHealthy() ? "✅ Outbox looks healthy." : "❌ Outbox has issues — see above.");
    }

    private static void printSignal(String label, List<String> ids) {
        if (ids.isEmpty()) {
            System.out.println("  ✅ " + label + ": none");
        } else {
            System.out.println("  ❌ " + label + ": " + ids.size() + " row(s) — " + ids);
        }
    }

    private static void printCount(String label, int count) {
        System.out.println("  " + (count == 0 ? "✅" : "ℹ️") + " " + label + ": " + count);
    }

    private static void printLatency(OutboxReport report) {
        if (report.latencyP50() == null) {
            System.out.println("  ℹ️  Publish latency: no completed rows yet");
            return;
        }
        System.out.println("  ℹ️  Publish latency — p50: " + report.latencyP50().toMillis()
                + "ms, p95: " + report.latencyP95().toMillis()
                + "ms, p99: " + report.latencyP99().toMillis() + "ms");
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                System.err.println("Ignoring unrecognized argument: " + arg);
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            options.put(parts[0], parts[1]);
        }
        return options;
    }

    private static String require(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            System.err.println("Missing required argument: --" + key);
            printUsage();
            System.exit(2);
        }
        return value;
    }

    private static void printUsage() {
        System.err.println("""
                Usage: java -jar kafka-outbox-verifier.jar --jdbc-url=<url> --table=<name> [options]

                Required:
                  --jdbc-url=<jdbc:postgresql://host/db>
                  --table=<outbox table name>

                Optional (defaults match bpmn-provisioning-patterns' portability_outbox shape):
                  --user=<db user>                     --password=<db password>
                  --id-column=id                        --created-at-column=created_at
                  --completed-at-column=published_at    --failed-at-column=<none>
                  --retry-count-column=<none>           --stagnation-minutes=5
                  --bloat-hours=24                      --retry-threshold=5
                """);
    }
}
