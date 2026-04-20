# 升级说明

这个仓库是 `Kilo-Org/kilocode.git` 的一个去 LFS fork。

它的升级方式不是直接合并 upstream 历史，而是：

1. 读取指定的 upstream tag
2. 删除所有被 LFS 规则匹配的文件
3. 清理 `.gitattributes` 中的 LFS 规则
4. 把清理后的文件树应用到当前 `main`
5. 生成一条普通的增量提交
6. 把 `main` 和对应 tag 推送到 `origin`

这样可以在不能使用 Git LFS 的环境中继续维护这个 fork，同时保留你自己的同步历史。

## 前置条件

运行脚本之前，请确认：

- 已安装 Git
- 可使用 PowerShell
- 当前仓库的 `origin` 指向你自己的 fork
- 当前机器可以访问：
  - `origin`：你的 fork
  - `upstream`：`git@github.com:Kilo-Org/kilocode.git`
- 当前账号对 `origin` 有推送权限

## 新机器使用方式

在一台新机器上，通常只需要：

```powershell
git clone git@github.com:zhangzejun123/kilocode.git
cd kilocode
```

然后执行升级脚本。

同步 upstream 最新稳定 tag：

```powershell
powershell -ExecutionPolicy Bypass -File script/sync-upstream-no-lfs.ps1
```

同步指定版本：

```powershell
powershell -ExecutionPolicy Bypass -File script/sync-upstream-no-lfs.ps1 -Tag v7.2.14
```

脚本也支持这两个别名：

```powershell
powershell -ExecutionPolicy Bypass -File script/sync-upstream-no-lfs.ps1 -Version v7.2.14
powershell -ExecutionPolicy Bypass -File script/sync-upstream-no-lfs.ps1 -UpstreamTag v7.2.14
```

## 脚本会做哪些检查

脚本启动后会先检查这些条件：

- 当前分支必须是 `main`
- 自动检查并设置 `upstream`
- 检查 `origin` 是否存在
- 打印当前 `origin` 和 `upstream` 的 URL
- 如果 `origin` 和 `upstream` 指向同一个地址，会直接报错退出

这样可以避免把内容误推到上游仓库。

## 日常升级流程

如果你已经有一个可用的 fork，并且历史已经按这个方案初始化好了，那么后续升级通常只要执行一条命令：

```powershell
powershell -ExecutionPolicy Bypass -File script/sync-upstream-no-lfs.ps1
```

或者指定目标版本：

```powershell
powershell -ExecutionPolicy Bypass -File script/sync-upstream-no-lfs.ps1 -Tag v7.2.14
```

脚本会在当前 `main` 基础上追加一条新的同步提交，不会再重建整个历史。

## 首次初始化建议

如果你想从一个干净的历史开始，推荐流程是：

1. 先把 fork 历史清空一次
2. 运行脚本初始化第一个版本，例如 `v7.2.12`
3. 再运行脚本升级到下一个版本，例如 `v7.2.14`

完成后，后续就只需要继续运行这个脚本做增量升级。

## 当前历史模型

当前这套方案的目标历史结构类似这样：

```text
v7.2.12 的同步提交
v7.2.14 的同步提交
v7.2.15 的同步提交
...
```

也就是说：

- 不保留 upstream 原始历史
- 只保留你自己的无 LFS 同步历史

## 只验证不推送

如果你想先看脚本能不能正常跑通，但不想更新远端，可以加 `-NoPush`：

```powershell
powershell -ExecutionPolicy Bypass -File script/sync-upstream-no-lfs.ps1 -Tag v7.2.14 -NoPush
```

## 运行建议

- 虽然脚本会在必要时自动 stash 当前未提交改动，但仍然建议在干净工作区里运行
- 每次同步时，脚本会强制更新当前同步到的 tag
- 这个流程不依赖 Git LFS

## 常见问题

### 1. 报错 `origin and upstream point to the same URL`

说明当前仓库的 `origin` 和 `upstream` 指到了同一个地址。

先检查：

```powershell
git remote -v
```

然后把 `origin` 改回你自己的 fork，例如：

```powershell
git remote set-url origin git@github.com:zhangzejun123/kilocode.git
```

### 2. 想查看当前同步历史

可以执行：

```powershell
git log --oneline --decorate -10
```

### 3. 想先确认当前远端状态

可以执行：

```powershell
git remote -v
git branch -vv
git tag --list
```

### 4. 本地 hook 阻止 push

如果本地存在 `pre-push` 等 hook，可能会拦住脚本最后的推送。

这种情况下：

- 可以在一个不会被本地 hook 干扰的克隆仓库中执行升级
- 或者按你自己的流程临时绕过 hook

## 建议

如果这个 fork 只是用于内网或无 LFS 环境下的持续同步，建议长期保持下面这套约定：

- `origin` 永远指向你自己的 fork
- `upstream` 永远指向 `Kilo-Org/kilocode.git`
- 所有升级都通过 `script/sync-upstream-no-lfs.ps1` 完成
- 不要手工把 upstream 原始历史 merge 回来

这样可以最大限度避免 LFS 问题重新进入你的分支历史。
