import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { ProtectedRoute } from "./ProtectedRoute";

const useAuthMock = vi.fn();
vi.mock("~/lib/auth-context", () => ({
  useAuth: () => useAuthMock(),
}));

function renderAtProtectedPath() {
  return render(
    <MemoryRouter initialEntries={["/protected"]}>
      <Routes>
        <Route
          path="/protected"
          element={
            <ProtectedRoute>
              <div>Protected content</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div>Login page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe("ProtectedRoute", () => {
  it("renders nothing while auth state is not yet initialized", () => {
    useAuthMock.mockReturnValue({ user: null, initialized: false });

    const { container } = renderAtProtectedPath();

    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
    expect(screen.queryByText("Login page")).not.toBeInTheDocument();
  });

  it("redirects to /login once initialized without a user", () => {
    useAuthMock.mockReturnValue({ user: null, initialized: true });

    renderAtProtectedPath();

    expect(screen.getByText("Login page")).toBeInTheDocument();
    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
  });

  it("renders children once initialized with a user", () => {
    useAuthMock.mockReturnValue({ user: { name: "Ada", token: "jwt" }, initialized: true });

    renderAtProtectedPath();

    expect(screen.getByText("Protected content")).toBeInTheDocument();
    expect(screen.queryByText("Login page")).not.toBeInTheDocument();
  });
});
