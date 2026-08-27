package cn.qizhang.cnpcwizardsintegration.bridge;

/** Stable lifecycle stages for diagnostics and future acceptance evidence. */
public enum CastTraceStage {
    RECEIVED,
    VALIDATED,
    TARGET_RESOLVED,
    DISPATCHING,
    ACCEPTED,
    CHANNEL_SCHEDULED,
    CHANNEL_PULSE,
    CHANNEL_COMPLETED,
    CHANNEL_CANCELLED,
    CHANNEL_FAILED,
    CHARGE_SCHEDULED,
    CHARGE_RELEASED,
    CHARGE_CANCELLED,
    CHARGE_FAILED,
    REJECTED,
    FAILED
}
