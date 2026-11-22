using LBPUnion.ProjectLighthouse.Configuration;
using LBPUnion.ProjectLighthouse.Database;
using LBPUnion.ProjectLighthouse.Middlewares;
using LBPUnion.ProjectLighthouse.Serialization;
using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi.Models;

namespace LBPUnion.ProjectLighthouse.Servers.API.Startup;

public class ApiStartup
{
    public ApiStartup(IConfiguration configuration)
    {
        this.Configuration = configuration;
    }

    public IConfiguration Configuration { get; }

    public void ConfigureServices(IServiceCollection services)
    {
        services.AddControllers();

        services.AddMvc
        (
            options =>
            {
                options.OutputFormatters.Add(new JsonOutputFormatter());
            }
        );

        services.AddDbContext<DatabaseContext>(builder =>
        {
            builder.UseMySql(ServerConfiguration.Instance.DbConnectionString,
                MySqlServerVersion.LatestSupportedServerVersion);
        });

        services.AddSwaggerGen
        (
            c =>
            {
                c.SwaggerDoc
                (
                    "v1",
                    new OpenApiInfo
                    {
                        Title = "Project Lighthouse API",
                        Version = "v1",
                    }
                );

                c.DocumentFilter<SwaggerFilter>();

                // FIXED: Check if the XML file exists before trying to load it to prevent crashing
                var xmlFile = Path.Combine(AppContext.BaseDirectory, "LBPUnion.ProjectLighthouse.Servers.API.xml");
                if (File.Exists(xmlFile))
                {
                    c.IncludeXmlComments(xmlFile);
                }
            }
        );
    }

    public virtual void Configure(IApplicationBuilder app, IWebHostEnvironment env)
    {
        #if DEBUG
        app.UseDeveloperExceptionPage();
        #endif

        // --- PS4 UNMODDED HANDSHAKE FIX ---
        // This intercepts the 'CONNECT' command from Fiddler/PS4 
        // and tells the console the bridge is open (200 OK)
        app.Use(async (context, next) =>
        {
            if (context.Request.Method == "CONNECT")
            {
                context.Response.StatusCode = 200;
                return;
            }
            await next();
        });

        app.UseSwagger();
        app.UseSwaggerUI
        (
            c =>
            {
                c.SwaggerEndpoint("v1/swagger.json", "Project Lighthouse API");
            }
        );

        app.UseMiddleware<RequestLogMiddleware>();

        app.UseRouting();
        app.UseEndpoints(endpoints => endpoints.MapControllers());
    }
}
