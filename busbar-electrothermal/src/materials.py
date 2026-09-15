"""
B00 -- material properties and unit-safe conversions.

All functions take and return SI base units (metres, ohm.metres,
siemens/metre, kelvin, watts, etc.) unless a function name explicitly
says otherwise (e.g. ``mm2_to_m2``). This module has no STAR-CCM+
dependency and is pure Python + stdlib csv, so it can be unit-tested
without any solver license.

Data provenance: every numeric constant loaded from
``data/materials.csv`` carries a ``source`` and ``source_type`` field.
See that file and ``docs/units_and_sign_conventions.md`` for details.
Do not hardcode a material constant anywhere else in this repository --
always load it from here so there is exactly one place to audit.
"""
from __future__ import annotations

import csv
import os
from dataclasses import dataclass

_THIS_DIR = os.path.dirname(os.path.abspath(__file__))
_MATERIALS_CSV = os.path.normpath(os.path.join(_THIS_DIR, "..", "data", "materials.csv"))


@dataclass(frozen=True)
class MaterialProperty:
    material: str
    property: str
    symbol: str
    value: float
    unit: str
    reference_temperature_K: float | None
    source: str
    source_type: str
    notes: str


def load_materials_csv(path: str = _MATERIALS_CSV) -> dict[tuple[str, str], MaterialProperty]:
    """Loads data/materials.csv into a dict keyed by (material, property)."""
    out: dict[tuple[str, str], MaterialProperty] = {}
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ref_t = row["reference_temperature_K"]
            out[(row["material"], row["property"])] = MaterialProperty(
                material=row["material"],
                property=row["property"],
                symbol=row["symbol"],
                value=float(row["value"]),
                unit=row["unit"],
                reference_temperature_K=float(ref_t) if ref_t else None,
                source=row["source"],
                source_type=row["source_type"],
                notes=row["notes"],
            )
    return out


_TABLE = load_materials_csv()


def get_property(material: str, property_name: str) -> MaterialProperty:
    key = (material, property_name)
    if key not in _TABLE:
        raise KeyError(
            f"No material property '{property_name}' for '{material}' in "
            f"{_MATERIALS_CSV}. Available: "
            f"{sorted(p for m, p in _TABLE if m == material)}"
        )
    return _TABLE[key]


# ---------------------------------------------------------------------------
# Unit conversions (B00 deliverable: unit tests cover every one of these)
# ---------------------------------------------------------------------------

def mm2_to_m2(area_mm2: float) -> float:
    return area_mm2 * 1e-6


def m2_to_mm2(area_m2: float) -> float:
    return area_m2 * 1e6


def uohm_to_ohm(r_uohm: float) -> float:
    return r_uohm * 1e-6


def ohm_to_uohm(r_ohm: float) -> float:
    return r_ohm * 1e6


def uohm_cm2_to_ohm_m2(value_uohm_cm2: float) -> float:
    """Converts an area-normalized contact resistivity: uOhm.cm^2 -> Ohm.m^2.

    1 uOhm = 1e-6 Ohm; 1 cm^2 = 1e-4 m^2.
    So 1 uOhm.cm^2 = 1e-6 Ohm * 1e-4 m^2 = 1e-10 Ohm.m^2.
    """
    return value_uohm_cm2 * 1e-10


def ohm_m2_to_uohm_cm2(value_ohm_m2: float) -> float:
    return value_ohm_m2 / 1e-10


# ---------------------------------------------------------------------------
# Temperature-dependent electrical resistivity / conductivity
# ---------------------------------------------------------------------------
# Sign convention (documented in docs/units_and_sign_conventions.md):
#   rho_e(T) = rho_e,ref * (1 + alpha * (T - T_ref))
#   T and T_ref in KELVIN throughout this module. alpha is the linear
#   temperature coefficient of resistance referenced AT T_ref (not at 0C,
#   which is a common alternate convention -- see docs).

def rho_e(material: str, T_K: float) -> float:
    """Electrical resistivity [ohm.m] at temperature T_K [K]."""
    rho_ref = get_property(material, "electrical_resistivity")
    alpha = get_property(material, "temperature_coefficient_of_resistance")
    T_ref = rho_ref.reference_temperature_K
    return rho_ref.value * (1.0 + alpha.value * (T_K - T_ref))


def sigma_e(material: str, T_K: float) -> float:
    """Electrical conductivity [S/m] at temperature T_K [K]. sigma = 1/rho_e."""
    r = rho_e(material, T_K)
    if r <= 0:
        raise ValueError(
            f"rho_e({material}, T={T_K}K) = {r} <= 0 -- linear TCR model has "
            "broken down (extrapolated too far). Clamp the valid range before "
            "using this material model at this temperature."
        )
    return 1.0 / r


def thermal_conductivity(material: str, T_K: float | None = None) -> float:
    """Thermal conductivity [W/(m.K)]. Constant model in B00/B01/B02 baseline."""
    del T_K  # constant-property model; kept for a future T-dependent variant
    return get_property(material, "thermal_conductivity").value


def density(material: str) -> float:
    return get_property(material, "density").value


def specific_heat(material: str) -> float:
    return get_property(material, "specific_heat").value


def stefan_boltzmann() -> float:
    return get_property("constants", "stefan_boltzmann").value
