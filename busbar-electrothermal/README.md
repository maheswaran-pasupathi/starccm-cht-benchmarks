# Busbar Electro-Thermal Open Benchmark

Public, reproducible engineering benchmark program for busbars and bolted
electrical connectors: hand calculation -> 1D reduced-order model -> 3D
electro-thermal CFD -> current crowding -> contact resistance -> published
reference correlation -> DOE.

Full program roadmap, acceptance gates, and execution brief:
`../BUSBAR_ELECTROTHERMAL_OPEN_BENCHMARK_ROADMAP.md` (personal-mentor
repository, not duplicated here to avoid drift between two copies).

**Status: B00-B03 (analytical/1D) complete and gated. B10+ (3D STAR-CCM+)
not yet started.** See `docs/validation_matrix.md` for the authoritative,
per-case status table -- never infer status from this README alone.

## Quick start

```bash
pip install -r requirements.txt
python -m pytest tests/ -v          # 41 tests, all analytical (B00-B03)
python src/b01_straight_busbar.py   # regenerates results/processed/b01_results.csv + figures
python src/b02_steady_thermal.py    # regenerates b02 CSV + figures
python src/b03_transient_trn.py     # regenerates b03 CSV + figures
```

No STAR-CCM+ license is required for anything above -- B00-B03 are pure
Python/NumPy/SciPy with no solver dependency, by design (so the analytical
ground truth is checkable independently of the proprietary solver used for
B10+).

## Repository layout

```
data/           material properties + full source traceability (CSV, cited)
docs/           units/sign conventions, validation matrix, discrepancy log
src/            B00-B03 Python modules (materials, electrical, thermal, transient, plotting)
tests/          pytest suite -- one file per benchmark stage, gates enforced as assertions
geometry/       (B10+, not yet populated) parameterized geometry instructions
starccm/        (B10+, not yet populated) macros, setup checklists, field-function/export docs
notebooks/      (not yet populated)
results/        raw/ processed/ figures/ reports/ -- all generated, none hand-edited
```

## Evidence-level and status labels

Every number in this repository carries one of: `REFERENCE`, `TARGET`,
`ASSUMED`, `SIMULATED`, `PENDING`, `VERIFIED`, `VALIDATED`. See
`docs/units_and_sign_conventions.md` for definitions. **A `PENDING` value
is never presented as if it were a real result.**

## Open items / not yet decided (flagged, not silently resolved)

- **License:** not yet chosen. The roadmap's Day-1 task list includes a
  license decision; this has not been made and no LICENSE file exists yet
  in the parent repository. Do not assume MIT/Apache/etc. without an
  explicit decision.
- **CITATION.cff, pyproject.toml, environment.yml:** not yet created;
  `requirements.txt` (pinned to the versions actually used) is the interim
  reproducibility record.
- B10 onward requires a STAR-CCM+ license session -- not yet started, see
  `docs/validation_matrix.md`.

## Contributing to this benchmark's integrity

Never invent a STAR-CCM+ result. Never reuse an area-normalized material
or contact property across a different geometry without recomputing the
actual (non-normalized) value first -- see
`tests/test_units.py::test_area_normalized_contact_resistance_worked_example`
for why this specific mistake is worth a permanent regression test.
