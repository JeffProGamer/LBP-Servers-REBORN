#nullable enable
using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using LBPUnion.ProjectLighthouse.Database;
using LBPUnion.ProjectLighthouse.Extensions;
using LBPUnion.ProjectLighthouse.Logging;
using LBPUnion.ProjectLighthouse.Types.Logging;
using LBPUnion.ProjectLighthouse.Types.Maintenance;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

namespace LBPUnion.ProjectLighthouse.Services;

public class RepeatingTaskService : BackgroundService
{
    private readonly IServiceProvider provider;
    private readonly List<IRepeatingTask> taskList;

    public RepeatingTaskService(IServiceProvider provider, List<IRepeatingTask> tasks)
    {
        this.provider = provider;
        this.taskList = tasks;

        Logger.Info("Initializing repeating tasks service", LogArea.Startup);
    }

    public bool TryGetNextTask(out IRepeatingTask? outTask)
    {
        TimeSpan smallestSpan = TimeSpan.MaxValue;
        IRepeatingTask? smallestTask = null;
        foreach (IRepeatingTask task in this.taskList)
        {
            TimeSpan smallestTimeRemaining = task.RepeatInterval.Subtract(DateTime.UtcNow.Subtract(task.LastRan));
            if (smallestTimeRemaining >= smallestSpan) continue;

            smallestSpan = smallestTimeRemaining;
            smallestTask = task;
        }
        outTask = smallestTask;

        return outTask != null;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        await Task.Yield();
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                if (!this.TryGetNextTask(out IRepeatingTask? task) || task == null)
                {
                    Logger.Debug("Failed to fetch next smallest task", LogArea.Maintenance);
                    return;
                }

                TimeSpan timeElapsedSinceRun = DateTime.UtcNow.Subtract(task.LastRan);

                if (timeElapsedSinceRun < task.RepeatInterval)
                {
                    TimeSpan timeToWait = task.RepeatInterval.Subtract(timeElapsedSinceRun);
                    Logger.Debug($"Waiting {timeToWait} for {task.Name}", LogArea.Maintenance);
                    
                    // Added: Check token to avoid waiting if shutting down
                    await Task.Delay(timeToWait, stoppingToken);
                }

                using IServiceScope scope = this.provider.CreateScope();
                DatabaseContext database = scope.ServiceProvider.GetRequiredService<DatabaseContext>();
                
                task.LastRan = DateTime.UtcNow;
                await task.Run(database);
                Logger.Debug($"Successfully ran task \"{task.Name}\"", LogArea.Maintenance);
            }
            catch (OperationCanceledException) // Handles TaskCanceled during Delay
            {
                break;
            }
            catch (ObjectDisposedException) // Catch the "LoggerFactory" disposal specifically
            {
                // If we are shutting down, just break the loop
                if (stoppingToken.IsCancellationRequested) break;
            }
            catch (Exception e)
            {
                // Final safety: Don't log if the token is cancelled (logger is likely dead)
                if (!stoppingToken.IsCancellationRequested)
                {
                    Logger.Error($"Error while running repeating task: {e.ToDetailedException()}", LogArea.Maintenance);
                }
            }
        }
    }
}
