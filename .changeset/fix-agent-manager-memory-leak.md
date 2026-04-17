---
"kilo-code": patch
---

Fix a native memory leak on Windows where `kilo serve` would grow to several GB of RAM within minutes of opening the Agent Manager. Git diff polling now runs directly in the extension host instead of routing through the CLI subprocess, and the diff detail view caps per-file reads at 20 MB to prevent memory spikes when opening very large files.
