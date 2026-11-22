using LBPUnion.ProjectLighthouse.Configuration;

namespace LBPUnion.ProjectLighthouse.Helpers;

public static class VersionHelper
{
    public static string CommitHash => "LocalBuild";
    public static string Branch => "main";

    /// <summary>
    ///     The full revision string. States current revision hash and, if not main, the branch.
    /// </summary>
    private static string FullRevision => (Branch == "main" ? "" : $"{Branch}_") + $"r{CommitHash}_{Build}";

    /// <summary>
    ///     The server's branding (environment version) to show to LBP clients. Shows the environment name next to the
    ///     revision.
    /// </summary>
    public static string EnvVer => $"Lighthouse {FullRevision}";
    
    public static string FullVersion =>
        $"Project Lighthouse {Branch}@{CommitHash} {Build}";
        
    public static string RepositoryUrl => "https://github.com";

    public const string Build =
    #if DEBUG
        "Debug";
    #elif RELEASE
        "Release";
    #else
         "Unknown";
    #endif
}
