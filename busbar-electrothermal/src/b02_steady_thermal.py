"""
B02 -- straight busbar, 1D steady electro-thermal balance (lumped node).

Solves the nonlinear steady energy balance for a single lumped bar
temperature T_s [K]:

    I^2 * R(T_s) = Q_conv(T_s) + Q_rad(T_s) + Q_end(T_s)

    Q_conv(T_s) = h * A_ext * (T_s - T_inf)
    Q_rad(T_s)  = epsilon * sigma_SB * A_ext * (T_s^4 - T_sur^4)
    Q_end(T_s)  = (T_s - T_end) / R_end        [ideal-terminal end conduction]
    R(T_s)      = rho_e(material, T_s) * L / A  [from B01's material model]

All five required variants (convection-only/constant-R, +radiation,
temperature-dependent R, +end conduction, full combined) are the SAME
function with terms switched on/off via flags -- one implementation, not
five near-duplicates, so there is exactly one place to get the physics
right.

Gate (roadmap): normalized energy residual below 1e-6 for the numerical
root solution.
"""
from __future__ import annotations

from dataclasses import dataclass

from scipy.optimize import brentq

import materials as mat
from b01_straight_busbar import BusbarGeometry, solve as solve_b01


@dataclass(frozen=True)
class ThermalBalanceInputs:
    geometry: BusbarGeometry
    current_A: float
    material: str
    T_inf_K: float = 293.15          # ambient (convective) far-field
    T_sur_K: float | None = None      # radiative surroundings; defaults to T_inf_K if None
    h_W_m2K: float = 10.0             # ASSUMED natural-convection coefficient, generic
    epsilon: float = 0.78             # ASSUMED oxidized-copper emissivity, see data/materials.csv
    R_end_K_W: float | None = None    # end-conduction thermal resistance; None disables that term
    T_end_K: float = 293.15
    include_radiation: bool = True
    include_end_conduction: bool = False
    temperature_dependent_resistance: bool = True

    @property
    def A_ext_m2(self) -> float:
        """External (convective/radiative) surface area: perimeter * length,
        the standard lumped-busbar approximation (ignores end-cap area,
        which is small for L >> w, t)."""
        perimeter = 2.0 * (self.geometry.width_m + self.geometry.thickness_m)
        return perimeter * self.geometry.length_m


@dataclass(frozen=True)
class ThermalBalanceResult:
    inputs: ThermalBalanceInputs
    T_s_K: float
    R_ohm: float
    P_joule_W: float
    Q_conv_W: float
    Q_rad_W: float
    Q_end_W: float
    normalized_residual: float
    status: str = "SIMULATED"


def _resistance(inputs: ThermalBalanceInputs, T_s_K: float) -> float:
    T_for_R = T_s_K if inputs.temperature_dependent_resistance else inputs.T_inf_K
    rho = mat.rho_e(inputs.material, T_for_R)
    return rho * inputs.geometry.length_m / inputs.geometry.area_m2


def _heat_terms(inputs: ThermalBalanceInputs, T_s_K: float) -> tuple[float, float, float, float]:
    R = _resistance(inputs, T_s_K)
    P_joule = inputs.current_A**2 * R

    Q_conv = inputs.h_W_m2K * inputs.A_ext_m2 * (T_s_K - inputs.T_inf_K)

    if inputs.include_radiation:
        T_sur = inputs.T_sur_K if inputs.T_sur_K is not None else inputs.T_inf_K
        sb = mat.stefan_boltzmann()
        Q_rad = inputs.epsilon * sb * inputs.A_ext_m2 * (T_s_K**4 - T_sur**4)
    else:
        Q_rad = 0.0

    if inputs.include_end_conduction and inputs.R_end_K_W:
        Q_end = (T_s_K - inputs.T_end_K) / inputs.R_end_K_W
    else:
        Q_end = 0.0

    return R, P_joule, Q_conv, Q_rad, Q_end


def _residual(T_s_K: float, inputs: ThermalBalanceInputs) -> float:
    _, P_joule, Q_conv, Q_rad, Q_end = _heat_terms(inputs, T_s_K)
    return P_joule - (Q_conv + Q_rad + Q_end)


def solve_steady(inputs: ThermalBalanceInputs, T_bracket_K: tuple[float, float] = (200.0, 1200.0),
                  xtol: float = 1e-8) -> ThermalBalanceResult:
    """Root-solves the nonlinear steady energy balance via bisection
    (scipy.optimize.brentq -- robust, no derivative/initial-guess needed,
    appropriate for a monotonic Q_removed(T) - P_generated(T) residual)."""
    lo, hi = T_bracket_K
    f_lo, f_hi = _residual(lo, inputs), _residual(hi, inputs)
    if f_lo * f_hi > 0:
        raise ValueError(
            f"No sign change in energy residual over T in [{lo},{hi}]K "
            f"(f_lo={f_lo:.4g}, f_hi={f_hi:.4g}) -- widen the bracket or "
            "check for a genuine non-convergent (thermal-runaway) input "
            "combination before assuming a bug."
        )
    T_s = brentq(_residual, lo, hi, args=(inputs,), xtol=xtol)
    R, P_joule, Q_conv, Q_rad, Q_end = _heat_terms(inputs, T_s)
    Q_total = Q_conv + Q_rad + Q_end
    normalized_residual = abs(P_joule - Q_total) / max(abs(P_joule), 1e-30)
    return ThermalBalanceResult(
        inputs=inputs, T_s_K=T_s, R_ohm=R, P_joule_W=P_joule,
        Q_conv_W=Q_conv, Q_rad_W=Q_rad, Q_end_W=Q_end,
        normalized_residual=normalized_residual, status="SIMULATED",
    )


# ---------------------------------------------------------------------------
# Required variants (roadmap B02): run all five explicitly, not implied.
# ---------------------------------------------------------------------------

BASELINE_GEOMETRY = BusbarGeometry(length_m=0.300, width_m=0.040, thickness_m=0.005)
BASELINE_CURRENT_A = 400.0


def variant_1_convection_only_constant_R() -> ThermalBalanceResult:
    return solve_steady(ThermalBalanceInputs(
        BASELINE_GEOMETRY, BASELINE_CURRENT_A, "copper",
        include_radiation=False, include_end_conduction=False,
        temperature_dependent_resistance=False,
    ))


def variant_2_convection_plus_radiation() -> ThermalBalanceResult:
    return solve_steady(ThermalBalanceInputs(
        BASELINE_GEOMETRY, BASELINE_CURRENT_A, "copper",
        include_radiation=True, include_end_conduction=False,
        temperature_dependent_resistance=False,
    ))


def variant_3_temperature_dependent_resistance() -> ThermalBalanceResult:
    return solve_steady(ThermalBalanceInputs(
        BASELINE_GEOMETRY, BASELINE_CURRENT_A, "copper",
        include_radiation=True, include_end_conduction=False,
        temperature_dependent_resistance=True,
    ))


def variant_4_end_conduction() -> ThermalBalanceResult:
    return solve_steady(ThermalBalanceInputs(
        BASELINE_GEOMETRY, BASELINE_CURRENT_A, "copper",
        include_radiation=True, include_end_conduction=True,
        R_end_K_W=5.0,  # ASSUMED generic ideal-terminal conduction path
        temperature_dependent_resistance=True,
    ))


def variant_5_combined_nonlinear() -> ThermalBalanceResult:
    """Same as variant 4 -- 'combined' means all terms active together,
    which variant 4 already is; kept as a separate named entry point per
    the roadmap's explicit 5-variant list rather than silently merging it
    into variant 4, so the progression stays traceable in the results CSV."""
    return variant_4_end_conduction()


def sensitivity_sweep() -> list[ThermalBalanceResult]:
    """Sensitivity to h, emissivity, ambient temperature, and end
    temperature -- one parameter varied at a time from the variant-5 base."""
    base = ThermalBalanceInputs(
        BASELINE_GEOMETRY, BASELINE_CURRENT_A, "copper",
        include_radiation=True, include_end_conduction=True, R_end_K_W=5.0,
        temperature_dependent_resistance=True,
    )
    results = []
    for h in (5.0, 10.0, 20.0, 40.0):
        results.append(solve_steady(ThermalBalanceInputs(**{**base.__dict__, "h_W_m2K": h})))
    for eps in (0.05, 0.3, 0.78, 0.9):
        results.append(solve_steady(ThermalBalanceInputs(**{**base.__dict__, "epsilon": eps})))
    for T_inf in (263.15, 293.15, 313.15, 333.15):
        results.append(solve_steady(ThermalBalanceInputs(**{**base.__dict__, "T_inf_K": T_inf})))
    for T_end in (273.15, 293.15, 313.15):
        results.append(solve_steady(ThermalBalanceInputs(**{**base.__dict__, "T_end_K": T_end})))
    return results


if __name__ == "__main__":
    import csv
    import os

    out_dir = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "results", "processed"))
    os.makedirs(out_dir, exist_ok=True)

    variants = {
        "v1_convection_only_constant_R": variant_1_convection_only_constant_R(),
        "v2_convection_plus_radiation": variant_2_convection_plus_radiation(),
        "v3_temperature_dependent_R": variant_3_temperature_dependent_resistance(),
        "v4_end_conduction": variant_4_end_conduction(),
        "v5_combined_nonlinear": variant_5_combined_nonlinear(),
    }

    rows = []
    for tag, r in variants.items():
        assert r.normalized_residual < 1e-6, f"{tag} failed the B02 gate: residual={r.normalized_residual}"
        rows.append({
            "variant": tag, "T_s_K": r.T_s_K, "T_s_C": r.T_s_K - 273.15,
            "R_ohm": r.R_ohm, "P_joule_W": r.P_joule_W,
            "Q_conv_W": r.Q_conv_W, "Q_rad_W": r.Q_rad_W, "Q_end_W": r.Q_end_W,
            "normalized_residual": r.normalized_residual, "status": r.status,
        })
    print("All 5 B02 variants passed the <1e-6 normalized-residual gate.")

    for r in sensitivity_sweep():
        rows.append({
            "variant": f"sensitivity_h{r.inputs.h_W_m2K}_eps{r.inputs.epsilon}"
                       f"_Tinf{r.inputs.T_inf_K}_Tend{r.inputs.T_end_K}",
            "T_s_K": r.T_s_K, "T_s_C": r.T_s_K - 273.15,
            "R_ohm": r.R_ohm, "P_joule_W": r.P_joule_W,
            "Q_conv_W": r.Q_conv_W, "Q_rad_W": r.Q_rad_W, "Q_end_W": r.Q_end_W,
            "normalized_residual": r.normalized_residual, "status": r.status,
        })

    csv_path = os.path.join(out_dir, "b02_results.csv")
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {len(rows)} rows to {csv_path}")

    import plotting
    fig_dir = os.path.normpath(os.path.join(out_dir, "..", "figures"))
    currents = list(range(100, 601, 100))
    T_ss, Q_conv_l, Q_rad_l, P_l = [], [], [], []
    for I in currents:
        r = solve_steady(ThermalBalanceInputs(
            BASELINE_GEOMETRY, I, "copper", include_radiation=True,
            include_end_conduction=True, R_end_K_W=5.0, temperature_dependent_resistance=True,
        ))
        T_ss.append(r.T_s_K); Q_conv_l.append(r.Q_conv_W); Q_rad_l.append(r.Q_rad_W); P_l.append(r.P_joule_W)
    plotting.plot_b02_energy_balance(currents, T_ss, Q_conv_l, Q_rad_l, P_l, fig_dir)
    plotting.plot_b02_temperature_vs_current(currents, T_ss, fig_dir)
    print(f"wrote B02 figures to {fig_dir}")
