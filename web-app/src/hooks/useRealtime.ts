import { useEffect, useRef, useCallback } from 'react'
import type { RealtimeMessage } from '@/types'
import { tokenStore } from '@/api/client'

/**
 * 实时推送 Hook: 连接 ws://host/ws/realtime, 接收采集结果与告警事件。
 * 自动重连 (指数退避), 心跳保活; 令牌失效时不连接。
 * 对应架构设计 §11: 大盘/详情页实时指标与告警推送。
 */
export function useRealtime(onMessage: (msg: RealtimeMessage) => void, enabled = true) {
  const wsRef = useRef<WebSocket | null>(null)
  const retryRef = useRef(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const handlerRef = useRef(onMessage)
  handlerRef.current = onMessage

  const connect = useCallback(() => {
    if (!enabled || !tokenStore.get()) return
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${window.location.host}/ws/realtime`
    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      retryRef.current = 0
      // 心跳保活
      heartbeatRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.send('ping')
      }, 30000)
    }
    ws.onmessage = (ev) => {
      if (ev.data === 'pong') return
      try {
        handlerRef.current(JSON.parse(ev.data) as RealtimeMessage)
      } catch {
        /* 忽略非 JSON 消息 */
      }
    }
    ws.onclose = () => {
      if (heartbeatRef.current) clearInterval(heartbeatRef.current)
      // 指数退避重连 (上限 30s)
      if (enabled && tokenStore.get()) {
        const delay = Math.min(30000, 1000 * 2 ** retryRef.current++)
        timerRef.current = setTimeout(connect, delay)
      }
    }
    ws.onerror = () => ws.close()
  }, [enabled])

  useEffect(() => {
    connect()
    return () => {
      enabled = false
      if (timerRef.current) clearTimeout(timerRef.current)
      if (heartbeatRef.current) clearInterval(heartbeatRef.current)
      wsRef.current?.close()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled])

  return wsRef
}
