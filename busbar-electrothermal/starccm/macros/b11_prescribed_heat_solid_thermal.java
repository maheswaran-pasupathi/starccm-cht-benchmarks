// ============================================================================
//  b11_prescribed_heat_solid_thermal.java
//  B11 -- straight bar, prescribed uniform volumetric heat-generation solid
//  thermal benchmark. Isolates conduction + convection boundary-condition
//  setup BEFORE coupling real electrical Joule heating (that's B12).
//
//  Same geometry as B10 (0.300 x 0.040 x 0.005 m copper bar). Volumetric
//  heat source set to EXACTLY B01's q''' at 400A (67,200 W/m^3 -- see
//  b01_straight_busbar.py's BASELINE case), so the total applied heat
//  matches B02's P_joule input by construction, letting this case be
//  checked directly against B02's variant_1_convection_only_constant_R()
//  result (34.9C steady lumped temperature).
//
//  Model chain: SolidModel + SegregatedSolidEnergyModel (energy model,
//  no electrical model this case -- opposite emphasis from B10).
//
//  Convection BC matches B02 exactly: h=10 W/m^2K, T_inf=293.15K, on ALL
//  outer faces (both terminal end-caps AND the four long sides -- unlike
//  B10/B12 where the end-caps carry electrical BCs, B11 has no electrical
//  physics at all, so every face is available for heat rejection).
//
//  Run: starccm+ -new -batch b11_prescribed_heat_solid_thermal.java
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
import star.energy.*;
import star.segregatedenergy.SegregatedSolidEnergyModel;

public class b11_prescribed_heat_solid_thermal extends StarMacro {

    // -- geometry: MUST match B01/B10 baseline exactly --
    static final double L = 0.300, W = 0.040, T = 0.005;
    static final double VOLUME = L * W * T;

    // -- material: copper, MUST match B01/materials.csv exactly --
    static final double K_CU = 401.0, RHO_MASS = 8933.0, CP = 385.0;

    // -- prescribed heat source: EXACTLY B01's q''' at 400A --
    // q''' = P / (L*A) = I^2*R / (L*W*T); R = rho_e*L/(W*T) = 1.68e-8*0.3/(2e-4) = 2.52e-5 ohm
    // P = 400^2 * 2.52e-5 = 4.032 W; q''' = 4.032 / (0.3*2e-4) = 67200 W/m^3
    static final double RHO_E_REF = 1.68e-8;
    static final double CURRENT_A = 400.0;
    static final double AREA = W * T;
    static final double R_OHM = RHO_E_REF * L / AREA;
    static final double P_TOTAL_W = CURRENT_A * CURRENT_A * R_OHM;
    static final double Q_PPP_W_M3 = P_TOTAL_W / VOLUME;

    // -- ambient BC, MUST match B02's variant_1_convection_only_constant_R --
    static final double H_CONV = 10.0;
    static final double T_INF_K = 293.15;

    static final String OUT_PATH = "results/processed/b11_results.csv";
    Simulation sim; Units m;

    public void execute() {
        sim = getActiveSimulation();
        m = sim.getUnitsManager().getUnits("m");
        sim.println("=== b11_prescribed_heat_solid_thermal start ===");
        sim.println(">> P_total=" + P_TOTAL_W + " W, Volume=" + VOLUME + " m^3, q'''=" + Q_PPP_W_M3 + " W/m^3");

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
        meshAll(Arrays.asList((GeometryPart) bar), 0.0025);

        List<Boundary> allWalls = new ArrayList<Boundary>(r.getBoundaryManager().getBoundaries());
        sim.println(">> " + allWalls.size() + " boundaries, all get convection this case (no electrical BCs in B11)");

        // -----------------------------------------------------------------
        // Physics: solid conduction + energy, NO electrical model
        // -----------------------------------------------------------------
        PhysicsContinuum pc = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pc.setPresentationName("b11-prescribed-heat");
        pc.enable(ThreeDimensionalModel.class);
        pc.enable(SteadyModel.class);
        pc.enable(SolidModel.class);
        pc.enable(SegregatedSolidEnergyModel.class);
        pc.enable(ConstantDensityModel.class);
        r.setPhysicsContinuum(pc);

        Material cu = pc.getModelManager().getModel(SolidModel.class).getMaterial();
        MaterialPropertyManager cuProps = cu.getMaterialProperties();
        cuProps.getMaterialProperty(ConstantDensityProperty.class).setConstant(RHO_MASS);
        cuProps.getMaterialProperty(SpecificHeatProperty.class).setConstant(CP);
        cuProps.getMaterialProperty(ThermalConductivityProperty.class).setConstant(K_CU);

        // -- prescribed uniform volumetric heat source, region-level condition --
        r.getConditions().get(EnergyUserVolumeSourceOption.class)
                .setSelected(EnergyUserVolumeSourceOption.Type.VOLUMETRIC_HEAT_SOURCE);
        ScalarProfile qProfile = (ScalarProfile) r.getValues().get(VolumetricHeatSourceProfile.class);
        qProfile.setMethod(ConstantScalarProfileMethod.class);
        ((ConstantScalarProfileMethod) qProfile.getMethod()).getQuantity().setValue(Q_PPP_W_M3);

        // -- convection on every outer face --
        for (Boundary bd : allWalls) {
            bd.getConditions().get(WallThermalOption.class).setSelected(WallThermalOption.Type.CONVECTION);
            scal(bd, HeatTransferCoefficientProfile.class, H_CONV);
            scal(bd, AmbientTemperatureProfile.class, T_INF_K);
        }

        star.common.StepStoppingCriterion maxSteps = (star.common.StepStoppingCriterion)
                sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
        maxSteps.setMaximumNumberSteps(3000);
        sim.getSimulationIterator().run(3000);
        sim.println(">> iters " + sim.getSimulationIterator().getCurrentIteration());

        // -----------------------------------------------------------------
        // Reports: applied-heat-vs-volume-integral check, 3D avg/max T,
        // total heat rejected (should equal applied heat at steady state)
        // -----------------------------------------------------------------
        sim.println(">> field functions matching heat/source/flux:");
        for (Object o : sim.getFieldFunctionManager().getObjects()) {
            FieldFunction fo = (FieldFunction) o;
            String nm = fo.getFunctionName().toLowerCase();
            if (nm.contains("heat") || nm.contains("source") || nm.contains("flux")) {
                sim.println("      [" + fo.getFunctionName() + "]  presentationName=[" + fo.getPresentationName() + "]");
            }
        }
        FieldFunction qFF = ff("UserSpecifiedEnergySource");
        if (qFF == null) {
            throw new IllegalStateException("VolumetricHeatSource field function not found -- see the "
                    + "diagnostic dump above for the real name and fix the ff(...) lookup.");
        }
        VolumeIntegralReport qIntReport = sim.getReportManager().createReport(VolumeIntegralReport.class);
        qIntReport.setPresentationName("Q_applied_integral");
        qIntReport.getParts().setObjects(Arrays.asList(r));
        qIntReport.setFieldFunction(qFF);
        double Q_applied_integral = qIntReport.getReportMonitorValue();
        double appliedHeatMismatchPct = 100.0 * Math.abs(Q_applied_integral - P_TOTAL_W) / P_TOTAL_W;

        FieldFunction wallHeatFlux = ff("BoundaryHeatFlux");
        if (wallHeatFlux == null) {
            throw new IllegalStateException("WallHeatFlux field function not found -- see the "
                    + "diagnostic dump above for the real name and fix the ff(...) lookup.");
        }
        double Q_rejected = surfaceIntegralAll(allWalls, wallHeatFlux);
        double heatBalanceMismatchPct = 100.0 * Math.abs(Q_rejected - P_TOTAL_W) / P_TOTAL_W;

        FieldFunction T_ff = ff("Temperature");
        MaxReport tmx = sim.getReportManager().createReport(MaxReport.class);
        tmx.setPresentationName("Tmax"); tmx.getParts().setObjects(Arrays.asList(r)); tmx.setFieldFunction(T_ff);
        double Tmax_K = tmx.getReportMonitorValue();

        VolumeAverageReport tavg = sim.getReportManager().createReport(VolumeAverageReport.class);
        tavg.setPresentationName("Tavg"); tavg.getParts().setObjects(Arrays.asList(r)); tavg.setFieldFunction(T_ff);
        double Tavg_K = tavg.getReportMonitorValue();

        // B02 comparison: variant_1_convection_only_constant_R steady lumped T
        double B02_variant1_T_K = T_INF_K + P_TOTAL_W / (H_CONV * (2.0 * (W + T) * L));  // matches b02's own linear formula
        double TvsB02Pct = 100.0 * Math.abs(Tavg_K - B02_variant1_T_K) / (B02_variant1_T_K - T_INF_K);

        boolean gateAppliedHeat = appliedHeatMismatchPct < 0.1;    // "numerical precision"
        boolean gateHeatBalance = heatBalanceMismatchPct < 1.0;    // roadmap B11 gate
        String gateStatus = (gateAppliedHeat && gateHeatBalance) ? "PASSED" : "FAILED";

        sim.println("");
        sim.println("=== B11 RESULTS ===");
        sim.println("Q_applied(volume integral)=" + Q_applied_integral + " W vs P_total=" + P_TOTAL_W
                + " W, mismatch=" + appliedHeatMismatchPct + "%");
        sim.println("Q_rejected(wall heat flux integral)=" + Q_rejected + " W, heat balance mismatch="
                + heatBalanceMismatchPct + "%");
        sim.println("Tmax=" + Tmax_K + " K (" + (Tmax_K - 273.15) + " C), Tavg=" + Tavg_K
                + " K (" + (Tavg_K - 273.15) + " C)");
        sim.println("B02 variant-1 lumped T=" + B02_variant1_T_K + " K (" + (B02_variant1_T_K - 273.15)
                + " C) -- 3D Tavg vs 1D lumped diff (normalized by rise)=" + TvsB02Pct + "%");
        sim.println("GATE applied-heat(<0.1%)=" + gateAppliedHeat + " GATE heat-balance(<1%)=" + gateHeatBalance);
        sim.println("OVERALL GATE STATUS: " + gateStatus);
        sim.println(">> NOTE: 3D Tmax > 3D Tavg > 1D lumped T is EXPECTED (axial conduction gradient exists in "
                + "3D but not in the 1D lumped abstraction) -- a difference here is not automatically a failure, "
                + "per the roadmap's B11 gate wording; see docs/discrepancy_log.md for the interpretation.");

        StringBuilder csv = new StringBuilder();
        csv.append("case_id,P_total_W,Q_applied_integral_W,applied_heat_mismatch_pct,Q_rejected_W,"
                + "heat_balance_mismatch_pct,Tmax_K,Tavg_K,B02_variant1_T_K,T_vs_B02_pct,gate_status\n");
        csv.append(String.format("b11_prescribed_heat_solid_thermal,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.4f,%.4f,%.4f,%s%n",
                P_TOTAL_W, Q_applied_integral, appliedHeatMismatchPct, Q_rejected, heatBalanceMismatchPct,
                Tmax_K, Tavg_K, B02_variant1_T_K, TvsB02Pct, gateStatus));
        write(OUT_PATH, csv.toString());
        sim.println("=== b11_prescribed_heat_solid_thermal done ===");
    }

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
    double surfaceIntegralAll(List<Boundary> boundaries, FieldFunction f) {
        SurfaceIntegralReport r = sim.getReportManager().createReport(SurfaceIntegralReport.class);
        r.setPresentationName("si" + System.nanoTime());
        r.getParts().setObjects(boundaries);
        r.setFieldFunction(f);
        return r.getReportMonitorValue();
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
