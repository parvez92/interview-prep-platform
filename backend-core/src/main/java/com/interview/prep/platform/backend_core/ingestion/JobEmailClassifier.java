package com.interview.prep.platform.backend_core.ingestion;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a fetched email is a job email and extracts company/role/JD.
 *
 * Defense in depth behind the Gmail query filter: anything this classifier
 * rejects is dropped immediately — never persisted, never sent to an LLM.
 * Extraction is heuristic and best-effort; the user can edit the job later.
 */
@Component
public class JobEmailClassifier {

    /** Known job boards / ATS sender domains — also drives the Gmail search query. */
    public static final List<String> SENDER_ALLOWLIST = List.of(
            "linkedin.com", "indeed.com", "glassdoor.com", "greenhouse.io", "lever.co",
            "myworkday.com", "workday.com", "icims.com", "ziprecruiter.com", "wellfound.com",
            "smartrecruiters.com", "ashbyhq.com", "jobvite.com", "hired.com", "dice.com");

    private static final Pattern JOB_SUBJECT = Pattern.compile(
            "\\b(job|application|interview|opportunit\\w*|position|opening|hiring|recruit\\w*|career|vacanc\\w*)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SENDER_ADDRESS = Pattern.compile("<([^>]+)>\\s*$");

    private static final List<Pattern> COMPANY_PATTERNS = List.of(
            Pattern.compile("(?i)application (?:to|at|with) ([A-Z][\\w&.'\\- ]{1,40}?)(?:\\s*[-–—:,!]|\\s+for\\b|$)"),
            Pattern.compile("(?i)interview (?:with|at) ([A-Z][\\w&.'\\- ]{1,40}?)(?:\\s*[-–—:,!]|$)"),
            Pattern.compile("(?i)\\bat ([A-Z][\\w&.'\\- ]{1,40}?)(?:\\s*[-–—:,!]|$)"));

    private static final List<Pattern> ROLE_PATTERNS = List.of(
            Pattern.compile("[\"“]([^\"”]{3,80})[\"”]"),
            Pattern.compile("(?i)(?:for|as) (?:a |an |the )?([\\w+#/.'\\- ]{3,60}?)(?:\\s+(?:at|with|@)\\b|\\s*[-–—:,]|$)"),
            Pattern.compile("(?i)^([\\w+#/.'\\- ]{3,60}?)\\s+(?:at|@)\\s+"),
            Pattern.compile("(?i)job alert:?\\s*(.{3,80}?)(?:\\s*[-–—:]|$)"));

    private static final Pattern NOISE_WORDS = Pattern.compile(
            "(?i)\\b(no[- ]?reply|notifications?|careers?|jobs?|recruiting|talent|team|via linkedin)\\b");

    private static final int MAX_JD_CHARS = 20_000;

    public record JobEmail(String company, String role, String jdText) {}

    public Optional<JobEmail> classify(GmailClient.GmailMessage msg) {
        String from = msg.from() != null ? msg.from() : "";
        String subject = msg.subject() != null ? msg.subject() : "";

        boolean allowedSender = senderDomain(from)
                .map(d -> SENDER_ALLOWLIST.stream().anyMatch(a -> d.equals(a) || d.endsWith("." + a)))
                .orElse(false);
        // subject arm covers label-mode, where the user explicitly routed the mail here
        if (!allowedSender && !JOB_SUBJECT.matcher(subject).find()) {
            return Optional.empty();
        }
        return Optional.of(new JobEmail(company(from, subject), role(subject), jdText(msg.body())));
    }

    private static Optional<String> senderDomain(String from) {
        Matcher m = SENDER_ADDRESS.matcher(from.strip());
        String address = m.find() ? m.group(1) : from.strip();
        int at = address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) return Optional.empty();
        return Optional.of(address.substring(at + 1).toLowerCase().strip());
    }

    private static String company(String from, String subject) {
        for (Pattern p : COMPANY_PATTERNS) {
            Matcher m = p.matcher(subject);
            if (m.find()) return m.group(1).strip();
        }
        // sender display name, minus notification noise
        String display = SENDER_ADDRESS.matcher(from).replaceAll("").replace("\"", "").strip();
        display = NOISE_WORDS.matcher(display).replaceAll("").replaceAll("\\s+", " ").strip();
        return display.isBlank() ? "Unknown" : display;
    }

    private static String role(String subject) {
        for (Pattern p : ROLE_PATTERNS) {
            Matcher m = p.matcher(subject);
            if (m.find()) {
                String role = m.group(1).strip();
                if (!role.isBlank()) return role;
            }
        }
        String fallback = subject.strip();
        return fallback.isBlank() ? "Unknown"
                : fallback.substring(0, Math.min(fallback.length(), 120));
    }

    private static String jdText(String body) {
        if (body == null) return "";
        String text = body.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
        return text.length() > MAX_JD_CHARS ? text.substring(0, MAX_JD_CHARS) : text;
    }
}
