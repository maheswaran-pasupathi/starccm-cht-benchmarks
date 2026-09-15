"""
B03 -- straight busbar, 1D transient thermal network.

One-node linear model (constant properties, convection-only -- the case
with a known closed form):

    Cth dT/dt = P - (T - T_inf)/Rth
    tau = Rth * Cth
    T(t) = T_inf + P*Rth*(1 - exp(-t/tau))          [current step at t=0]

Gate (roadmap): the ONE-NODE NUMERICAL solution must reproduce this
analytical step response before the nonlinear/multi-node extensions are
accepted as correct. That check is `test_one_node_numeric_matches_analytical`
in tests/test_thermal.py's sibling test module for B03.

Then extended to:
  - nonlinear one-node (temperature-dependent R(T), radiation) -- solved
    numerically only, no closed form exists;
  - a three-node network scaffold: busbar node <-> terminal/end node
    <-> surrounding/enclosure node, each with its own Cth and coupling
    conductance. This is a SCAFFOLD (structure + working linear solve),
    not yet a fully validated multi-node benchmark -- see the roadmap's
    B03 gate wording ("...three-node network" is listed as an extension
    after the one-node gate passes).
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
from scipy.integrate import solve_ivp

import materials as mat
from b01_straight_busbar import BusbarGeometry
from b02_steady_thermal import ThermalBalanceInputs, _heat_terms as b02_heat_terms


# ---------------------------------------------------------------------------
# One-node model
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class OneNodeInputs:
    geometry: BusbarGeometry
    material: str
    h_W_m2K: float = 10.0
    T_inf_K: float = 293.15
    include_radiation: bool = False
    epsilon: float = 0.78
    T_sur_K: float | None = None
    temperature_dependent_resistance: bool = False

    @property
    def A_ext_m2(self) -> float:
        perimeter = 2.0 * (self.geometry.width_m + self.geometry.thickness_m)
        return perimeter * self.geometry.length_m

    @property
    def volume_m3(self) -> float:
        return self.geometry.area_m2 * self.geometry.length_m

    @property
    def Cth_J_K(self) -> float:
        return mat.density(self.material) * self.volume_m3 * mat.specific_heat(self.material)

    @property
    def Rth_convection_only_K_W(self) -> float:
        """Linear thermal resistance for the convection-only case -- the
        one with a closed form. Not meaningful once radiation is enabled
        (radiation is nonlinear in T), used only for the analytical check
        and as a reporting quantity."""
        return 1.0 / (self.h_W_m2K * self.A_ext_m2)


def current_step(I_A: float):
    def I_of_t(t: float) -> float:
        return I_A if t >= 0 else 0.0
    return I_of_t


def overload_pulse(I_base_A: float, I_overload_A: float, pulse_start_s: float, pulse_duration_s: float):
    def I_of_t(t: float) -> float:
        if pulse_start_s <= t < pulse_start_s + pulse_duration_s:
            return I_overload_A
        return I_base_A
    return I_of_t


def duty_cycle(I_high_A: float, I_low_A: float, period_s: float, duty_fraction: float):
    def I_of_t(t: float) -> float:
        phase = t % period_s
        return I_high_A if phase < duty_fraction * period_s else I_low_A
    return I_of_t


def cooling_after_current_off(I_A: float, off_time_s: float):
    def I_of_t(t: float) -> float:
        return I_A if t < off_time_s else 0.0
    return I_of_t


def _resistance(inputs: OneNodeInputs, T_K: float) -> float:
    T_for_R = T_K if inputs.temperature_dependent_resistance else inputs.T_inf_K
    return mat.rho_e(inputs.material, T_for_R) * inputs.geometry.length_m / inputs.geometry.area_m2


def _rhs(t: float, y: np.ndarray, inputs: OneNodeInputs, I_of_t) -> np.ndarray:
    T = y[0]
    I = I_of_t(t)
    R = _resistance(inputs, T)
    P = I**2 * R
    Q_conv = inputs.h_W_m2K * inputs.A_ext_m2 * (T - inputs.T_inf_K)
    if inputs.include_radiation:
        T_sur = inputs.T_sur_K if inputs.T_sur_K is not None else inputs.T_inf_K
        Q_rad = inputs.epsilon * mat.stefan_boltzmann() * inputs.A_ext_m2 * (T**4 - T_sur**4)
    else:
        Q_rad = 0.0
    dTdt = (P - Q_conv - Q_rad) / inputs.Cth_J_K
    return np.array([dTdt])


def solve_one_node(inputs: OneNodeInputs, I_of_t, t_span_s: tuple[float, float],
                    T0_K: float | None = None, max_step_s: float | None = None,
                    n_eval: int = 400) -> dict:
    T0 = T0_K if T0_K is not None else inputs.T_inf_K
    t_eval = np.linspace(t_span_s[0], t_span_s[1], n_eval)
    kwargs = dict(method="RK45", rtol=1e-9, atol=1e-9, t_eval=t_eval)
    if max_step_s is not None:
        kwargs["max_step"] = max_step_s
    sol = solve_ivp(_rhs, t_span_s, [T0], args=(inputs, I_of_t), **kwargs)
    if not sol.success:
        raise RuntimeError(f"B03 one-node ODE solve failed: {sol.message}")
    T_t = sol.y[0]
    return {"t_s": sol.t, "T_K": T_t, "status": "SIMULATED"}


def analytical_step_response(inputs: OneNodeInputs, I_A: float, t_s: np.ndarray) -> np.ndarray:
    """Linear closed form, valid ONLY for convection-only + constant R."""
    R = _resistance(inputs, inputs.T_inf_K)
    P = I_A**2 * R
    Rth = inputs.Rth_convection_only_K_W
    tau = Rth * inputs.Cth_J_K
    return inputs.T_inf_K + P * Rth * (1.0 - np.exp(-t_s / tau))


def time_to_fraction(t_s: np.ndarray, T_K: np.ndarray, T_inf_K: float, T_final_K: float,
                      fraction: float) -> float:
    target = T_inf_K + fraction * (T_final_K - T_inf_K)
    idx = np.searchsorted(T_K, target)
    if idx >= len(t_s):
        return float("nan")
    if idx == 0:
        return float(t_s[0])
    t0, t1 = t_s[idx - 1], t_s[idx]
    T0, T1 = T_K[idx - 1], T_K[idx]
    if T1 == T0:
        return float(t1)
    return float(t0 + (t1 - t0) * (target - T0) / (T1 - T0))


# ---------------------------------------------------------------------------
# Three-node network SCAFFOLD (linear, constant-conductance version only)
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class ThreeNodeInputs:
    """Node 0 = busbar (Joule heat source), Node 1 = terminal/end,
    Node 2 = surrounding/enclosure. G_01, G_12 are LINEAR coupling
    conductances [W/K] (a linearized stand-in for conduction/convection
    between nodes); G_2_ambient couples node 2 to a fixed ambient.
    This is intentionally the simplest possible extension beyond one node
    -- a genuine nonlinear multi-node solve (contact resistance, radiation
    between nodes, etc.) is deferred to B22/B30, not claimed here."""
    Cth_0_J_K: float
    Cth_1_J_K: float
    Cth_2_J_K: float
    G_01_W_K: float
    G_12_W_K: float
    G_2_ambient_W_K: float
    T_inf_K: float = 293.15


def _three_node_rhs(t: float, y: np.ndarray, inputs: ThreeNodeInputs, P_of_t) -> np.ndarray:
    T0, T1, T2 = y
    P = P_of_t(t)
    dT0 = (P - inputs.G_01_W_K * (T0 - T1)) / inputs.Cth_0_J_K
    dT1 = (inputs.G_01_W_K * (T0 - T1) - inputs.G_12_W_K * (T1 - T2)) / inputs.Cth_1_J_K
    dT2 = (inputs.G_12_W_K * (T1 - T2) - inputs.G_2_ambient_W_K * (T2 - inputs.T_inf_K)) / inputs.Cth_2_J_K
    return np.array([dT0, dT1, dT2])


def solve_three_node(inputs: ThreeNodeInputs, P_of_t, t_span_s: tuple[float, float],
                      T0_K: tuple[float, float, float] | None = None, n_eval: int = 400) -> dict:
    T0 = T0_K if T0_K is not None else (inputs.T_inf_K,) * 3
    t_eval = np.linspace(t_span_s[0], t_span_s[1], n_eval)
    sol = solve_ivp(_three_node_rhs, t_span_s, list(T0), args=(inputs, P_of_t),
                     method="RK45", rtol=1e-9, atol=1e-9, t_eval=t_eval)
    if not sol.success:
        raise RuntimeError(f"B03 three-node ODE solve failed: {sol.message}")
    return {"t_s": sol.t, "T0_K": sol.y[0], "T1_K": sol.y[1], "T2_K": sol.y[2], "status": "SIMULATED"}


if __name__ == "__main__":
    import csv
    import os

    from b01_straight_busbar import BusbarGeometry as _BG

    geom = _BG(length_m=0.300, width_m=0.040, thickness_m=0.005)
    inputs = OneNodeInputs(geom, "copper", h_W_m2K=10.0, include_radiation=False,
                            temperature_dependent_resistance=False)
    I_A = 400.0
    tau = inputs.Rth_convection_only_K_W * inputs.Cth_J_K
    t_span = (0.0, 6.0 * tau)

    numeric = solve_one_node(inputs, current_step(I_A), t_span)
    analytical = analytical_step_response(inputs, I_A, numeric["t_s"])
    max_diff_K = float(np.max(np.abs(numeric["T_K"] - analytical)))
    print(f"tau = {tau:.2f} s; max |numeric - analytical| = {max_diff_K:.6e} K over {t_span[1]:.1f}s")
    assert max_diff_K < 1e-3, "B03 gate failed: one-node numeric does not match analytical step response"
    print("B03 gate PASSED: one-node numeric matches analytical step response to <1e-3 K.")

    T_final = analytical[-1]
    t63 = time_to_fraction(numeric["t_s"], numeric["T_K"], inputs.T_inf_K, T_final, 0.632)
    t90 = time_to_fraction(numeric["t_s"], numeric["T_K"], inputs.T_inf_K, T_final, 0.90)
    print(f"t63 = {t63:.2f}s (tau={tau:.2f}s, ratio={t63/tau:.4f}, expect ~1.0)")
    print(f"t90 = {t90:.2f}s (tau*ln(10)={tau*np.log(10):.2f}s)")

    out_dir = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "results", "processed"))
    os.makedirs(out_dir, exist_ok=True)
    csv_path = os.path.join(out_dir, "b03_step_response.csv")
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["t_s", "T_numeric_K", "T_analytical_K"])
        for t, tn, ta in zip(numeric["t_s"], numeric["T_K"], analytical):
            w.writerow([t, tn, ta])
    print(f"wrote {csv_path}")

    import plotting
    fig_dir = os.path.normpath(os.path.join(out_dir, "..", "figures"))
    plotting.plot_b03_step_response(numeric["t_s"], numeric["T_K"], analytical, fig_dir)
    print(f"wrote B03 figure to {fig_dir}")

    # Three-node scaffold: sanity run (not yet a validated benchmark), no
    # closed-form gate exists for this per the roadmap.
    tn_inputs = ThreeNodeInputs(
        Cth_0_J_K=inputs.Cth_J_K, Cth_1_J_K=inputs.Cth_J_K * 0.3, Cth_2_J_K=inputs.Cth_J_K * 5.0,
        G_01_W_K=5.0, G_12_W_K=2.0, G_2_ambient_W_K=inputs.h_W_m2K * inputs.A_ext_m2,
    )
    P_step = lambda t: I_A**2 * _resistance(inputs, inputs.T_inf_K)  # noqa: E731
    tn_result = solve_three_node(tn_inputs, P_step, t_span)
    print(f"three-node scaffold ran: T0 final={tn_result['T0_K'][-1]:.2f}K, "
          f"T1 final={tn_result['T1_K'][-1]:.2f}K, T2 final={tn_result['T2_K'][-1]:.2f}K "
          "(SCAFFOLD -- not yet validated against an independent reference)")
