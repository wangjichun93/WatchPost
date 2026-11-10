import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { ConfigProvider, App as AntApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { router } from '@/router'
import 'dayjs/locale/zh-cn'

// react-query 客户端: 失败重试1次, 窗口聚焦不自动重刷 (监控数据靠 WS 推送 + 轮询)
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 5000,
    },
  },
})

/**
 * 应用根组件: AntD 中文语境 + 主题 token + react-query + 路由。
 * 主题色 #165dff (与登录页/品牌一致)。
 */
export default function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#165dff',
          borderRadius: 6,
          fontSize: 14,
        },
      }}
    >
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  )
}
