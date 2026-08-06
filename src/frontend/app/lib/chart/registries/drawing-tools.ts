import type { FC } from "react";
import type { ChartTheme } from "../theme";
import type { ChartScale } from "../types";
import { createRegistry, type Registry } from "./registry";

// Where the user activated the tool, expressed in data space (bar index and
// price) rather than pixels, so the resulting instance stays correctly
// placed across zoom/pan/resize.
export type DrawingToolPointer = { index: number; price: number };

export type DrawingToolRenderProps = {
  instance: unknown;
  xScale: ChartScale;
  yScale: ChartScale;
  theme: ChartTheme;
};

// A drawing-tool plugin turns a pointer interaction into a persisted
// `instance` (plugin-defined shape) and knows how to render that instance
// as an overlay. No concrete tool ships in this module - this interface and
// its registry exist so one can be added later, by Munehisa or another
// project, without touching the engine.
export type DrawingToolPlugin = {
  id: string;
  label: string;
  createInstance(pointer: DrawingToolPointer): unknown;
  Component: FC<DrawingToolRenderProps>;
};

export type DrawingToolRegistry = Registry<DrawingToolPlugin>;

export function createDrawingToolRegistry(): DrawingToolRegistry {
  return createRegistry<DrawingToolPlugin>();
}
