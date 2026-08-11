import { act, fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { useParams } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders, seedAuthenticatedUser, STORAGE_KEY } from "~/test/test-utils";
import { ApiError, simulationApi, type Simulation } from "~/lib/api";
import { formatCurrency, formatCurrencyExact } from "~/lib/format";
import Home from "./home";

// mirrors simulation-dashboard.test.tsx's money()/exactMoney() helpers.
function money(amount: number, baseCurrency: "BRL" | "USD"): string {
  return formatCurrency(amount, baseCurrency).replace(/[  ]/g, " ");
}
function exactMoney(amount: number, baseCurrency: "BRL" | "USD"): string {
  return formatCurrencyExact(amount, baseCurrency).replace(/[  ]/g, " ");
}

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    simulationApi: {
      list: vi.fn(),
      create: vi.fn(),
      rename: vi.fn(),
      remove: vi.fn(),
    },
  };
});

const loginStub = { path: "/login", element: <div>Login page</div> };
const settingsStub = { path: "/settings", element: <div>Settings page</div> };

function SimulationStub() {
  const { id } = useParams();
  return <div>Simulation dashboard for {id}</div>;
}
const simulationStub = { path: "/simulations/:id", element: <SimulationStub /> };

const sim1: Simulation = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Retirement plan",
  baseCurrency: "BRL",
  startMonth: "2019-12",
  currentMonth: "2020-01",
  cashBalance: 722000,
  totalPatrimony: 900000,
};

const sim2: Simulation = {
  id: "22222222-2222-2222-2222-222222222222",
  name: "Short-term trading",
  baseCurrency: "USD",
  startMonth: "2021-06",
  currentMonth: "2021-07",
  cashBalance: 15000.5,
  totalPatrimony: 20000,
};

let seededUser: { name: string; token: string };

function renderHome(redirectStubs: { path: string; element: ReactElement }[] = []) {
  return renderWithProviders(<Home />, { route: "/", redirectStubs });
}

async function renderWithOneSimulation() {
  vi.mocked(simulationApi.list).mockResolvedValueOnce([sim1]);
  const user = userEvent.setup();
  renderHome();
  await screen.findByText(sim1.name);
  return user;
}

beforeEach(() => {
  localStorage.clear();
  seededUser = seedAuthenticatedUser({ name: "Ada Lovelace" });
  vi.mocked(simulationApi.list).mockReset();
  vi.mocked(simulationApi.create).mockReset();
  vi.mocked(simulationApi.rename).mockReset();
  vi.mocked(simulationApi.remove).mockReset();
});

describe("authentication", () => {
  it("redirects to /login when unauthenticated", () => {
    localStorage.clear();
    renderHome([loginStub]);
    expect(screen.getByText("Login page")).toBeInTheDocument();
  });
});

describe("header", () => {
  it("always shows the Munehisa logo, Settings link, and Log out button", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    renderHome();
    await screen.findByText(/Welcome,/);

    expect(screen.getByText("Munehisa")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Settings" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Log out" })).toBeInTheDocument();
  });

  it("navigates to /settings when Settings is clicked", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    const user = userEvent.setup();
    renderHome([settingsStub]);
    await screen.findByText(/Welcome,/);

    await user.click(screen.getByRole("link", { name: "Settings" }));

    expect(await screen.findByText("Settings page")).toBeInTheDocument();
  });

  it("opens a confirmation dialog instead of logging out immediately when Log out is clicked", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    const user = userEvent.setup();
    renderHome([loginStub]);
    await screen.findByText(/Welcome,/);

    await user.click(screen.getByRole("button", { name: "Log out" }));

    expect(screen.getByRole("dialog")).toHaveTextContent("Log out?");
    expect(screen.queryByText("Login page")).not.toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull();
  });

  it("logs out and navigates to /login when confirming inside the dialog", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    const user = userEvent.setup();
    renderHome([loginStub]);
    await screen.findByText(/Welcome,/);

    await user.click(screen.getByRole("button", { name: "Log out" }));
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Log out" }));

    expect(await screen.findByText("Login page")).toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it("closes the dialog without logging out when Cancel is clicked", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    const user = userEvent.setup();
    renderHome([loginStub]);
    await screen.findByText(/Welcome,/);

    await user.click(screen.getByRole("button", { name: "Log out" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText(/Welcome,/)).toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull();
  });

  it("closes the dialog without logging out on Escape", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    const user = userEvent.setup();
    renderHome([loginStub]);
    await screen.findByText(/Welcome,/);

    await user.click(screen.getByRole("button", { name: "Log out" }));
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText(/Welcome,/)).toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull();
  });

  it("styles Cancel as vermilion/red and Confirm as a solid ink fill", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    const user = userEvent.setup();
    renderHome([loginStub]);
    await screen.findByText(/Welcome,/);

    await user.click(screen.getByRole("button", { name: "Log out" }));
    const dialog = screen.getByRole("dialog");

    const cancelButton = within(dialog).getByRole("button", { name: "Cancel" });
    const confirmButton = within(dialog).getByRole("button", { name: "Log out" });

    expect(cancelButton).toHaveClass("bg-vermilion");
    expect(confirmButton).toHaveClass("bg-ink", "text-paper");
    expect(confirmButton).not.toHaveClass("bg-vermilion");
  });

  it("closes the dialog without logging out when clicking outside it", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    const user = userEvent.setup();
    renderHome([loginStub]);
    await screen.findByText(/Welcome,/);

    await user.click(screen.getByRole("button", { name: "Log out" }));
    const dialog = screen.getByRole("dialog");
    fireEvent.click(dialog);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText(/Welcome,/)).toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull();
  });
});

describe("loading state", () => {
  it("shows a loading indicator instead of the grid or empty state while GET /simulations is pending", async () => {
    let resolveList!: (value: Simulation[]) => void;
    vi.mocked(simulationApi.list).mockReturnValue(
      new Promise((resolve) => {
        resolveList = resolve;
      })
    );
    renderHome();

    expect(screen.getByText("Loading simulations…")).toBeInTheDocument();
    expect(screen.queryByText(/Welcome,/)).not.toBeInTheDocument();

    await act(async () => {
      resolveList([]);
    });
    expect(await screen.findByText(/Welcome,/)).toBeInTheDocument();
  });
});

describe("error state", () => {
  it("shows an error banner instead of a blank/empty grid when GET /simulations fails", async () => {
    vi.mocked(simulationApi.list).mockRejectedValueOnce(new ApiError(500, "Request failed (500)"));
    renderHome();

    expect(await screen.findByText("Request failed (500)")).toBeInTheDocument();
    expect(screen.queryByText(/Welcome,/)).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 3 })).not.toBeInTheDocument();
  });
});

describe("empty state", () => {
  it("shows a welcome message and a single centered New Simulation action, no grid", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    renderHome();

    expect(await screen.findByText("Welcome, Ada Lovelace")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "New Simulation" })).toHaveLength(1);
    expect(screen.queryByRole("heading", { level: 3 })).not.toBeInTheDocument();
  });
});

describe("populated state", () => {
  it("shows a grid with one card per simulation, sorted by startMonth descending, and a header New Simulation button", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([sim1, sim2]);
    renderHome();

    const headings = await screen.findAllByRole("heading", { level: 3 });
    expect(headings.map((h) => h.textContent)).toEqual([sim2.name, sim1.name]);
    expect(screen.getByRole("button", { name: "New Simulation" })).toBeInTheDocument();
  });

  it("renders each card's start/current month and cash balance, with no totalPatrimony shown", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([sim1]);
    renderHome();

    await screen.findByText(sim1.name);
    expect(screen.getByText("DEC 2019")).toBeInTheDocument();
    expect(screen.getByText("JAN 2020")).toBeInTheDocument();
    expect(screen.getByText("R$ 722.000,00")).toBeInTheDocument();
    expect(screen.queryByText(/900\.000/)).not.toBeInTheDocument();
    expect(screen.queryByText(/900,000/)).not.toBeInTheDocument();
  });

  it("formats USD cash balances with en-US conventions", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([sim2]);
    renderHome();

    expect(await screen.findByText("$15,000.50")).toBeInTheDocument();
  });

  it("abbreviates a cash balance of 1 billion or more and reveals the exact value on hover", async () => {
    const largeSim: Simulation = { ...sim1, cashBalance: 1_234_567_890 };
    vi.mocked(simulationApi.list).mockResolvedValueOnce([largeSim]);
    const user = userEvent.setup();
    renderHome();
    await screen.findByText(largeSim.name);

    const abbreviated = money(largeSim.cashBalance, largeSim.baseCurrency);
    expect(screen.getByText(abbreviated)).toBeInTheDocument();
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();

    await user.hover(screen.getByText(abbreviated));
    expect(await screen.findByRole("tooltip")).toHaveTextContent(exactMoney(largeSim.cashBalance, largeSim.baseCurrency));
  });

  it("does not add a tooltip for a cash balance under 1 billion", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([sim1]);
    const user = userEvent.setup();
    renderHome();
    await screen.findByText(sim1.name);

    await user.hover(screen.getByText(money(sim1.cashBalance, sim1.baseCurrency)));
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("navigates to /simulations/{id} when Open is clicked", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([sim1]);
    const user = userEvent.setup();
    renderHome([simulationStub]);
    await screen.findByText(sim1.name);

    await user.click(screen.getByRole("button", { name: "Open" }));

    expect(await screen.findByText(`Simulation dashboard for ${sim1.id}`)).toBeInTheDocument();
  });
});

describe("rename", () => {
  it("turns the name into a focused, pre-filled editable field when the edit icon is clicked", async () => {
    const user = await renderWithOneSimulation();

    await user.click(screen.getByRole("button", { name: "Edit name" }));

    const nameInput = screen.getByLabelText("Simulation name");
    expect(nameInput).toHaveFocus();
    expect(nameInput).toHaveValue(sim1.name);
  });

  it("saves the new name and exits edit mode on success", async () => {
    const user = await renderWithOneSimulation();
    vi.mocked(simulationApi.rename).mockResolvedValueOnce({ ...sim1, name: "New name" });

    await user.click(screen.getByRole("button", { name: "Edit name" }));
    const nameInput = screen.getByLabelText("Simulation name");
    await user.clear(nameInput);
    await user.type(nameInput, "New name");
    await user.click(screen.getByRole("button", { name: "Save name" }));

    expect(await screen.findByText("New name")).toBeInTheDocument();
    expect(screen.queryByLabelText("Simulation name")).not.toBeInTheDocument();
    expect(simulationApi.rename).toHaveBeenCalledWith(sim1.id, "New name", seededUser.token);
  });

  it("exits edit mode without calling the API on Escape, restoring the original name", async () => {
    const user = await renderWithOneSimulation();

    await user.click(screen.getByRole("button", { name: "Edit name" }));
    await user.type(screen.getByLabelText("Simulation name"), " extra");
    await user.keyboard("{Escape}");

    expect(screen.queryByLabelText("Simulation name")).not.toBeInTheDocument();
    expect(screen.getByText(sim1.name)).toBeInTheDocument();
    expect(simulationApi.rename).not.toHaveBeenCalled();
  });

  it("exits edit mode without calling the API when the explicit cancel control is clicked", async () => {
    const user = await renderWithOneSimulation();

    await user.click(screen.getByRole("button", { name: "Edit name" }));
    await user.type(screen.getByLabelText("Simulation name"), " extra");
    await user.click(screen.getByRole("button", { name: "Cancel rename" }));

    expect(screen.getByText(sim1.name)).toBeInTheDocument();
    expect(simulationApi.rename).not.toHaveBeenCalled();
  });

  it("keeps edit mode and shows an inline error for a blank name, without calling the API", async () => {
    const user = await renderWithOneSimulation();

    await user.click(screen.getByRole("button", { name: "Edit name" }));
    await user.clear(screen.getByLabelText("Simulation name"));
    await user.click(screen.getByRole("button", { name: "Save name" }));

    expect(await screen.findByText("Name cannot be blank.")).toBeInTheDocument();
    expect(screen.getByLabelText("Simulation name")).toBeInTheDocument();
    expect(simulationApi.rename).not.toHaveBeenCalled();
  });

  it("keeps edit mode, the typed name, and shows an inline error when the API rejects the rename", async () => {
    const user = await renderWithOneSimulation();
    vi.mocked(simulationApi.rename).mockRejectedValueOnce(new ApiError(400, "name: must not be blank"));

    await user.click(screen.getByRole("button", { name: "Edit name" }));
    const nameInput = screen.getByLabelText("Simulation name");
    await user.clear(nameInput);
    await user.type(nameInput, "Bad Name");
    await user.click(screen.getByRole("button", { name: "Save name" }));

    expect(await screen.findByText("name: must not be blank")).toBeInTheDocument();
    expect(screen.getByLabelText("Simulation name")).toHaveValue("Bad Name");
  });

  it("shows no toast after a successful rename", async () => {
    const user = await renderWithOneSimulation();
    vi.mocked(simulationApi.rename).mockResolvedValueOnce({ ...sim1, name: "New name" });

    await user.click(screen.getByRole("button", { name: "Edit name" }));
    await user.click(screen.getByRole("button", { name: "Save name" }));
    await screen.findByText("New name");

    expect(screen.queryByText(/was permanently deleted/)).not.toBeInTheDocument();
    expect(screen.queryAllByRole("status")).toHaveLength(0);
  });

  it("does not change a card's sort position after a rename", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([sim1, sim2]);
    vi.mocked(simulationApi.rename).mockResolvedValueOnce({ ...sim1, name: "Renamed plan" });
    const user = userEvent.setup();
    renderHome();
    await screen.findByText(sim2.name);

    const sim1Card = screen.getByTestId(`simulation-card-${sim1.id}`);
    await user.click(within(sim1Card).getByRole("button", { name: "Edit name" }));
    await user.click(within(sim1Card).getByRole("button", { name: "Save name" }));
    await screen.findByText("Renamed plan");

    const headings = screen.getAllByRole("heading", { level: 3 });
    expect(headings.map((h) => h.textContent)).toEqual([sim2.name, "Renamed plan"]);
  });
});

describe("delete", () => {
  it("opens a confirmation dialog naming the simulation before any request is sent", async () => {
    const user = await renderWithOneSimulation();

    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(screen.getByRole("dialog")).toHaveTextContent(`Delete "${sim1.name}"?`);
    expect(simulationApi.remove).not.toHaveBeenCalled();
  });

  it("sends no request and leaves the card in place when canceled", async () => {
    const user = await renderWithOneSimulation();

    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText(sim1.name)).toBeInTheDocument();
    expect(simulationApi.remove).not.toHaveBeenCalled();
  });

  it("styles Cancel as vermilion/red and Confirm as a solid ink fill", async () => {
    const user = await renderWithOneSimulation();

    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("dialog");

    const cancelButton = within(dialog).getByRole("button", { name: "Cancel" });
    const confirmButton = within(dialog).getByRole("button", { name: "Delete" });

    expect(cancelButton).toHaveClass("bg-vermilion");
    expect(confirmButton).toHaveClass("bg-ink", "text-paper");
    expect(confirmButton).not.toHaveClass("bg-vermilion");
    expect(confirmButton).not.toHaveClass("border-ink/25");
  });

  it("keeps the dialog open with an inline error and the card in place when delete fails", async () => {
    const user = await renderWithOneSimulation();
    vi.mocked(simulationApi.remove).mockRejectedValueOnce(new ApiError(500, "Request failed (500)"));

    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    expect(await within(dialog).findByText("Request failed (500)")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText(sim1.name)).toBeInTheDocument();
    expect(screen.queryByText(/was permanently deleted/)).not.toBeInTheDocument();
  });

  it("closes the dialog, removes the card without a full page reload, and shows a toast on success", async () => {
    const user = await renderWithOneSimulation();
    vi.mocked(simulationApi.remove).mockResolvedValueOnce(undefined);

    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    expect(await screen.findByText(`"${sim1.name}" was permanently deleted.`)).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByText(sim1.name)).not.toBeInTheDocument();
  });

  it("stacks multiple toasts when deleting more than one simulation in succession", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([sim1, sim2]);
    vi.mocked(simulationApi.remove).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderHome();
    await screen.findByText(sim1.name);

    await user.click(
      within(screen.getByTestId(`simulation-card-${sim1.id}`)).getByRole("button", { name: "Delete" })
    );
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Delete" }));
    await screen.findByText(`"${sim1.name}" was permanently deleted.`);

    await user.click(
      within(screen.getByTestId(`simulation-card-${sim2.id}`)).getByRole("button", { name: "Delete" })
    );
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Delete" }));
    await screen.findByText(`"${sim2.name}" was permanently deleted.`);

    // Both toasts remain visible together - the second's arrival didn't
    // dismiss the first, and each is still counting down on its own timer
    // (independent per-toast dismissal is covered directly in Toast.test.tsx).
    expect(screen.getByText(`"${sim1.name}" was permanently deleted.`)).toBeInTheDocument();
    expect(screen.getByText(`"${sim2.name}" was permanently deleted.`)).toBeInTheDocument();
  });
});

describe("create", () => {
  it("opens the create form from the empty-state CTA and navigates to the new simulation's dashboard on success", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    const newSim: Simulation = { ...sim1, id: "33333333-3333-3333-3333-333333333333", name: "Fresh start" };
    vi.mocked(simulationApi.create).mockResolvedValueOnce(newSim);
    const user = userEvent.setup();
    renderHome([simulationStub]);
    await screen.findByText(/Welcome,/);

    await user.click(screen.getByRole("button", { name: "New Simulation" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("Name"), "Fresh start");
    await user.selectOptions(within(dialog).getByLabelText("Base currency"), "BRL");
    fireEvent.change(within(dialog).getByLabelText("Start month"), { target: { value: "2024-01" } });
    await user.click(within(dialog).getByRole("button", { name: "Create" }));

    expect(await screen.findByText(`Simulation dashboard for ${newSim.id}`)).toBeInTheDocument();
    expect(simulationApi.create).toHaveBeenCalledWith(
      { name: "Fresh start", baseCurrency: "BRL", startMonth: "2024-01" },
      seededUser.token
    );
  });

  it("also opens the create form from the header button when simulations already exist", async () => {
    const user = await renderWithOneSimulation();

    await user.click(screen.getByRole("button", { name: "New Simulation" }));

    expect(screen.getByRole("dialog", { name: "New simulation" })).toBeInTheDocument();
  });

  it("keeps the form open with entered values and shows an inline error on failure", async () => {
    vi.mocked(simulationApi.list).mockResolvedValueOnce([]);
    vi.mocked(simulationApi.create).mockRejectedValueOnce(new ApiError(400, "name: must not be blank"));
    const user = userEvent.setup();
    renderHome();
    await screen.findByText(/Welcome,/);

    await user.click(screen.getByRole("button", { name: "New Simulation" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("Name"), "Broken form");
    fireEvent.change(within(dialog).getByLabelText("Start month"), { target: { value: "2024-01" } });
    await user.click(within(dialog).getByRole("button", { name: "Create" }));

    expect(await within(dialog).findByText("name: must not be blank")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Broken form");
  });
});
