package io.github.naimjeg.obeliskdepths.tempering;

public record TemperingFailure(String reason) {
    public static final TemperingFailure NONE = new TemperingFailure("");

    public TemperingFailure {
        reason = reason == null ? "unknown" : reason;
    }

    public boolean failed() {
        return !this.reason.isBlank();
    }
}
