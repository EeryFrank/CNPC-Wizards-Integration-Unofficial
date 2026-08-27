package cn.qizhang.cnpcwizardsintegration.bridge;

/** Receives structured cast diagnostics without coupling the bridge to one logger. */
@FunctionalInterface
public interface CastTraceSink {
    void record(CastTraceEvent event);

    static CastTraceSink noop() {
        return event -> { };
    }
}
