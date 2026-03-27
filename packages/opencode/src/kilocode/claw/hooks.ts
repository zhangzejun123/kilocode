// kilocode_change - new file

/**
 * KiloClaw SolidJS reactive helpers
 *
 * Provides reactive state management for polling instance status
 * and managing the Stream Chat connection lifecycle.
 */

import { createSignal, onMount, onCleanup } from "solid-js"
import type { ClawStatus, ChatCredentials, ChatMessage } from "./types"
import { connect, history, presence, type ClawChatClient } from "./client"
import { Log } from "@/util/log"

const log = Log.create({ service: "claw-chat" })

/**
 * Poll the KiloClaw instance status every `interval` ms.
 * Returns a reactive signal with the latest status.
 */
export function createClawStatus(sdk: any, interval = 10_000) {
  const [status, setStatus] = createSignal<ClawStatus | null>(null)
  const [error, setError] = createSignal<string | null>(null)
  const [loading, setLoading] = createSignal(true)

  onMount(() => {
    const poll = async () => {
      const res = await sdk.client.kilo.claw.status().catch(() => null)
      if (res?.data && !res.error) {
        setStatus(res.data as ClawStatus)
        setError(null)
      } else if (res?.error) {
        setError(typeof res.error === "string" ? res.error : (res.error?.error ?? "Unknown error"))
      }
      setLoading(false)
    }
    poll()
    const timer = setInterval(poll, interval)
    onCleanup(() => clearInterval(timer))
  })

  return { status, error, loading }
}

/**
 * Fetch Stream Chat credentials and manage the chat connection.
 * Returns reactive signals for messages, bot presence, and connection state.
 */
export function createClawChat(sdk: any) {
  const [messages, setMessages] = createSignal<ChatMessage[]>([])
  const [online, setOnline] = createSignal(false)
  const [connected, setConnected] = createSignal(false)
  const [error, setError] = createSignal<string | null>(null)
  const [loading, setLoading] = createSignal(true)

  const MAX_MESSAGES = 500
  let chat: ClawChatClient | null = null

  const send = async (text: string): Promise<boolean> => {
    if (!chat) return false
    try {
      await chat.send(text)
      return true
    } catch (err: any) {
      log.error("send failed", { error: err?.message ?? String(err) })
      setError("Failed to send message")
      return false
    }
  }

  onMount(async () => {
    log.info("fetching credentials")
    const res = await sdk.client.kilo.claw.chatCredentials().catch((e: any) => {
      log.error("chatCredentials() threw", { error: e?.message ?? String(e) })
      return null
    })

    log.info("credentials response", {
      hasData: String(!!res?.data),
      dataIsNull: String(res?.data === null),
      error: res?.error ? String(res.error) : "none",
      dataKeys: res?.data ? Object.keys(res.data).join(",") : "none",
    })

    if (!res?.data || res.error) {
      setError(res?.data === null ? null : "Failed to fetch chat credentials")
      setLoading(false)
      return
    }

    const creds = res.data as ChatCredentials

    try {
      log.info("calling connect()")
      chat = await connect(creds)
      log.info("connect() succeeded")

      // Load existing messages
      const bot = `bot-${creds.channelId.replace(/^default-/, "")}`
      const existing = await history(chat.channel, bot)
      log.info("loaded history", { count: existing.length })
      setMessages(existing)

      // Read initial bot presence from channel member state
      setOnline(presence(chat.channel, bot))

      // Subscribe to new messages
      const unsub = chat.onMessage((msg) => {
        setMessages((prev) => {
          const next = [...prev, msg]
          return next.length > MAX_MESSAGES ? next.slice(-MAX_MESSAGES) : next
        })
      })

      // Subscribe to message updates (bot streams token-by-token via message.updated)
      const unsubUpdated = chat.onMessageUpdated((msg) => {
        setMessages((prev) => {
          const idx = prev.findIndex((m) => m.id === msg.id)
          if (idx === -1) {
            const next = [...prev, msg]
            return next.length > MAX_MESSAGES ? next.slice(-MAX_MESSAGES) : next
          }
          const next = [...prev]
          next[idx] = msg
          return next
        })
      })

      // Subscribe to bot presence changes
      const unsubPresence = chat.onPresence(setOnline)

      setConnected(true)
      setLoading(false)

      onCleanup(async () => {
        unsub()
        unsubUpdated()
        unsubPresence()
        if (chat) await chat.disconnect()
        chat = null
      })
    } catch (err: any) {
      log.error("connect failed", {
        error: err?.message ?? String(err),
        name: err?.name,
        code: err?.code,
        stack: err?.stack?.split("\n").slice(0, 5).join("\n"),
      })
      setError(err?.message ?? "Failed to connect to chat")
      setLoading(false)
    }
  })

  return { messages, online, connected, error, loading, send }
}
