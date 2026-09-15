// ============================================================================
//  b10_straight_bar_electrical.java
//  B10 -- straight bar, 3D solid electrical benchmark. Electrical-only (NO
//  thermal/energy model), checked directly against src/b01_straight_busbar.py's
//  closed-form result for the IDENTICAL geometry, material, and current.
//
//  Geometry: 0.300 x 0.040 x 0.005 m copper bar (matches B01's
//  BASELINE_GEOMETRY exactly). Current: 400 A (matches B01's
//  BASELINE_CURRENT_A). Material: copper, rho_e_ref = 1.68e-8 ohm.m at
//  293.15K (matches data/materials.csv / B01's constant -- NOT the 1.72e-8
//  value used in the separate, earlier personal-mentor hubbell_cae project).
//
//  Model chain: SolidModel + ConstantDensityModel + ElectromagnetismModel
//  (explicit -- GUI auto-attach does not happen via the raw enable() API,
//  found live in hubbell_cae Case 3) + ElectrodynamicsPotentialModel.
//  Deliberately NO SegregatedSolidEnergyModel / OhmicHeatingModel this case
//  -- B10 is electrical-only per the roadmap.
//
//  SIMPLIFICATION vs the checklist (starccm/case_setup_checklists/
//  b10_straight_bar_electrical.md): uses a uniform fine mesh rather than
//  explicit local terminal-region refinement, for this first run. The gate
//  only requires bulk mid-section accuracy and integrated terminal
//  currents, both of which a uniform fine mesh can satisfy; noted here
//  rather than silently matching the checklist's stated intent.
//
//  Run: starccm+ -new -batch b10_straight_bar_electrical.java
// ============================================================================
import java.io.*;
import java.util.*;
import star.base.neo.*;
import star.base.report.*;
import star.common.*;
import star.meshing.*;
import star.metrics.ThreeDimensionalModel;
import star.material.*;
import star.flow.*;
import star.electromagnetism.electricpotential.*;
import star.electromagnetism.common.*;
import star.vis.*;

public class b10_straight_bar_electrical extends StarMacro {

    // -- geometry: MUST match B01's baseline exactly --
    static final double L = 0.300, W = 0.040, T = 0.005;

    // -- material: copper, MUST match B01/materials.csv exactly --
    static final double RHO_E_REF = 1.68e-8;   // ohm.m @ 293.15K
    static final double SIGMA_CU = 1.0 / RHO_E_REF;
    static final double RHO_MASS = 8933.0, CP = 385.0;  // unused (no energy model) but set to avoid a placeholder-material trap

    // -- load: MUST match B01's baseline current --
    static final double CURRENT_A = 400.0;

    // -- B01 reference values (computed independently in Python; hardcoded
    //    here ONLY for the printed comparison -- the real gate check lives
    //    in tests/test_b10_vs_b01.py which imports the actual b01 module,
    //    not this hardcoded copy) --
    static final double B01_R_OHM = RHO_E_REF * L / (W * T);           // 2.52e-5 ohm
    static final double B01_V_DROP_V = CURRENT_A * B01_R_OHM;          // 0.01008 V
    static final double B01_POWER_W = CURRENT_A * CURRENT_A * B01_R_OHM; // 4.032 W
    static final double B01_J_NOMINAL = CURRENT_A / (W * T);           // 2.0e6 A/m^2

    static final String OUT_PATH = "results/processed/b10_results.csv";
    Simulation sim; Units m;

    public void execute() {
        sim = getActiveSimulation();
        m = sim.getUnitsManager().getUnits("m");
        sim.println("=== b10_straight_bar_electrical start ===");
        sim.println(">> B01 reference: R=" + B01_R_OHM + " ohm, V=" + B01_V_DROP_V
                + " V, P=" + B01_POWER_W + " W, J_nominal=" + B01_J_NOMINAL + " A/m^2");

        // -----------------------------------------------------------------
        // Geometry: simple straight block (no bends -- this is B10)
        // -----------------------------------------------------------------
        MeshPartFactory f = sim.get(MeshPartFactory.class);
        SimpleBlockPart bar = f.createNewBlockPart(sim.get(SimulationPartManager.class));
        bar.setCoordinateSystem(sim.getCoordinateSystemManager().getLabCoordinateSystem());
        bar.setCorners(new DoubleVector(new double[]{0.0, -W / 2.0, 0.0}),
                       new DoubleVector(new double[]{L, W / 2.0, T}));
        bar.setPresentationName("straightBar");
        bar.rebuildSimpleShapePart();

        PartSurface defSurf = bar.getPartSurfaces().iterator().next();
        Vector<PartSurface> v = new Vector<PartSurface>(); v.add(defSurf);
        bar.getPartSurfaceManager().splitPartSurfacesByAngle(v, 40.0);

        Region r = mkRegion(bar, "straightBar");
        meshAll(Arrays.asList((GeometryPart) bar), 0.0025);  // 2.5mm base size, uniform fine mesh

        Boundary vinFace = extremeXFace(r, true);
        Boundary ioutFace = extremeXFace(r, false);
        vinFace.setPresentationName("Vin");
        ioutFace.setPresentationName("Iout");
        List<Boundary> sides = otherFaces(r, vinFace, ioutFace);
        sim.println(">> Vin=" + vinFace.getPresentationName() + " Iout=" + ioutFace.getPresentationName());

        // -----------------------------------------------------------------
        // Physics: electrical-only, no energy/thermal model
        // -----------------------------------------------------------------
        PhysicsContinuum pc = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pc.setPresentationName("b10-electrical-only");
        pc.enable(ThreeDimensionalModel.class);
        pc.enable(SteadyModel.class);
        pc.enable(SolidModel.class);
        pc.enable(ElectromagnetismModel.class);
        pc.enable(ElectrodynamicsPotentialModel.class);
        r.setPhysicsContinuum(pc);

        Material cu = pc.getModelManager().getModel(SolidModel.class).getMaterial();
        MaterialPropertyManager cuProps = cu.getMaterialProperties();
        cuProps.getMaterialProperty(ElectricalConductivityProperty.class).setConstant(SIGMA_CU);

        // Insulated remaining surfaces is the DEFAULT for ElectrodynamicsPotentialWallOption
        // (confirmed live: the KB reference case's outer "interface" wall used
        // Method=Insulator without explicit setting -- hubbell_cae RUN_LOG.md 2026-09-15).
        vinFace.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_POTENTIAL);
        scal(vinFace, ElectricPotentialProfile.class, 0.0);

        ioutFace.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_CURRENT);
        scal(ioutFace, ElectricCurrentProfile.class, CURRENT_A);

        // -----------------------------------------------------------------
        // Solve (electrical-only -- expect very fast convergence, no
        // thermal feedback loop exists in this model)
        // -----------------------------------------------------------------
        star.common.StepStoppingCriterion maxSteps = (star.common.StepStoppingCriterion)
                sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
        maxSteps.setMaximumNumberSteps(500);
        sim.getSimulationIterator().run(500);
        sim.println(">> iters " + sim.getSimulationIterator().getCurrentIteration());

        // -----------------------------------------------------------------
        // Custom field function: J.E (no OhmicHeatingModel -> no built-in
        // Specific Ohmic Heat Source this case; see starccm/field_functions.md)
        // -----------------------------------------------------------------
        UserFieldFunction jDotE = sim.getFieldFunctionManager().createFieldFunction();
        jDotE.setPresentationName("JdotE");
        jDotE.setFunctionName("JdotE");
        jDotE.setDefinition(
                "$$ElectricCurrentDensity[0]*$$ElectricField[0]"
                + "+$$ElectricCurrentDensity[1]*$$ElectricField[1]"
                + "+$$ElectricCurrentDensity[2]*$$ElectricField[2]");

        // -----------------------------------------------------------------
        // Reports: current balance, voltage, J.E power, current density
        // -----------------------------------------------------------------
        FieldFunction phi = ff("ElectricPotential");
        double V_vin = aaC(vinFace, phi);
        double V_iout = aaC(ioutFace, phi);
        double V_drop = V_iout - V_vin;

        FieldFunction jCurrent = null;
        for (Object o : sim.getFieldFunctionManager().getObjects()) {
            FieldFunction fo = (FieldFunction) o;
            if (fo.getFunctionName().toLowerCase().contains("current") && !(fo instanceof VectorComponentFieldFunction)) {
                sim.println("   candidate current field function: " + fo.getFunctionName() + " / " + fo.getPresentationName());
            }
        }
        // "Boundary Specific Electric Current" is the per-area normal current
        // density at a boundary -- confirmed present on the KB reference case
        // (hubbell_cae RUN_LOG.md 2026-09-15). Integrate it over each terminal
        // to get total current -- this is the real current-balance check.
        FieldFunction boundarySpecificCurrent = ff("BoundarySpecificElectricCurrent");
        double I_iout, I_vin;
        if (boundarySpecificCurrent != null) {
            I_iout = surfaceIntegral(ioutFace, boundarySpecificCurrent);
            I_vin = surfaceIntegral(vinFace, boundarySpecificCurrent);
        } else {
            sim.println(">> WARNING: BoundarySpecificElectricCurrent field function not found -- "
                    + "falling back to J.n via ElectricCurrentDensity magnitude (less rigorous; see discrepancy_log.md)");
            FieldFunction jMag = ff("ElectricCurrentDensity").getMagnitudeFunction();
            I_iout = surfaceIntegral(ioutFace, jMag);
            I_vin = surfaceIntegral(vinFace, jMag);
        }
        // Signed surface-integral convention: the outward normal at Iout
        // points the SAME direction current is entering (against it), and
        // at Vin the same direction current is leaving (along it) -- so a
        // perfectly balanced current gives I_iout = -I_vin, NOT I_iout ==
        // I_vin. Found live: first attempt compared raw values and got a
        // spurious 200% "imbalance" purely from this sign convention, while
        // the magnitudes actually agreed to 1e-13%. The correct balance
        // check is on magnitudes (equivalently, |I_iout + I_vin| ~= 0).
        double currentImbalancePct = 100.0 * Math.abs(Math.abs(I_iout) - Math.abs(I_vin)) / Math.abs(I_iout);

        VolumeIntegralReport pIntReport = sim.getReportManager().createReport(VolumeIntegralReport.class);
        pIntReport.setPresentationName("P_JdotE_integral");
        pIntReport.getParts().setObjects(Arrays.asList(r));
        pIntReport.setFieldFunction(jDotE);
        double P_JdotE = pIntReport.getReportMonitorValue();
        double P_IV = CURRENT_A * V_drop;
        double powerMismatchPct = 100.0 * Math.abs(P_JdotE - P_IV) / Math.abs(P_IV);

        FieldFunction jMagAll = ff("ElectricCurrentDensity").getMagnitudeFunction();
        MaxReport jmx = sim.getReportManager().createReport(MaxReport.class);
        jmx.setPresentationName("Jmax");
        jmx.getParts().setObjects(Arrays.asList(r));
        jmx.setFieldFunction(jMagAll);
        double Jmax = jmx.getReportMonitorValue();

        // Mid-bar section plane, away from terminal entrance effects
        PlaneSection midSection = (PlaneSection) sim.getPartManager().createImplicitPart(
                new NeoObjectVector(new Object[]{r}), new DoubleVector(new double[]{1, 0, 0}),
                new DoubleVector(new double[]{L / 2.0, 0, 0}), 0, 1, new DoubleVector(new double[]{0.0}));
        midSection.setPresentationName("MidBarSection");
        AreaAverageReport jSectionReport = sim.getReportManager().createReport(AreaAverageReport.class);
        jSectionReport.setPresentationName("J_section_avg");
        jSectionReport.getParts().setObjects(Arrays.asList(midSection));
        jSectionReport.setFieldFunction(jMagAll);
        double J_section_avg = jSectionReport.getReportMonitorValue();

        double voltageDropVsB01Pct = 100.0 * Math.abs(V_drop - B01_V_DROP_V) / B01_V_DROP_V;
        double powerVsB01Pct = 100.0 * Math.abs(P_JdotE - B01_POWER_W) / B01_POWER_W;
        double jSectionVsNominalPct = 100.0 * Math.abs(J_section_avg - B01_J_NOMINAL) / B01_J_NOMINAL;

        boolean gateCurrentBalance = currentImbalancePct < 0.5;
        boolean gateVoltagePower = voltageDropVsB01Pct < 1.0 && powerVsB01Pct < 1.0;
        boolean gateJSection = jSectionVsNominalPct < 1.0;
        String gateStatus = (gateCurrentBalance && gateVoltagePower && gateJSection) ? "PASSED" : "FAILED";

        sim.println("");
        sim.println("=== B10 RESULTS ===");
        sim.println("I_iout=" + I_iout + " A, I_vin=" + I_vin + " A, imbalance=" + currentImbalancePct + "%");
        sim.println("V_vin=" + V_vin + " V, V_iout=" + V_iout + " V, V_drop=" + V_drop + " V");
        sim.println("P_JdotE=" + P_JdotE + " W, P_I*V=" + P_IV + " W, mismatch=" + powerMismatchPct + "%");
        sim.println("Jmax=" + Jmax + " A/m^2, J_section_avg=" + J_section_avg + " A/m^2, J_nominal=" + B01_J_NOMINAL + " A/m^2");
        sim.println("vs B01: V_drop diff=" + voltageDropVsB01Pct + "%, P diff=" + powerVsB01Pct
                + "%, J_section diff=" + jSectionVsNominalPct + "%");
        sim.println("GATE current-balance(<0.5%)=" + gateCurrentBalance
                + " GATE V&P-vs-B01(<1%)=" + gateVoltagePower + " GATE J-section(<1%)=" + gateJSection);
        sim.println("OVERALL GATE STATUS: " + gateStatus);

        StringBuilder csv = new StringBuilder();
        csv.append("case_id,current_A,I_iout_A,I_vin_A,current_imbalance_pct,V_vin_V,V_iout_V,V_drop_V,"
                + "P_JdotE_W,P_IV_W,power_mismatch_pct,Jmax_A_m2,J_section_avg_A_m2,J_nominal_A_m2,"
                + "voltage_drop_vs_B01_pct,power_vs_B01_pct,J_section_vs_nominal_pct,gate_status\n");
        csv.append(String.format(
                "b10_straight_bar_electrical,%.2f,%.6f,%.6f,%.6f,%.8f,%.8f,%.8f,%.6f,%.6f,%.6f,%.3f,%.3f,%.3f,%.6f,%.6f,%.6f,%s%n",
                CURRENT_A, I_iout, I_vin, currentImbalancePct, V_vin, V_iout, V_drop,
                P_JdotE, P_IV, powerMismatchPct, Jmax, J_section_avg, B01_J_NOMINAL,
                voltageDropVsB01Pct, powerVsB01Pct, jSectionVsNominalPct, gateStatus));
        write(OUT_PATH, csv.toString());
        sim.println("=== b10_straight_bar_electrical done ===");
    }

    // ---- helpers (proven pattern, reused from hubbell_cae Cases 3-7) ----
    Region mkRegion(GeometryPart p, String name) {
        Set<Region> before = new HashSet<Region>();
        for (Object o : sim.getRegionManager().getRegions()) before.add((Region) o);
        sim.getRegionManager().newRegionsFromParts(
                new ArrayList<GeometryPart>(Arrays.asList(p)),
                "OneRegion", "OneBoundaryPerPartSurface", "OneFeatureCurve", true);
        Region r = null;
        for (Object o : sim.getRegionManager().getRegions()) { Region rr = (Region) o; if (!before.contains(rr)) r = rr; }
        r.setPresentationName(name);
        return r;
    }
    void meshAll(List<GeometryPart> parts, double base) {
        List<String> meshers = Arrays.asList("star.resurfacer.ResurfacerAutoMesher", "star.dualmesher.DualAutoMesher");
        AutoMeshOperation op = sim.get(MeshOperationManager.class).createAutoMeshOperation(meshers, parts);
        op.getDefaultValues().get(BaseSize.class).setValueAndUnits(base, m);
        op.execute();
        sim.println(">> meshed " + parts.size() + " parts, base=" + base);
    }
    double aaC(Boundary bd, FieldFunction f) {
        AreaAverageReport r = sim.getReportManager().createReport(AreaAverageReport.class);
        r.setPresentationName("c" + System.nanoTime()); r.getParts().setObjects(bd); r.setFieldFunction(f);
        return r.getReportMonitorValue();
    }
    double surfaceIntegral(Boundary bd, FieldFunction f) {
        SurfaceIntegralReport r = sim.getReportManager().createReport(SurfaceIntegralReport.class);
        r.setPresentationName("si" + System.nanoTime()); r.getParts().setObjects(bd); r.setFieldFunction(f);
        return r.getReportMonitorValue();
    }
    Boundary extremeXFace(Region r, boolean wantMin) {
        PrimitiveFieldFunction pos = (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Position");
        Boundary best = null; double bestX = wantMin ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (Boundary bd : r.getBoundaryManager().getBoundaries()) {
            double xc = aaC(bd, pos.getComponentFunction(0));
            if ((wantMin && xc < bestX) || (!wantMin && xc > bestX)) { bestX = xc; best = bd; }
        }
        return best;
    }
    List<Boundary> otherFaces(Region r, Boundary... exclude) {
        Set<Boundary> ex = new HashSet<Boundary>(Arrays.asList(exclude));
        List<Boundary> out = new ArrayList<Boundary>();
        for (Boundary bd : r.getBoundaryManager().getBoundaries())
            if (!ex.contains(bd)) out.add(bd);
        return out;
    }
    void scal(Boundary bd, Class c, double val) {
        ScalarProfile sp = (ScalarProfile) bd.getValues().get(c);
        sp.setMethod(ConstantScalarProfileMethod.class);
        ((ConstantScalarProfileMethod) sp.getMethod()).getQuantity().setValue(val);
    }
    FieldFunction ff(String n) {
        for (Object o : sim.getFieldFunctionManager().getObjects()) {
            FieldFunction ffo = (FieldFunction) o;
            if (ffo.getFunctionName().equalsIgnoreCase(n)) return ffo;
        }
        return sim.getFieldFunctionManager().getFunction(n);
    }
    void write(String p, String s) {
        try {
            new File(p).getParentFile().mkdirs();
            FileWriter w = new FileWriter(p); w.write(s); w.close();
            sim.println(">> wrote " + p);
        } catch (IOException e) { sim.println("!! " + e); }
    }
}
