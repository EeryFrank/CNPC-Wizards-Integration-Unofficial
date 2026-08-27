package cn.qizhang.cnpcwizardsintegration.api;

/** Stable result codes for Java and future ECMAScript callers. */
public enum CastResultCode {
    ACCEPTED,
    INVALID_REQUEST,
    UNSUPPORTED_SPELL,
    TARGET_NOT_FOUND,
    COOLDOWN_ACTIVE,
    RECURSION_BLOCKED,
    CASTER_BUSY,
    INTERNAL_ERROR
}
