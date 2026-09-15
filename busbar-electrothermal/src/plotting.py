"""Shared plotting helpers for B01/B02/B03. Matplotlib only, no seaborn
dependency, saved as PNG under results/figures/. Every plot is generated
from a SIMULATED (i.e. our own closed-form/numerical) results list -- see
docs/units_and_sign_conventions.md for the status-label convention."""
from __future__ import annotations

import os

import matplotlib
matplotlib.use("Agg")  # headless: no display available in this environment
import matplotlib.pyplot as plt


def _save(fig, out_dir: str, name: str) -> str:
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, name)
    fig.savefig(path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    return path


def plot_b01(rows: list[dict], out_dir: str) -> list[str]:
    written = []

    # R(A) from the thickness+width sweeps (vary area, fixed L, material, T)
    fig, ax = plt.subplots()
    for tag, marker in (("thickness_sweep_copper", "o"), ("width_sweep_copper", "s")):
        pts = [r for r in rows if r["sweep"] == tag]
        pts.sort(key=lambda r: r["area_m2"])
        ax.plot([r["area_m2"] * 1e6 for r in pts], [r["resistance_ohm"] * 1e6 for r in pts],
                marker=marker, label=tag)
    ax.set_xlabel("Cross-section area [mm^2]")
    ax.set_ylabel("Resistance [uOhm]")
    ax.set_title("B01: R vs A (expect R ~ 1/A)")
    ax.legend()
    ax.grid(True, alpha=0.3)
    written.append(_save(fig, out_dir, "b01_R_vs_A.png"))

    # P(I) from the current sweep
    fig, ax = plt.subplots()
    pts = [r for r in rows if r["sweep"] == "current_sweep_copper"]
    pts.sort(key=lambda r: r["current_A"])
    ax.plot([r["current_A"] for r in pts], [r["power_W"] for r in pts], marker="o")
    ax.set_xlabel("Current [A]")
    ax.set_ylabel("Power [W]")
    ax.set_title("B01: P vs I (expect P ~ I^2 at fixed R)")
    ax.grid(True, alpha=0.3)
    written.append(_save(fig, out_dir, "b01_P_vs_I.png"))

    # J(A) from thickness sweep
    fig, ax = plt.subplots()
    pts = [r for r in rows if r["sweep"] == "thickness_sweep_copper"]
    pts.sort(key=lambda r: r["area_m2"])
    ax.plot([r["area_m2"] * 1e6 for r in pts], [r["current_density_A_m2"] / 1e6 for r in pts], marker="o")
    ax.set_xlabel("Cross-section area [mm^2]")
    ax.set_ylabel("Current density [A/mm^2]")
    ax.set_title("B01: J vs A (expect J ~ 1/A)")
    ax.grid(True, alpha=0.3)
    written.append(_save(fig, out_dir, "b01_J_vs_A.png"))

    return written


def plot_b02_energy_balance(current_A_list, T_ss_list, Q_conv_list, Q_rad_list,
                             P_joule_list, out_dir: str) -> str:
    fig, ax = plt.subplots()
    ax.plot(current_A_list, P_joule_list, marker="o", label="P_Joule generated")
    ax.plot(current_A_list, Q_conv_list, marker="s", label="Q_conv rejected")
    ax.plot(current_A_list, Q_rad_list, marker="^", label="Q_rad rejected")
    ax.set_xlabel("Current [A]")
    ax.set_ylabel("Power [W]")
    ax.set_title("B02: steady energy balance vs current")
    ax.legend()
    ax.grid(True, alpha=0.3)
    return _save(fig, out_dir, "b02_energy_balance.png")


def plot_b02_temperature_vs_current(current_A_list, T_ss_list, out_dir: str) -> str:
    fig, ax = plt.subplots()
    ax.plot(current_A_list, [t - 273.15 for t in T_ss_list], marker="o")
    ax.set_xlabel("Current [A]")
    ax.set_ylabel("Steady-state temperature [C]")
    ax.set_title("B02: steady-state temperature vs current")
    ax.grid(True, alpha=0.3)
    return _save(fig, out_dir, "b02_Tss_vs_I.png")


def plot_b03_step_response(t_s_list, T_numeric_K_list, T_analytical_K_list, out_dir: str) -> str:
    fig, ax = plt.subplots()
    ax.plot(t_s_list, [t - 273.15 for t in T_analytical_K_list], "-", label="analytical", linewidth=2)
    ax.plot(t_s_list, [t - 273.15 for t in T_numeric_K_list], "--", label="numerical", linewidth=1)
    ax.set_xlabel("Time [s]")
    ax.set_ylabel("Temperature [C]")
    ax.set_title("B03: one-node transient step response, numerical vs analytical")
    ax.legend()
    ax.grid(True, alpha=0.3)
    return _save(fig, out_dir, "b03_step_response.png")
