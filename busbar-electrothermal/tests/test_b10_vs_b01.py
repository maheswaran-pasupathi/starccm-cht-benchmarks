"""B10 -- checks the STAR-CCM+ 3D electrical result (results/processed/
b10_results.csv, written by starccm/macros/b10_straight_bar_electrical.java)
against B01's OWN live-computed closed-form values (not a hardcoded copy),
so this test would fail if b01_straight_busbar.py's constants ever drift
from what the STAR-CCM+ macro assumes.

Skips (does not fail) if b10_results.csv does not exist yet -- this test
is meaningless before B10 has actually been run, and the roadmap's own
rule is that a PENDING result must never be treated as a failure or a
silent pass; skipping with a clear reason is the correct behavior."""
import csv
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
import b01_straight_busbar as b01  # noqa: E402

RESULTS_CSV = os.path.normpath(os.path.join(
    os.path.dirname(__file__), "..", "results", "processed", "b10_results.csv"
))


def _load_b10_row():
    if not os.path.exists(RESULTS_CSV):
        pytest.skip(f"{RESULTS_CSV} does not exist -- B10 has not been run yet (PENDING, not a failure)")
    with open(RESULTS_CSV, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    assert len(rows) == 1, f"expected exactly one row in {RESULTS_CSV}, got {len(rows)}"
    return {k: float(v) if k != "case_id" and k != "gate_status" else v for k, v in rows[0].items()}


def _b01_reference(current_A: float):
    geom = b01.BASELINE_GEOMETRY
    assert geom.length_m == pytest.approx(0.300)
    assert geom.width_m == pytest.approx(0.040)
    assert geom.thickness_m == pytest.approx(0.005)
    return b01.solve(geom, current_A, "copper", 293.15)


def test_b10_current_balance_gate():
    row = _load_b10_row()
    assert row["current_imbalance_pct"] < 0.5, (
        f"B10 current balance gate FAILED: {row['current_imbalance_pct']}% >= 0.5%"
    )


def test_b10_voltage_and_power_vs_b01_gate():
    row = _load_b10_row()
    ref = _b01_reference(row["current_A"])
    v_diff_pct = 100.0 * abs(row["V_drop_V"] - ref.voltage_drop_V) / ref.voltage_drop_V
    p_diff_pct = 100.0 * abs(row["P_JdotE_W"] - ref.power_W) / ref.power_W
    assert v_diff_pct < 1.0, f"B10 voltage-drop-vs-B01 gate FAILED: {v_diff_pct}% >= 1%"
    assert p_diff_pct < 1.0, f"B10 power-vs-B01 gate FAILED: {p_diff_pct}% >= 1%"


def test_b10_section_average_current_density_gate():
    row = _load_b10_row()
    ref = _b01_reference(row["current_A"])
    j_diff_pct = 100.0 * abs(row["J_section_avg_A_m2"] - ref.current_density_A_m2) / ref.current_density_A_m2
    assert j_diff_pct < 1.0, f"B10 J-section-vs-nominal gate FAILED: {j_diff_pct}% >= 1%"


def test_b10_overall_gate_status_column_says_passed():
    """The macro's own printed gate_status column must agree with this
    test's independent recomputation -- if they disagree, something in
    the macro's inline gate logic has drifted from this test."""
    row = _load_b10_row()
    assert row["gate_status"] == "PASSED"
