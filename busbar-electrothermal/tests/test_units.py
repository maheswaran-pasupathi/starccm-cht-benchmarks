"""B00 -- unit-conversion tests. Every conversion helper in src/materials.py
must be covered here, per the roadmap's B00 deliverable list
(mm^2<->m^2, uOhm<->Ohm, uOhm.cm^2<->Ohm.m^2)."""
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
import materials as mat  # noqa: E402


def test_mm2_to_m2():
    assert mat.mm2_to_m2(1.0) == 1e-6
    assert mat.mm2_to_m2(128.0) == pytest.approx(128e-6)


def test_m2_to_mm2_roundtrip():
    for area_mm2 in (0.1, 1.0, 128.0, 5000.0):
        assert mat.m2_to_mm2(mat.mm2_to_m2(area_mm2)) == pytest.approx(area_mm2)


def test_uohm_to_ohm():
    assert mat.uohm_to_ohm(1.0) == 1e-6
    assert mat.uohm_to_ohm(40.0) == pytest.approx(4e-5)


def test_ohm_to_uohm_roundtrip():
    for r_uohm in (1.0, 40.0, 2.6e4):
        assert mat.ohm_to_uohm(mat.uohm_to_ohm(r_uohm)) == pytest.approx(r_uohm)


def test_uohm_cm2_to_ohm_m2():
    # 1 uOhm.cm^2 = 1e-6 Ohm * 1e-4 m^2 = 1e-10 Ohm.m^2 -- derived directly
    # from the two unit conversions, not copied from any external source.
    assert mat.uohm_cm2_to_ohm_m2(1.0) == pytest.approx(1e-10)
    # The Siemens KB000126485 demo case's own contact-resistance value,
    # reused (and found WRONG when applied to a different contact area) in
    # personal-mentor hubbell_cae Case 6: 2.6E-8 ohm.m^2 == 2.6e2 uOhm.cm^2.
    assert mat.uohm_cm2_to_ohm_m2(2.6e2) == pytest.approx(2.6e-8, rel=1e-9)


def test_ohm_m2_to_uohm_cm2_roundtrip():
    for v in (1e-10, 2.6e-8, 5.12e-9):
        assert mat.ohm_m2_to_uohm_cm2(mat.uohm_cm2_to_ohm_m2(v)) == pytest.approx(v)


def test_area_normalized_contact_resistance_worked_example():
    """Reproduces, as a regression test, the exact real mistake found live
    in personal-mentor's hubbell_cae Case 6 (RUN_LOG.md, 2026-09-15): an
    area-normalized contact resistivity calibrated for one contact area
    gives a wildly different actual resistance on a smaller contact area,
    and that must be computed explicitly, never assumed portable."""
    contact_area_m2 = 0.032 * 0.004  # the undersized 32x4mm terminal, m^2
    r_area_ohm_m2 = 2.6e-8  # the KB value, reused (wrongly) unscaled
    r_actual_ohm = r_area_ohm_m2 / contact_area_m2
    assert r_actual_ohm == pytest.approx(2.03125e-4)  # ~203 uOhm -- too high
    current_A = 400.0
    power_w_both_terminals = 2 * current_A**2 * r_actual_ohm
    assert power_w_both_terminals == pytest.approx(65.0, rel=1e-6)  # matches RUN_LOG's finding
