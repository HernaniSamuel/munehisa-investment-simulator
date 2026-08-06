import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { Tooltip } from "./Tooltip";

describe("Tooltip", () => {
  it("shows the label on hover and hides it on unhover", async () => {
    const user = userEvent.setup();
    render(
      <Tooltip label="Explains the thing">
        <button type="button">Trigger</button>
      </Tooltip>
    );

    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();

    await user.hover(screen.getByRole("button", { name: "Trigger" }));
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Explains the thing");

    await user.unhover(screen.getByRole("button", { name: "Trigger" }));
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("shows the label on focus and hides it on blur", async () => {
    const user = userEvent.setup();
    render(
      <Tooltip label="Explains the thing">
        <button type="button">Trigger</button>
      </Tooltip>
    );

    await user.tab();
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Explains the thing");

    await user.tab();
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("links the trigger to the tooltip via aria-describedby", async () => {
    const user = userEvent.setup();
    render(
      <Tooltip label="Explains the thing">
        <button type="button">Trigger</button>
      </Tooltip>
    );

    const trigger = screen.getByRole("button", { name: "Trigger" });
    await user.hover(trigger);
    const tooltip = await screen.findByRole("tooltip");

    expect(trigger).toHaveAttribute("aria-describedby", tooltip.id);
  });

  it("wraps an SVG element without throwing, and still shows its tooltip on hover", async () => {
    const user = userEvent.setup();
    render(
      <svg>
        <Tooltip label="Slice detail">
          <circle data-testid="slice" cx={10} cy={10} r={5} />
        </Tooltip>
      </svg>
    );

    await user.hover(screen.getByTestId("slice"));
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Slice detail");
  });

  it("composes with a handler already present on the trigger instead of replacing it", async () => {
    const user = userEvent.setup();
    let existingFired = false;
    render(
      <Tooltip label="Explains the thing">
        <button type="button" onMouseEnter={() => (existingFired = true)}>
          Trigger
        </button>
      </Tooltip>
    );

    await user.hover(screen.getByRole("button", { name: "Trigger" }));

    expect(existingFired).toBe(true);
    expect(await screen.findByRole("tooltip")).toBeInTheDocument();
  });
});
