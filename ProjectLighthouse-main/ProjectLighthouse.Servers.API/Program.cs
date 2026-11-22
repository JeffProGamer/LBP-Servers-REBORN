using LBPUnion.ProjectLighthouse;
using LBPUnion.ProjectLighthouse.Configuration;
using LBPUnion.ProjectLighthouse.Logging.Loggers.AspNet;
using LBPUnion.ProjectLighthouse.Servers.API.Startup;
using LBPUnion.ProjectLighthouse.Types.Misc;

await StartupTasks.Run(ServerType.Api);

IHostBuilder builder = Host.CreateDefaultBuilder();

builder.ConfigureWebHostDefaults(webBuilder =>
{
    webBuilder.UseStartup<ApiStartup>();
    
    // Use the URL from config (should be 0.0.0.0:10062)
    // In ProjectLighthouse.Servers.API/Program.cs
webBuilder.UseUrls(ServerConfiguration.Instance.ApiListenUrl); // Use 10062

});

builder.ConfigureLogging(logging =>
{
    logging.ClearProviders();
    logging.AddProvider(new AspNetToLighthouseLoggerProvider());
});

await builder.Build().RunAsync();