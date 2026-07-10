package com.interview.prep.platform.backend_core.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pulls the technology/mechanism terms out of a coarse topic's title + scope line
 * (fix-brief §1). These terms are the contract the depth pass must honour: every one
 * of them has to survive into some child card, otherwise the pass narrowed the unit
 * instead of splitting it.
 *
 * <p>"Java concurrency internals — ThreadPoolExecutor, CompletableFuture, locks"
 * → [ThreadPoolExecutor, CompletableFuture, locks]
 */
public final class ScopeTerms {

    private ScopeTerms() {}

    /** Segment separators: comma, ampersand, em/en dash, slash, "vs", "and". */
    private static final Pattern SEPARATORS =
            Pattern.compile("\\s*(?:,|&|—|–|/|\\bvs\\.?\\b|\\band\\b)\\s*", Pattern.CASE_INSENSITIVE);

    /** CamelCase / PascalCase identifiers: ThreadPoolExecutor, parallelStream */
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[a-zA-Z]+[A-Z]\\w*\\b");
    /** technology acronyms: JVM, ZGC, CQRS, N+1 */
    private static final Pattern ACRONYM = Pattern.compile("\\b[A-Z]{2,}\\d*\\b");
    /** flags and dotted/annotated identifiers: -XX:…, @Cacheable, spring.datasource.url */
    private static final Pattern SYMBOL = Pattern.compile("[-@]\\w|\\w\\.\\w");

    /**
     * Head phrases that describe the shape of a unit rather than its content. A segment
     * made only of these carries no scope and would match everything.
     */
    private static final Set<String> FILLER = Set.of(
            "internals", "internal", "patterns", "pattern", "basics", "fundamentals",
            "overview", "deep dive", "deep-dive", "introduction", "intro", "essentials",
            "concepts", "core", "advanced", "topics", "review", "practice", "applied",
            "and", "the", "a", "an", "in", "of", "at", "scale", "under", "load",
            "when", "why", "how", "each", "wins", "beyond");

    private static final int MAX_PLAIN_WORDS = 2;

    /** Every concrete term named in the title or scope, in source order, case-insensitively unique. */
    public static List<String> extract(String title, String scope) {
        return dedupe(terms(title), terms(scope));
    }

    /**
     * The subset the depth pass is CONTRACTED to cover. A title that doesn't enumerate —
     * one segment, like "kafka-internals" or "Two Sum" — names the unit rather than listing
     * its parts, so it obliges nothing; only the scope line does.
     */
    public static List<String> contract(String title, String scope) {
        boolean titleEnumerates = title != null && SEPARATORS.split(title).length > 1;
        return dedupe(titleEnumerates ? terms(title) : List.of(), terms(scope));
    }

    private static List<String> terms(String source) {
        if (source == null || source.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String segment : SEPARATORS.split(source)) {
            String term = clean(segment);
            if (term != null) out.add(term);
        }
        return out;
    }

    @SafeVarargs
    private static List<String> dedupe(List<String>... groups) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (List<String> group : groups) {
            for (String term : group) {
                if (seen.add(term.toLowerCase(Locale.ROOT))) out.add(term);
            }
        }
        return out;
    }

    /**
     * A segment survives if it names something concrete: an identifier, acronym or symbol,
     * or is short enough ("locks", "Optional pitfalls") to be a leaf rather than a heading.
     * "Java concurrency internals" is dropped — it is the unit's heading, not its scope.
     */
    private static String clean(String raw) {
        String s = raw.strip().replaceAll("^[^\\w@-]+|[^\\w)]+$", "").strip();
        if (s.length() < 2) return null;

        String lower = s.toLowerCase(Locale.ROOT);
        if (FILLER.contains(lower)) return null;

        boolean concrete = IDENTIFIER.matcher(s).find()
                || ACRONYM.matcher(s).find()
                || SYMBOL.matcher(s).find();
        if (concrete) return s;

        String[] words = s.split("\\s+");
        if (words.length > MAX_PLAIN_WORDS) return null;
        // "deep dive", "under load" — filler even as a short phrase
        for (String w : words) {
            if (!FILLER.contains(w.toLowerCase(Locale.ROOT))) return s;
        }
        return null;
    }

    /**
     * A term is covered when it appears, case-insensitively, in the child's text at a word
     * edge — either starting a word or ending one. "lock" is covered by "ReentrantLock"
     * (suffix) but NOT by "LinkedBlockingQueue", where it hides inside "blocking".
     * Plural drift is tolerated: "locks" ↔ "lock", "generics" ↔ "generic".
     */
    public static boolean covers(String term, String text) {
        if (term == null || text == null) return false;
        String haystack = text.toLowerCase(Locale.ROOT);
        String needle = term.toLowerCase(Locale.ROOT);
        if (needle.isBlank()) return false;

        if (atWordEdge(haystack, needle)) return true;
        if (needle.endsWith("s") && needle.length() > 3) {
            return atWordEdge(haystack, needle.substring(0, needle.length() - 1));
        }
        return atWordEdge(haystack, needle + "s");
    }

    private static boolean atWordEdge(String haystack, String needle) {
        char first = needle.charAt(0);
        char last = needle.charAt(needle.length() - 1);
        // terms bounded by symbols (-XX, @Cacheable, N+1) anchor themselves
        if (!Character.isLetterOrDigit(first) || !Character.isLetterOrDigit(last)) {
            return haystack.contains(needle);
        }
        String quoted = Pattern.quote(needle);
        return Pattern.compile("\\b" + quoted + "|" + quoted + "\\b").matcher(haystack).find();
    }

    /** Terms from {@code terms} that no candidate text covers. */
    public static List<String> uncovered(List<String> terms, List<String> candidateTexts) {
        List<String> missing = new ArrayList<>();
        for (String term : terms) {
            boolean found = candidateTexts.stream().anyMatch(text -> covers(term, text));
            if (!found) missing.add(term);
        }
        return missing;
    }
}
