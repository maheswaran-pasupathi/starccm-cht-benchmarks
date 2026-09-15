# Assumptions

The authoritative, machine-readable record of every input's status is
`data/source_traceability.csv` -- this file gives the narrative rationale
for each `ASSUMED` entry there. If the two ever disagree, the CSV is the
source of truth and this file should be corrected to match it.

## B00-B03 (current scope)

- **Baseline geometry (300 x 40 x 5 mm bar):** not a cited product.
  Chosen to sit in the same cross-section/current regime as the
  personal-mentor `hubbell_cae` battery-pack busbar cases, purely for
  narrative continuity between the two projects -- it carries no
  independent engineering authority of its own.
- **Baseline current (400 A):** representative EV/ESS module-interconnect
  continuous current per general industry guidance (GRL Copper's busbar
  design guide), not a certified or product-specific rating.
- **Natural-convection coefficient h = 10 W/m^2K:** a generic
  order-of-magnitude placeholder for a vertical/horizontal plate in still
  air. The textbook range is roughly 2-25 W/m^2K depending on geometry,
  orientation, and temperature difference -- this is why B02's
  `sensitivity_sweep()` varies it across 5/10/20/40 W/m^2K rather than
  reporting a single value as if it were validated.
- **Emissivity = 0.78 (oxidized copper):** representative, not measured.
  Bright/polished copper can be as low as 0.02-0.15, over 10x lower. Any
  claim depending on the absolute radiative heat rejected must state which
  emissivity was used and ideally show the sensitivity range, per the same
  logic as `h`.
- **End-conduction thermal resistance = 5 K/W:** a placeholder
  representing a moderately conductive ideal terminal, not derived from
  any specific terminal geometry. Intended to be replaced once B22's
  overlap-joint geometry is defined and an actual conduction path can be
  computed.

## Not yet applicable (B10+)

Geometry, mesh, contact-resistance, and CHT-domain assumptions for B10
onward will be added here once those cases are started -- none exist yet
because none of those cases have been run (see `docs/validation_matrix.md`).
