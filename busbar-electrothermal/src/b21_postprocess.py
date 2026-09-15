"""
B21 -- current-crowding geometry set, Python post-processing.

STAR-CCM+'s own report catalog (checked live while setting up B21 -- see
starccm/case_setup_checklists/b21_current_crowding_geometry_set.md) was not
found to include a direct percentile/histogram report type in this version.
So J95/J99 and the hotspot-zone metrics the roadmap's B21 section asks for
are computed here from a per-cell field export (`XyzInternalTable`, written
by each b21*.java macro to results/processed/b21{b,c,d,e}_field_export.csv),
not hardcoded inside the STAR-CCM+ macro where the exclusion-distance and
hotspot-threshold choices would be harder to audit.

LIMITATION, stated rather than silently worked around (see
docs/limitations.md): the field export is at cell CENTERS with no cell
VOLUME column, so J95/J99 and the hotspot-zone fraction here are
CELL-COUNT-weighted, not volume-integrated. The total Joule loss
(`P_JdotE_W` in each variant's own results CSV) IS a true STAR-CCM+
volume integral and is reported alongside for the actual conservation
comparison -- only the percentile/hotspot-fraction numbers carry this
cell-count caveat.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd

RESULTS_DIR = Path(__file__).resolve().parent.parent / "results" / "processed"

BASELINE_CURRENT_A = 400.0
BASELINE_W_M = 0.040
BASELINE_T_M = 0.005
BASELINE_AREA_M2 = BASELINE_W_M * BASELINE_T_M
J_NOMINAL_A_M2 = BASELINE_CURRENT_A / BASELINE_AREA_M2  # 2.0e6 A/m^2

# ASSUMED, stated here (not buried in the macro): a cell within this distance
# of a terminal face is excluded from J95/J99/hotspot metrics, because the
# idealized point/uniform electrical BC itself creates a non-physical
# entrance-effect concentration there, per the roadmap's own B21 rule
# ("define a physically meaningful sampling zone and exclusion distance from
# idealized terminal edges").
EXCLUSION_DIST_M = 0.020  # = 0.5x bar width

# ASSUMED: a cell is in the "hotspot zone" if J exceeds 1.5x the nominal
# straight-bar current density (2.0e6 A/m^2), i.e. > 3.0e6 A/m^2.
HOTSPOT_J_THRESHOLD_A_M2 = 1.5 * J_NOMINAL_A_M2

FIELD_COLS = {
    "x": "Position[X] (m)",
    "y": "Position[Y] (m)",
    "z": "Position[Z] (m)",
    "j": "Electric Current Density: Magnitude (A/m^2)",
    "t": "Temperature (K)",
}


@dataclass(frozen=True)
class VariantGeometry:
    """Terminal locations used only to build the exclusion mask -- NOT the
    solver geometry itself (that lives in the .java macros)."""

    variant_id: str
    terminal_points_m: tuple[tuple[float, float, float], ...]


VARIANT_GEOMETRY = {
    "b21b_hole": VariantGeometry("b21b_hole", ((0.0, 0.0, 0.0025), (0.300, 0.0, 0.0025))),
    "b21c_neck": VariantGeometry("b21c_neck", ((0.0, 0.0, 0.0025), (0.300, 0.0, 0.0025))),
    "b21d_sharp_corner": VariantGeometry(
        "b21d_sharp_corner", ((0.0, 0.0, 0.0025), (0.150, 0.150, 0.0025))
    ),
    "b21e_filleted_corner": VariantGeometry(
        "b21e_filleted_corner", ((0.0, 0.0, 0.0025), (0.150, 0.150, 0.0025))
    ),
}


@dataclass(frozen=True)
class CrowdingMetrics:
    variant_id: str
    n_cells_total: int
    n_cells_excluded: int
    n_cells_included: int
    Jmax_included_A_m2: float
    J95_A_m2: float
    J99_A_m2: float
    J95_over_Jnominal: float
    J99_over_Jnominal: float
    hotspot_cell_fraction: float
    hotspot_mean_J_A_m2: float
    Tmax_K: float
    Tmax_location_m: tuple[float, float, float]
    Tavg_K: float
    thermal_spreading_K: float


def exclusion_mask(df: pd.DataFrame, geom: VariantGeometry) -> np.ndarray:
    """True where a cell is close enough to ANY terminal to exclude."""
    xyz = df[[FIELD_COLS["x"], FIELD_COLS["y"], FIELD_COLS["z"]]].to_numpy()
    excluded = np.zeros(len(df), dtype=bool)
    for tx, ty, tz in geom.terminal_points_m:
        dist = np.sqrt((xyz[:, 0] - tx) ** 2 + (xyz[:, 1] - ty) ** 2 + (xyz[:, 2] - tz) ** 2)
        excluded |= dist < EXCLUSION_DIST_M
    return excluded


def compute_metrics(variant_id: str, csv_path: Path) -> CrowdingMetrics:
    df = pd.read_csv(csv_path)
    geom = VARIANT_GEOMETRY[variant_id]
    excluded = exclusion_mask(df, geom)
    included = df.loc[~excluded]
    if len(included) == 0:
        raise ValueError(f"{variant_id}: exclusion zone removed every cell -- check EXCLUSION_DIST_M")

    j = included[FIELD_COLS["j"]].to_numpy()
    j95 = float(np.percentile(j, 95))
    j99 = float(np.percentile(j, 99))
    hotspot = j > HOTSPOT_J_THRESHOLD_A_M2

    t_all = df[FIELD_COLS["t"]].to_numpy()
    tmax_idx = int(np.argmax(t_all))
    tmax_loc = (
        float(df.iloc[tmax_idx][FIELD_COLS["x"]]),
        float(df.iloc[tmax_idx][FIELD_COLS["y"]]),
        float(df.iloc[tmax_idx][FIELD_COLS["z"]]),
    )
    tmax = float(t_all[tmax_idx])
    tavg = float(np.mean(t_all))

    return CrowdingMetrics(
        variant_id=variant_id,
        n_cells_total=len(df),
        n_cells_excluded=int(excluded.sum()),
        n_cells_included=len(included),
        Jmax_included_A_m2=float(np.max(j)),
        J95_A_m2=j95,
        J99_A_m2=j99,
        J95_over_Jnominal=j95 / J_NOMINAL_A_M2,
        J99_over_Jnominal=j99 / J_NOMINAL_A_M2,
        hotspot_cell_fraction=float(np.mean(hotspot)),
        hotspot_mean_J_A_m2=float(np.mean(j[hotspot])) if hotspot.any() else 0.0,
        Tmax_K=tmax,
        Tmax_location_m=tmax_loc,
        Tavg_K=tavg,
        thermal_spreading_K=tmax - tavg,
    )


def baseline_metrics_from_b01() -> CrowdingMetrics:
    """B21-A (straight reference) has no per-cell field export -- it is
    reused directly from B12's already-passed v1_constant_properties case
    (see the B21 checklist doc), which confirmed (via B10's independent
    1e-13%-level match to B01) that J is uniform at J_nominal everywhere
    outside the terminal entrance regions. So J95=J99=Jmax=J_nominal
    analytically for this variant -- not a field-export computation, and
    documented as such rather than silently treated the same as B-E."""
    b12 = pd.read_csv(RESULTS_DIR / "b12_results.csv")
    row = b12[b12["variant"] == "v1_constant_properties"].iloc[0]
    return CrowdingMetrics(
        variant_id="b21a_straight_reference_reused_from_b12v1",
        n_cells_total=-1,
        n_cells_excluded=-1,
        n_cells_included=-1,
        Jmax_included_A_m2=J_NOMINAL_A_M2,
        J95_A_m2=J_NOMINAL_A_M2,
        J99_A_m2=J_NOMINAL_A_M2,
        J95_over_Jnominal=1.0,
        J99_over_Jnominal=1.0,
        hotspot_cell_fraction=0.0,
        hotspot_mean_J_A_m2=0.0,
        Tmax_K=float(row["Tmax_K"]),
        Tmax_location_m=(0.150, 0.0, 0.0025),
        Tavg_K=float(row["Tavg_K"]),
        thermal_spreading_K=float(row["Tmax_K"]) - float(row["Tavg_K"]),
    )


def build_summary_table(variant_csvs: dict[str, Path]) -> pd.DataFrame:
    rows = [baseline_metrics_from_b01()]
    for variant_id, path in variant_csvs.items():
        if path.exists():
            rows.append(compute_metrics(variant_id, path))
        else:
            print(f"SKIPPED (not yet run): {variant_id} -- {path} not found")
    return pd.DataFrame([r.__dict__ for r in rows])


if __name__ == "__main__":
    csvs = {
        "b21b_hole": RESULTS_DIR / "b21b_field_export.csv",
        "b21c_neck": RESULTS_DIR / "b21c_field_export.csv",
        "b21d_sharp_corner": RESULTS_DIR / "b21d_field_export.csv",
        "b21e_filleted_corner": RESULTS_DIR / "b21e_field_export.csv",
    }
    summary = build_summary_table(csvs)
    out_path = RESULTS_DIR / "b21_crowding_summary.csv"
    summary.to_csv(out_path, index=False)
    print(summary.to_string(index=False))
    print(f"\nwrote {out_path}")
