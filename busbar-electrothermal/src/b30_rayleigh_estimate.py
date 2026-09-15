"""
B30 -- Rayleigh-number scoping estimate, done BEFORE building the STAR-CCM+
natural-convection case, per the roadmap's own B30 checklist item
("laminar/turbulent choice justified with Rayleigh/Reynolds estimates").

The bar is assumed to lie horizontal with its wide (0.040m) face up/down
(the top/bottom faces are the dominant natural-convection surfaces; the
two thin 0.005m edge faces contribute comparatively little). The
characteristic length for a horizontal plate is the standard
`L_c = A_s / P` (surface area / perimeter), per Incropera & Bergman
Ch. 9 (horizontal-plate natural convection correlations), not the plate's
long dimension directly.
"""
from __future__ import annotations

from dataclasses import dataclass

import materials as mat

G = 9.81  # m/s^2

# Baseline geometry -- MUST match B01/B10-B22's own baseline exactly.
L_M, W_M, T_M = 0.300, 0.040, 0.005

# Representative delta-T: B12 v1's own converged Tmax rise (~34.8C at
# 400A with the fixed h=10 approximation) -- used only to SCOPE the flow
# regime before building the real buoyancy-driven case, not as an input
# to that case itself (the real case solves its own temperature field).
DELTA_T_K = 15.0  # ASSUMED representative order-of-magnitude rise, see note above


@dataclass(frozen=True)
class RayleighEstimate:
    L_c_m: float
    Ra: float
    Pr: float
    regime: str


def horizontal_plate_characteristic_length(length_m: float, width_m: float) -> float:
    area = length_m * width_m
    perimeter = 2.0 * (length_m + width_m)
    return area / perimeter


def rayleigh_number(L_c_m: float, delta_T_K: float) -> float:
    beta = mat.get_property("air", "thermal_expansion_coefficient").value
    nu = mat.get_property("air", "kinematic_viscosity").value
    alpha_th = mat.get_property("air", "thermal_diffusivity").value
    return G * beta * delta_T_K * L_c_m**3 / (nu * alpha_th)


def prandtl_number() -> float:
    nu = mat.get_property("air", "kinematic_viscosity").value
    alpha_th = mat.get_property("air", "thermal_diffusivity").value
    return nu / alpha_th


def classify_regime(Ra: float) -> str:
    # Horizontal-plate natural convection transitions to turbulent around
    # Ra ~ 1e7-1e8 (Incropera & Bergman Ch. 9); vertical-plate transition
    # is higher still (~1e9). Using the more conservative (lower)
    # horizontal-plate threshold since that is this geometry's dominant
    # heat-transfer surface.
    if Ra < 1e7:
        return "LAMINAR"
    return "TURBULENT (needs justification for a laminar model)"


def estimate() -> RayleighEstimate:
    L_c = horizontal_plate_characteristic_length(L_M, W_M)
    Ra = rayleigh_number(L_c, DELTA_T_K)
    Pr = prandtl_number()
    return RayleighEstimate(L_c_m=L_c, Ra=Ra, Pr=Pr, regime=classify_regime(Ra))


if __name__ == "__main__":
    r = estimate()
    print(f"Characteristic length L_c = {r.L_c_m:.5f} m  (horizontal-plate A_s/P)")
    print(f"Rayleigh number Ra = {r.Ra:.3e}  (at assumed delta-T = {DELTA_T_K} K)")
    print(f"Prandtl number Pr = {r.Pr:.4f}")
    print(f"Regime: {r.regime}")
    print(f"Transition threshold (horizontal plate): ~1e7-1e8 (Incropera & Bergman Ch. 9)")
