using LBPUnion.ProjectLighthouse.Configuration;
using LBPUnion.ProjectLighthouse.Database;
using LBPUnion.ProjectLighthouse.Helpers;
using LBPUnion.ProjectLighthouse.Servers.Website.Pages.Layouts;
using LBPUnion.ProjectLighthouse.Types.Entities.Notifications;
using LBPUnion.ProjectLighthouse.Types.Entities.Profile;
using LBPUnion.ProjectLighthouse.Types.Entities.Website;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LBPUnion.ProjectLighthouse.Servers.Website.Pages;

public class NotificationsPage : BaseLayout
{
    private const int DiscordPreviewLength = 250;

    public List<WebsiteAnnouncementEntity> Announcements { get; private set; } = new();
    public List<NotificationEntity> Notifications { get; private set; } = new();
    public string Error { get; private set; } = string.Empty;

    public NotificationsPage(DatabaseContext database) : base(database)
    {
    }

    public async Task<IActionResult> OnGet()
    {
        await LoadAnnouncements();

        if (User != null)
            await LoadNotifications();

        return Page();
    }

    public async Task<IActionResult> OnPost(
        [FromForm] string title,
        [FromForm] string content)
    {
        var user = Database.UserFromWebRequest(Request);

        if (user?.IsAdmin != true)
            return BadRequest();

        title = title.Trim();
        content = content.Trim();

        if (string.IsNullOrWhiteSpace(title) ||
            string.IsNullOrWhiteSpace(content))
        {
            Error = "All fields are required.";
            await LoadAnnouncements();
            return Page();
        }

        var announcement = new WebsiteAnnouncementEntity
        {
            Title = title,
            Content = content,
            PublisherId = user.UserId
        };

        Database.WebsiteAnnouncements.Add(announcement);
        await Database.SaveChangesAsync();

        if (DiscordConfiguration.Instance.DiscordIntegrationEnabled)
            await SendDiscordAnnouncement(title, content);

        return RedirectToPage();
    }

    public async Task<IActionResult> OnPostDelete(string type, int id)
    {
        var user = Database.UserFromWebRequest(Request);

        if (user == null)
            return BadRequest();

        return type switch
        {
            "announcement" => await DeleteAnnouncement(user, id),
            "notification" => await DismissNotification(user, id),
            _ => BadRequest()
        };
    }

    private async Task LoadAnnouncements()
    {
        Announcements = await Database.WebsiteAnnouncements
            .Include(a => a.Publisher)
            .OrderByDescending(a => a.AnnouncementId)
            .ToListAsync();
    }

    private async Task LoadNotifications()
    {
        Notifications = await Database.Notifications
            .Where(n => n.UserId == User!.UserId && !n.IsDismissed)
            .OrderByDescending(n => n.Id)
            .ToListAsync();
    }

    private async Task<IActionResult> DeleteAnnouncement(UserEntity user, int id)
    {
        if (!user.IsAdmin)
            return BadRequest();

        var announcement = await Database.WebsiteAnnouncements
            .FirstOrDefaultAsync(a => a.AnnouncementId == id);

        if (announcement == null)
            return BadRequest();

        Database.WebsiteAnnouncements.Remove(announcement);
        await Database.SaveChangesAsync();

        return RedirectToPage();
    }

    private async Task<IActionResult> DismissNotification(UserEntity user, int id)
    {
        var notification = await Database.Notifications
            .FirstOrDefaultAsync(n =>
                n.Id == id &&
                !n.IsDismissed);

        if (notification == null || notification.UserId != user.UserId)
            return BadRequest();

        notification.IsDismissed = true;

        await Database.SaveChangesAsync();

        return RedirectToPage();
    }

    private async Task SendDiscordAnnouncement(string title, string content)
    {
        string preview = content.Length > DiscordPreviewLength
            ? $"{content[..DiscordPreviewLength]}... [read more]({ServerConfiguration.Instance.ExternalUrl}/notifications)"
            : content;

        await WebhookHelper.SendWebhook(
            $":mega: {title}",
            preview
        );
    }
}