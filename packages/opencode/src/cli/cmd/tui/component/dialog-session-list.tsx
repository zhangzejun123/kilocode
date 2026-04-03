import { useDialog } from "@tui/ui/dialog"
import { DialogSelect } from "@tui/ui/dialog-select"
import { useRoute } from "@tui/context/route"
import { useSync } from "@tui/context/sync"
import { createMemo, createSignal, createResource, onMount } from "solid-js"
import { Locale } from "@/util/locale"
import { useKeybind } from "../context/keybind"
import { useTheme } from "../context/theme"
import { useSDK } from "../context/sdk"
import { DialogSessionRename } from "./dialog-session-rename"
import { createDebouncedSignal } from "../util/signal"
import { Spinner } from "./spinner"

export function DialogSessionList() {
  const dialog = useDialog()
  const route = useRoute()
  const sync = useSync()
  const keybind = useKeybind()
  const { theme } = useTheme()
  const sdk = useSDK()

  const [toDelete, setToDelete] = createSignal<string>()
  const [search, setSearch] = createDebouncedSignal("", 150)
  const [global, setGlobal] = createSignal(false) // kilocode_change

  // kilocode_change start
  const [searchResults, searchActions] = createResource(
    () => ({ query: search(), global: global() }),
    async ({ query, global: all }) => {
      if (!query && !all) return undefined
      if (all) {
        const project = await sdk.client.project.current({}, { throwOnError: true })
        const result = await sdk.client.experimental.session.list(
          {
            projectID: project.data?.id,
            search: query || undefined,
            roots: true,
            limit: 30,
          },
          { throwOnError: true },
        )
        return result.data ?? []
      }
      const result = await sdk.client.session.list({ search: query || undefined, limit: 30 }, { throwOnError: true })
      return result.data ?? []
    },
  )
  // kilocode_change end

  const currentSessionID = createMemo(() => (route.data.type === "session" ? route.data.sessionID : undefined))

  const sessions = createMemo(() => {
    if (global() || search()) return searchResults() ?? [] // kilocode_change
    return searchResults() ?? sync.data.session
  })

  const options = createMemo(() => {
    const today = new Date().toDateString()
    const all = global() // kilocode_change
    return sessions()
      .filter((x) => x.parentID === undefined)
      .toSorted((a, b) => b.time.updated - a.time.updated)
      .map((x) => {
        const date = new Date(x.time.updated)
        let category = date.toDateString()
        if (category === today) {
          category = "Today"
        }
        const isDeleting = toDelete() === x.id
        const status = sync.data.session_status?.[x.id]
        const isWorking = status?.type === "busy"
        return {
          title: isDeleting ? `Press ${keybind.print("session_delete")} again to confirm` : x.title,
          bg: isDeleting ? theme.error : undefined,
          value: x.id,
          category,
          footer: Locale.time(x.time.updated),
          gutter: isWorking ? <Spinner /> : undefined,
        }
      })
  })

  onMount(() => {
    dialog.setSize("large")
  })

  return (
    <DialogSelect
      title={global() ? "Sessions (all worktrees)" : "Sessions"} // kilocode_change
      options={options()}
      skipFilter={true}
      current={currentSessionID()}
      onFilter={setSearch}
      onMove={() => {
        setToDelete(undefined)
      }}
      onSelect={(option) => {
        route.navigate({
          type: "session",
          sessionID: option.value,
        })
        dialog.clear()
      }}
      keybind={[
        {
          keybind: keybind.all.session_delete?.[0],
          title: "delete",
          onTrigger: async (option) => {
            if (toDelete() === option.value) {
              await sdk.client.session.delete({
                sessionID: option.value,
              })
              setToDelete(undefined)
              if (global() || search()) void searchActions.refetch() // kilocode_change
              return
            }
            setToDelete(option.value)
          },
        },
        {
          keybind: keybind.all.session_rename?.[0],
          title: "rename",
          onTrigger: async (option) => {
            const item = sessions().find((x) => x.id === option.value)
            dialog.replace(() => (
              <DialogSessionRename
                session={option.value}
                title={item?.title}
                onConfirm={() => {
                  if (global() || search()) void searchActions.refetch() // kilocode_change
                }}
              />
            ))
          },
        },
        // kilocode_change start
        {
          keybind: { name: "a", ctrl: true, meta: false, shift: false, leader: false },
          title: global() ? "recent" : "all",
          onTrigger: async () => {
            setToDelete(undefined)
            setGlobal((v) => !v)
          },
        },
        // kilocode_change end
      ]}
    />
  )
}
