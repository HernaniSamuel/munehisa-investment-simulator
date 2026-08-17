import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PasswordField, TextField } from "./ui";

describe("TextField", () => {
  it("renders without a trailing wrapper or extra padding when no trailing prop is given", () => {
    const { container } = render(<TextField id="name" label="Name" />);

    expect(container.querySelector(".relative")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Name")).not.toHaveClass("pr-10");
  });

  it("still renders an error message when no trailing prop is given", () => {
    render(<TextField id="name" label="Name" error="Required" />);

    expect(screen.getByText("Required")).toBeInTheDocument();
  });

  it("renders a trailing node and adds right padding to the input when trailing is given", () => {
    render(<TextField id="name" label="Name" trailing={<span>trailing content</span>} />);

    expect(screen.getByText("trailing content")).toBeInTheDocument();
    expect(screen.getByLabelText("Name")).toHaveClass("pr-10");
  });
});

describe("PasswordField", () => {
  it("renders as a masked password input with a 'Show password' toggle by default", () => {
    render(<PasswordField id="password" label="Password" />);

    expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
    expect(screen.getByRole("button", { name: "Show password" })).toBeInTheDocument();
  });

  it("toggles the input type and aria-label when the toggle is clicked, and back again", async () => {
    const user = userEvent.setup();
    render(<PasswordField id="password" label="Password" />);

    await user.click(screen.getByRole("button", { name: "Show password" }));

    expect(screen.getByLabelText("Password")).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Hide password" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Hide password" }));

    expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
    expect(screen.getByRole("button", { name: "Show password" })).toBeInTheDocument();
  });

  it("is keyboard-operable via a real button element", async () => {
    const user = userEvent.setup();
    render(<PasswordField id="password" label="Password" />);

    const toggle = screen.getByRole("button", { name: "Show password" });
    toggle.focus();
    await user.keyboard("{Enter}");

    expect(screen.getByLabelText("Password")).toHaveAttribute("type", "text");
  });

  it("keeps independent visibility state across multiple instances", async () => {
    const user = userEvent.setup();
    render(
      <>
        <PasswordField id="a" label="Password A" />
        <PasswordField id="b" label="Password B" />
      </>
    );

    const inputA = screen.getByLabelText("Password A");
    const inputB = screen.getByLabelText("Password B");
    const toggleA = inputA.parentElement!.querySelector("button")!;
    const toggleB = inputB.parentElement!.querySelector("button")!;

    await user.click(toggleA);

    expect(inputA).toHaveAttribute("type", "text");
    expect(toggleA).toHaveAttribute("aria-label", "Hide password");
    expect(inputB).toHaveAttribute("type", "password");
    expect(toggleB).toHaveAttribute("aria-label", "Show password");
  });
});
