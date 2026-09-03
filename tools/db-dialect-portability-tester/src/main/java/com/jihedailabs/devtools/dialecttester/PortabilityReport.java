package com.jihedailabs.devtools.dialecttester;

import java.util.List;

/** The outcome of one comparison run, and how it should be printed and exited on. */
public record PortabilityReport(String leftName,
                                String rightName,
                                int statementCount,
                                List<Divergence> divergences) {

    public List<Divergence> failures() {
        return divergences.stream().filter(Divergence::isFailure).toList();
    }

    public List<Divergence> warnings() {
        return divergences.stream().filter(d -> !d.isFailure()).toList();
    }

    /** True when nothing that can change application behaviour differed. */
    public boolean isPortable() {
        return failures().isEmpty();
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("Compared ").append(statementCount).append(" statement(s): ")
                .append(leftName).append(" vs ").append(rightName).append('\n');

        if (divergences.isEmpty()) {
            out.append("\n  ✅ No divergence. Both engines accepted every statement and agreed on every result.\n");
            return out.toString();
        }

        List<Divergence> failures = failures();
        if (!failures.isEmpty()) {
            out.append('\n');
            for (Divergence d : failures) {
                out.append("  ❌ [").append(d.kind()).append("] statement ").append(d.statementIndex() + 1)
                        .append(" — ").append(d.statement()).append('\n')
                        .append("       ").append(d.detail()).append('\n');
            }
        }

        List<Divergence> warnings = warnings();
        if (!warnings.isEmpty()) {
            out.append('\n');
            for (Divergence d : warnings) {
                out.append("  ⚠️  [").append(d.kind()).append("] statement ").append(d.statementIndex() + 1)
                        .append(" — ").append(d.statement()).append('\n')
                        .append("       ").append(d.detail()).append('\n');
            }
        }

        out.append('\n').append(failures.size()).append(" divergence(s) that change behaviour, ")
                .append(warnings.size()).append(" note(s) that do not.\n");
        return out.toString();
    }
}
