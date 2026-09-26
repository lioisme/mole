# Security policy

## What Modev is

An Xposed module and its viewer app in one APK. It hooks *other applications on
your own rooted device* to observe what they call (crypto, SQL, file/pref/WebView
activity) and, optionally, to change what they see (dialog blocking, screen-record
release, SSL trust bypass, proxy/VPN hiding, device-environment spoofing).

It ships no server, no telemetry and no account. Records live in a local Room
database on the device and leave it only when you export them yourself.

## What Modev is not

- Not a stealth or anti-forensics tool. Detection evasion against
  Play Integrity / hardware attestation / banking-grade sensors is out of scope
  and not supported: several of those checks are answered by code outside the
  hooked process, which an Xposed module cannot reach.
- Not a defense against a hostile root, a hostile framework, or the device owner.
- Not a substitute for the target app's own security. Intercepting SSL trust or
  disabling certificate pinning *in a target you scope* weakens that target by
  design; it is a debugging switch, off by default, per app.

## Threat model you should read before enabling anything

| Surface | Default | Notes |
|---|---|---|
| External access (wire + MCP bind address) | **disabled** → `127.0.0.1` | Enabling it binds `0.0.0.0`. That is an explicit user choice and exposes record contents and configuration writes to the local network unless you also enable token auth. |
| MCP token authentication | **disabled** | When enabled, **every** endpoint requires `Bearer` — including loopback. Enable it before or together with external access, never after. |
| MCP bearer tokens | 32 hex chars from `SecureRandom` | Stored in host-local preferences. They never appear in MCP tool output or in the diagnostics snapshot. |
| Wire config channel (port 1511) | loopback | Carries the effective hook configuration into the injected process. |
| On-disk config fallback | `/data/local/tmp/modev_config_<pkg>.json` | Written only for the injected process to read when the host is unreachable. That path is readable by `shell`/`root`, so **profile content is not secret on a rooted device**. Treat "rooted" as "config is disclosed". |
| Captured records | local Room DB | Plaintext at rest (Android FBE protects per-user storage). Keys, IVs, SQL bind args and file contents you chose to preview are all in there. Export files accordingly. |

## Known hard limits (documented, not bugs to file)

- `GETSTATIC` reads of `static final` platform fields cannot be intercepted by
  ART. Anything that reads `Build.MODEL` as a field rather than through a hooked
  accessor may see the real value.
- Hooks whose target method does not exist on the running platform resolve to
  nothing. The injection side records every such unresolved hook point; an
  unresolvable hook is a silent no-op, so a "masked" label may still be a no-op.
- A method already hooked by another module may win the registration race; with
  two modules scoping the same app, neither's behaviour is guaranteed.

## Reporting a vulnerability

Open a **private** advisory through your usual channel to the maintainer rather
than a public issue, and include:

1. Module version and variant (`modern` = libxposed API 101/102, `legacy` =
   Xposed API 82) and the framework name/version.
2. The exact scope list (which apps were hooked) and which toggles were on.
3. Whether the MCP/wire services were externally reachable at the time.

Expect acknowledgement within a week. Fixes land in a normal release; the
advisory stays private until a fix ships or 90 days pass, whichever is first.

## Hardening checklist for anyone using this on a real device

- Keep external access off; if you must turn it on, turn token auth on first and
  put the device behind a network you own.
- Scope the minimum set of apps. Every scoped app is an app whose behaviour this
  module can change.
- Leave the intervention-domain toggles off unless you are actively debugging
  that behaviour; the observation (listen) domain is read-only and passive.
- Delete records and re-issue tokens after you finish a debugging session
  (Settings → Data, Settings → Services → Tokens).
