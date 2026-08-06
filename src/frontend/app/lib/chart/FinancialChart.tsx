import { format, line as d3Line, select, timeFormat, zoom, zoomIdentity, type ZoomTransform } from "d3";
import { useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { findNearestBarIndex } from "./crosshair";
import type { ChartTypeRegistry } from "./registries/chart-types";
import {
  defaultChartTypeRegistry,
  defaultDrawingToolRegistry,
  defaultIndicatorRegistry,
} from "./registries/defaults";
import type { DrawingToolRegistry } from "./registries/drawing-tools";
import type { IndicatorRegistry } from "./registries/indicators";
import { createPriceScale, createVolumeScale, createXScale } from "./scales";
import { sumiTheme, type ChartTheme } from "./theme";
import type { Bar } from "./types";

const MARGIN = { top: 8, right: 12, bottom: 24, left: 56 };
const VOLUME_HEIGHT_RATIO = 0.2;
const PANEL_GAP = 8;
const DEFAULT_SIZE = { width: 600, height: 400 };

const priceFormat = format(",.2f");
const volumeFormat = format(",.0f");
const axisDateFormat = timeFormat("%b %d");
const readoutDateFormat = timeFormat("%b %d, %Y");

function pickTickIndices(barCount: number, maxTicks: number): number[] {
  if (barCount === 0) return [];
  if (barCount <= maxTicks) return Array.from({ length: barCount }, (_, i) => i);
  const step = (barCount - 1) / (maxTicks - 1);
  return Array.from({ length: maxTicks }, (_, i) => Math.round(i * step));
}

export type FinancialChartProps = {
  bars: Bar[];
  width?: number;
  height?: number;
  chartType?: string;
  indicators?: { id: string; config?: unknown }[];
  showVolume?: boolean;
  theme?: ChartTheme;
  chartTypeRegistry?: ChartTypeRegistry;
  indicatorRegistry?: IndicatorRegistry;
  drawingToolRegistry?: DrawingToolRegistry;
  activeDrawingTool?: string;
};

export function FinancialChart({
  bars,
  width,
  height,
  chartType,
  indicators = [],
  showVolume = true,
  theme = sumiTheme,
  chartTypeRegistry = defaultChartTypeRegistry,
  indicatorRegistry = defaultIndicatorRegistry,
  drawingToolRegistry = defaultDrawingToolRegistry,
  activeDrawingTool,
}: FinancialChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const overlayRef = useRef<SVGRectElement>(null);

  const [autoSize, setAutoSize] = useState(DEFAULT_SIZE);
  const [transform, setTransform] = useState<ZoomTransform>(zoomIdentity);
  const [hoverIndex, setHoverIndex] = useState<number | undefined>(undefined);
  const [drawings, setDrawings] = useState<{ pluginId: string; instance: unknown }[]>([]);
  const [entered, setEntered] = useState(false);

  const size = { width: width ?? autoSize.width, height: height ?? autoSize.height };
  const innerWidth = Math.max(size.width - MARGIN.left - MARGIN.right, 0);
  const innerHeight = Math.max(size.height - MARGIN.top - MARGIN.bottom, 0);
  const volumeHeight = showVolume ? innerHeight * VOLUME_HEIGHT_RATIO : 0;
  const priceHeight = Math.max(innerHeight - (showVolume ? volumeHeight + PANEL_GAP : 0), 0);
  const volumeTop = priceHeight + PANEL_GAP;

  // Redraws at the container's actual size on resize. Falls back to
  // DEFAULT_SIZE (and never observes) in environments/tests without
  // ResizeObserver - the `width`/`height` props are the escape hatch for
  // deterministic sizing there.
  useEffect(() => {
    if (width !== undefined && height !== undefined) return;
    const node = containerRef.current;
    if (!node || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver((entries) => {
      const rect = entries[0]?.contentRect;
      if (rect && rect.width > 0 && rect.height > 0) {
        setAutoSize({ width: rect.width, height: rect.height });
      }
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [width, height]);

  const baseXScale = useMemo(() => createXScale(bars.length, innerWidth), [bars.length, innerWidth]);
  const xScale = useMemo(() => transform.rescaleX(baseXScale), [transform, baseXScale]);
  const yScale = useMemo(() => createPriceScale(bars, priceHeight), [bars, priceHeight]);
  const volumeScale = useMemo(() => createVolumeScale(bars, volumeHeight), [bars, volumeHeight]);

  // Wheel-to-zoom, drag-to-pan across the time axis only - the price axis
  // never rescales. Kept in a ref-driven effect (d3 owns the gesture math
  // and dispatches a transform; React state and JSX own the actual DOM).
  useEffect(() => {
    const node = overlayRef.current;
    if (!node) return;
    const behavior = zoom<SVGRectElement, unknown>()
      .scaleExtent([1, Math.max(bars.length, 4)])
      .translateExtent([
        [0, 0],
        [innerWidth, innerHeight],
      ])
      .extent([
        [0, 0],
        [innerWidth, innerHeight],
      ])
      .on("zoom", (event) => setTransform(event.transform));
    select(node).call(behavior);
    return () => {
      select(node).on(".zoom", null);
    };
  }, [bars.length, innerWidth, innerHeight]);

  // Fades price-panel content in whenever the bars array changes (including
  // first mount). Duration/easing are an implementation judgment call, not
  // covered by an acceptance criterion.
  useEffect(() => {
    setEntered(false);
    const id = window.setTimeout(() => setEntered(true), 0);
    return () => window.clearTimeout(id);
  }, [bars]);

  function localPoint(event: ReactPointerEvent) {
    const rect = event.currentTarget.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }

  function handlePointerMove(event: ReactPointerEvent<SVGRectElement>) {
    const { x } = localPoint(event);
    setHoverIndex(findNearestBarIndex(xScale, bars.length, x));
  }

  function handlePointerLeave() {
    setHoverIndex(undefined);
  }

  function handlePointerDown(event: ReactPointerEvent<SVGRectElement>) {
    if (!activeDrawingTool) return;
    const plugin = drawingToolRegistry.get(activeDrawingTool);
    if (!plugin) return;
    const { x, y } = localPoint(event);
    const index = Math.round(xScale.invert(x));
    const price = yScale.invert(y);
    const instance = plugin.createInstance({ index, price });
    setDrawings((prev) => [...prev, { pluginId: activeDrawingTool, instance }]);
  }

  if (bars.length === 0) {
    return (
      <div ref={containerRef} style={{ width: "100%", height: "100%" }}>
        <p role="status" style={{ color: theme.text, background: theme.background }}>
          No data available
        </p>
      </div>
    );
  }

  // Falls back to whichever plugin is first in the given registry rather
  // than a hardcoded id, so the engine's own source never names a specific
  // chart type (see registries/defaults.ts for what "first" resolves to by
  // default).
  const resolvedChartType = chartType ?? chartTypeRegistry.list()[0]?.id;
  const chartPlugin = resolvedChartType ? chartTypeRegistry.get(resolvedChartType) : undefined;
  const tickIndices = pickTickIndices(bars.length, 6);
  const hoverBar = hoverIndex !== undefined ? bars[hoverIndex] : undefined;

  return (
    <div ref={containerRef} style={{ width: "100%", height: "100%" }}>
      <svg width={size.width} height={size.height} role="img" aria-label="Financial chart">
        <rect
          data-testid="chart-background"
          x={0}
          y={0}
          width={size.width}
          height={size.height}
          fill={theme.background}
        />
        <g transform={`translate(${MARGIN.left}, ${MARGIN.top})`}>
          {/* Price panel grid + axis */}
          <g data-testid="chart-price-axis">
            {yScale.ticks(4).map((tick) => (
              <g key={tick}>
                <line x1={0} x2={innerWidth} y1={yScale(tick)} y2={yScale(tick)} stroke={theme.grid} />
                <text x={-8} y={yScale(tick)} dy="0.32em" textAnchor="end" fontSize={10} fill={theme.text}>
                  {priceFormat(tick)}
                </text>
              </g>
            ))}
          </g>

          <g style={{ opacity: entered ? 1 : 0, transition: "opacity 250ms ease-out" }}>
            {chartPlugin && (
              <chartPlugin.Component
                bars={bars}
                xScale={xScale}
                yScale={yScale}
                theme={theme}
              />
            )}

            {indicators.map(({ id, config }) => {
              const plugin = indicatorRegistry.get(id);
              if (!plugin) return null;
              const values = plugin.compute(bars, config ?? plugin.defaultConfig);
              const path = d3Line<number | undefined>()
                .defined((value) => value !== undefined)
                .x((_, index) => xScale(index))
                .y((value) => yScale(value as number))(values);
              return (
                <path
                  key={id}
                  data-testid={`indicator-overlay-${id}`}
                  d={path ?? undefined}
                  fill="none"
                  stroke={plugin.color}
                  strokeWidth={1.5}
                />
              );
            })}
          </g>

          {/* Volume panel */}
          {showVolume && (
            <g data-testid="chart-volume-panel" transform={`translate(0, ${volumeTop})`}>
              {bars.map((bar, index) => {
                const barWidth = Math.max(Math.abs(xScale(1) - xScale(0)) * 0.6, 1);
                const barHeight = volumeHeight - volumeScale(bar.volume);
                return (
                  <rect
                    key={index}
                    x={xScale(index) - barWidth / 2}
                    y={volumeScale(bar.volume)}
                    width={barWidth}
                    height={Math.max(barHeight, 0)}
                    fill={bar.close >= bar.open ? theme.candleUp : theme.candleDown}
                    opacity={0.6}
                  />
                );
              })}
            </g>
          )}

          {/* Time axis */}
          <g data-testid="chart-time-axis" transform={`translate(0, ${innerHeight})`}>
            {tickIndices.map((index) => (
              <text
                key={index}
                x={xScale(index)}
                y={16}
                textAnchor="middle"
                fontSize={10}
                fill={theme.text}
              >
                {axisDateFormat(bars[index].date)}
              </text>
            ))}
          </g>

          {/* Drawing-tool overlays */}
          {drawings.map(({ pluginId, instance }, index) => {
            const plugin = drawingToolRegistry.get(pluginId);
            if (!plugin) return null;
            return (
              <plugin.Component key={index} instance={instance} xScale={xScale} yScale={yScale} theme={theme} />
            );
          })}

          {/* Crosshair readout */}
          {hoverBar && hoverIndex !== undefined && (
            <g data-testid="chart-crosshair" pointerEvents="none">
              <line
                x1={xScale(hoverIndex)}
                x2={xScale(hoverIndex)}
                y1={0}
                y2={innerHeight}
                stroke={theme.text}
                strokeDasharray="2,2"
              />
              <line
                x1={0}
                x2={innerWidth}
                y1={yScale(hoverBar.close)}
                y2={yScale(hoverBar.close)}
                stroke={theme.text}
                strokeDasharray="2,2"
              />
              <text x={4} y={12} fontSize={10} fill={theme.text}>
                {`${readoutDateFormat(hoverBar.date)}  O ${priceFormat(hoverBar.open)}  H ${priceFormat(
                  hoverBar.high
                )}  L ${priceFormat(hoverBar.low)}  C ${priceFormat(hoverBar.close)}  V ${volumeFormat(
                  hoverBar.volume
                )}`}
              </text>
            </g>
          )}

          {/* Zoom/pan + hover/click capture surface */}
          <rect
            ref={overlayRef}
            data-testid="chart-overlay"
            x={0}
            y={0}
            width={innerWidth}
            height={innerHeight}
            fill="transparent"
            onPointerMove={handlePointerMove}
            onPointerLeave={handlePointerLeave}
            onPointerDown={handlePointerDown}
          />
        </g>
      </svg>
    </div>
  );
}
