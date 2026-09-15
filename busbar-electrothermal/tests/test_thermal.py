"""B02 -- steady nonlinear thermal balance tests.

Gate (roadmap): normalized energy residual below 1e-6 for the numerical
root solution. Every variant test asserts this explicitly, not just that
the solver returned a number.
"""
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
import b02_steady_thermal as b02  # noqa: E402

GATE = 1e-6


def test_variant_1_convection_only_meets_gate_and_matches_linear_formula():
    r = b02.variant_1_convection_only_constant_R()
    assert r.normalized_residual < GATE
    # Linear check: T_s = T_inf + P/(h*A_ext) for convection-only, constant R.
    expected_T = r.inputs.T_inf_K + r.P_joule_W / (r.inputs.h_W_m2K * r.inputs.A_ext_m2)
    assert r.T_s_K == pytest.approx(expected_T, rel=1e-6)


def test_variant_2_radiation_reduces_steady_temperature():
    """Adding a heat-rejection path (radiation) at the same P must lower
    the steady temperature relative to convection-only."""
    v1 = b02.variant_1_convection_only_constant_R()
    v2 = b02.variant_2_convection_plus_radiation()
    assert v2.normalized_residual < GATE
    assert v2.T_s_K < v1.T_s_K
    assert v2.Q_rad_W > 0.0


def test_variant_3_temperature_dependent_resistance_increases_power_and_temperature():
    v2 = b02.variant_2_convection_plus_radiation()
    v3 = b02.variant_3_temperature_dependent_resistance()
    assert v3.normalized_residual < GATE
    assert v3.R_ohm > v2.R_ohm  # hotter -> higher resistivity -> higher R
    assert v3.P_joule_W > v2.P_joule_W
    assert v3.T_s_K > v2.T_s_K


def test_variant_4_end_conduction_provides_additional_cooling():
    v3 = b02.variant_3_temperature_dependent_resistance()
    v4 = b02.variant_4_end_conduction()
    assert v4.normalized_residual < GATE
    assert v4.Q_end_W > 0.0
    assert v4.T_s_K < v3.T_s_K  # extra heat-rejection path -> lower steady T


def test_variant_5_combined_is_identical_to_variant_4():
    v4 = b02.variant_4_end_conduction()
    v5 = b02.variant_5_combined_nonlinear()
    assert v5.T_s_K == v4.T_s_K
    assert v5.normalized_residual < GATE


def test_all_sensitivity_points_meet_the_residual_gate():
    for r in b02.sensitivity_sweep():
        assert r.normalized_residual < GATE, (
            f"sensitivity point h={r.inputs.h_W_m2K} eps={r.inputs.epsilon} "
            f"Tinf={r.inputs.T_inf_K} Tend={r.inputs.T_end_K} failed the gate: "
            f"residual={r.normalized_residual}"
        )


def test_higher_convection_coefficient_lowers_steady_temperature():
    results = [r for r in b02.sensitivity_sweep() if r.inputs.epsilon == 0.78
               and r.inputs.T_inf_K == 293.15 and r.inputs.T_end_K == 293.15]
    results.sort(key=lambda r: r.inputs.h_W_m2K)
    temps = [r.T_s_K for r in results]
    assert temps == sorted(temps, reverse=True)  # monotonically decreasing with h


def test_bracket_error_is_explicit_not_silent():
    """A genuinely non-convergent (thermal-runaway-scale) case must raise a
    clear error, never return a spurious/clamped number."""
    huge_current_inputs = b02.ThermalBalanceInputs(
        b02.BASELINE_GEOMETRY, current_A=1.0e6, material="copper",
        include_radiation=True, temperature_dependent_resistance=True,
    )
    with pytest.raises(ValueError, match="No sign change"):
        b02.solve_steady(huge_current_inputs, T_bracket_K=(200.0, 1200.0))
