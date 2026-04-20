param(
  [string]$UpstreamUrl = "git@github.com:Kilo-Org/kilocode.git",
  [string]$Branch = "main",
  [Alias("Version", "UpstreamTag")]
  [string]$Tag,
  [switch]$NoPush
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
  $PSNativeCommandUseErrorActionPreference = $false
}

$SelfPath = "script/sync-upstream-no-lfs.ps1"
$SelfBody = Get-Content $PSCommandPath -Raw
$SyncRef = "refs/kilo-sync/tag"
$TempBranch = "__kilo_sync_no_lfs__"
$Stash = $null
$Temp = $null

function Log([string]$msg) {
  Write-Host "[sync-no-lfs] $msg"
}

function Fail([string]$msg) {
  throw "[sync-no-lfs] $msg"
}

function Run(
  [Parameter(Mandatory = $true)][string]$cmd,
  [switch]$AllowFail,
  [string]$Dir
) {
  Log $cmd
  $path = if ($Dir) { $Dir } else { (Get-Location).Path }
  Push-Location $path
  try {
    if ($AllowFail) {
      Invoke-Expression $cmd 2>$null
    } else {
      Invoke-Expression $cmd
    }
    if (-not $AllowFail -and $LASTEXITCODE -ne 0) {
      Fail "command failed ($LASTEXITCODE): $cmd"
    }
    return $LASTEXITCODE
  } finally {
    Pop-Location
  }
}

function Out(
  [Parameter(Mandatory = $true)][string]$cmd,
  [switch]$AllowFail,
  [string]$Dir
) {
  Log $cmd
  $path = if ($Dir) { $Dir } else { (Get-Location).Path }
  Push-Location $path
  try {
    if ($AllowFail) {
      $text = Invoke-Expression $cmd 2>$null | Out-String
    } else {
      $text = Invoke-Expression $cmd | Out-String
    }
    if (-not $AllowFail -and $LASTEXITCODE -ne 0) {
      Fail "command failed ($LASTEXITCODE): $cmd"
    }
    return $text.Trim()
  } finally {
    Pop-Location
  }
}

function Ensure-RepoRoot() {
  $root = Out "git rev-parse --show-toplevel"
  $here = (Get-Location).Path
  if ($root -ne $here) {
    Log "switching to repo root: $root"
    Set-Location $root
  }
}

function Ensure-Branch() {
  $cur = Out "git branch --show-current"
  if ($cur -ne $Branch) {
    Fail "current branch is '$cur'. Switch to '$Branch' before running this script."
  }
}

function Ensure-Remote([string]$name, [string]$url) {
  $remotes = Out "git remote"
  $has = $false
  foreach ($line in ($remotes -split "`r?`n")) {
    if ($line.Trim() -eq $name) {
      $has = $true
      break
    }
  }

  if (-not $has) {
    Run "git remote add $name $url"
    return
  }

  $cur = Out "git remote get-url $name"
  if ($cur -ne $url) {
    Run "git remote set-url $name $url"
  }
}

function Ensure-Origin() {
  $remotes = Out "git remote"
  $has = $false
  foreach ($line in ($remotes -split "`r?`n")) {
    if ($line.Trim() -eq "origin") {
      $has = $true
      break
    }
  }

  if (-not $has) {
    Fail "origin remote is missing"
  }

  return (Out "git remote get-url origin")
}

function Validate-Remotes([string]$origin, [string]$upstream) {
  Log "origin:   $origin"
  Log "upstream: $upstream"

  if (-not $origin) {
    Fail "origin remote is missing"
  }

  if (-not $upstream) {
    Fail "upstream remote is missing"
  }

  if ($origin -eq $upstream) {
    Fail "origin and upstream point to the same URL. Refusing to push to upstream by mistake."
  }
}

function Save-Dirty() {
  $status = Out "git status --porcelain"
  if (-not $status) {
    return
  }

  $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $Stash = "sync-no-lfs-$stamp"
  Run "git stash push --include-untracked -m $Stash"
}

function Restore-Dirty() {
  if (-not $Stash) {
    return
  }

  $list = Out "git stash list"
  $hit = ($list -split "`r?`n" | Where-Object { $_ -match [regex]::Escape($Stash) } | Select-Object -First 1)
  if (-not $hit) {
    Log "stash '$Stash' was not found during restore"
    return
  }

  $ref = $hit.Split(":")[0]
  Run "git stash pop `"$ref`"" -AllowFail
  $Stash = $null
}

function Get-LatestTag() {
  $rows = Out "git ls-remote --tags --refs upstream v*"
  if (-not $rows) {
    Fail "no tags found on upstream"
  }

  $tags = @{}
  foreach ($row in ($rows -split "`r?`n")) {
    if (-not $row) {
      continue
    }

    $parts = $row -split "\s+"
    if ($parts.Length -lt 2) {
      continue
    }

    $ref = $parts[1]
    if (-not $ref.StartsWith("refs/tags/")) {
      continue
    }

    $name = $ref.Substring(10)
    if ($name -notmatch "^v(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)(?:-(?<pre>.+))?$") {
      continue
    }

    if ($Matches["pre"]) {
      continue
    }

    if (-not $tags.ContainsKey($name)) {
      $tags[$name] = [pscustomobject]@{
        Name  = $name
        Major = [int]$Matches["major"]
        Minor = [int]$Matches["minor"]
        Patch = [int]$Matches["patch"]
      }
    }
  }

  $pick = $tags.Values |
    Sort-Object @{ Expression = "Major"; Descending = $true }, @{ Expression = "Minor"; Descending = $true }, @{ Expression = "Patch"; Descending = $true } |
    Select-Object -First 1

  if (-not $pick) {
    Fail "no matching release tags found on upstream"
  }

  return $pick
}

function Resolve-Target() {
  if ($Tag) {
    $rows = Out "git ls-remote --tags --refs upstream refs/tags/$Tag"
    if (-not $rows) {
      Fail "upstream tag '$Tag' not found"
    }

    return [pscustomobject]@{
      Name = $Tag
    }
  }

  return Get-LatestTag
}

function Restore-Self([string]$dir) {
  $path = Join-Path $dir $SelfPath
  $parent = Split-Path $path -Parent
  if ($parent) {
    New-Item -ItemType Directory -Force $parent | Out-Null
  }
  Set-Content -Path $path -Value $SelfBody -NoNewline
}

function Get-LfsPatterns([string]$dir) {
  $path = Join-Path $dir ".gitattributes"
  if (-not (Test-Path $path)) {
    return @()
  }

  $rules = @()
  foreach ($line in Get-Content $path) {
    $item = $line.Trim()
    if (-not $item) {
      continue
    }
    if ($item.StartsWith("#")) {
      continue
    }
    if ($item -notmatch "filter=lfs") {
      continue
    }

    $parts = $item -split "\s+"
    if ($parts.Length -gt 0) {
      $rules += $parts[0]
    }
  }

  return @($rules | Select-Object -Unique)
}

function Remove-Pattern([string]$dir, [string]$pattern) {
  if ($pattern -match '^\*\.(.+)$') {
    $ext = $Matches[1]
    Get-ChildItem -Path $dir -Recurse -File -Filter "*.$ext" | Remove-Item -Force
    return
  }

  if ($pattern -match '^(.*)/\*\*/\*\.(.+)$') {
    $root = Join-Path $dir $Matches[1]
    $ext = $Matches[2]
    if (Test-Path $root) {
      Get-ChildItem -Path $root -Recurse -File -Filter "*.$ext" | Remove-Item -Force
    }
    return
  }

  Fail "unsupported LFS pattern: $pattern"
}

function Remove-LfsFiles([string]$dir, [string[]]$patterns) {
  foreach ($pattern in $patterns) {
    Remove-Pattern $dir $pattern
  }
}

function Fix-Gitattributes([string]$dir) {
  $path = Join-Path $dir ".gitattributes"
  if (-not (Test-Path $path)) {
    return
  }

  $old = Get-Content $path -Raw
  $lines = @(
    ($old -split "`r?`n" | Where-Object {
      $_ -and $_ -notmatch "filter=lfs"
    })
  )

  $new = ($lines -join "`n")
  if ($old.EndsWith("`r`n")) {
    $new += "`r`n"
  } elseif ($old.EndsWith("`n")) {
    $new += "`n"
  }

  Set-Content -Path $path -Value $new -NoNewline
}

function Set-Tag([string]$name) {
  $have = Out "git tag --list $name"
  if ($have) {
    Run "git tag -f $name HEAD"
    return
  }

  Run "git tag $name HEAD"
}

function Remove-Deleted([string]$commit) {
  $list = Out "git diff --name-only --diff-filter=D HEAD $commit"
  if (-not $list) {
    return
  }

  foreach ($item in ($list -split "`r?`n")) {
    if (-not $item) {
      continue
    }

    Run "git rm -r --ignore-unmatch -- `"$item`""
  }
}

function Cleanup() {
  if ($Temp) {
    Run "git worktree remove `"$Temp`" --force" -AllowFail
  }

  $has = Out "git branch --list $TempBranch" -AllowFail
  if ($has) {
    Run "git branch -D $TempBranch" -AllowFail
  }

  Run "git update-ref -d $SyncRef" -AllowFail
}

Log "This workflow appends an incremental no-lfs sync commit onto '$Branch'."
Log "Upstream history is not merged. Only the target tag's sanitized file tree is applied."

Ensure-RepoRoot
Ensure-Branch
Ensure-Remote "upstream" $UpstreamUrl
 $origin = Ensure-Origin
 $upstream = Out "git remote get-url upstream"
 Validate-Remotes $origin $upstream
Save-Dirty

try {
  $before = Out "git rev-parse HEAD"
  Run "git fetch origin $Branch"

  $target = Resolve-Target
  Log "selected upstream tag: $($target.Name)"
  Run "git fetch upstream refs/tags/$($target.Name):$SyncRef"

  $base = Out "git rev-parse $SyncRef"
  $Temp = Join-Path ([System.IO.Path]::GetTempPath()) "kilo-sync-$((Get-Date).ToString('yyyyMMddHHmmss'))"

  Run "git worktree add --detach `"$Temp`" $SyncRef"
  Run "git checkout --orphan $TempBranch" -Dir $Temp

  Restore-Self $Temp
  $patterns = Get-LfsPatterns $Temp
  if ($patterns.Count -gt 0) {
    Log "removing $($patterns.Count) lfs pattern(s)"
    Remove-LfsFiles $Temp $patterns
  }
  Fix-Gitattributes $Temp

  Run "git add -A" -Dir $Temp
  $msg = "chore: sync upstream $($target.Name) without lfs"
  Run "git commit -m `"$msg`"" -Dir $Temp
  $snap = Out "git rev-parse HEAD" -Dir $Temp

  Run "git checkout $snap -- ."
  Remove-Deleted $snap
  Run "git add -A"

  $dirty = Run "git diff --cached --quiet" -AllowFail
  if ($dirty -eq 0) {
    Log "No content changes relative to current '$Branch'."
    Set-Tag $target.Name
  } else {
    Run "git commit -m `"$msg`""
    Set-Tag $target.Name
  }

  $left = Out "git lfs ls-files" -AllowFail
  if ($LASTEXITCODE -eq 0 -and $left) {
    Fail "repository still contains LFS-tracked files after sync"
  }

  $after = Out "git rev-parse HEAD"
  Log "Sync complete."
  Log "Tag:    $($target.Name)"
  Log "Base:   $base"
  Log "Before: $before"
  Log "After:  $after"

  if ($NoPush) {
    Log "Skipping push (--NoPush)."
  } else {
    Run "git push --force-with-lease origin $Branch"
    Run "git push --force origin refs/tags/$($target.Name)"
    Log "Pushed '$Branch' and tag '$($target.Name)' to origin."
  }
} finally {
  Cleanup
  Restore-Dirty
}
