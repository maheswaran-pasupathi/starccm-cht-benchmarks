# Discrepancy log

Records every case where a result did not match its comparison target
(analytical, published reference, or a prior case), what the discrepancy
was, and how it was resolved or left open. Per the roadmap: never tune an
undocumented input silently -- every resolution here must be traceable to
a specific, stated cause.

No entries yet for THIS repository's own B00-B03 runs (all gates passed
on first implementation, see `docs/validation_matrix.md`).

## Carried-forward lesson from a related project (not this repo's own run)

**2026-09-15 -- personal-mentor `hubbell_cae` Case 6: area-normalized
contact resistivity reused across different contact areas.**

- **Discrepancy:** reusing the Siemens KB000126485 demo case's own
  bolted-joint contact-resistance value (2.6E-8 ohm.m^2) verbatim on a
  battery-pack-scale busbar terminal (128 mm^2, much smaller than the KB
  case's own terminal) produced a converged-but-nonphysical 474 K
  temperature rise.
- **Root cause:** `ElectricalResistanceAreaProfile` is area-normalized
  (ohm.m^2); actual resistance = value / contact_area. The KB value was
  calibrated for a different (larger) contact area and is not portable
  without rescaling.
- **Resolution:** derived the area-resistivity from a representative real
  bolted-copper-joint contact RESISTANCE (40 uOhm, ASSUMED generic) scaled
  to the actual contact area, giving a physically plausible 128 K rise.
- **Why it's carried into this repository:** `tests/test_units.py::
  test_area_normalized_contact_resistance_worked_example` reproduces this
  exact calculation as a regression test, so THIS program's B22 (bolted
  overlap joint) cannot repeat the same class of error silently.
- This is not a discrepancy in this repository's own results -- it is
  recorded here because the roadmap instructs preserving lessons that
  generalize, and this one directly bears on B22's design.

## Template for future entries

```
### YYYY-MM-DD -- <case ID>: <one-line summary>

- **Comparison target:** (analytical / published reference / prior case), value, status label
- **Our result:** value, status label
- **Discrepancy:** magnitude and direction
- **Investigation:** what was checked (units, BC, mesh, property source, geometry)
- **Root cause:** stated explicitly, or "not yet identified" if still open
- **Resolution:** what was changed, OR "left open, tracked as risk in docs/limitations.md"
- **Re-verification:** result after fix, confirming the gate now passes
```
