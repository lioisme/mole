# Instructions for AI coding agents

The operating manual for this repository is [`AGENTS.md`](../AGENTS.md). Read it
before editing: most of its "constraints" are enforced by tests that fail with a
message that does not explain themselves, and several deliberate-looking oddities
in the code are load-bearing workarounds for documented toolchain defects.

Rules of engagement, shortest form:

1. Do not delete or "clean up" anything under `app/src/test/.../architecture/`.
   Those are contract gates (layer boundaries, i18n key parity, Room migration
   chain, MCP tool-surface parity, orphan detection). They scan source text on
   purpose. Deleting one reopens a hole that is already closed.
2. Do not run `git clean -fd` or `git checkout --` blindly: this repo's history
   contains gate tests that were once nearly lost that way.
3. `AGENTS.md` is agent-facing and long by design. Human-facing documentation
   lives in `README.md`, `CONTRIBUTING.md`, `SECURITY.md` and `docs/`.
   If you change a contract, change both sides in the same commit.
4. Before claiming anything is done, run the gates in
   `.github/PULL_REQUEST_TEMPLATE.md`. A green unit suite is not evidence that
   injection-side behaviour works; that requires the device matrix.
5. Never commit: build output, logs, acceptance reports, keystores, device
   captures. Local scratch belongs in `dev-local/` (gitignored).
