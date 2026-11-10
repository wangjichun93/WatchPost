import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'

/** 403 无权限页 (RBAC 角色不足时展示)。 */
export default function Forbidden() {
  const navigate = useNavigate()
  const roles = useAuthStore((s) => s.roles)
  return (
    <Result
      status="403"
      title="403"
      subTitle={`当前角色 (${roles.join('/') || '无'}) 无权访问此页面。如需权限请联系管理员。`}
      extra={<Button type="primary" onClick={() => navigate('/dashboard')}>返回大盘</Button>}
    />
  )
}
