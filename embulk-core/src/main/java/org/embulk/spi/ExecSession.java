package org.embulk.spi;

import com.google.inject.Injector;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.DataSourceImpl;
import org.embulk.config.ModelManager;
import org.embulk.config.Task;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.embulk.plugin.PluginManager;
import org.embulk.plugin.PluginType;
import org.embulk.spi.time.Instants;
import org.embulk.spi.time.Timestamp;
import org.embulk.spi.time.TimestampFormatter;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class ExecSession {
    private static final DateTimeFormatter ISO8601_BASIC =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final Injector injector;

    // TODO: Remove it.
    private final ILoggerFactory loggerFactory;

    private final ModelManager modelManager;
    private final PluginManager pluginManager;
    private final BufferAllocator bufferAllocator;

    private final Timestamp transactionTime;
    private final TempFileSpace tempFileSpace;

    private final boolean preview;

    @Deprecated
    public interface SessionTask extends Task {
        // It is kept as Guava's Optional since already @Deprecated.
        @Config("transaction_time")
        @ConfigDefault("null")
        com.google.common.base.Optional<Timestamp> getTransactionTime();
    }

    public static class Builder {
        private final Injector injector;
        private ILoggerFactory loggerFactory;
        private Timestamp transactionTime;

        public Builder(Injector injector) {
            this.injector = injector;
        }

        public Builder fromExecConfig(ConfigSource configSource) {
            this.transactionTime = configSource.get(Timestamp.class, "transaction_time", null);
            return this;
        }

        @Deprecated  // @see docs/design/slf4j.md
        public Builder setLoggerFactory(ILoggerFactory loggerFactory) {
            // TODO: Make it ineffective.
            this.loggerFactory = loggerFactory;
            return this;
        }

        public Builder setTransactionTime(Timestamp timestamp) {
            this.transactionTime = timestamp;
            return this;
        }

        public ExecSession build() {
            if (transactionTime == null) {
                transactionTime = Timestamp.ofEpochMilli(System.currentTimeMillis());  // TODO get nanoseconds for default
            }
            return new ExecSession(injector, transactionTime, Optional.ofNullable(loggerFactory));
        }
    }

    public static Builder builder(Injector injector) {
        return new Builder(injector);
    }

    @Deprecated
    public ExecSession(Injector injector, ConfigSource configSource) {
        this(injector,
             configSource.loadConfig(SessionTask.class).getTransactionTime().or(
                     Timestamp.ofEpochMilli(System.currentTimeMillis())),  // TODO get nanoseconds for default
             null);
    }

    private ExecSession(Injector injector, Timestamp transactionTime, Optional<ILoggerFactory> loggerFactory) {
        this.injector = injector;
        this.loggerFactory = loggerFactory.orElse(injector.getInstance(ILoggerFactory.class));
        this.modelManager = injector.getInstance(ModelManager.class);
        this.pluginManager = injector.getInstance(PluginManager.class);
        this.bufferAllocator = injector.getInstance(BufferAllocator.class);

        this.transactionTime = transactionTime;

        final TempFileSpaceAllocator tempFileSpaceAllocator = injector.getInstance(TempFileSpaceAllocator.class);
        this.tempFileSpace = tempFileSpaceAllocator.newSpace(ISO8601_BASIC.format(transactionTime.getInstant()));

        this.preview = false;
    }

    private ExecSession(ExecSession copy, boolean preview) {
        this.injector = copy.injector;
        this.loggerFactory = copy.loggerFactory;
        this.modelManager = copy.modelManager;
        this.pluginManager = copy.pluginManager;
        this.bufferAllocator = copy.bufferAllocator;

        this.transactionTime = copy.transactionTime;
        this.tempFileSpace = copy.tempFileSpace;

        this.preview = preview;
    }

    public ExecSession forPreview() {
        return new ExecSession(this, true);
    }

    public ConfigSource getSessionExecConfig() {
        return newConfigSource()
                .set("transaction_time", transactionTime);
    }

    public Injector getInjector() {
        return injector;
    }

    public Timestamp getTransactionTime() {
        return transactionTime;
    }

    public Instant getTransactionTimeInstant() {
        return this.transactionTime.getInstant();
    }

    public String getTransactionTimeString() {
        return Instants.toString(this.transactionTime.getInstant());
    }

    @Deprecated  // @see docs/design/slf4j.md
    public Logger getLogger(String name) {
        // TODO: Make it always return org.slf4j.LoggerFactory.getLogger(...).
        return loggerFactory.getLogger(name);
    }

    @Deprecated  // @see docs/design/slf4j.md
    public Logger getLogger(Class<?> name) {
        // TODO: Make it always return org.slf4j.LoggerFactory.getLogger(...).
        return loggerFactory.getLogger(name.getName());
    }

    public BufferAllocator getBufferAllocator() {
        return bufferAllocator;
    }

    public PageBuilder getPageBuilder(final BufferAllocator allocator, final Schema schema, final PageOutput output) {
        return new PageBuilder(bufferAllocator, schema, output);
    }

    public PageReader getPageReader(final Schema schema) {
        return new PageReader(schema);
    }

    public ModelManager getModelManager() {
        return modelManager;
    }

    public <T> T newPlugin(Class<T> iface, PluginType type) {
        return pluginManager.newPlugin(iface, type);
    }

    public TaskReport newTaskReport() {
        return new DataSourceImpl(modelManager);
    }

    // To be removed by v0.10 or earlier.
    @Deprecated  // https://github.com/embulk/embulk/issues/933
    @SuppressWarnings("deprecation")
    public org.embulk.config.CommitReport newCommitReport() {
        return new DataSourceImpl(modelManager);
    }

    public ConfigDiff newConfigDiff() {
        return new DataSourceImpl(modelManager);
    }

    public ConfigSource newConfigSource() {
        return new DataSourceImpl(modelManager);
    }

    public TaskSource newTaskSource() {
        return new DataSourceImpl(modelManager);
    }

    // To be removed by v0.10 or earlier.
    @Deprecated  // https://github.com/embulk/embulk/issues/936
    @SuppressWarnings("deprecation")
    public TimestampFormatter newTimestampFormatter(String format, org.joda.time.DateTimeZone timezone) {
        return new TimestampFormatter(format, timezone);
    }

    public TempFileSpace getTempFileSpace() {
        return tempFileSpace;
    }

    public boolean isPreview() {
        return preview;
    }

    public void cleanup() {
        tempFileSpace.cleanup();
    }
}
