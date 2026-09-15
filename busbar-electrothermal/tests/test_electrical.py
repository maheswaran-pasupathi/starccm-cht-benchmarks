"""B01 -- straight busbar 1D electrical benchmark tests.

Gate (roadmap Section 3): "closed-form and Python agree within 0.1%".
Since b01_straight_busbar.py IS the closed form, this suite instead checks
(a) the closed-form identities hold to numerical precision, and
(b) the expected physical TRENDS the roadmap states explicitly:
    - power follows I^2 when resistance is fixed;
    - resistance varies as 1/A;
    - uniform geometry produces uniform current density (J = I/A exactly,
      by construction, away from any terminal effect this 1D model doesn't
      have anyway).
"""
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
import b01_straight_busbar as b01  # noqa: E402


GEOM = b01.BusbarGeometry(length_m=0.300, width_m=0.040, thickness_m=0.005)
T_K = 293.15


def test_self_consistency_all_baseline_sweeps():
    for result in (
        *b01.current_sweep(GEOM, "copper", T_K),
        *b01.thickness_sweep(GEOM, 400.0, "copper", T_K),
        *b01.width_sweep(GEOM, 400.0, "copper", T_K),
        *b01.material_comparison(GEOM, 400.0, T_K).values(),
    ):
        b01.self_consistency_check(result)  # raises AssertionError on failure


def test_power_scales_as_current_squared_at_fixed_resistance():
    results = b01.current_sweep(GEOM, "copper", T_K)
    R = results[0].resistance_ohm
    for r in results:
        assert r.resistance_ohm == pytest.approx(R, rel=1e-12)  # fixed geometry/T -> fixed R
        expected_P = r.current_A**2 * R
        assert r.power_W == pytest.approx(expected_P, rel=1e-9)


def test_resistance_scales_as_one_over_area():
    results = b01.thickness_sweep(GEOM, 400.0, "copper", T_K)
    rho_L = results[0].resistivity_ohm_m * GEOM.length_m
    for r in results:
        expected_R = rho_L / r.geometry.area_m2
        assert r.resistance_ohm == pytest.approx(expected_R, rel=1e-9)


def test_current_density_equals_I_over_A():
    for r in b01.width_sweep(GEOM, 400.0, "copper", T_K):
        assert r.current_density_A_m2 == pytest.approx(r.current_A / r.geometry.area_m2, rel=1e-12)


def test_aluminum_has_higher_resistance_than_copper_same_geometry():
    cmp_ = b01.material_comparison(GEOM, 400.0, T_K)
    assert cmp_["aluminum"].resistance_ohm > cmp_["copper"].resistance_ohm


def test_temperature_dependent_resistivity_raises_power_at_elevated_temperature():
    """Higher T -> higher rho_e -> higher R,V,P at FIXED prescribed current.
    (Not the coupled feedback itself -- that is B12 -- just the property
    model's direct effect, isolated.)"""
    cmp_ = b01.constant_vs_temperature_dependent(GEOM, 400.0, "copper")
    cold = cmp_["constant_property_at_T_constant"]
    hot = cmp_["temperature_dependent_at_T_operating"]
    assert hot.resistivity_ohm_m > cold.resistivity_ohm_m
    assert hot.power_W > cold.power_W


def test_baseline_matches_hand_calculation():
    """A fully independent hand calculation for one specific point, so this
    suite does not only check the module against itself."""
    # rho_e(Cu, 293.15K) = 1.68e-8 ohm.m (reference value, no T correction
    # needed since T == T_ref exactly).
    L, w, t, I = 0.300, 0.040, 0.005, 400.0
    A = w * t  # 2.0e-4 m^2
    R_expected = 1.68e-8 * L / A  # = 2.52e-5 ohm
    V_expected = I * R_expected  # = 0.01008 V
    P_expected = I**2 * R_expected  # = 4.032 W
    result = b01.solve(b01.BusbarGeometry(L, w, t), I, "copper", 293.15)
    assert result.resistance_ohm == pytest.approx(R_expected, rel=1e-9)
    assert result.voltage_drop_V == pytest.approx(V_expected, rel=1e-9)
    assert result.power_W == pytest.approx(P_expected, rel=1e-9)
