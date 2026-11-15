package admin

import (
	"context"
	"errors"
	"fmt"
	"sync"

	"github.com/coreos/go-oidc/v3/oidc"
	"golang.org/x/oauth2"
)

const googleIssuer = "https://accounts.google.com"

// Google signs operators in with Google (OIDC Authorization Code + PKCE, ADR-037). The discovery document is fetched
// on the first sign-in, not at startup, so the API starts even when Google is unreachable.
type Google struct {
	clientID, clientSecret, redirectURL string

	mu       sync.Mutex
	conf     *oauth2.Config
	verifier *oidc.IDTokenVerifier
}

func NewGoogle(clientID, clientSecret, redirectURL string) *Google {
	return &Google{clientID: clientID, clientSecret: clientSecret, redirectURL: redirectURL}
}

func (g *Google) setup(ctx context.Context) (*oauth2.Config, *oidc.IDTokenVerifier, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.conf == nil {
		p, err := oidc.NewProvider(ctx, googleIssuer)
		if err != nil {
			return nil, nil, fmt.Errorf("google discovery: %w", err)
		}
		g.conf = &oauth2.Config{
			ClientID: g.clientID, ClientSecret: g.clientSecret, RedirectURL: g.redirectURL,
			Endpoint: p.Endpoint(), Scopes: []string{oidc.ScopeOpenID, "email", "profile"},
		}
		// Checks signature (Google's JWKS), issuer, audience = our client id and expiry.
		g.verifier = p.Verifier(&oidc.Config{ClientID: g.clientID})
	}
	return g.conf, g.verifier, nil
}

func (g *Google) AuthURL(ctx context.Context, state, nonce, verifier string) (string, error) {
	conf, _, err := g.setup(ctx)
	if err != nil {
		return "", err
	}
	return conf.AuthCodeURL(state, oidc.Nonce(nonce), oauth2.S256ChallengeOption(verifier), oauth2.SetAuthURLParam("prompt", "select_account")), nil
}

func (g *Google) Exchange(ctx context.Context, code, verifier, nonce string) (Identity, error) {
	conf, idVerifier, err := g.setup(ctx)
	if err != nil {
		return Identity{}, err
	}
	tok, err := conf.Exchange(ctx, code, oauth2.VerifierOption(verifier))
	if err != nil {
		return Identity{}, fmt.Errorf("code exchange: %w", err)
	}
	raw, ok := tok.Extra("id_token").(string)
	if !ok {
		return Identity{}, errors.New("no id_token in the token response")
	}
	idt, err := idVerifier.Verify(ctx, raw)
	if err != nil {
		return Identity{}, fmt.Errorf("id token: %w", err)
	}
	if idt.Nonce != nonce {
		return Identity{}, errors.New("id token nonce mismatch")
	}
	var claims struct {
		Email         string `json:"email"`
		EmailVerified bool   `json:"email_verified"`
		Name          string `json:"name"`
	}
	if err := idt.Claims(&claims); err != nil {
		return Identity{}, fmt.Errorf("id token claims: %w", err)
	}
	// The Google access and refresh tokens are not kept (design 3.2: no provider tokens when not needed).
	return Identity{Issuer: idt.Issuer, Subject: idt.Subject, Email: claims.Email, EmailVerified: claims.EmailVerified, Name: claims.Name}, nil
}
