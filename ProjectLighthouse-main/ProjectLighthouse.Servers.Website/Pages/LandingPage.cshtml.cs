#nullable enable

using JetBrains.Annotations;
using LBPUnion.ProjectLighthouse.Database;
using LBPUnion.ProjectLighthouse.Helpers;
using LBPUnion.ProjectLighthouse.Servers.Website.Pages.Layouts;
using LBPUnion.ProjectLighthouse.Types.Entities.Level;
using LBPUnion.ProjectLighthouse.Types.Entities.Profile;
using LBPUnion.ProjectLighthouse.Types.Entities.Website;
using LBPUnion.ProjectLighthouse.Types.Levels;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LBPUnion.ProjectLighthouse.Servers.Website.Pages;

public class LandingPage : BaseLayout
{
    private const int MaxShownLevels = 5;
    private const int OnlineThresholdSeconds = 300;

    public List<SlotEntity> LatestTeamPicks { get; private set; } = new();
    public List<SlotEntity> NewestLevels { get; private set; } = new();
    public List<UserEntity> PlayersOnline { get; private set; } = new();

    public int PendingAuthAttempts { get; private set; }
    public WebsiteAnnouncementEntity? LatestAnnouncement { get; private set; }

    public LandingPage(DatabaseContext database) : base(database)
    {
    }

    [UsedImplicitly]
    public async Task<IActionResult> OnGet()
    {
        var user = this.Database.UserFromWebRequest(this.Request);

        if (user?.PasswordResetRequired == true)
            return this.Redirect("~/passwordResetRequired");

        if (user != null)
        {
            PendingAuthAttempts = await this.Database.PlatformLinkAttempts
                .CountAsync(l => l.UserId == user.UserId);
        }

        await LoadOnlinePlayers();
        await LoadLevels();
        await LoadAnnouncement();

        return this.Page();
    }

    private async Task LoadOnlinePlayers()
    {
        long threshold = TimeHelper.Timestamp - OnlineThresholdSeconds;

        var userIds = await this.Database.LastContacts
            .Where(l => l.Timestamp > threshold)
            .Select(l => l.UserId)
            .Distinct()
            .ToListAsync();

        PlayersOnline = await this.Database.Users
            .Where(u => userIds.Contains(u.UserId))
            .ToListAsync();
    }

    private async Task LoadLevels()
    {
        IQueryable<SlotEntity> baseQuery = this.Database.Slots
            .Where(s =>
                s.Type == SlotType.User &&
                !s.SubLevel &&
                !s.Hidden)
            .Include(s => s.Creator);

        LatestTeamPicks = await baseQuery
            .Where(s => s.TeamPickTime != 0)
            .OrderByDescending(s => s.TeamPickTime)
            .ThenByDescending(s => s.FirstUploaded)
            .Take(MaxShownLevels)
            .ToListAsync();

        NewestLevels = await baseQuery
            .OrderByDescending(s => s.FirstUploaded)
            .Take(MaxShownLevels)
            .ToListAsync();
    }

    private async Task LoadAnnouncement()
    {
        LatestAnnouncement = await this.Database.WebsiteAnnouncements
            .Include(a => a.Publisher)
            .OrderByDescending(a => a.AnnouncementId)
            .FirstOrDefaultAsync();
    }
}