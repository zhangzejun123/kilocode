---
title: "FAQ"
description: "Frequently asked questions about KiloClaw"
---

# FAQ

## How can I change my model?

You can change the model in two ways:

- **From chat** — Type `/model` in the Chat window within the OpenClaw Control UI to switch models directly.
- **From the dashboard** — Go to [https://app.kilo.ai/claw](https://app.kilo.ai/claw), select the model you want, and click **Save**. No redeploy is needed.

## Can I access the filesystem?

You can access instance files in `/root/.openclaw/` directly from the [KiloClaw Dashboard](https://app.kilo.ai/claw). This is useful for examining or restoring config files. You can also interact with files through your OpenClaw agent using its built-in file tools.

## Can I access my KiloClaw via SSH?

For security reasons, SSH access is currently disabled for all KiloClaw instances. Our primary goal is to provide a secure environment for all users, and restricting direct SSH access is one of the many measures we take to ensure the platform remains safe and protected for everyone.

## How can I update my OpenClaw?

Do **not** click **Update Now** inside the OpenClaw Control UI — this is not supported for KiloClaw instances and may break your setup.

Updates are managed by the KiloClaw platform team to ensure stability. When a new version is available, it will be announced in the **Changelog** on your dashboard. To apply the update, click **Upgrade & Redeploy** from the [KiloClaw Dashboard](/docs/kiloclaw/dashboard#redeploy).

## How do I migrate my workspace?

Whether you're migrating from another OpenClaw provider to KiloClaw, moving between KiloClaw instances (e.g., individual to org or vice versa), or leaving KiloClaw for another OpenClaw provider, the process is the same:

1. **Back up** your workspace files from your current Claw.
2. **Set up** your new provider or KiloClaw instance.
3. **Restore** the workspace files on the new Claw.
4. **Reconfigure** your integrations on the new instance.

### What migrates automatically vs. what doesn't

- **Migrates via backup/restore** — Workspace files, memory, and context. Your new Claw retains the same knowledge and preferences as before.
- **Requires manual reconfiguration** — Integrations and authentication tokens (Google, GitHub, GitLab, Linear, Telegram, Discord, Slack, etc.). These are tied to the instance and must be set up fresh.

### Backing up your workspace

Have your current Claw export the workspace before switching. Two options:

**GitHub export** — Ask your Claw:

> Create a new GitHub repo and push your entire workspace there with the `gh` CLI. Tell me the URL of the repo you used.

**Manual download** — Ask your Claw:

> Tar compress your workspace and push the file to Google Drive with the `gog` CLI. Then share the filename you used.

### Restoring on the new instance

On your new Claw, restore the workspace from whichever backup method you used:

**From GitHub:**

> The GitHub repo `<repo>` has a backup of your workspace. Pull the workspace from the repo with the `gh` CLI and overwrite the existing workspace directory with the repo's contents.

**From Google Drive:**

> The Google Drive file `<filename>` has a backup of your workspace. Pull the tar file from Google Drive with the `gog` CLI and overwrite the existing workspace directory with its contents.

{% callout type="note" %}
Replace `<repo>` or `<filename>` with the actual repository URL or filename from the backup step.
{% /callout %}

### Reconfiguring integrations

After restoring your workspace, set up each integration on the new instance:

- **Google Workspace** — Re-authenticate with Google. See [Google integration setup](/docs/kiloclaw/development-tools/google).
- **GitHub** — Re-authenticate with GitHub. See [GitHub integration setup](/docs/kiloclaw/development-tools/github).
- **GitLab** — Re-add your GitLab token or SSH key.
- **Linear** — Re-add your Linear API token.
- **Telegram / Discord / Slack** — Re-enter your bot tokens in the [KiloClaw Dashboard Settings](/docs/kiloclaw/dashboard#channels) and redeploy.

See [Development Tools](/docs/kiloclaw/development-tools) and [Chat Platforms](/docs/kiloclaw/chat-platforms) for full setup guides.
