package com.interview.prep.platform.backend_core.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mechanical validation of depth-pass output (content-pipeline-v2 §3).
 * Pure functions — free to run, deterministic, so a mid-size local model's
 * output can be rejected and regenerated per-topic instead of trusted blindly.
 */
public final class ContentValidator {

    private ContentValidator() {}

    /** CLI/JVM flag: -Xmx, --max-old-space-size, -XX:MaxGCPauseMillis */
    private static final Pattern FLAG = Pattern.compile("(^|[\\s(])-{1,2}[A-Za-z][\\w:+=.-]*");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern CODE_SPAN = Pattern.compile("<code>[^<]+</code>|`[^`]+`");
    /**
     * camelCase / PascalCase / dotted / snake_case / key=value / call-like identifiers:
     * ThreadPoolExecutor, enable.idempotence, read_committed, acks=all, poll()
     */
    private static final Pattern IDENTIFIER = Pattern.compile(
            "\\b[a-z]+[A-Z]\\w*"
                    + "|\\b[A-Z][a-z]+[A-Z]\\w*"
                    + "|\\b[\\w-]+\\.[\\w-]+(\\.[\\w-]+)*\\b"  // dotted, 2+ segments (dot must be followed by a word char)
                    + "|\\b[a-z]+_[a-z_]+\\b"
                    + "|\\b[\\w.-]+=[\\w.-]+"
                    + "|\\b[A-Za-z_]\\w*\\(\\)");
    /** Title-Case named problem/tool/pattern: "Two Sum", "Sliding Window", "Definitive Guide" */
    private static final Pattern TITLE_CASE_NAME = Pattern.compile("\\b[A-Z][a-z]+(?: [A-Z][a-z]+)+\\b");
    /** technology acronyms count as concrete nouns: ZGC, JVM, ISR, OOM, CQRS */
    private static final Pattern ACRONYM = Pattern.compile("\\b[A-Z]{2,}\\d*\\b");

    private static final Pattern VAGUE_CONCEPT = Pattern.compile(
            "\\b(learn|understand|explore|familiari[sz]e|get comfortable)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANGLE_SIGNAL = Pattern.compile(
            "\\?|\\b(trade-?offs?|vs\\.?|when|why)\\b", Pattern.CASE_INSENSITIVE);

    public static final int MIN_POINTS = 4;
    public static final int MAX_POINTS = 6;
    public static final int MIN_EST_MINUTES = 20;
    public static final int MAX_EST_MINUTES = 180;

    /** A point passes iff it carries at least one specificity token. */
    public static boolean pointIsSpecific(String point) {
        if (point == null || point.isBlank()) return false;
        return FLAG.matcher(point).find()
                || DIGIT.matcher(point).find()
                || CODE_SPAN.matcher(point).find()
                || IDENTIFIER.matcher(point).find()
                || TITLE_CASE_NAME.matcher(point).find()
                || ACRONYM.matcher(point).find();
    }

    public static boolean conceptOk(String concept) {
        return concept != null && concept.strip().length() >= 40
                && !VAGUE_CONCEPT.matcher(concept).find();
    }

    public static boolean angleOk(String angle) {
        return angle != null && !angle.isBlank() && ANGLE_SIGNAL.matcher(angle).find();
    }

    /**
     * Validates one depth-pass topic map ({concept, points[], angle, est_minutes}).
     * Returns failure reasons; empty list = pass.
     */
    public static List<String> validateDepthTopic(Map<String, Object> topic) {
        List<String> failures = new ArrayList<>();

        Object concept = topic.get("concept");
        if (!(concept instanceof String c) || !conceptOk(c)) {
            failures.add("concept missing, too short, or vague (learn/understand/explore…)");
        }

        Object rawPoints = topic.get("points");
        if (!(rawPoints instanceof List<?> points) || points.size() < MIN_POINTS || points.size() > MAX_POINTS) {
            failures.add("points must be a list of " + MIN_POINTS + "-" + MAX_POINTS);
        } else {
            for (int i = 0; i < points.size(); i++) {
                if (!(points.get(i) instanceof String p) || !pointIsSpecific(p)) {
                    failures.add("point " + (i + 1) + " lacks a specificity token (flag/number/identifier/named pattern)");
                }
            }
        }

        Object angle = topic.get("angle");
        if (!(angle instanceof String a) || !angleOk(a)) {
            failures.add("angle needs a question or an explicit trade-off/when/why");
        }

        Object est = topic.get("est_minutes");
        if (!(est instanceof Number n) || n.intValue() < MIN_EST_MINUTES || n.intValue() > MAX_EST_MINUTES) {
            failures.add("est_minutes must be " + MIN_EST_MINUTES + "-" + MAX_EST_MINUTES);
        }

        return failures;
    }
}
