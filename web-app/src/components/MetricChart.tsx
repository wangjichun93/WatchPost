import { useMemo } from 'react'
import ReactECharts from 'echarts-for-react'
import type { EChartsOption } from 'echarts'

interface Point {
  ts: number
  value?: number | null
  avg?: number | null
  min?: number | null
  max?: number | null
}

interface MetricChartProps {
  title?: string
  unit?: string
  points: Point[]
  height?: number
  color?: string
}

/**
 * 指标历史趋势图 (ECharts)。对应 PRD 06: /monitors/:id 历史趋势图。
 * 数据来自 TSDB 降采样查询 (queryInterval), 支持 min/max/avg 区间带。
 */
export default function MetricChart({ title, unit, points, height = 220, color = '#165dff' }: MetricChartProps) {
  const option = useMemo<EChartsOption>(() => {
    const xs = points.map((p) => p.ts)
    const hasBand = points.some((p) => p.min != null && p.max != null && p.min !== p.max)
    return {
      title: title ? { text: title, left: 8, top: 4, textStyle: { fontSize: 13, fontWeight: 500 } } : undefined,
      tooltip: {
        trigger: 'axis',
        valueFormatter: (v) => (v == null ? '-' : `${Number(v).toFixed(2)}${unit ? ' ' + unit : ''}`),
      },
      grid: { left: 48, right: 16, top: title ? 40 : 16, bottom: 28 },
      xAxis: {
        type: 'time',
        axisLabel: { fontSize: 10, hideOverlap: true },
      },
      yAxis: {
        type: 'value',
        scale: true,
        axisLabel: { fontSize: 10, formatter: unit ? `{value} ${unit}` : '{value}' },
        splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } },
      },
      series: hasBand
        ? [
            { name: 'max', type: 'line', data: xs.map((x, i) => [x, points[i].max]), lineStyle: { opacity: 0 }, stack: 'band', symbol: 'none', silent: true },
            { name: 'min', type: 'line', data: xs.map((x, i) => [x, points[i].min]), lineStyle: { opacity: 0 }, areaStyle: { color: 'rgba(22,93,255,0.12)' }, stack: 'band', symbol: 'none', silent: true },
            { name: 'avg', type: 'line', data: xs.map((x, i) => [x, points[i].avg]), smooth: true, symbol: 'none', lineStyle: { color, width: 2 } },
          ]
        : [
            { name: title ?? 'value', type: 'line', data: xs.map((x, i) => [x, points[i].value ?? points[i].avg]),
              smooth: true, symbol: 'none', lineStyle: { color, width: 2 },
              areaStyle: { color: 'rgba(22,93,255,0.08)' } },
          ],
    }
  }, [points, title, unit, color])

  return <ReactECharts option={option} style={{ height }} notMerge lazyUpdate />
}
