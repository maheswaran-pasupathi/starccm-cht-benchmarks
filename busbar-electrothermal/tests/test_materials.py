"""B00 -- material-property module tests: loading, temperature dependence,
and the positivity gate stated in the roadmap ("plotted properties remain
positive over the study temperature range")."""
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
import materials as mat  # noqa: E402

STUDY_RANGE_K = [t + 273.15 for t in range(0, 201, 10)]  # 0-200 C, B00/B01/B02 stated validity range


@pytest.mark.parametrize("material", ["copper", "aluminum"])
def test_rho_e_positive_over_study_range(material):
    for T_K in STUDY_RANGE_K:
        assert mat.rho_e(material, T_K) > 0, f"{material} rho_e went <=0 at T={T_K}K"


@pytest.mark.parametrize("material", ["copper", "aluminum"])
def test_sigma_e_positive_over_study_range(material):
    for T_K in STUDY_RANGE_K:
        assert mat.sigma_e(material, T_K) > 0


@pytest.mark.parametrize("material", ["copper", "aluminum"])
def test_rho_e_increases_with_temperature(material):
    """Physical sanity: metallic resistivity rises with temperature."""
    r_cold = mat.rho_e(material, 273.15)
    r_hot = mat.rho_e(material, 373.15)
    assert r_hot > r_cold


def test_rho_e_matches_reference_at_reference_temperature():
    ref = mat.get_property("copper", "electrical_resistivity")
    assert mat.rho_e("copper", ref.reference_temperature_K) == pytest.approx(ref.value)


def test_sigma_e_raises_on_nonphysical_negative_resistivity():
    """Extrapolating the linear TCR model far enough gives rho_e <= 0
    (documented in docs/units_and_sign_conventions.md). sigma_e must raise,
    never silently return a negative/inf conductivity."""
    # copper alpha=0.00393/K, rho_ref>0 at Tref=293.15K -> rho_e=0 at
    # T = Tref - 1/alpha ~= -1.7K -- but the model is also unphysical well
    # before that at very high T if alpha were negative; test the actual
    # crossing found live in the personal-mentor Case 5 investigation
    # (RUN_LOG.md, 2026-09-15): sigma(T) crossed zero around T~548K for a
    # DIFFERENT (unscaled) resistivity-vs-conductivity linearization. This
    # module's rho_e(T) grows with T (never crosses zero going up), so the
    # only crossing is at very low/negative T -- assert that regime raises.
    with pytest.raises(ValueError):
        mat.sigma_e("copper", 1.0)  # 1K -- far below any calibrated range


def test_thermal_conductivity_density_specific_heat_are_positive():
    for material in ("copper", "aluminum"):
        assert mat.thermal_conductivity(material) > 0
        assert mat.density(material) > 0
        assert mat.specific_heat(material) > 0


def test_stefan_boltzmann_matches_codata():
    assert mat.stefan_boltzmann() == pytest.approx(5.670374419e-8, rel=1e-9)


def test_get_property_unknown_key_raises_with_helpful_message():
    with pytest.raises(KeyError, match="steel"):
        mat.get_property("steel", "electrical_resistivity")
