# Remote Control: drive this Claude Code session from your phone or a browser

Machine: `claude-driver` (Linux cloud box), repo `~/projects/vibe/2021`.
Claude Code version on the box: 2.1.273. Source: https://code.claude.com/docs/en/remote-control

Remote Control keeps the session running **on this box**. Your phone or browser
is just a second keyboard/screen for it. The `claude` process must stay alive,
so start it inside `tmux` when you are logged in over SSH.

---

## Option A: turn on Remote Control in the session that is ALREADY running

In the Claude Code prompt (the same place you type messages), type:

```
/rc
```

or, to give it a name you can find in the session list:

```
/remote-control vibe-2021
```

First time only, a dialog asks "Enable Remote Control?" Choose **Enable Remote Control**.

Claude Code then prints a session URL and a QR code, and the footer shows an
`/rc active` indicator. Open the URL on any device, or scan the QR code with
the Claude phone app.

If the session is not in tmux and your SSH connection drops, the session dies
and Remote Control goes offline. Use Option B or C for anything long-running.

---

## Option B (recommended): fresh interactive session inside tmux, with Remote Control on

```
ssh <you>@claude-driver
tmux new -s claude
cd ~/projects/vibe/2021
claude --rc "vibe-2021"
```

`--rc` is short for `--remote-control`. You get a normal interactive session in
the terminal that is also reachable from claude.ai/code and the phone app.

Detach from tmux with `Ctrl-b` then `d`. The session keeps running.
Reattach later with:

```
tmux attach -t claude
```

---

## Option C: server mode (no local typing, multiple remote sessions)

```
tmux new -s claude
cd ~/projects/vibe/2021
claude remote-control --name "vibe-2021"
```

First time it asks `Enable Remote Control? (y/n)`. Answer `y`.

It prints the session URL. Press **spacebar** to toggle the QR code.
Stop it with `Ctrl-C`. Useful flags (put them AFTER `remote-control`):

| Flag | Effect |
|---|---|
| `--name "vibe-2021"` | Session title in the claude.ai/code list |
| `--continue` | Bring back the session this directory last served (works ~4 hours after stopping) |
| `--session-id <id>` | Bring back one specific session. The id is the part of the URL after `/code/` |
| `--permission-mode acceptEdits` | Starting permission mode for the sessions it serves |

Note: `claude remote-control --help` starts the server instead of printing
help if you are not signed in with an eligible account, so don't rely on it.

---

## Connecting from another device

1. Open the printed session URL in any browser, **or**
2. Scan the QR code with the Claude iOS/Android app, **or**
3. Go to https://claude.ai/code (or tap **Code** in the phone app) and pick
   the session by name from the list.

Don't have the app? Type `/mobile` in Claude Code to get a QR code that opens
the right app store.

---

## Prerequisites and troubleshooting

* You must be logged in via claude.ai (Pro/Max/Team/Enterprise), not an API key.
  If in doubt, run `claude` and type `/login`.
* "Remote Control requires a claude.ai subscription": you are logged in with an
  API key or an ineligible account. Run `/login` and sign in through claude.ai.
* Session went offline: the `claude` process on the box stopped (SSH dropped,
  terminal closed). Reattach with `tmux attach -t claude`, or resume with
  `claude --continue` then `/rc`, or `claude remote-control --continue`.
* "Another connection took over this session": another device or terminal has
  it. Run `/rc` only if you want it back.
* Connection failed but process still up: type `/rc` again to reconnect.
* Push notifications for permission prompts: run `/config` in Claude Code and
  enable **Push when actions required**.

---

## Turn it on for every session automatically

Run `/config` inside Claude Code and enable the Remote Control auto-connect
option, or set it in the desktop app under
**Settings > Claude Code > Enable remote control by default**.
