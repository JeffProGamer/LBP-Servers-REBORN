using LBPUnion.ProjectLighthouse.Tickets;
using LBPUnion.ProjectLighthouse.Tickets.Signature;
using LBPUnion.ProjectLighthouse.Types.Users;
using Xunit;

namespace LBPUnion.ProjectLighthouse.Tests.Unit;

[Trait("Category", "Unit")]
public class TicketTests
{
    [Fact]
    public void CanReadTicket()
    {
        // Cleaned up to match the SCEE TicketBuilder
        TicketBuilder builder = new TicketBuilder()
            .SetUserId(21)
            .SetIssuerId(0x74657374)
            .SetUsername("unittest")
            .SetIssueTime(0)
            .SetTitleId("BCES01663"); // Set to SCEE Title ID

        byte[] ticketData = builder.Build();

        NPTicket? ticket = NPTicket.CreateFromBytes(ticketData);
        Assert.NotNull(ticket);
        Assert.Equal((ulong)0, ticket.IssueTime);
        Assert.Equal("unittest", ticket.Username);
        Assert.Equal((ulong)0x74657374, ticket.IssuerId);
        Assert.Equal((ulong)21, ticket.UserId);
    }

    [Fact]
    public void CanVerifyTicketSignature()
    {
        TicketBuilder builder = new();
        byte[] ticketData = builder.Build();

        NPTicket? ticket = NPTicket.CreateFromBytes(ticketData);
        Assert.NotNull(ticket);
        Assert.True(new UnitTestSignatureVerifier(ticket).ValidateSignature());
    }
}
