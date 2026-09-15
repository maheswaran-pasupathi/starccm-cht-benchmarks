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

## B21 (current-crowding geometry set)

- **J95/J99 and the hotspot-zone fraction are CELL-COUNT-weighted, not
  volume-integrated.** `src/b21_postprocess.py`'s per-cell field export
  (`XyzInternalTable`) does not include a cell-volume column in this pass,
  so a percentile computed from it treats every exported cell equally
  regardless of its actual volume. The one number that IS a true STAR-CCM+
  volume integral is each variant's `P_JdotE_W` (total Joule loss),
  reported alongside in `results/processed/b21{b,c,d,e}_results.csv` for
  the actual conservation checks. A future increment could add a `Volume`
  field function to the export and redo the percentiles as volume-weighted
  if the distinction turns out to matter for the DOE work in B50.
- **The terminal-exclusion zone (0.5x bar width) and hotspot threshold
  (1.5x nominal J) are both `ASSUMED`, stated in `src/b21_postprocess.py`,
  not derived from a mesh-convergence or entrance-effect-decay study.** A
  follow-up could check sensitivity of J95/J99 to this exclusion distance.
- **B21-E's fillet radius (1x bar width) is a rule-of-thumb, not a cited
  manufacturer bend-radius standard** for 0.005m copper sheet stock.
- **No mesh-independence check was run for B21-D/B21-E.** The sharp
  corner's `Jmax` is a near-singular geometric feature and is the most
  mesh-sensitive value in the whole B21 set. The counter-intuitive
  finding that the fillet lowers `Jmax` but raises `J95`/`J99`/`Tmax`
  (see `docs/discrepancy_log.md`) is reported as-is but has not been
  confirmed to survive mesh refinement.

## B30 (CHT natural convection + radiation)

- **The explicit radiative-vs-convective split of `Q_cht` was not
  reliably isolated.** A field-function-name guess (`BoundaryRadiationHeatFlux`)
  gave a near-zero value inconsistent with a hand-calculated Stefan-
  Boltzmann estimate (~1.5W expected). The AGGREGATE heat balance
  (verified correct, 4.68% vs the 5% gate) and the Tmax-reduction
  comparison (radiation lowers Tmax from 35.66C to 33.73C at the same
  400A, the physically expected direction) are the trustworthy evidence
  instead. Finding the correct field function name for an explicit split
  is an open follow-up.
- **No boundary-distance (domain-size) sensitivity check was run** for
  the external air box (0.30x0.40m cross-section, ~7-11x the Rayleigh
  characteristic length in clearance) -- an ASSUMED size, not verified
  independent of the domain.
- **Only ONE natural-convection case was run** ("open ambient," a
  pressure-boundary opening) -- the roadmap's "sealed still air" and
  "forced air at multiple speeds" cases are explicitly deferred, not
  attempted.
- **Only ONE emissivity value (0.78, oxidized copper) was used** --
  no emissivity sensitivity sweep, matching this repo's existing B02
  convention of flagging (not silently ignoring) that oxidized/polished
  copper emissivity spans a >10x range.
- **Solar load is explicitly deferred**, per the roadmap's own wording
  ("only after the basic balances are correct").

## Known risk carried from a related (but separate) project

The personal-mentor `hubbell_cae` project found that an area-normalized
contact resistivity is NOT portable across different contact areas without
explicit rescaling (see `docs/discrepancy_log.md`). This repository's B22
has not yet been built, so this risk has not yet been re-encountered here,
but the regression test in `tests/test_units.py` exists specifically to
prevent repeating it.
