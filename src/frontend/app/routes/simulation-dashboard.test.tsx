import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { useParams } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders, seedAuthenticatedUser } from "~/test/test-utils";
import {
  ApiError,
  simulationApi,
  type AdvanceMonthResponse,
  type CashMovementResponse,
  type Position,
  type PositionsResponse,
  type Simulation,
  type Transaction,
  type TransactionType,
} from "~/lib/api";
import { formatCurrency, formatPercent } from "~/lib/format";
import SimulationDashboard from "./simulation-dashboard";

// RTL's getByText/toHaveTextContent normalize a DOM node's own whitespace
// (collapsing any run of whitespace, including the non-breaking spaces
// Intl.NumberFormat inserts, into a single regular space) but do not apply
// that same normalization to the string being searched for - so comparing
// formatCurrency's raw output (which contains a real NBSP) against the
// normalized DOM text would spuriously fail. money() mirrors format.test.ts's
// normalizeSpaces() so both sides compare on equal footing.
function money(amount: number, baseCurrency: "BRL" | "USD"): string {
  return formatCurrency(amount, baseCurrency).replace(/[  ]/g, " ");
}

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    simulationApi: {
      get: vi.fn(),
      positions: vi.fn(),
      transactions: vi.fn(),
      deposit: vi.fn(),
      withdraw: vi.fn(),
      reset: vi.fn(),
      advance: vi.fn(),
    },
  };
});

const loginStub = { path: "/login", element: <div>Login page</div> };

function TradeStub() {
  const { id } = useParams();
  return <div>Trade screen for {id}</div>;
}
const tradeStub = { path: "/simulations/:id/trade", element: <TradeStub /> };

const sim: Simulation = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Retirement plan",
  baseCurrency: "BRL",
  startMonth: "2020-01",
  currentMonth: "2020-01",
  cashBalance: 50000,
  totalPatrimony: 150000,
};

const position1: Position = {
  ticker: "AAPL",
  assetName: "Apple Inc.",
  quantity: 10,
  currentPrice: 150,
  wasTruncated: false,
  marketValue: 1500,
  weight: 0.6,
  costBasis: 1400,
  gainAmount: 100,
  gainPercent: 0.0714,
};

const position2: Position = {
  ticker: "MSFT",
  assetName: "Microsoft Corporation",
  quantity: 5,
  currentPrice: 200,
  wasTruncated: false,
  marketValue: 1000,
  weight: 0.4,
  costBasis: 1100,
  gainAmount: -100,
  gainPercent: -0.0909,
};

const positionsResponse: PositionsResponse = {
  positions: [position1, position2],
  totalAssetValue: 2500,
  totalGainAmount: 0,
  totalGainPercent: 0,
};

const transactions: Transaction[] = [
  { type: "DEPOSIT", month: "2020-01", amount: 50000, ticker: null, assetName: null, quantity: null },
  { type: "BUY", month: "2020-01", amount: 1400, ticker: "AAPL", assetName: "Apple Inc.", quantity: 10 },
  { type: "SELL", month: "2019-12", amount: 500, ticker: "MSFT", assetName: "Microsoft Corporation", quantity: 2 },
  { type: "WITHDRAWAL", month: "2019-12", amount: 200, ticker: null, assetName: null, quantity: null },
];

let seededUser: { name: string; token: string };

function renderDashboard(id = sim.id, redirectStubs: { path: string; element: ReactElement }[] = []) {
  return renderWithProviders(<SimulationDashboard />, {
    route: `/simulations/${id}`,
    path: "/simulations/:id",
    redirectStubs,
  });
}

function mockLoadSuccess({
  simulation = sim,
  positions = positionsResponse,
  txs = transactions,
}: { simulation?: Simulation; positions?: PositionsResponse; txs?: Transaction[] } = {}) {
  vi.mocked(simulationApi.get).mockResolvedValueOnce(simulation);
  vi.mocked(simulationApi.positions).mockResolvedValueOnce(positions);
  vi.mocked(simulationApi.transactions).mockResolvedValueOnce(txs);
}

beforeEach(() => {
  localStorage.clear();
  seededUser = seedAuthenticatedUser({ name: "Ada Lovelace" });
  vi.mocked(simulationApi.get).mockReset();
  vi.mocked(simulationApi.positions).mockReset();
  vi.mocked(simulationApi.transactions).mockReset();
  vi.mocked(simulationApi.deposit).mockReset();
  vi.mocked(simulationApi.withdraw).mockReset();
  vi.mocked(simulationApi.reset).mockReset();
  vi.mocked(simulationApi.advance).mockReset();
});

describe("authentication", () => {
  it("redirects to /login when unauthenticated", () => {
    localStorage.clear();
    renderDashboard(sim.id, [loginStub]);
    expect(screen.getByText("Login page")).toBeInTheDocument();
  });
});

describe("loading and error states", () => {
  it("fetches the simulation, positions, and transactions with the token on mount", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    expect(simulationApi.get).toHaveBeenCalledWith(sim.id, seededUser.token);
    expect(simulationApi.positions).toHaveBeenCalledWith(sim.id, seededUser.token);
    expect(simulationApi.transactions).toHaveBeenCalledWith(sim.id, seededUser.token);
  });

  it("shows a loading indicator instead of the dashboard while requests are pending", async () => {
    let resolveGet!: (value: Simulation) => void;
    vi.mocked(simulationApi.get).mockReturnValue(
      new Promise((resolve) => {
        resolveGet = resolve;
      })
    );
    vi.mocked(simulationApi.positions).mockResolvedValue(positionsResponse);
    vi.mocked(simulationApi.transactions).mockResolvedValue(transactions);
    renderDashboard();

    expect(screen.getByText("Loading simulation…")).toBeInTheDocument();
    expect(screen.queryByText(sim.name)).not.toBeInTheDocument();

    await act(async () => {
      resolveGet(sim);
    });
    expect(await screen.findByText(sim.name)).toBeInTheDocument();
  });

  it("shows an error Banner instead of the dashboard if any request fails", async () => {
    vi.mocked(simulationApi.get).mockRejectedValueOnce(new ApiError(500, "Request failed (500)"));
    vi.mocked(simulationApi.positions).mockResolvedValueOnce(positionsResponse);
    vi.mocked(simulationApi.transactions).mockResolvedValueOnce(transactions);
    renderDashboard();

    expect(await screen.findByText("Request failed (500)")).toBeInTheDocument();
    expect(screen.queryByText(sim.name)).not.toBeInTheDocument();
  });
});

describe("header", () => {
  it("truncates a long simulation name and reveals the full name on hover", async () => {
    const longName = "A".repeat(80);
    mockLoadSuccess({ simulation: { ...sim, name: longName } });
    const user = userEvent.setup();
    renderDashboard();

    const heading = await screen.findByRole("heading", { level: 1 });
    expect(heading).toHaveClass("truncate");

    await user.hover(heading);
    expect(await screen.findByRole("tooltip")).toHaveTextContent(longName);
  });

  it("shows the current month and year without a day component", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    expect(screen.getByText("Current date")).toBeInTheDocument();
    expect(screen.getByTestId("current-date-value")).toHaveTextContent("JAN 2020");
  });

  it("navigates to /simulations/{id}/trade when Trade assets is clicked", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard(sim.id, [tradeStub]);
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("link", { name: "⇄ Trade assets" }));

    expect(await screen.findByText(`Trade screen for ${sim.id}`)).toBeInTheDocument();
  });

  it("does not render a refresh-prices control", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    expect(screen.queryByRole("button", { name: /refresh/i })).not.toBeInTheDocument();
  });
});

describe("stat cards", () => {
  it("renders cash balance from cashBalance", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    expect(screen.getByTestId("cash-balance-value")).toHaveTextContent(
      money(sim.cashBalance, sim.baseCurrency)
    );
  });

  it("renders portfolio value and position count from positions", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    expect(
      screen.getByText(money(positionsResponse.totalAssetValue, sim.baseCurrency))
    ).toBeInTheDocument();
    expect(screen.getByText("2 positions")).toBeInTheDocument();
  });

  it("computes total value as cashBalance + totalAssetValue", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    expect(
      screen.getByText(money(sim.cashBalance + positionsResponse.totalAssetValue, sim.baseCurrency))
    ).toBeInTheDocument();
  });

  it("renders gain/loss in vermilion when totalGainAmount is non-negative", async () => {
    mockLoadSuccess({ positions: { ...positionsResponse, totalGainAmount: 500, totalGainPercent: 0.05 } });
    renderDashboard();
    await screen.findByText(sim.name);

    const value = screen.getByTestId("gain-loss-value");
    expect(value).toHaveTextContent(money(500, sim.baseCurrency));
    expect(value).toHaveClass("text-vermilion");
  });

  it("renders gain/loss in ink when totalGainAmount is negative", async () => {
    mockLoadSuccess({ positions: { ...positionsResponse, totalGainAmount: -500, totalGainPercent: -0.05 } });
    renderDashboard();
    await screen.findByText(sim.name);

    const value = screen.getByTestId("gain-loss-value");
    expect(value).toHaveTextContent(money(-500, sim.baseCurrency));
    expect(value).toHaveClass("text-ink");
  });
});

describe("hover explanations", () => {
  it("hovering the cash balance card shows its explanation", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.hover(screen.getByText("Cash balance"));
    expect(await screen.findByRole("tooltip")).toHaveTextContent("amount available to buy assets");
  });

  it("hovering the portfolio value card shows its explanation", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.hover(screen.getByText("Portfolio value"));
    expect(await screen.findByRole("tooltip")).toHaveTextContent("currently invested and available for sale");
  });

  it("hovering the total value card shows its explanation", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.hover(screen.getByText("Total value"));
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Cash plus invested value");
  });

  it("hovering the gain/loss card shows its explanation", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.hover(screen.getByText("Gain / loss"));
    expect(await screen.findByRole("tooltip")).toHaveTextContent("invested cost to current market value");
  });

  it("hovering the Contribute button shows its explanation", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.hover(screen.getByRole("button", { name: "+ Contribute" }));
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Adds money to your cash balance");
  });

  it("hovering the Withdraw button shows its explanation", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.hover(screen.getByRole("button", { name: "− Withdraw" }));
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Removes money from your cash balance");
  });
});

describe("positions table", () => {
  it("renders one row per position with the expected columns", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    const tableScroll = within(screen.getByTestId("positions-table-scroll"));
    const rows = tableScroll.getAllByRole("row");
    expect(rows).toHaveLength(positionsResponse.positions.length + 1);
    expect(tableScroll.getByText("AAPL")).toBeInTheDocument();
    expect(tableScroll.getByText("MSFT")).toBeInTheDocument();
    expect(tableScroll.getByText(money(position1.currentPrice, sim.baseCurrency))).toBeInTheDocument();
    expect(tableScroll.getByText(money(position1.marketValue, sim.baseCurrency))).toBeInTheDocument();
  });

  it("scrolls internally within a fixed-height container, in both directions", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    // overflow-auto (not just overflow-y-auto): the table also needs to scroll horizontally
    // on narrow viewports instead of the page overflowing - see issue #101.
    expect(screen.getByTestId("positions-table-scroll")).toHaveClass("overflow-auto");
  });

  it("keeps padding between the scrollbars and the content in both directions", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    // pr-3: gap between the vertical scrollbar and the right-aligned Gain column.
    // pb-3: gap between the horizontal scrollbar and the last row - see issue #108.
    expect(screen.getByTestId("positions-table-scroll")).toHaveClass("pr-3", "pb-3");
  });

  it("truncates a long asset name and shows the full name plus an explanation on hover", async () => {
    const longName = "B".repeat(80);
    mockLoadSuccess({
      positions: { ...positionsResponse, positions: [{ ...position1, assetName: longName }] },
    });
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    const link = screen.getByRole("link", { name: longName });
    expect(link).toHaveClass("truncate");

    await user.hover(link);
    const tooltip = await screen.findByRole("tooltip");
    expect(tooltip).toHaveTextContent(longName);
    expect(tooltip).toHaveTextContent("opens the trading screen for this asset");
  });

  it("links the asset name to /simulations/{id}/trade?ticker={ticker}", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    const link = screen.getByRole("link", { name: position1.assetName });
    expect(link).toHaveAttribute("href", `/simulations/${sim.id}/trade?ticker=${position1.ticker}`);
  });

  it("clicking the asset name navigates to the trade screen", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard(sim.id, [tradeStub]);
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("link", { name: position1.assetName }));

    expect(await screen.findByText(`Trade screen for ${sim.id}`)).toBeInTheDocument();
  });
});

describe("allocation donut", () => {
  it("renders one segment per position, never grouped by category", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    expect(
      screen.getByRole("img", { name: `${position1.ticker} — ${position1.assetName}` })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: `${position2.ticker} — ${position2.assetName}` })
    ).toBeInTheDocument();
  });

  it("shows the position count in the core", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    expect(screen.getByTestId("donut-core-count")).toHaveTextContent("2");
  });

  it("hovering a slice shows its name, ticker, weight, quantity, costBasis, and market value", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.hover(screen.getByRole("img", { name: `${position1.ticker} — ${position1.assetName}` }));
    const tooltip = await screen.findByRole("tooltip");

    expect(tooltip).toHaveTextContent(position1.assetName);
    expect(tooltip).toHaveTextContent(position1.ticker);
    expect(tooltip).toHaveTextContent(formatPercent(position1.weight));
    expect(tooltip).toHaveTextContent(String(position1.quantity));
    expect(tooltip).toHaveTextContent(money(position1.costBasis, sim.baseCurrency));
    expect(tooltip).toHaveTextContent(money(position1.marketValue, sim.baseCurrency));
  });

  it("the allocation legend scrolls internally while the chart remains visible outside the scroll container", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    const chart = screen.getByTestId("donut-chart");
    const legend = screen.getByTestId("donut-legend");
    expect(legend).toHaveClass("overflow-y-auto");
    expect(chart.contains(legend)).toBe(false);
    expect(legend.contains(chart)).toBe(false);
  });

  it("keeps padding between the legend's scrollbar and the percentage values", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    // See issue #108: the scrollbar must not touch the percentage values.
    expect(screen.getByTestId("donut-legend")).toHaveClass("pr-3");
  });
});

describe("transaction history", () => {
  it("groups transactions by month, most recent first, with no collapse control", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    const monthHeadings = within(screen.getByTestId("transaction-history-scroll"))
      .getAllByRole("heading", { level: 3 })
      .map((h) => h.textContent);
    expect(monthHeadings).toEqual(["JAN 2020", "DEC 2019"]);
    expect(screen.queryByRole("button", { name: /show|expand|collapse/i })).not.toBeInTheDocument();
  });

  it.each([
    ["DEPOSIT", "Contribution", "text-vermilion"],
    ["WITHDRAWAL", "Withdrawal", "text-ink"],
    ["BUY", "Buy", "text-ink"],
    ["SELL", "Sell", "text-vermilion"],
    ["DIVIDEND", "Dividend", "text-vermilion"],
  ] as [TransactionType, string, string][])(
    "maps %s to label %s with color %s",
    async (type, label, colorClass) => {
      const needsAsset = type !== "DEPOSIT" && type !== "WITHDRAWAL";
      mockLoadSuccess({
        txs: [
          {
            type,
            month: "2020-01",
            amount: 100,
            ticker: needsAsset ? "AAPL" : null,
            assetName: needsAsset ? "Apple Inc." : null,
            quantity: needsAsset ? 1 : null,
          },
        ],
      });
      renderDashboard();
      await screen.findByText(sim.name);

      expect(screen.getByText(money(100, sim.baseCurrency))).toHaveClass(colorClass);
      expect(screen.getByText(new RegExp(label))).toBeInTheDocument();
    }
  );

  it("the transaction panel scrolls internally", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    expect(screen.getByTestId("transaction-history-scroll")).toHaveClass("overflow-y-auto");
  });

  it("keeps padding between the scrollbar and the amount values", async () => {
    mockLoadSuccess();
    renderDashboard();
    await screen.findByText(sim.name);

    // See issue #108: the scrollbar must not touch the amount values.
    expect(screen.getByTestId("transaction-history-scroll")).toHaveClass("pr-3");
  });

  it("the dashboard page itself is not height-constrained, so it scrolls vertically", async () => {
    mockLoadSuccess();
    const { container } = renderDashboard();
    await screen.findByText(sim.name);

    const main = container.querySelector("main");
    expect(main).not.toHaveClass("overflow-hidden");
    expect(main).toHaveClass("min-h-screen");
  });
});

describe("deposit and withdraw", () => {
  it("submits a deposit with amount and todaysMoney", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.deposit).mockResolvedValueOnce({
      simulationId: sim.id,
      appliedAmount: 1000,
      cashBalance: 51000,
      totalPatrimony: 151000,
      deflation: null,
    });
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "+ Contribute" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Amount/), "1000");
    await user.click(within(dialog).getByLabelText(/cumulative inflation/i));
    await user.click(within(dialog).getByRole("button", { name: "Contribute" }));

    expect(simulationApi.deposit).toHaveBeenCalledWith(
      sim.id,
      { amount: 1000, todaysMoney: true },
      seededUser.token
    );
  });

  it("closes the modal, refreshes transactions, and toasts the applied amount on deposit success", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.deposit).mockResolvedValueOnce({
      simulationId: sim.id,
      appliedAmount: 1000,
      cashBalance: 51000,
      totalPatrimony: 151000,
      deflation: null,
    });
    vi.mocked(simulationApi.transactions).mockResolvedValueOnce(transactions);
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "+ Contribute" }));
    await user.type(screen.getByLabelText(/Amount/), "1000");
    await user.click(screen.getByRole("button", { name: "Contribute" }));

    expect(await screen.findByText(/Deposited/)).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText(money(51000, sim.baseCurrency))).toBeInTheDocument();
    await waitFor(() => expect(simulationApi.transactions).toHaveBeenCalledTimes(2));
  });

  it("keeps the deposit modal open with an inline Banner error on failure", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.deposit).mockRejectedValueOnce(
      new ApiError(400, "amount: must be greater than 0")
    );
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "+ Contribute" }));
    await user.type(screen.getByLabelText(/Amount/), "1000");
    await user.click(screen.getByRole("button", { name: "Contribute" }));

    expect(await screen.findByText("amount: must be greater than 0")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("submits a withdrawal with amount and todaysMoney", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.withdraw).mockResolvedValueOnce({
      simulationId: sim.id,
      appliedAmount: 500,
      cashBalance: 49500,
      totalPatrimony: 149500,
      deflation: null,
    });
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "− Withdraw" }));
    await user.type(screen.getByLabelText(/Amount/), "500");
    await user.click(screen.getByRole("button", { name: "Withdraw" }));

    expect(simulationApi.withdraw).toHaveBeenCalledWith(
      sim.id,
      { amount: 500, todaysMoney: false },
      seededUser.token
    );
  });

  it("closes the modal, refreshes transactions, and toasts the applied amount on withdraw success", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.withdraw).mockResolvedValueOnce({
      simulationId: sim.id,
      appliedAmount: 500,
      cashBalance: 49500,
      totalPatrimony: 149500,
      deflation: null,
    });
    vi.mocked(simulationApi.transactions).mockResolvedValueOnce(transactions);
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "− Withdraw" }));
    await user.type(screen.getByLabelText(/Amount/), "500");
    await user.click(screen.getByRole("button", { name: "Withdraw" }));

    expect(await screen.findByText(/Withdrew/)).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByText(money(49500, sim.baseCurrency))).toBeInTheDocument();
  });

  it("keeps the withdraw modal open with an inline Banner error on insufficient balance", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.withdraw).mockRejectedValueOnce(
      new ApiError(400, "Insufficient cash balance")
    );
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "− Withdraw" }));
    await user.type(screen.getByLabelText(/Amount/), "999999");
    await user.click(screen.getByRole("button", { name: "Withdraw" }));

    expect(await screen.findByText("Insufficient cash balance")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});

describe("reset", () => {
  it("disables Reset before any month has been advanced, with an explanatory hover", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    const resetButton = screen.getByRole("button", { name: "⟲ Reset" });
    expect(resetButton).toBeDisabled();

    await user.hover(resetButton.closest("span") ?? resetButton);
    expect(await screen.findByRole("tooltip")).toHaveTextContent("unlocks after you advance");
  });

  it("enables Reset once currentMonth differs from startMonth", async () => {
    mockLoadSuccess({ simulation: { ...sim, currentMonth: "2020-02" } });
    renderDashboard();
    await screen.findByText(sim.name);

    expect(screen.getByRole("button", { name: "⟲ Reset" })).toBeEnabled();
  });

  it("the reset confirmation dialog explains irreversibility", async () => {
    mockLoadSuccess({ simulation: { ...sim, currentMonth: "2020-02" } });
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "⟲ Reset" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("cannot be undone");
  });

  it("cancel sends no request and closes the dialog", async () => {
    mockLoadSuccess({ simulation: { ...sim, currentMonth: "2020-02" } });
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "⟲ Reset" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(simulationApi.reset).not.toHaveBeenCalled();
  });

  it("confirming reset calls POST /reset, refreshes positions and transactions, and toasts", async () => {
    mockLoadSuccess({ simulation: { ...sim, currentMonth: "2020-02" } });
    vi.mocked(simulationApi.reset).mockResolvedValueOnce({
      ...sim,
      currentMonth: "2020-02",
      cashBalance: 60000,
    });
    vi.mocked(simulationApi.positions).mockResolvedValueOnce(positionsResponse);
    vi.mocked(simulationApi.transactions).mockResolvedValueOnce(transactions);
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "⟲ Reset" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "⟲ Reset" }));

    expect(simulationApi.reset).toHaveBeenCalledWith(sim.id, seededUser.token);
    expect(await screen.findByRole("status")).toHaveTextContent("Simulation reset to the last month advance.");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await waitFor(() => expect(simulationApi.positions).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(simulationApi.transactions).toHaveBeenCalledTimes(2));
  });

  it("keeps the reset dialog open with an inline Banner error on failure", async () => {
    mockLoadSuccess({ simulation: { ...sim, currentMonth: "2020-02" } });
    vi.mocked(simulationApi.reset).mockRejectedValueOnce(new ApiError(500, "Request failed (500)"));
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "⟲ Reset" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "⟲ Reset" }));

    expect(await screen.findByText("Request failed (500)")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});

describe("advance month", () => {
  it("the advance confirmation dialog explains irreversibility", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "▸▸ Advance month" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("cannot be undone");
  });

  it("cancel sends no request and closes the dialog", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "▸▸ Advance month" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(simulationApi.advance).not.toHaveBeenCalled();
  });

  it("confirming advance calls POST /advance and updates simulation state from the response", async () => {
    mockLoadSuccess();
    const advanceResponse: AdvanceMonthResponse = {
      simulationId: sim.id,
      currentMonth: "2020-02",
      cashBalance: 45000,
      totalAssetValue: 3000,
      totalPatrimony: 48000,
      positions: [],
    };
    vi.mocked(simulationApi.advance).mockResolvedValueOnce(advanceResponse);
    vi.mocked(simulationApi.positions).mockResolvedValueOnce(positionsResponse);
    vi.mocked(simulationApi.transactions).mockResolvedValueOnce(transactions);
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "▸▸ Advance month" }));
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "▸▸ Advance month" })
    );

    expect(simulationApi.advance).toHaveBeenCalledWith(sim.id, seededUser.token);
    await waitFor(() => expect(screen.getByTestId("current-date-value")).toHaveTextContent("FEB 2020"));
    expect(screen.getByText(money(45000, sim.baseCurrency))).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await waitFor(() => expect(simulationApi.positions).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(simulationApi.transactions).toHaveBeenCalledTimes(2));
  });

  it("toasts once per position with dividendReceived > 0, and not for zero-dividend positions", async () => {
    mockLoadSuccess();
    const advanceResponse: AdvanceMonthResponse = {
      simulationId: sim.id,
      currentMonth: "2020-02",
      cashBalance: 45000,
      totalAssetValue: 3000,
      totalPatrimony: 48000,
      positions: [
        {
          ticker: "AAPL",
          assetName: "Apple Inc.",
          quantity: 10,
          price: 150,
          wasTruncated: false,
          dividendReceived: 25,
          weight: 0.5,
          costBasis: 1400,
          totalDividendsReceived: 25,
        },
        {
          ticker: "MSFT",
          assetName: "Microsoft Corporation",
          quantity: 5,
          price: 200,
          wasTruncated: false,
          dividendReceived: 0,
          weight: 0.5,
          costBasis: 1100,
          totalDividendsReceived: 0,
        },
      ],
    };
    vi.mocked(simulationApi.advance).mockResolvedValueOnce(advanceResponse);
    vi.mocked(simulationApi.positions).mockResolvedValueOnce(positionsResponse);
    vi.mocked(simulationApi.transactions).mockResolvedValueOnce(transactions);
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "▸▸ Advance month" }));
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "▸▸ Advance month" })
    );

    expect(await screen.findByText(/Apple Inc\..*dividend/)).toBeInTheDocument();
    expect(screen.queryByText(/Microsoft Corporation.*dividend/)).not.toBeInTheDocument();
  });

  it("keeps the advance dialog open with an inline Banner error on failure", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.advance).mockRejectedValueOnce(new ApiError(500, "Request failed (500)"));
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByText(sim.name);

    await user.click(screen.getByRole("button", { name: "▸▸ Advance month" }));
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "▸▸ Advance month" })
    );

    expect(await screen.findByText("Request failed (500)")).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
