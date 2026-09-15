# STAR-CCM+ field functions used across B10+

Built-in field functions (present automatically once the relevant model is
enabled — confirmed live by inspecting a real solved case in the
personal-mentor `hubbell_cae` project, RUN_LOG.md 2026-09-15):

| Field function | Enabled by | Type | Notes |
|---|---|---|---|
| `Electric Potential` | `ElectrodynamicsPotentialModel` | scalar | volts |
| `Electric Field` | `ElectrodynamicsPotentialModel` | vector | `E = -grad(phi)` |
| `Electric Current Density` | `ElectrodynamicsPotentialModel` | vector | `J = sigma * E`; use `.getMagnitudeFunction()` for `\|J\|` |
| `Electrical Conductivity` | `ElectrodynamicsPotentialModel` | scalar | S/m |
| `Specific Ohmic Heat Source` | `OhmicHeatingModel` (B12+ only) | scalar | `q''' = J.E`; NOT available in B10 (no thermal model enabled) |
| `UserSpecifiedEnergySource` | `EnergyUserVolumeSourceOption.VOLUMETRIC_HEAT_SOURCE` (B11) | scalar | The prescribed volumetric heat source itself, exposed as a field function for `VolumeIntegralReport` verification -- NOT named `VolumetricHeatSource` despite the condition/profile classes using that name (found live, B11, 2026-09-15) |
| `BoundaryHeatFlux` | `SegregatedSolidEnergyModel` | scalar | Wall-normal heat flux at a boundary; use with `SurfaceIntegralReport` for a heat-balance check. NOT named `WallHeatFlux` (found live, B11) |

## Custom field functions this program defines

### `JdotE` (B10+)

Volumetric Joule dissipation density, computed directly as the dot product
of the built-in current-density and electric-field vectors — needed in
B10 specifically because `Specific Ohmic Heat Source` does not exist
without `OhmicHeatingModel` enabled, and B10 is deliberately electrical-only.

- **Definition (STAR-CCM+ user field function expression):**
  `$$ElectricCurrentDensity[0]*$$ElectricField[0] + $$ElectricCurrentDensity[1]*$$ElectricField[1] + $$ElectricCurrentDensity[2]*$$ElectricField[2]`
- **Dimensions:** W/m^3 (A/m^2 * V/m)
- **Used by:** `VolumeIntegralReport` over the solid region, compared
  against B01's `power_W` (should match `I*V` to within the B10 gate).

## Derived parts

### `MidBarSection` (B10+)

A `PlaneSection` derived part cutting the bar at `x = L/2` (the geometric
midpoint, far from both terminal entrance-effect zones for a 300mm-long
bar with terminals refined over roughly the first/last 20-30mm). Used for
the "section-average current density away from terminals" gate check —
sampling at the terminal faces themselves would conflate genuine bulk
behavior with terminal-corner singularities, which the roadmap explicitly
warns against ("never use a single-node or mathematically singular
maximum as the only metric").
