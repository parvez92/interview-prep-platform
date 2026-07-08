package com.interview.prep.platform.backend_core.ingestion;

import com.interview.prep.platform.backend_core.ingestion.GmailClient.GmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobEmailClassifierTest {

    private final JobEmailClassifier classifier = new JobEmailClassifier();

    @Test
    void greenhouseApplication_extractsCompanyFromSubject() {
        var result = classifier.classify(new GmailMessage("m1",
                "Greenhouse <no-reply@greenhouse.io>",
                "Your application to Stripe",
                "Thanks for applying to the Senior Backend Engineer role."));

        assertThat(result).isPresent();
        assertThat(result.get().company()).isEqualTo("Stripe");
    }

    @Test
    void linkedinJobAlert_classifiedWithRoleFromSubject() {
        var result = classifier.classify(new GmailMessage("m2",
                "LinkedIn Job Alerts <jobalerts-noreply@linkedin.com>",
                "\"Java Developer\" and more jobs for you",
                "10 new jobs match your preferences."));

        assertThat(result).isPresent();
        assertThat(result.get().role()).isEqualTo("Java Developer");
    }

    @Test
    void subdomainOfAllowlistedSender_matches() {
        var result = classifier.classify(new GmailMessage("m3",
                "Acme Recruiting <careers@acme.greenhouse.io>",
                "Interview with Acme",
                "We'd like to schedule an interview."));

        assertThat(result).isPresent();
        assertThat(result.get().company()).isEqualTo("Acme");
    }

    @Test
    void bankStatement_rejected() {
        var result = classifier.classify(new GmailMessage("m4",
                "Chase <alerts@chase.com>",
                "Your statement is ready",
                "Account balance details inside."));

        assertThat(result).isEmpty();
    }

    @Test
    void personalEmail_rejected() {
        var result = classifier.classify(new GmailMessage("m5",
                "Mom <mom@gmail.com>",
                "Dinner on Sunday?",
                "Call me when you can."));

        assertThat(result).isEmpty();
    }

    @Test
    void unknownSenderWithJobSubject_acceptedForLabelMode() {
        var result = classifier.classify(new GmailMessage("m6",
                "Jane Recruiter <jane@acmecorp.com>",
                "Exciting opportunity for a Staff Engineer at AcmeCorp",
                "We came across your profile..."));

        assertThat(result).isPresent();
    }

    @Test
    void longBody_truncatedTo20k() {
        var result = classifier.classify(new GmailMessage("m7",
                "Indeed <alert@indeed.com>",
                "New jobs for you",
                "x".repeat(50_000)));

        assertThat(result).isPresent();
        assertThat(result.get().jdText()).hasSize(20_000);
    }
}
