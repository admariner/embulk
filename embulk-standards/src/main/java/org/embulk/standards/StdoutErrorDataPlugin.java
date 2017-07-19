package org.embulk.standards;

import org.embulk.config.ConfigSource;
import org.embulk.config.Task;
import org.embulk.config.TaskSource;
import org.embulk.spi.ErrorDataPlugin;
import org.embulk.spi.ErrorDataReporter;

public class StdoutErrorDataPlugin
        implements ErrorDataPlugin
{
    @Override
    public TaskSource createTaskSource(final ConfigSource config)
    {
        return config.loadConfig(Task.class).dump();
    }

    @Override
    public ErrorDataReporter open(final TaskSource taskSource)
    {
        return new StdoutErrorDataReporter();
    }

    private static class StdoutErrorDataReporter
            implements ErrorDataReporter
    {

        @Override
        public void skip(String errorData)
        {
            System.out.println(errorData);
        }

        @Override
        public void close()
        {
        }

        @Override
        public void commit()
        {

        }
    }
}