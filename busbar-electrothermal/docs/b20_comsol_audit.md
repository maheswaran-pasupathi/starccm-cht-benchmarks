# B20 source audit — COMSOL "Electrical Heating in a Busbar" family

Per the roadmap's Section 12 rule ("Stop and report blockers when a required
dimension, property, license permission, or solver capability is
unavailable; do not silently substitute or tune it") and B20's own
implementation policy ("Extract geometry dimensions, materials, thermal
contacts, and boundary conditions only from the public
documentation/model files ... If an input is missing or ambiguous, do not
tune silently. Mark it `ASSUMED` ... Separate the exact reference
reproduction from a simplified STAR-CCM+ proxy"), this document records a
full audit of the COMSOL busbar tutorial family BEFORE any B20 case was
built, because the roadmap's own Section 9 source-register entry could not
be verified.

## What the roadmap claims

> "COMSOL's public 'Electrical Heating in a Busbar' example, commonly
> described with a 160 A DC load and a published maximum temperature near
> 330.42 K for the standard case." (roadmap line 243)

`data/source_traceability.csv` (pre-existing rows `comsol_busbar_current`
and `comsol_busbar_Tmax_published`) carried this claim forward as
`REFERENCE`, citing `comsol.com/model/electrical-heating-in-a-busbar-8484`
(landing page, no data) and
`doc.comsol.com/6.3/.../busbar_llac.html` (HTML doc page).

## What was actually checked (both official COMSOL busbar tutorials)

### 1. "Electrical Heating in a Busbar" (LiveLink for AutoCAD / base tutorial, `busbar_llac`)

Source: full model-report PDF,
`doc.comsol.com/5.6/doc/com.comsol.help.models.llproe.busbar_llproe/models.llproe.busbar_llproe.pdf`
(directly fetched and read in full, 16 pages).

- Geometry: an L-shaped bracket with **three bolt holes** (one vertical
  bolt at the upper-right, two horizontal bolts at the lower base) —
  **not** a simple straight bar. Width (40–70 mm) and length (60–90 mm)
  are BOTH swept parametrically ("All combinations"); thickness is
  embedded in the linked CAD file and not stated in the text.
- Materials: **copper** (busbar body) + **Titanium beta-21S** (bolts),
  chosen "assuming a highly corrosive environment" — not copper-only.
- Electrical BC: **20 mV** electric potential at the upper-right vertical
  bolt surface; **0 V** (ground) at the two lower bolt surfaces. This is
  **voltage-driven**, not a prescribed 160 A current.
- Thermal BC: natural convection on all faces except bolt cross-sections,
  `h = htca` (a named parameter whose numeric value is defined in an
  external `busbar_parameters.txt` file not retrievable from the text
  extraction — a genuinely `MISSING` input, not merely undocumented in
  prose). Ambient = 293 K.
- Reported result: whole-device temperature variation **"less than 10 K"**
  above the (unspecified numerically, but referenced) ambient; design
  acceptance criterion **ΔT < 30°C** above ambient; Figure 4 colorbar at
  the largest swept geometry (70×90 mm) shows **≈42.2–42.6°C**.
- **No occurrence of "160 A" or "330.42 K" anywhere in this 16-page
  document.**

### 2. "Electrical Heating in a Busbar Assembly" (LiveLink for Inventor/SolidWorks/Solid Edge, `busbar_llinventor`)

Source: full model-report PDF,
`doc.comsol.com/5.6/doc/com.comsol.help.models.llinventor.busbar_llinventor/models.llinventor.busbar_llinventor.pdf`
(directly fetched and read in full, 16 pages). This is a **different**
tutorial — a chlor-alkali electrolysis anode-to-busbar coupling, not a
simple busbar.

- Geometry: anode top with 4 columns, copper rods/connectors, an intercell
  busbar — an assembly synchronized live from an Inventor CAD file, with
  rod diameter (16–20 mm) and connector width (60–90 mm) swept
  parametrically. No single "busbar length/width/thickness" triplet
  exists for this geometry family.
- Materials: **copper** (busbar/rods) + **Titanium beta-21S** (anode body
  and bolts) — again titanium, not steel, and again not copper-only.
- Electrical BC: **normal current density of 8,000 A/m²** applied at the
  anode-bottom "electrolyte" boundary — a distributed current density
  over an unstated area, not a single lumped "160 A" terminal current.
- Thermal BC: convective heat flux, split into two different reference
  temperatures — **35°C** ambient air on most faces, **100°C** electrolyte
  on the anode-bottom boundary (`htca`/`htce`, both parameter-file values,
  again not given numerically in the text).
- Design gate: `Tmax < 90°C` in the copper. Reported temperature range
  across the full parametric sweep (Figure 3, Figure 4): roughly
  **60–100°C**.
- `330.42 K = 57.27°C` does not fall inside this model's reported
  60–100°C band. **No occurrence of "160 A" or "330.42 K" anywhere in
  this document either.**

## Conclusion

Both authoritative COMSOL busbar tutorials were fetched and read in full.
**Neither states a 160 A current or a 330.42 K maximum temperature.**
Both are voltage/current-density-driven (not simple lumped-current-driven)
and both use copper + titanium (not copper-only). This is treated as a
genuine, reported discrepancy per the roadmap's own rule — not silently
resolved by picking whichever number sounds closest.

**Decision (per B20's own stated policy, "separate the exact reference
reproduction from a simplified STAR-CCM+ proxy"):**

- The roadmap's cited `160 A` / `330.42 K` pair is reclassified from
  `REFERENCE` to **`UNVERIFIED`** in `data/source_traceability.csv` — kept
  in the record (not deleted) with this audit as the citation for why it
  is not trusted.
- The exact 3-bolt L-bracket / anode-assembly geometries are **not**
  reproduced in STAR-CCM+ in this increment — building either exact CAD
  geometry (bolt holes, two-material titanium+copper assignment, external
  parameter-file-only convection coefficients) is a substantial new
  CAD-modeling task outside B20's original "straight-bar family"
  time-box, and the convection coefficients (`htca`/`htce`) needed for an
  exact match are `MISSING` (not published in the text) regardless.
- Instead, B20 is implemented as the **explicitly labelled simplified
  proxy** the roadmap's own policy permits: the existing B10/B11/B12
  straight-bar CAD, re-driven with the **verified, correctly-sourced**
  electrical/thermal boundary-condition *type* from the base "Busbar"
  tutorial — a prescribed **20 mV** potential BC (voltage-driven, not
  current-driven) and **293 K** ambient — rather than B10–B12's prescribed
  400 A. This reproduces the real tutorial's BC *methodology* (a
  voltage-driven Joule-heating solve) on our own geometry; it is **not**
  claimed to reproduce the real tutorial's `Tmax` or absolute geometry,
  and the validation matrix must not present it as such.
- Per B20's own validation hierarchy, only items 1–3 (BC/methodology
  audit, electrical power/voltage checks, boundary heat-flow audit) are
  in scope for this proxy. Items 4–6 (temperature-field pattern vs the
  published figure, `Tmax` and its location, mesh sensitivity vs a
  published value) are explicitly **not attempted**, because they would
  require the un-reproduced exact geometry — attempting them against a
  different geometry and calling it a match would be exactly the
  "compensating-error" failure mode the roadmap's own B20 section warns
  against ("Do not call the case 'validated' solely because `Tmax`
  matches").
