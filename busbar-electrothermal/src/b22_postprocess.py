"""
B22 -- bolted overlap joint, Python post-processing.

The real STAR-CCM+ macro (starccm/macros/b22_bolted_overlap_joint.java)
hit a real bug: its FIRST run used a stale `Boundary` reference for the
joint interface's two-sided field queries, so `V_drop_joint` and `T_jump`
came back as `NaN` in `results/processed/b22_results.csv`, even though
`I_measured`, `P_bulk`, `Tmax`, and the current/power gates were all
computed correctly (see docs/discrepancy_log.md for the full root-cause
write-up and the fix applied to the macro for future re-runs).

Rather than re-running the (many-hours) 4-variant STAR-CCM+ case, this
module recovers `V_drop_joint` ANALYTICALLY from data the buggy run DID
report correctly: the contact interface behaves as a simple resistor
under Ohm's law, so `V_drop_joint = I_measured * R_contact / contact_area`
is exact given the prescribed (not measured) contact resistance -- this
was independently confirmed against a live STAR-CCM+ AreaAverageReport
in a short diagnostic run (0.008 V predicted and measured, exactly, for
the nominal case).

`T_jump` is NOT recovered this way (it is not simply analytical) and is
reported as `NOT_MEASURED_THIS_INCREMENT` -- consistent with thermal
contact resistance itself being deferred this increment (see
data/source_traceability.csv, row `b22_thermal_contact_resistance`).
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import pandas as pd

RESULTS_DIR = Path(__file__).resolve().parent.parent / "results" / "processed"
CONTACT_AREA_M2 = 8.0e-4  # W * OVERLAP = 0.040 * 0.020, confirmed live -- see checklist doc


@dataclass(frozen=True)
class JointMetrics:
    variant: str
    contact_R_ohm_m2: float
    I_measured_A: float
    V_drop_joint_analytical_V: float
    V_drop_total_V: float
    R_total_ohm: float
    P_bulk_W: float
    P_interface_W: float
    interface_loss_fraction_pct: float
    Tmax_overall_K: float
    T_jump_K: str  # "NOT_MEASURED_THIS_INCREMENT" -- see module docstring
    gate_status: str


def compute_joint_metrics(results_csv: Path) -> pd.DataFrame:
    df = pd.read_csv(results_csv)
    rows = []
    for _, row in df.iterrows():
        R = float(row["contact_R_ohm_m2"])
        I = float(row["I_measured_A"])
        v_analytical = I * R / CONTACT_AREA_M2
        rows.append(JointMetrics(
            variant=row["variant"],
            contact_R_ohm_m2=R,
            I_measured_A=I,
            V_drop_joint_analytical_V=v_analytical,
            V_drop_total_V=float(row["V_drop_total_V"]),
            R_total_ohm=float(row["R_total_ohm"]),
            P_bulk_W=float(row["P_bulk_W"]),
            P_interface_W=float(row["P_interface_analytical_W"]),
            interface_loss_fraction_pct=float(row["interface_loss_fraction_pct"]),
            Tmax_overall_K=float(row["Tmax_overall_K"]),
            T_jump_K="NOT_MEASURED_THIS_INCREMENT",
            gate_status=row["gate_status"],
        ))
    return pd.DataFrame([r.__dict__ for r in rows])


if __name__ == "__main__":
    summary = compute_joint_metrics(RESULTS_DIR / "b22_results.csv")
    out_path = RESULTS_DIR / "b22_joint_summary.csv"
    summary.to_csv(out_path, index=False)
    print(summary.to_string(index=False))
    print(f"\nwrote {out_path}")
