# Validation matrix

Updated after every case, per the roadmap's Section 12 instruction. Status
labels follow `docs/units_and_sign_conventions.md`.

| Case | Evidence type | Acceptance gate | Status | Result | Notes |
|---|---|---|---|---|---|
| B00 | Unit tests | All conversion/property tests pass; properties positive over 0-200C | **PASSED** | 18/18 tests pass (`tests/test_units.py`, `tests/test_materials.py`) | Cu/Al resistivity, conductivity, k, rho, cp all verified positive over study range |
| B01 | Closed-form electrical | Internal identities (`P=IV`, `q'''=J^2/sigma`) exact to numerical precision; stated trends (`P~I^2`, `R~1/A`, `J=I/A`) hold | **PASSED** | 7/7 tests pass (`tests/test_electrical.py`); CSV `results/processed/b01_results.csv`; plots `results/figures/b01_*.png` | Baseline: 300x40x5mm Cu bar, 400A -> R=25.2 uOhm, V=10.08 mV, P=4.032 W (SIMULATED, matches independent hand calc) |
| B02 | Nonlinear steady thermal | Normalized energy residual < 1e-6 for all 5 required variants | **PASSED** | 8/8 tests pass (`tests/test_thermal.py`); all 5 variants residual < 1.4e-11; CSV `results/processed/b02_results.csv` | v1 (conv-only)=34.9C, v2 (+rad)=30.2C, v3 (+T-dep R)=30.6C, v4/v5 (+end cond)=27.0C -- all directionally correct |
| B03 | Transient (1-node) | Numerical matches analytical step response to the stated tolerance BEFORE nonlinear/multi-node accepted | **PASSED** | 8/8 tests pass (`tests/test_transient.py`); max diff = 6.9e-8 K over 6*tau; plot `results/figures/b03_step_response.png` | tau=764.3s, t63/tau=0.995, t90 matches tau*ln(10) within 2% |
| B03 (3-node) | Transient scaffold | Structural/directional sanity only (T0>=T1>=T2); NOT an independent validation | **SCAFFOLD, not validated** | Runs, heat-flow direction correct | Explicitly not claimed as validated per roadmap wording ("scaffold" is the correct label, not PASSED) |
| B10 | 3D electrical (STAR-CCM+) | Current imbalance <0.5%; V/P vs B01 within 1%; J vs I/A within 1% away from terminals | **PASSED** | Current imbalance 2.5e-12%; V_drop vs B01 5.2e-14%; P vs B01 2.0e-13%; J_section vs nominal 2.3e-14% -- all far inside gate | Straight uniform bar reduces exactly to the 1D solution as expected (no geometric crowding in this case -- that's B21's job); `results/processed/b10_results.csv`, `results/figures/b10_{geometry,potential,current_density}.png` |
| B11 | 3D prescribed-heat solid thermal | Applied heat = volume-integral source; 3D avg T consistent with 1D abstraction | **PASSED** | Applied-heat mismatch 2.0e-13% (exact, construction check); heat-balance mismatch 0.61% (<1%); 3D Tavg=34.63C vs B02 1D lumped 34.93C (2.06% of rise, EXPLAINED by real axial gradient, not a failure) | `results/processed/b11_results.csv`, `results/figures/b11_temperature.png` |
| B12 | 3D coupled DC electro-thermal | Current balance; `P_Joule` vs `I*dV`; steady heat balance; boundary heat-flow % reported | `PENDING` | `PENDING` | Not yet started |
| B20 | Published reference (COMSOL busbar) | Full input/source/discrepancy traceability; `Tmax` alone is not sufficient for "validated" | `PENDING` | `PENDING` | Source register entry exists (roadmap Section 9); geometry/BC audit not yet performed |
| B21 | Current-crowding geometry set | `J95`/`J99` + percentile metrics, not singular maxima; matched mesh | `PENDING` | `PENDING` | Related informal work exists in personal-mentor `hubbell_cae` (Cases 3-7, bent-bar current crowding) but that used a DIFFERENT geometry family and material-property set, ASSUMED values, and is not a substitute for this program's own B21 |
| B22 | Bolted contact joint | Interface/bulk loss reported separately; Rc,e and Rc,th varied independently | `PENDING` | `PENDING` | Related informal work exists in personal-mentor `hubbell_cae` Case 6 (bolted contact, found and fixed a real area-normalization bug -- see `test_units.py::test_area_normalized_contact_resistance_worked_example` for the regression test carried into THIS repo) |
| B30 | CHT + radiation | Global energy imbalance <2% | `PENDING` | `PENDING` | Not yet started |
| B31 | AC/skin effect | Solver-capability-dependent | `PENDING` | `PENDING` | Explicitly deferred per roadmap; requires checking STAR-CCM+ harmonic/eddy-current licensing first |
| B40 | Test correlation | Requires real measurement data | `PENDING` | `PENDING` | No physical test data available; would be synthetic/published-only per roadmap's evidence-level rules |
| B50 | DOE | Only after baseline gates pass | `PENDING` | `PENDING` | Not started |

## Reading this table

- `PASSED` means the stated acceptance gate was checked programmatically
  (pytest) and passed, with the check itself committed to this repo.
- `PENDING` means literally not run -- never inferred, never estimated.
- `SCAFFOLD` means code exists and runs but has not been validated against
  an independent reference or closed form; do not cite it as evidence.
- Any change to this table must be accompanied by the commit that produced
  the underlying result (see `CHANGELOG.md` once created, and git log).
