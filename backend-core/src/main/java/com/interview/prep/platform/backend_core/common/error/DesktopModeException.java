package com.interview.prep.platform.backend_core.common.error;

public class DesktopModeException extends RuntimeException {

    private final String prompt;

    public DesktopModeException(String prompt) {
        super("desktop_mode");
        this.prompt = prompt;
    }

    public String getPrompt() {
        return prompt;
    }
}
