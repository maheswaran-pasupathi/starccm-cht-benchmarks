// ============================================================================
//  b12_coupled_electrothermal.java
//  B12 -- straight bar, COUPLED DC electro-thermal. Combines B10's
//  electrical setup (ElectrodynamicsPotentialModel, current-driven BC)
//  with B11's thermal setup (SegregatedSolidEnergyModel, convection),
//  PLUS OhmicHeatingModel to couple them: q''' = J.E feeds the energy
//  equation as a real (solved, not prescribed) source term.
//
//  Two variants, run in the SAME macro invocation (roadmap progression
//  items 1 and 2 -- items 3/4/5 (T-dependent k, radiation, transient) are
//  explicitly deferred to a follow-up case, not silently skipped):
//    Variant 1: CONSTANT electrical and thermal properties.
//    Variant 2: TEMPERATURE-DEPENDENT electrical resistivity (same linear
//               model as data/materials.csv / src/materials.py), to
//               QUANTIFY the positive-feedback coupling the roadmap asks
//               for ("do not only state it").
//
//  Electrical BC: Iout=400A prescribed current, Vin=0V ground (same as
//  B10). Thermal BC: convection on the FOUR SIDE faces only (h=10 W/m^2K,
//  T_inf=293.15K) -- NOT on the two terminal end-caps, which carry the
//  electrical BCs instead (matching the personal-mentor hubbell_cae
//  Cases 3-7 convention, and physically sensible: a real busbar terminal
//  is where current enters/exits, not primarily where it convects).
//
//  Mandatory closure checks (roadmap B12): terminal current balance,
//  P_Joule = integral(J.E dV), P_Joule vs I*V, steady P_Joule = Qconv
//  (Qrad=Qend=0 in this case -- no radiation model, no end-conduction path
//  since ends carry electrical BCs), and the % of heat leaving through
//  each boundary group (here: 100% through the four sides, by construction,
//  since the end-caps are electrically-insulated-for-heat in this setup --
//  actually NOT insulated for heat; see the run for the real split).
//
//  Run: starccm+ -new -batch b12_coupled_electrothermal.java
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

public class b12_coupled_electrothermal extends StarMacro {

    static final double L = 0.300, W = 0.040, T = 0.005;
    static final double AREA = W * T;

    static final double RHO_E_REF = 1.68e-8;      // ohm.m @ 293.15K, matches B01/B10
    static final double ALPHA = 0.00393;           // copper TCR, matches materials.csv
    static final double T_REF_K = 293.15;
    static final double SIGMA_REF = 1.0 / RHO_E_REF;
    static final double K_CU = 401.0, RHO_MASS = 8933.0, CP = 385.0;

    static final double CURRENT_A = 400.0;
    static final double H_CONV = 10.0, T_INF_K = 293.15;

    // B01 reference (constant-property, at T_ref -- for Variant 1 comparison)
    static final double B01_R_OHM = RHO_E_REF * L / AREA;
    static final double B01_V_DROP_V = CURRENT_A * B01_R_OHM;
    static final double B01_P_W = CURRENT_A * CURRENT_A * B01_R_OHM;

    static final String OUT_PATH = "results/processed/b12_results.csv";
    Simulation sim; Units m;
    StringBuilder csv = new StringBuilder(
        "variant,current_A,I_iout_A,I_vin_A,current_imbalance_pct,V_drop_V,P_JdotE_W,P_IV_W,"
        + "power_mismatch_pct,Q_side_conv_W,heat_balance_pct,Tmax_K,Tavg_K,gate_status\n");

    public void execute() {
        sim = getActiveSimulation();
        m = sim.getUnitsManager().getUnits("m");
        sim.println("=== b12_coupled_electrothermal start ===");

        runVariant("v1_constant_properties", false, 3000);
        runVariant("v2_temperature_dependent_resistivity", true, 6000);

        write(OUT_PATH, csv.toString());
        sim.println("=== b12_coupled_electrothermal done ===");
    }

    void runVariant(String tag, boolean tDependent, int cumulativeTargetIteration) {
        sim.println("");
        sim.println(">>> VARIANT: " + tag + " (temperature-dependent sigma=" + tDependent + ")");

        MeshPartFactory f = sim.get(MeshPartFactory.class);
        SimpleBlockPart bar = f.createNewBlockPart(sim.get(SimulationPartManager.class));
        bar.setCoordinateSystem(sim.getCoordinateSystemManager().getLabCoordinateSystem());
        bar.setCorners(new DoubleVector(new double[]{0.0, -W / 2.0, 0.0}),
                       new DoubleVector(new double[]{L, W / 2.0, T}));
        bar.setPresentationName("bar_" + tag);
        bar.rebuildSimpleShapePart();
        PartSurface defSurf = bar.getPartSurfaces().iterator().next();
        Vector<PartSurface> v = new Vector<PartSurface>(); v.add(defSurf);
        bar.getPartSurfaceManager().splitPartSurfacesByAngle(v, 40.0);

        Region r = mkRegion(bar, "bar_" + tag);
        meshAll(Arrays.asList((GeometryPart) bar), 0.0025);

        Boundary vinFace = extremeXFace(r, true);
        Boundary ioutFace = extremeXFace(r, false);
        vinFace.setPresentationName("Vin_" + tag);
        ioutFace.setPresentationName("Iout_" + tag);
        List<Boundary> sides = otherFaces(r, vinFace, ioutFace);

        PhysicsContinuum pc = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pc.setPresentationName("b12-" + tag);
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
        cuProps.getMaterialProperty(ThermalConductivityProperty.class).setConstant(K_CU);  // k constant both variants (item 3 deferred)

        if (tDependent) {
            double a1 = -SIGMA_REF * ALPHA;
            double a0 = SIGMA_REF * (1.0 + ALPHA * T_REF_K);
            setLinearPolynomialInTemperature(cuProps.getMaterialProperty(ElectricalConductivityProperty.class), a0, a1);
            sim.println("   sigma(T) = " + a0 + " + (" + a1 + ")*T  [S/m, T in K]");
        } else {
            cuProps.getMaterialProperty(ElectricalConductivityProperty.class).setConstant(SIGMA_REF);
        }

        vinFace.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_POTENTIAL);
        scal(vinFace, ElectricPotentialProfile.class, 0.0);
        ioutFace.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_CURRENT);
        scal(ioutFace, ElectricCurrentProfile.class, CURRENT_A);

        for (Boundary bd : sides) {
            bd.getConditions().get(WallThermalOption.class).setSelected(WallThermalOption.Type.CONVECTION);
            scal(bd, HeatTransferCoefficientProfile.class, H_CONV);
            scal(bd, AmbientTemperatureProfile.class, T_INF_K);
        }
        // Terminal end-caps: default thermal condition (adiabatic) -- they
        // carry the electrical BC instead. This means ALL rejected heat
        // exits through the four sides in this case -- checked explicitly
        // below, not assumed.

        // NOTE (found live, B12): "Maximum Steps" is a CUMULATIVE, whole-
        // simulation iteration counter, not reset per run() call. Running
        // multiple variants as separate regions in ONE simulation requires
        // an INCREASING absolute target each time, or the second variant's
        // run() does almost nothing (stopping criterion already satisfied
        // from the first variant's iteration count). See docs/discrepancy_log.md.
        star.common.StepStoppingCriterion maxSteps = (star.common.StepStoppingCriterion)
                sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
        maxSteps.setMaximumNumberSteps(cumulativeTargetIteration);
        sim.getSimulationIterator().run(cumulativeTargetIteration);
        sim.println("   iters " + sim.getSimulationIterator().getCurrentIteration());

        UserFieldFunction jDotE = sim.getFieldFunctionManager().createFieldFunction();
        jDotE.setPresentationName("JdotE_" + tag);
        jDotE.setFunctionName("JdotE_" + tag);
        jDotE.setDefinition(
                "$$ElectricCurrentDensity[0]*$$ElectricField[0]"
                + "+$$ElectricCurrentDensity[1]*$$ElectricField[1]"
                + "+$$ElectricCurrentDensity[2]*$$ElectricField[2]");

        FieldFunction boundarySpecificCurrent = ff("BoundarySpecificElectricCurrent");
        double I_iout = surfaceIntegral(ioutFace, boundarySpecificCurrent);
        double I_vin = surfaceIntegral(vinFace, boundarySpecificCurrent);
        double currentImbalancePct = 100.0 * Math.abs(Math.abs(I_iout) - Math.abs(I_vin)) / Math.abs(I_iout);

        FieldFunction phi = ff("ElectricPotential");
        double V_vin = aaC(vinFace, phi);
        double V_iout = aaC(ioutFace, phi);
        double V_drop = V_iout - V_vin;

        VolumeIntegralReport pIntReport = sim.getReportManager().createReport(VolumeIntegralReport.class);
        pIntReport.setPresentationName("P_JdotE_" + tag);
        pIntReport.getParts().setObjects(Arrays.asList(r));
        pIntReport.setFieldFunction(jDotE);
        double P_JdotE = pIntReport.getReportMonitorValue();
        double P_IV = CURRENT_A * V_drop;
        double powerMismatchPct = 100.0 * Math.abs(P_JdotE - P_IV) / Math.abs(P_IV);

        FieldFunction wallHeatFlux = ff("BoundaryHeatFlux");
        double Q_sides = surfaceIntegralAll(sides, wallHeatFlux);
        double Q_terminals = surfaceIntegral(vinFace, wallHeatFlux) + surfaceIntegral(ioutFace, wallHeatFlux);
        double heatBalancePct = 100.0 * Math.abs(Q_sides - P_JdotE) / P_JdotE;

        FieldFunction T_ff = ff("Temperature");
        MaxReport tmx = sim.getReportManager().createReport(MaxReport.class);
        tmx.setPresentationName("Tmax_" + tag); tmx.getParts().setObjects(Arrays.asList(r)); tmx.setFieldFunction(T_ff);
        double Tmax_K = tmx.getReportMonitorValue();
        VolumeAverageReport tavg = sim.getReportManager().createReport(VolumeAverageReport.class);
        tavg.setPresentationName("Tavg_" + tag); tavg.getParts().setObjects(Arrays.asList(r)); tavg.setFieldFunction(T_ff);
        double Tavg_K = tavg.getReportMonitorValue();

        boolean gateCurrent = currentImbalancePct < 0.5;
        boolean gatePower = powerMismatchPct < 1.0;
        boolean gateHeat = heatBalancePct < 2.0;  // roadmap B12 doesn't state a number explicitly; using B02/B11's <1-2% family, stated here not silently assumed
        String gateStatus = (gateCurrent && gatePower && gateHeat) ? "PASSED" : "FAILED";

        sim.println("   I_iout=" + I_iout + " I_vin=" + I_vin + " imbalance=" + currentImbalancePct + "%");
        sim.println("   V_drop=" + V_drop + " V (B01 constant-property ref=" + B01_V_DROP_V + " V)");
        sim.println("   P_JdotE=" + P_JdotE + " W, P_IV=" + P_IV + " W, mismatch=" + powerMismatchPct + "%");
        sim.println("   Q_sides=" + Q_sides + " W, Q_terminals(should be ~0, adiabatic)=" + Q_terminals
                + " W, heat balance vs P_JdotE=" + heatBalancePct + "%");
        sim.println("   Tmax=" + Tmax_K + " K (" + (Tmax_K - 273.15) + " C), Tavg=" + Tavg_K
                + " K (" + (Tavg_K - 273.15) + " C)");
        sim.println("   GATE current(<0.5%)=" + gateCurrent + " power(<1%)=" + gatePower
                + " heat(<2%)=" + gateHeat + " -> " + gateStatus);

        csv.append(String.format("%s,%.2f,%.6f,%.6f,%.6f,%.8f,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.4f,%s%n",
                tag, CURRENT_A, I_iout, I_vin, currentImbalancePct, V_drop, P_JdotE, P_IV, powerMismatchPct,
                Q_sides, heatBalancePct, Tmax_K, Tavg_K, gateStatus));
    }

    void setLinearPolynomialInTemperature(MaterialProperty prop, double a0, double a1) {
        prop.setMethod(PolynomialMaterialPropertyMethod.class);
        PolynomialMaterialPropertyMethod method = (PolynomialMaterialPropertyMethod) prop.getMethod();
        Polynomial poly = method.getPolynomial();
        double cutoff = 420.0;  // clamp beyond this -- see hubbell_cae Case 5 finding: linear sigma(T) goes negative ~548K
        double valueAtCutoff = a0 + a1 * cutoff;
        poly.setInputUnits(sim.getUnitsManager().getUnits("K"));
        poly.setOutputUnits(sim.getUnitsManager().getPreferredUnits(prop.getDimensions()));
        poly.setNumberOfIntervals(2);
        poly.setIntervalRanges(new DoubleVector(new double[]{250.0, cutoff, 5000.0}));
        poly.setNumberOfCoefficients(new IntVector(new int[]{2, 1}));
        poly.setCoefficients(new DoubleVector(new double[]{a0, a1, valueAtCutoff}));
        poly.setExponents(new DoubleVector(new double[]{0.0, 1.0, 0.0}));
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
