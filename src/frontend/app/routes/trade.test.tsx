import { act, fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders, seedAuthenticatedUser } from "~/test/test-utils";
import {
  ApiError,
  simulationApi,
  type AssetDetail,
  type Position,
  type PositionsResponse,
  type Simulation,
  type TickerSearchResult,
} from "~/lib/api";
import Trade from "./trade";

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    simulationApi: {
      get: vi.fn(),
      positions: vi.fn(),
      searchAssets: vi.fn(),
      getAsset: vi.fn(),
      buy: vi.fn(),
      sell: vi.fn(),
    },
  };
});

const loginStub = { path: "/login", element: <div>Login page</div> };
const dashboardStub = { path: "/simulations/:id", element: <div>Dashboard page</div> };

// RTL's getByText/toHaveTextContent normalize a DOM node's own whitespace
// (collapsing any run of whitespace, including the non-breaking spaces
// Intl.NumberFormat inserts, into a single regular space) but do not apply
// that same normalization to the string being searched for - mirrors
// simulation-dashboard.test.tsx's money() helper for the same reason.
function usdCurrency(amount: number): string {
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "USD" })
    .format(amount)
    .replace(/[  ]/g, " ");
}

const sim: Simulation = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Retirement plan",
  baseCurrency: "BRL",
  startMonth: "2020-01",
  currentMonth: "2020-01",
  cashBalance: 50000,
  totalPatrimony: 150000,
};

const heldPosition: Position = {
  ticker: "AAPL",
  assetName: "Apple Inc.",
  quantity: 10,
  currentPrice: 750,
  wasTruncated: false,
  marketValue: 7500,
  weight: 0.6,
  costBasis: 7000,
  gainAmount: 500,
  gainPercent: 0.0714,
};

const positionsResponse: PositionsResponse = {
  positions: [heldPosition],
  totalAssetValue: 7500,
  totalGainAmount: 500,
  totalGainPercent: 0.0714,
};

const emptyPositionsResponse: PositionsResponse = {
  positions: [],
  totalAssetValue: 0,
  totalGainAmount: 0,
  totalGainPercent: 0,
};

const assetDetail: AssetDetail = {
  ticker: "AAPL",
  name: "Apple Inc.",
  currency: "USD",
  requestedMonth: "2020-01",
  returnedMonth: "2020-01",
  truncated: false,
  series: [
    { referenceMonth: "2020-01", open: 95, high: 105, low: 90, close: 100, volume: 1000, dividends: 0, splits: 0 },
  ],
  cashBalance: { amount: 20000, currency: "USD", wasConverted: true },
};

const searchResults: TickerSearchResult[] = [
  { ticker: "AAPL", name: "Apple Inc.", exchange: "NASDAQ", assetType: "EQUITY" },
];

let seededUser: { name: string; token: string };

function renderTrade(
  route = `/simulations/${sim.id}/trade`,
  redirectStubs: { path: string; element: ReactElement }[] = []
) {
  return renderWithProviders(<Trade />, {
    route,
    path: "/simulations/:id/trade",
    redirectStubs,
  });
}

function mockLoadSuccess({
  simulation = sim,
  positions = positionsResponse,
}: { simulation?: Simulation; positions?: PositionsResponse } = {}) {
  vi.mocked(simulationApi.get).mockResolvedValueOnce(simulation);
  vi.mocked(simulationApi.positions).mockResolvedValueOnce(positions);
}

beforeEach(() => {
  localStorage.clear();
  seededUser = seedAuthenticatedUser({ name: "Ada Lovelace" });
  vi.mocked(simulationApi.get).mockReset();
  vi.mocked(simulationApi.positions).mockReset();
  vi.mocked(simulationApi.searchAssets).mockReset();
  vi.mocked(simulationApi.getAsset).mockReset();
  vi.mocked(simulationApi.buy).mockReset();
  vi.mocked(simulationApi.sell).mockReset();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("authentication", () => {
  it("redirects to /login when unauthenticated", () => {
    localStorage.clear();
    renderTrade(`/simulations/${sim.id}/trade`, [loginStub]);
    expect(screen.getByText("Login page")).toBeInTheDocument();
  });
});

describe("ticker param", () => {
  it("with a ticker param, loads and renders the asset directly without requiring a search", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);

    await screen.findByText(assetDetail.name);

    expect(simulationApi.getAsset).toHaveBeenCalledWith(sim.id, "AAPL", seededUser.token);
    expect(simulationApi.searchAssets).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "Chart" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buy shares" })).toBeInTheDocument();
  });

  it("with a ticker param that 400s, falls back to the empty search state and shows an error toast", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockRejectedValueOnce(new ApiError(400, "Asset predates start date"));
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);

    expect(await screen.findByText("Asset predates start date")).toBeInTheDocument();
    expect(screen.getByText("Search for an asset to begin trading.")).toBeInTheDocument();
  });

  it("with a ticker param that 404s, falls back to the empty search state and shows an error toast", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockRejectedValueOnce(new ApiError(404, "Asset not found"));
    renderTrade(`/simulations/${sim.id}/trade?ticker=UNKNOWN`);

    expect(await screen.findByText("Asset not found")).toBeInTheDocument();
    expect(screen.getByText("Search for an asset to begin trading.")).toBeInTheDocument();
  });

  it("with no ticker param, shows the empty search state and does not call getAsset", async () => {
    mockLoadSuccess();
    renderTrade();

    await screen.findByText("Search for an asset to begin trading.");
    expect(simulationApi.getAsset).not.toHaveBeenCalled();
  });
});

describe("navigation", () => {
  it("'← Back' navigates to the simulation dashboard", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade`, [dashboardStub]);
    await screen.findByRole("heading", { name: "Trade assets" });

    await user.click(screen.getByRole("link", { name: "← Back" }));

    expect(await screen.findByText("Dashboard page")).toBeInTheDocument();
  });
});

describe("search", () => {
  it("typing fewer than 2 characters triggers no search request and shows no results", async () => {
    mockLoadSuccess();
    const user = userEvent.setup();
    renderTrade();
    await screen.findByLabelText(/Ticker or name/i);

    await user.type(screen.getByLabelText(/Ticker or name/i), "A");

    expect(simulationApi.searchAssets).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: /AAPL/ })).not.toBeInTheDocument();
  });

  it("after the debounce, searches and renders up to 15 clickable results", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.searchAssets).mockResolvedValueOnce(searchResults);
    renderTrade();
    await screen.findByLabelText(/Ticker or name/i);

    vi.useFakeTimers();
    fireEvent.change(screen.getByLabelText(/Ticker or name/i), { target: { value: "AAPL" } });
    expect(simulationApi.searchAssets).not.toHaveBeenCalled();

    await act(() => vi.advanceTimersByTimeAsync(1000));

    expect(simulationApi.searchAssets).toHaveBeenCalledWith(sim.id, "AAPL", seededUser.token);
    const result = screen.getByRole("button", { name: /AAPL/ });
    expect(within(result).getByText("Apple Inc.")).toBeInTheDocument();
    expect(within(result).getByText("NASDAQ")).toBeInTheDocument();
  });

  it("clicking a search result loads and renders the selected asset", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.searchAssets).mockResolvedValueOnce(searchResults);
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    renderTrade();
    await screen.findByLabelText(/Ticker or name/i);

    vi.useFakeTimers();
    fireEvent.change(screen.getByLabelText(/Ticker or name/i), { target: { value: "AAPL" } });
    await act(() => vi.advanceTimersByTimeAsync(1000));
    vi.useRealTimers();

    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /AAPL/ }));

    expect(simulationApi.getAsset).toHaveBeenCalledWith(sim.id, "AAPL", seededUser.token);
    expect(await screen.findByRole("heading", { name: "Chart" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Buy shares" })).toBeInTheDocument();
  });

  it("closes the suggestion list once a result is selected, keeping the typed query", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.searchAssets).mockResolvedValueOnce(searchResults);
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    renderTrade();
    await screen.findByLabelText(/Ticker or name/i);

    vi.useFakeTimers();
    fireEvent.change(screen.getByLabelText(/Ticker or name/i), { target: { value: "AAPL" } });
    await act(() => vi.advanceTimersByTimeAsync(1000));
    vi.useRealTimers();

    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /AAPL/ }));
    await screen.findByRole("heading", { name: "Chart" });

    const resultsContainer = screen.getByTestId("search-results");
    expect(within(resultsContainer).queryAllByRole("button")).toHaveLength(0);
    expect(within(resultsContainer).queryByText("No matches.")).not.toBeInTheDocument();
    expect(screen.getByLabelText(/Ticker or name/i)).toHaveValue("AAPL");
  });

  it("selecting a search result that 400s shows an error toast and selects nothing", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.searchAssets).mockResolvedValueOnce(searchResults);
    vi.mocked(simulationApi.getAsset).mockRejectedValueOnce(new ApiError(400, "Not available this month"));
    renderTrade();
    await screen.findByLabelText(/Ticker or name/i);

    vi.useFakeTimers();
    fireEvent.change(screen.getByLabelText(/Ticker or name/i), { target: { value: "AAPL" } });
    await act(() => vi.advanceTimersByTimeAsync(1000));
    vi.useRealTimers();

    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /AAPL/ }));

    expect(await screen.findByText("Not available this month")).toBeInTheDocument();
    expect(screen.getByText("Search for an asset to begin trading.")).toBeInTheDocument();
  });
});

describe("currency conversion toast", () => {
  it("shows a toast when the selected asset's cash balance was converted to its own currency", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);

    expect(await screen.findByText("Cash balance converted to USD for this trade.")).toBeInTheDocument();
  });

  it("does not show a conversion toast when the cash balance was not converted", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce({
      ...assetDetail,
      cashBalance: { ...assetDetail.cashBalance, wasConverted: false },
    });
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);

    await screen.findByRole("heading", { name: "Chart" });
    expect(screen.queryByText(/converted to/)).not.toBeInTheDocument();
  });
});

describe("holding & sell availability", () => {
  it("disables Sell when the selected asset has no holding", async () => {
    mockLoadSuccess({ positions: emptyPositionsResponse });
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);

    await screen.findByRole("heading", { name: "Trade" });
    expect(screen.getByRole("button", { name: "Sell" })).toBeDisabled();
  });

  it("enables Sell when the selected asset has a holding", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);

    await screen.findByRole("heading", { name: "Trade" });
    expect(screen.getByRole("button", { name: "Sell" })).toBeEnabled();
  });
});

describe("quantity-driven readout", () => {
  it("updates the estimated cost and affordability as the buy quantity changes", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.type(screen.getByLabelText("Quantity"), "10");

    expect(screen.getByText(/Estimated cost/)).toHaveTextContent(usdCurrency(1000));
    expect(screen.getByText(/Cash available/)).toHaveClass("text-muted");
    expect(simulationApi.searchAssets).not.toHaveBeenCalled();
    expect(simulationApi.getAsset).toHaveBeenCalledTimes(1);
  });

  it("shows an insufficient-cash indicator when the buy quantity exceeds available cash", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce({
      ...assetDetail,
      cashBalance: { amount: 50, currency: "USD", wasConverted: true },
    });
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.type(screen.getByLabelText("Quantity"), "10");

    expect(screen.getByText(/Cash available/)).toHaveClass("text-vermilion");
  });

  it("updates the estimated proceeds and affordability as the sell quantity changes", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.click(screen.getByRole("button", { name: "Sell" }));
    await user.type(screen.getByLabelText("Quantity"), "20");

    expect(screen.getByText(/Estimated proceeds/)).toHaveTextContent(usdCurrency(2000));
    expect(screen.getByText(/Held/)).toHaveTextContent("10 shares");
    expect(screen.getByText(/Held/)).toHaveClass("text-vermilion");
  });
});

describe("Max", () => {
  it("'Max' fills floor(convertedCashBalance / currentPrice) for buy", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce({
      ...assetDetail,
      cashBalance: { amount: 250, currency: "USD", wasConverted: true },
    });
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.click(screen.getByRole("button", { name: "Max" }));

    expect(screen.getByLabelText("Quantity")).toHaveValue(2);
  });

  it("'Max' fills the full held quantity for sell", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.click(screen.getByRole("button", { name: "Sell" }));
    await user.click(screen.getByRole("button", { name: "Max" }));

    expect(screen.getByLabelText("Quantity")).toHaveValue(10);
  });
});

describe("submitting a trade", () => {
  it("submitting a buy calls POST /buy with {ticker, quantity} and updates cash/holding figures on success", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    vi.mocked(simulationApi.buy).mockResolvedValueOnce({
      simulationId: sim.id,
      type: "BUY",
      position: {
        ticker: "AAPL",
        assetName: "Apple Inc.",
        quantity: 15,
        costBasis: 7500,
        weight: 0.7,
        totalDividendsReceived: 0,
      },
      appliedAmount: 500,
      cashBalance: 49500,
      totalAssetValue: 8000,
      totalPatrimony: 151500,
    });
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.type(screen.getByLabelText("Quantity"), "5");
    await user.click(screen.getByRole("button", { name: "Buy shares" }));

    expect(simulationApi.buy).toHaveBeenCalledWith(sim.id, { ticker: "AAPL", quantity: 5 }, seededUser.token);
    expect(await screen.findByText(/Bought 5 shares of AAPL/)).toBeInTheDocument();
    expect(screen.getByLabelText("Quantity")).toHaveValue(null);
    // Quantity comes straight from the trade response; cash available is the
    // asset-detail cash balance minus the trade's own-currency cost.
    expect(screen.getByTestId("holding-quantity")).toHaveTextContent("15");
    expect(screen.getByText(/Cash available/)).toHaveTextContent(usdCurrency(19500));
  });

  it("submitting a sell calls POST /sell with {ticker, quantity} and updates cash/holding figures on success", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    vi.mocked(simulationApi.sell).mockResolvedValueOnce({
      simulationId: sim.id,
      type: "SELL",
      position: {
        ticker: "AAPL",
        assetName: "Apple Inc.",
        quantity: 5,
        costBasis: 3500,
        weight: 0.3,
        totalDividendsReceived: 0,
      },
      appliedAmount: 500,
      cashBalance: 50500,
      totalAssetValue: 4000,
      totalPatrimony: 151500,
    });
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.click(screen.getByRole("button", { name: "Sell" }));
    await user.type(screen.getByLabelText("Quantity"), "5");
    await user.click(screen.getByRole("button", { name: "Sell shares" }));

    expect(simulationApi.sell).toHaveBeenCalledWith(sim.id, { ticker: "AAPL", quantity: 5 }, seededUser.token);
    expect(await screen.findByText(/Sold 5 shares of AAPL/)).toBeInTheDocument();
    expect(screen.getByTestId("holding-quantity")).toHaveTextContent("5");
    expect(screen.getByText(/Held/)).toHaveTextContent("5 shares");
  });

  it("disables Sell and resets to Buy once a full sell zeroes the holding", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    vi.mocked(simulationApi.sell).mockResolvedValueOnce({
      simulationId: sim.id,
      type: "SELL",
      position: {
        ticker: "AAPL",
        assetName: "Apple Inc.",
        quantity: 0,
        costBasis: 0,
        weight: 0,
        totalDividendsReceived: 0,
      },
      appliedAmount: 7500,
      cashBalance: 57500,
      totalAssetValue: 0,
      totalPatrimony: 151500,
    });
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.click(screen.getByRole("button", { name: "Sell" }));
    await user.type(screen.getByLabelText("Quantity"), "10");
    await user.click(screen.getByRole("button", { name: "Sell shares" }));

    await screen.findByText(/Sold 10 shares of AAPL/);
    expect(screen.getByRole("button", { name: "Sell" })).toBeDisabled();
  });
});

describe("trade failures", () => {
  it("shows an error toast and preserves the entered quantity when a buy fails", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    vi.mocked(simulationApi.buy).mockRejectedValueOnce(
      new ApiError(400, "Purchase cost (2000.00 USD) exceeds available cash balance (20000.00 USD)")
    );
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.type(screen.getByLabelText("Quantity"), "20");
    await user.click(screen.getByRole("button", { name: "Buy shares" }));

    expect(await screen.findByText(/exceeds available cash balance/)).toBeInTheDocument();
    expect(screen.getByLabelText("Quantity")).toHaveValue(20);
  });

  it("shows an error toast and preserves the entered quantity when a sell fails", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    vi.mocked(simulationApi.sell).mockRejectedValueOnce(
      new ApiError(400, "Cannot sell 20 units of AAPL - only 10 held")
    );
    const user = userEvent.setup();
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);
    await screen.findByRole("heading", { name: "Trade" });

    await user.click(screen.getByRole("button", { name: "Sell" }));
    await user.type(screen.getByLabelText("Quantity"), "20");
    await user.click(screen.getByRole("button", { name: "Sell shares" }));

    expect(await screen.findByText(/only 10 held/)).toBeInTheDocument();
    expect(screen.getByLabelText("Quantity")).toHaveValue(20);
  });
});

describe("chart", () => {
  it("renders the chart with the selected asset's monthly series mapped to the Bar contract", async () => {
    mockLoadSuccess();
    vi.mocked(simulationApi.getAsset).mockResolvedValueOnce(assetDetail);
    renderTrade(`/simulations/${sim.id}/trade?ticker=AAPL`);

    expect(await screen.findByTestId("chart-type-candlestick")).toBeInTheDocument();
  });
});
