package com.jihedailabs.devtools.sagalinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LinterMain {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar bpmn-saga-linter.jar <file-or-directory> [more...]");
            System.err.println("Directories are scanned recursively for *.bpmn files.");
            System.exit(2);
        }

        List<File> files = new ArrayList<>();
        for (String arg : args) {
            files.addAll(resolveBpmnFiles(new File(arg)));
        }

        if (files.isEmpty()) {
            System.err.println("No .bpmn files found under the given path(s).");
            System.exit(2);
        }

        boolean anyHardFailure = false;
        for (File file : files) {
            try {
                LintResult result = SagaLinter.lint(file);
                printResult(result);
                anyHardFailure |= result.hasHardFailure();
            } catch (Exception e) {
                System.err.println("Failed to parse " + file + ": " + e.getMessage());
                anyHardFailure = true;
            }
        }

        System.exit(anyHardFailure ? 1 : 0);
    }

    private static List<File> resolveBpmnFiles(File path) {
        if (path.isFile()) {
            return List.of(path);
        }
        if (path.isDirectory()) {
            try (Stream<Path> walk = Files.walk(path.toPath())) {
                return walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".bpmn"))
                        .map(Path::toFile)
                        .toList();
            } catch (IOException e) {
                System.err.println("Could not scan directory " + path + ": " + e.getMessage());
                return List.of();
            }
        }
        System.err.println("Path does not exist: " + path);
        return List.of();
    }

    private static void printResult(LintResult result) {
        System.out.println("Linting: " + result.fileName());

        if (!result.hasCompensationNode()) {
            System.out.println("  ℹ️  No compensation-like task found (name/id containing "
                    + "\"compensate\" or \"rollback\") — skipping timer and gateway checks.");
            return;
        }

        if (result.timerFindings().isEmpty()) {
            System.out.println("  ℹ️  No boundary timer events in this process.");
        }
        for (LintResult.TimerFinding finding : result.timerFindings()) {
            if (finding.isViolation()) {
                System.out.println("  ❌ Boundary timer '" + finding.timerName() + "' (" + finding.timerId()
                        + ") cannot reach any compensation task — a timeout here would skip the "
                        + "rollback the rest of this process relies on.");
            } else {
                System.out.println("  ✅ Boundary timer '" + finding.timerName() + "' reaches compensation.");
            }
        }

        for (LintResult.GatewayFinding finding : result.gatewayFindings()) {
            if (!finding.anyBranchReachesCompensation()) {
                System.out.println("  ⚠️  Gateway '" + finding.gatewayName() + "' (" + finding.gatewayId()
                        + ") has no branch reaching compensation — informational only, verify this "
                        + "isn't a rejection path that forgot to route to cleanup.");
            }
        }
    }
}
