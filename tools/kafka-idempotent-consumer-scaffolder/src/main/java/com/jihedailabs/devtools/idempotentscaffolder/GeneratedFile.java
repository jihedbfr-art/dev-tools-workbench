package com.jihedailabs.devtools.idempotentscaffolder;

/**
 * One file the generator produced: a path relative to the output directory, and its content.
 * Nothing is written to disk until {@link ScaffolderMain} decides to — which is what makes the
 * whole generator testable without a filesystem, and what makes {@code --dry-run} free.
 */
public record GeneratedFile(String path, String content) {
}
