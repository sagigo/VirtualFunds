using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Tests;

public class AuthStateTests
{
    [Fact]
    public void AuthStateSignedIn_StoresUserId()
    {
        var state = new AuthStateSignedIn("user-123");
        Assert.Equal("user-123", state.UserId);
    }

    [Fact]
    public void AuthStateSignedIn_PatternMatches()
    {
        AuthState state = new AuthStateSignedIn("user-abc");

        var matched = state switch
        {
            AuthStateSignedIn s => s.UserId,
            _ => null
        };

        Assert.Equal("user-abc", matched);
    }

    [Fact]
    public void AuthStateSignedOut_PatternMatches()
    {
        AuthState state = new AuthStateSignedOut();

        var isSignedOut = state is AuthStateSignedOut;

        Assert.True(isSignedOut);
    }

    [Fact]
    public void AuthStateUnknown_PatternMatches()
    {
        AuthState state = new AuthStateUnknown();

        var isUnknown = state is AuthStateUnknown;

        Assert.True(isUnknown);
    }

    [Fact]
    public void AllThreeStates_AreDistinct()
    {
        AuthState unknown = new AuthStateUnknown();
        AuthState signedOut = new AuthStateSignedOut();
        AuthState signedIn = new AuthStateSignedIn("x");

        Assert.IsNotType<AuthStateUnknown>(signedOut);
        Assert.IsNotType<AuthStateSignedOut>(signedIn);
        Assert.IsNotType<AuthStateSignedIn>(unknown);
    }
}
