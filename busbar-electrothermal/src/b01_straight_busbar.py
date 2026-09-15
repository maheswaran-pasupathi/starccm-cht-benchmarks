"""
B01 -- straight uniform busbar, exact 1D electrical benchmark.

R = rho_e * L / A
V = I * R
P = I^2 * R = I * V
J = I / A
q''' = P / (L*A) = J^2 / sigma = J * E   (E = J/sigma for a uniform bar)

This module is deliberately solver-free (closed-form only) so it can act
as the ground truth that B10 (3D STAR-CCM+ electrical case) is checked
against, per the roadmap's Section 3 gate: "closed-form and Python agree
within 0.1%" is a tautology for this module itself (there is only one
closed form), but every downstream numeric comparison in this project
traces back to the functions here.
"""
from __future__ import annotations

from dataclasses import dataclass

import materials as mat


@dataclass(frozen=True)
class BusbarGeometry:
    length_m: float
    width_m: float
    thickness_m: float

    @property
    def area_m2(self) -> float:
        return self.width_m * self.thickness_m


@dataclass(frozen=True)
class ElectricalResult:
    material: str
    T_K: float
    geometry: BusbarGeometry
    current_A: float
    resistivity_ohm_m: float
    resistance_ohm: float
    voltage_drop_V: float
    power_W: float
    current_density_A_m2: float
    volumetric_heat_W_m3: float
    status: str = "SIMULATED"  # this module's own closed-form output; see docs/units_and_sign_conventions.md


def solve(geometry: BusbarGeometry, current_A: float, material: str, T_K: float) -> ElectricalResult:
    rho = mat.rho_e(material, T_K)
    R = rho * geometry.length_m / geometry.area_m2
    V = current_A * R
    P = current_A**2 * R
    J = current_A / geometry.area_m2
    q_ppp = P / (geometry.length_m * geometry.area_m2)
    return ElectricalResult(
        material=material,
        T_K=T_K,
        geometry=geometry,
        current_A=current_A,
        resistivity_ohm_m=rho,
        resistance_ohm=R,
        voltage_drop_V=V,
        power_W=P,
        current_density_A_m2=J,
        volumetric_heat_W_m3=q_ppp,
        status="SIMULATED",
    )


def self_consistency_check(result: ElectricalResult, tol_rel: float = 1e-9) -> None:
    """Internal identity checks: P == I*V and q''' == J^2/sigma == J*E.
    tol_rel is a numerical-precision tolerance (not the roadmap's 0.1%
    cross-tool gate, which applies to B10 vs B01, not B01 against itself).
    """
    P_from_IV = result.current_A * result.voltage_drop_V
    assert abs(P_from_IV - result.power_W) <= tol_rel * max(abs(result.power_W), 1e-30), (
        f"P={result.power_W} vs I*V={P_from_IV}"
    )
    sigma = 1.0 / result.resistivity_ohm_m
    q_ppp_from_J = result.current_density_A_m2**2 / sigma
    assert abs(q_ppp_from_J - result.volumetric_heat_W_m3) <= tol_rel * max(
        abs(result.volumetric_heat_W_m3), 1e-30
    ), f"q'''={result.volumetric_heat_W_m3} vs J^2/sigma={q_ppp_from_J}"


# ---------------------------------------------------------------------------
# Required sweeps (roadmap B01): current, thickness/width, Cu vs Al,
# constant vs temperature-dependent resistivity.
# ---------------------------------------------------------------------------

# Baseline geometry: ASSUMED generic busbar, not a cited product -- chosen
# to sit in the same current/cross-section regime as the personal-mentor
# hubbell_cae battery-pack busbar cases (32-40mm x 4-5mm), for narrative
# continuity across the two projects.
BASELINE_GEOMETRY = BusbarGeometry(length_m=0.300, width_m=0.040, thickness_m=0.005)
BASELINE_CURRENT_A = 400.0
BASELINE_T_K = 293.15  # 20C, room temperature, constant-property baseline


def current_sweep(geometry: BusbarGeometry, material: str, T_K: float,
                   nominal_A: float = BASELINE_CURRENT_A) -> list[ElectricalResult]:
    """At least five points including nominal, 0.5x, 1.5x, and 2x nominal."""
    factors = [0.5, 1.0, 1.5, 2.0, 2.5]
    return [solve(geometry, nominal_A * f, material, T_K) for f in factors]


def thickness_sweep(base_geometry: BusbarGeometry, current_A: float, material: str, T_K: float,
                     thickness_factors=(0.5, 0.75, 1.0, 1.5, 2.0)) -> list[ElectricalResult]:
    return [
        solve(
            BusbarGeometry(base_geometry.length_m, base_geometry.width_m, base_geometry.thickness_m * f),
            current_A, material, T_K,
        )
        for f in thickness_factors
    ]


def width_sweep(base_geometry: BusbarGeometry, current_A: float, material: str, T_K: float,
                 width_factors=(0.5, 0.75, 1.0, 1.5, 2.0)) -> list[ElectricalResult]:
    return [
        solve(
            BusbarGeometry(base_geometry.length_m, base_geometry.width_m * f, base_geometry.thickness_m),
            current_A, material, T_K,
        )
        for f in width_factors
    ]


def material_comparison(geometry: BusbarGeometry, current_A: float, T_K: float) -> dict[str, ElectricalResult]:
    return {m: solve(geometry, current_A, m, T_K) for m in ("copper", "aluminum")}


def constant_vs_temperature_dependent(geometry: BusbarGeometry, current_A: float, material: str,
                                       T_constant_K: float = BASELINE_T_K,
                                       T_operating_K: float = 353.15) -> dict[str, ElectricalResult]:
    """Compares a fixed-property result (evaluated at T_constant_K) against
    the same case evaluated at an elevated operating temperature using the
    temperature-dependent resistivity model -- NOT a coupled electro-thermal
    solve (that is B12); this only shows the property model's own effect."""
    return {
        "constant_property_at_T_constant": solve(geometry, current_A, material, T_constant_K),
        "temperature_dependent_at_T_operating": solve(geometry, current_A, material, T_operating_K),
    }


if __name__ == "__main__":
    import csv
    import os

    out_dir = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "results", "processed"))
    os.makedirs(out_dir, exist_ok=True)

    rows: list[dict] = []

    def add_rows(tag: str, results: list[ElectricalResult]) -> None:
        for r in results:
            self_consistency_check(r)
            rows.append({
                "sweep": tag,
                "material": r.material,
                "T_K": r.T_K,
                "length_m": r.geometry.length_m,
                "width_m": r.geometry.width_m,
                "thickness_m": r.geometry.thickness_m,
                "area_m2": r.geometry.area_m2,
                "current_A": r.current_A,
                "resistivity_ohm_m": r.resistivity_ohm_m,
                "resistance_ohm": r.resistance_ohm,
                "voltage_drop_V": r.voltage_drop_V,
                "power_W": r.power_W,
                "current_density_A_m2": r.current_density_A_m2,
                "volumetric_heat_W_m3": r.volumetric_heat_W_m3,
                "status": r.status,
            })

    add_rows("current_sweep_copper", current_sweep(BASELINE_GEOMETRY, "copper", BASELINE_T_K))
    add_rows("thickness_sweep_copper",
             thickness_sweep(BASELINE_GEOMETRY, BASELINE_CURRENT_A, "copper", BASELINE_T_K))
    add_rows("width_sweep_copper",
             width_sweep(BASELINE_GEOMETRY, BASELINE_CURRENT_A, "copper", BASELINE_T_K))
    mat_cmp = material_comparison(BASELINE_GEOMETRY, BASELINE_CURRENT_A, BASELINE_T_K)
    add_rows("material_comparison", list(mat_cmp.values()))
    t_cmp = constant_vs_temperature_dependent(BASELINE_GEOMETRY, BASELINE_CURRENT_A, "copper")
    add_rows("constant_vs_temperature_dependent", list(t_cmp.values()))

    csv_path = os.path.join(out_dir, "b01_results.csv")
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {len(rows)} rows to {csv_path}")

    # plots
    import plotting
    plotting.plot_b01(rows, out_dir=os.path.normpath(os.path.join(out_dir, "..", "figures")))
