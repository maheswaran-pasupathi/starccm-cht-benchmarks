// ============================================================================
//  b20_voltage_driven_proxy.java
//  B20 -- simplified, EXPLICITLY LABELLED proxy reproduction of the real,
//  verified COMSOL "Electrical Heating in a Busbar" (busbar_llac) tutorial's
//  BOUNDARY-CONDITION METHODOLOGY: a VOLTAGE-DRIVEN (not current-driven)
//  coupled electro-thermal Joule-heating solve.
//
//  READ docs/b20_comsol_audit.md FIRST. The roadmap's Section 9 claim
//  ("160 A DC load, published Tmax near 330.42 K") could NOT be verified in
//  either official COMSOL busbar model report (both fetched and read in
//  full). This case does NOT attempt to reproduce that unverified figure,
//  and does NOT attempt to reproduce the real tutorial's geometry (a 3-bolt
//  L-bracket, not a straight bar) or Tmax. It reproduces only the VERIFIED
//  electrical BC type (20 mV potential at one terminal, 0V ground at the
//  other -- both ELECTRIC_POTENTIAL, unlike B10/B12's current-driven BC)
//  and the VERIFIED ambient (293 K), on our own existing straight-bar
//  geometry, so gates 1-3 of B20's validation hierarchy (BC/methodology
//  audit, electrical power/voltage checks, boundary heat-flow audit) can be
//  checked. Gates 4-6 (temperature pattern, Tmax vs a reference, mesh
//  sensitivity vs a published value) are explicitly NOT attempted -- see
//  the audit doc for why.
//
//  Geometry, material, physics chain: identical to B12 (same 0.300 x 0.040
//  x 0.005 m copper bar, same SolidModel+SegregatedSolidEnergyModel+
//  ConstantDensityModel+ElectromagnetismModel+ElectrodynamicsPotentialModel
//  +OhmicHeatingModel chain). ONLY the electrical BC differs: BOTH
//  terminals are ELECTRIC_POTENTIAL (20mV / 0V) instead of one being
//  ELECTRIC_CURRENT (400A).
//
//  Expected result differs from B12 in magnitude BY DESIGN: our bar's own
//  resistance (R=25.2 uOhm, from B01) at 20mV implies I ~ V/R ~ 793 A --
//  larger than B12's prescribed 400A, because the real tutorial's 20mV
//  drives ITS bracket's resistance, not ours. This is documented, not a bug.
//
//  Run: starccm+ -new -batch b20_voltage_driven_proxy.java
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
import star.electromagnetism.electricpotential.*;
import star.electromagnetism.ohmicheating.*;
import star.electromagnetism.common.*;

public class b20_voltage_driven_proxy extends StarMacro {

    static final double L = 0.300, W = 0.040, T = 0.005;
    static final double AREA = W * T;

    static final double RHO_E_REF = 1.68e-8;      // ohm.m @ 293.15K, matches B01/B10/B12
    static final double SIGMA_REF = 1.0 / RHO_E_REF;
    static final double K_CU = 401.0, RHO_MASS = 8933.0, CP = 385.0;

    // -- VERIFIED from docs/b20_comsol_audit.md (busbar_llac model report) --
    static final double V_APPLIED = 0.020;   // 20 mV, verified electrical BC
    static final double T_INF_K = 293.0;     // verified ambient (busbar_llac states 293K exactly)
    static final double H_CONV = 10.0;       // ASSUMED (real tutorial's htca is in an unavailable parameter file)

    // Our own bar's expected current at this voltage (for the printed comparison only)
    static final double B01_R_OHM = RHO_E_REF * L / AREA;             // 2.52e-5 ohm
    static final double EXPECTED_I_A = V_APPLIED / B01_R_OHM;         // ~793 A
    static final double EXPECTED_P_W = V_APPLIED * V_APPLIED / B01_R_OHM; // ~15.9 W

    static final String OUT_PATH = "results/processed/b20_results.csv";
    Simulation sim; Units m;

    public void execute() {
        sim = getActiveSimulation();
        m = sim.getUnitsManager().getUnits("m");
        sim.println("=== b20_voltage_driven_proxy start ===");
        sim.println(">> SIMPLIFIED PROXY -- see docs/b20_comsol_audit.md. NOT a reproduction of "
                + "the real tutorial's geometry or Tmax. Verified BC only: V=" + V_APPLIED
                + " V, T_inf=" + T_INF_K + " K.");
        sim.println(">> Expected order-of-magnitude on OUR bar: I~" + EXPECTED_I_A
                + " A, P~" + EXPECTED_P_W + " W (differs from B12's 400A case by design -- "
                + "different driving BC on the same geometry, not a comparison to the real tutorial).");

        MeshPartFactory f = sim.get(MeshPartFactory.class);
        SimpleBlockPart bar = f.createNewBlockPart(sim.get(SimulationPartManager.class));
        bar.setCoordinateSystem(sim.getCoordinateSystemManager().getLabCoordinateSystem());
        bar.setCorners(new DoubleVector(new double[]{0.0, -W / 2.0, 0.0}),
                       new DoubleVector(new double[]{L, W / 2.0, T}));
        bar.setPresentationName("bar_b20");
        bar.rebuildSimpleShapePart();
        PartSurface defSurf = bar.getPartSurfaces().iterator().next();
        Vector<PartSurface> v = new Vector<PartSurface>(); v.add(defSurf);
        bar.getPartSurfaceManager().splitPartSurfacesByAngle(v, 40.0);

        Region r = mkRegion(bar, "bar_b20");
        meshAll(Arrays.asList((GeometryPart) bar), 0.0025);

        Boundary vinFace = extremeXFace(r, true);
        Boundary ioutFace = extremeXFace(r, false);
        vinFace.setPresentationName("Vhigh_b20");
        ioutFace.setPresentationName("Vground_b20");
        List<Boundary> sides = otherFaces(r, vinFace, ioutFace);

        PhysicsContinuum pc = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pc.setPresentationName("b20-voltage-driven-proxy");
        pc.enable(ThreeDimensionalModel.class);
        pc.enable(SteadyModel.class);
        pc.enable(SolidModel.class);
        pc.enable(SegregatedSolidEnergyModel.class);
        pc.enable(ConstantDensityModel.class);
        pc.enable(ElectromagnetismModel.class);
        pc.enable(ElectrodynamicsPotentialModel.class);
        pc.enable(OhmicHeatingModel.class);
        r.setPhysicsContinuum(pc);

        Material cu = pc.getModelManager().getModel(SolidModel.class).getMaterial();
        MaterialPropertyManager cuProps = cu.getMaterialProperties();
        cuProps.getMaterialProperty(ConstantDensityProperty.class).setConstant(RHO_MASS);
        cuProps.getMaterialProperty(SpecificHeatProperty.class).setConstant(CP);
        cuProps.getMaterialProperty(ThermalConductivityProperty.class).setConstant(K_CU);
        cuProps.getMaterialProperty(ElectricalConductivityProperty.class).setConstant(SIGMA_REF);

        // BOTH terminals ELECTRIC_POTENTIAL -- the real difference from B10/B12,
        // matching the verified busbar_llac BC methodology (voltage-driven,
        // not current-driven).
        vinFace.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_POTENTIAL);
        scal(vinFace, ElectricPotentialProfile.class, V_APPLIED);
        ioutFace.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_POTENTIAL);
        scal(ioutFace, ElectricPotentialProfile.class, 0.0);

        for (Boundary bd : sides) {
            bd.getConditions().get(WallThermalOption.class).setSelected(WallThermalOption.Type.CONVECTION);
            scal(bd, HeatTransferCoefficientProfile.class, H_CONV);
            scal(bd, AmbientTemperatureProfile.class, T_INF_K);
        }
        // Terminal end-caps: default (adiabatic) -- same convention as B12.

        star.common.StepStoppingCriterion maxSteps = (star.common.StepStoppingCriterion)
                sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
        maxSteps.setMaximumNumberSteps(3000);
        sim.getSimulationIterator().run(3000);
        sim.println(">> iters " + sim.getSimulationIterator().getCurrentIteration());

        UserFieldFunction jDotE = sim.getFieldFunctionManager().createFieldFunction();
        jDotE.setPresentationName("JdotE_b20");
        jDotE.setFunctionName("JdotE_b20");
        jDotE.setDefinition(
                "$$ElectricCurrentDensity[0]*$$ElectricField[0]"
                + "+$$ElectricCurrentDensity[1]*$$ElectricField[1]"
                + "+$$ElectricCurrentDensity[2]*$$ElectricField[2]");

        FieldFunction boundarySpecificCurrent = ff("BoundarySpecificElectricCurrent");
        double I_iout = surfaceIntegral(ioutFace, boundarySpecificCurrent);
        double I_vin = surfaceIntegral(vinFace, boundarySpecificCurrent);
        // Same outward-normal sign convention as B10/B12 -- see docs/units_and_sign_conventions.md
        double currentImbalancePct = 100.0 * Math.abs(Math.abs(I_iout) - Math.abs(I_vin)) / Math.abs(I_iout);

        FieldFunction phi = ff("ElectricPotential");
        double V_vin = aaC(vinFace, phi);
        double V_iout = aaC(ioutFace, phi);
        double V_drop = V_vin - V_iout;   // should equal V_APPLIED to numerical precision

        VolumeIntegralReport pIntReport = sim.getReportManager().createReport(VolumeIntegralReport.class);
        pIntReport.setPresentationName("P_JdotE_b20");
        pIntReport.getParts().setObjects(Arrays.asList(r));
        pIntReport.setFieldFunction(jDotE);
        double P_JdotE = pIntReport.getReportMonitorValue();
        double P_IV = Math.abs(I_vin) * V_drop;
        double powerMismatchPct = 100.0 * Math.abs(P_JdotE - P_IV) / Math.abs(P_IV);

        FieldFunction wallHeatFlux = ff("BoundaryHeatFlux");
        double Q_sides = surfaceIntegralAll(sides, wallHeatFlux);
        double Q_terminals = surfaceIntegral(vinFace, wallHeatFlux) + surfaceIntegral(ioutFace, wallHeatFlux);
        double heatBalancePct = 100.0 * Math.abs(Q_sides - P_JdotE) / P_JdotE;

        FieldFunction T_ff = ff("Temperature");
        MaxReport tmx = sim.getReportManager().createReport(MaxReport.class);
        tmx.setPresentationName("Tmax_b20"); tmx.getParts().setObjects(Arrays.asList(r)); tmx.setFieldFunction(T_ff);
        double Tmax_K = tmx.getReportMonitorValue();
        VolumeAverageReport tavg = sim.getReportManager().createReport(VolumeAverageReport.class);
        tavg.setPresentationName("Tavg_b20"); tavg.getParts().setObjects(Arrays.asList(r)); tavg.setFieldFunction(T_ff);
        double Tavg_K = tavg.getReportMonitorValue();

        double voltageAppliedCheckPct = 100.0 * Math.abs(V_drop - V_APPLIED) / V_APPLIED;

        boolean gateCurrent = currentImbalancePct < 0.5;
        boolean gatePower = powerMismatchPct < 1.0;
        boolean gateHeat = heatBalancePct < 2.0;
        boolean gateVoltageBC = voltageAppliedCheckPct < 0.1;
        String gateStatus = (gateCurrent && gatePower && gateHeat && gateVoltageBC) ? "PASSED" : "FAILED";

        sim.println("");
        sim.println("=== B20 RESULTS (voltage-driven proxy, NOT a reproduction of the real tutorial's Tmax) ===");
        sim.println("I_iout=" + I_iout + " A, I_vin=" + I_vin + " A, imbalance=" + currentImbalancePct + "%");
        sim.println("V_vin=" + V_vin + " V, V_iout=" + V_iout + " V, V_drop=" + V_drop
                + " V (applied=" + V_APPLIED + " V, BC check=" + voltageAppliedCheckPct + "%)");
        sim.println("P_JdotE=" + P_JdotE + " W, P_IV=" + P_IV + " W, mismatch=" + powerMismatchPct + "%");
        sim.println("Q_sides=" + Q_sides + " W, Q_terminals(should be ~0, adiabatic)=" + Q_terminals
                + " W, heat balance vs P_JdotE=" + heatBalancePct + "%");
        sim.println("Tmax=" + Tmax_K + " K (" + (Tmax_K - 273.15) + " C), Tavg=" + Tavg_K
                + " K (" + (Tavg_K - 273.15) + " C) -- NOT compared to the real tutorial's published figure");
        sim.println("Expected-order-of-magnitude check: I~" + EXPECTED_I_A + " A (got " + Math.abs(I_vin)
                + " A), P~" + EXPECTED_P_W + " W (got " + P_JdotE + " W)");
        sim.println("GATE current(<0.5%)=" + gateCurrent + " power(<1%)=" + gatePower
                + " heat(<2%)=" + gateHeat + " voltageBC(<0.1%)=" + gateVoltageBC + " -> " + gateStatus);

        StringBuilder csv = new StringBuilder();
        csv.append("case_id,V_applied_V,I_iout_A,I_vin_A,current_imbalance_pct,V_drop_V,voltage_bc_check_pct,"
                + "P_JdotE_W,P_IV_W,power_mismatch_pct,Q_sides_W,heat_balance_pct,Tmax_K,Tavg_K,gate_status\n");
        csv.append(String.format(
                "b20_voltage_driven_proxy,%.4f,%.6f,%.6f,%.6f,%.8f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.4f,%s%n",
                V_APPLIED, I_iout, I_vin, currentImbalancePct, V_drop, voltageAppliedCheckPct,
                P_JdotE, P_IV, powerMismatchPct, Q_sides, heatBalancePct, Tmax_K, Tavg_K, gateStatus));
        write(OUT_PATH, csv.toString());
        sim.println("=== b20_voltage_driven_proxy done ===");
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
    }
    double aaC(Boundary bd, FieldFunction fn) {
        AreaAverageReport r = sim.getReportManager().createReport(AreaAverageReport.class);
        r.setPresentationName("c" + System.nanoTime()); r.getParts().setObjects(bd); r.setFieldFunction(fn);
        return r.getReportMonitorValue();
    }
    double surfaceIntegral(Boundary bd, FieldFunction fn) {
        SurfaceIntegralReport r = sim.getReportManager().createReport(SurfaceIntegralReport.class);
        r.setPresentationName("si" + System.nanoTime()); r.getParts().setObjects(bd); r.setFieldFunction(fn);
        return r.getReportMonitorValue();
    }
    double surfaceIntegralAll(List<Boundary> bds, FieldFunction fn) {
        SurfaceIntegralReport r = sim.getReportManager().createReport(SurfaceIntegralReport.class);
        r.setPresentationName("sia" + System.nanoTime()); r.getParts().setObjects(bds); r.setFieldFunction(fn);
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
