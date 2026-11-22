#nullable enable

using LBPUnion.ProjectLighthouse.Configuration;
using LBPUnion.ProjectLighthouse.Database;
using LBPUnion.ProjectLighthouse.Servers.Website.Pages.Layouts;
using LBPUnion.ProjectLighthouse.Types.Entities.Profile;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace LBPUnion.ProjectLighthouse.Servers.Website.Pages;

public class PhotosPage : BaseLayout
{
    public int PageAmount { get; private set; }
    public int PageNumber { get; private set; }
    public int PhotoCount { get; private set; }

    public List<PhotoEntity> Photos { get; private set; } = new();

    public string SearchValue { get; private set; } = string.Empty;

    public PhotosPage(DatabaseContext database) : base(database)
    {
    }

    public async Task<IActionResult> OnGet(
        [FromRoute] int pageNumber,
        [FromQuery] string? name)
    {
        SearchValue = name?.Trim() ?? string.Empty;

        IQueryable<PhotoEntity> query = BuildPhotoQuery(SearchValue);

        PhotoCount = await query.CountAsync();

        PageAmount = Math.Max(
            1,
            (int)Math.Ceiling((double)PhotoCount / ServerStatics.PageSize)
        );

        PageNumber = Math.Clamp(pageNumber, 0, PageAmount - 1);

        if (pageNumber != PageNumber)
            return Redirect($"/photos/{PageNumber}?name={SearchValue}");

        Photos = await query
            .OrderByDescending(p => p.Timestamp)
            .Skip(PageNumber * ServerStatics.PageSize)
            .Take(ServerStatics.PageSize)
            .ToListAsync();

        return Page();
    }

    private IQueryable<PhotoEntity> BuildPhotoQuery(string search)
    {
        IQueryable<PhotoEntity> query = Database.Photos
            .Include(p => p.Creator)
            .Include(p => p.PhotoSubjects)
                .ThenInclude(ps => ps.User);

        if (string.IsNullOrWhiteSpace(search))
            return query;

        if (search.Contains("by:") || search.Contains("with:"))
            return ApplyAdvancedSearch(query, search);

        return query.Where(p =>
            p.Creator != null &&
            (
                p.Creator.Username.Contains(search) ||
                p.PhotoSubjects.Any(ps =>
                    ps.User.Username.Contains(search))
            ));
    }

    private static IQueryable<PhotoEntity> ApplyAdvancedSearch(
        IQueryable<PhotoEntity> query,
        string search)
    {
        foreach (string part in search.Split(
                     ' ',
                     StringSplitOptions.RemoveEmptyEntries))
        {
            if (part.StartsWith("by:"))
            {
                string creator = part[3..];

                query = query.Where(p =>
                    p.Creator != null &&
                    p.Creator.Username.Contains(creator));
            }
            else if (part.StartsWith("with:"))
            {
                string subject = part[5..];

                query = query.Where(p =>
                    p.PhotoSubjects.Any(ps =>
                        ps.User.Username.Contains(subject)));
            }
        }

        return query;
    }
}