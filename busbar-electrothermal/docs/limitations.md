# Limitations

Honest, standing record of what this program does NOT yet show, updated
as scope expands. This is not a discrepancy log (see
`docs/discrepancy_log.md` for specific result mismatches) -- it is a scope
boundary.

## Current scope (B00-B03 only)

- **No 3D geometry effects at all.** B00-B03 are 1D/lumped models. Current
  crowding, terminal entrance effects, and any spatially resolved
  current-density or temperature field are entirely out of scope until
  B10+.
- **No STAR-CCM+ result exists yet in this repository.** Every `PENDING`
  row in `docs/validation_matrix.md` means exactly that -- not "close to
  done," not "estimated," literally not run.
- **Thermal conductivity is constant-property**, not temperature-dependent,
  in this repository (unlike the personal-mentor `hubbell_cae` Case 5,
  which did implement T-dependent k and sigma for a specific 3D case --
  that is a different, informal project and its results are not
  substituted for this program's own B12).
- **The one-node and three-node transient models use LINEAR coupling
  conductances.** No radiation, no contact resistance, no
  temperature-dependent conductance in the transient network yet. The
  three-node model is explicitly a scaffold, not a validated result.
- **No physical test data.** Any future B40 correlation will be either a
  synthetic (self-generated) verification dataset or a digitized published
  curve -- not an independent experimental validation -- unless real
  measurement data becomes available.
- **Material property values are textbook (Incropera & Bergman) generic
  values**, not measured or supplier-certified for any specific commercial
  busbar stock. Commercial copper/aluminium purity and alloy affect
  resistivity and conductivity by several percent.

## Known risk carried from a related (but separate) project

The personal-mentor `hubbell_cae` project found that an area-normalized
contact resistivity is NOT portable across different contact areas without
explicit rescaling (see `docs/discrepancy_log.md`). This repository's B22
has not yet been built, so this risk has not yet been re-encountered here,
but the regression test in `tests/test_units.py` exists specifically to
prevent repeating it.
