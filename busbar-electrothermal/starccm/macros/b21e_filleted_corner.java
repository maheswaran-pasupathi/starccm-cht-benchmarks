// ============================================================================
//  b21e_filleted_corner.java
//  B21-E -- current-crowding geometry set, variant E: same L-shaped bar as
//  B21-D, but with a FILLET at the inner corner (radius = 0.040m = 1x bar
//  width, ASSUMED -- see source_traceability.csv). Everything else
//  (cross-section, leg lengths, current, physics chain) is IDENTICAL to
//  B21-D, so the ONLY difference between the two results is the fillet.
//
//  Geometry technique: Sketch.createSketchFillet(SketchPrimitive,
//  SketchPrimitive, double, Units) on the path sketch's two straight legs.
//  This was found via javap against cadmodeler.jar after a first guess
//  (createFilletBetweenLines) failed to compile with "cannot find
//  symbol" -- recorded in docs/discrepancy_log.md as a real API-discovery
//  step, not silently guessed right on the first try.
//
//  Run: starccm+ -new -batch b21e_filleted_corner.java
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
import star.cadmodeler.*;

public class b21e_filleted_corner extends StarMacro {

    static final double WC = 0.040, TC = 0.005;
    static final double L1 = 0.150, L2 = 0.150;
    static final double FILLET_R = 0.040;   // ASSUMED = 1x WC, see source_traceability.csv

    static final double RHO_E_REF = 1.68e-8;
    static final double SIGMA_REF = 1.0 / RHO_E_REF;
    static final double K_CU = 401.0, RHO_MASS = 8933.0, CP = 385.0;

    static final double CURRENT_A = 400.0;
    static final double H_CONV = 10.0, T_INF_K = 293.15;

    static final String OUT_PATH = "results/processed/b21e_results.csv";
    static final String TABLE_PATH = "results/processed/b21e_field_export.csv";
    Simulation sim; Units m;

    public void execute() {
        sim = getActiveSimulation();
        m = sim.getUnitsManager().getUnits("m");
        sim.println("=== b21e_filleted_corner start ===");

        SolidModelManager models = sim.get(SolidModelManager.class);
        CadModel cad = models.createSolidModel();
        cad.setPresentationName("b21eCAD");
        FeatureManager features = cad.getFeatureManager();

        Sketch path = features.createSketch(plane(cad, "XY"));
        features.startSketchEdit(path);
        drawFilletedPath(path);
        features.stopSketchEdit(path, true);

        Sketch profile = features.createSketch(plane(cad, "YZ"));
        features.startSketchEdit(profile);
        profile.createRectangle(xy(-WC / 2.0, 0.0), xy(WC / 2.0, TC));
        features.stopSketchEdit(profile, true);

        SweepMerge sweep = features.createSweepMerge(profile, path);
        sweep.setPresentationName("b21eSweep");

        cad.update();
        cad.createParts();
        models.endEditCadModel(cad);

        GeometryPart bar = null;
        for (GeometryPart gp : sim.get(SimulationPartManager.class).getParts()) {
            sim.println("     part: " + gp.getPresentationName());
            bar = gp;
        }
        if (bar == null) throw new IllegalStateException("No geometry part found after cad.createParts()");
        bar.setPresentationName("bar_b21e");
        splitAll(bar, 40.0);

        Region r = mkRegion(bar, "bar_b21e");
        meshAll(Arrays.asList(bar), 0.0025);

        Boundary vinFace = extremeXFace(r, true);
        Boundary ioutFace = extremeYFace(r, false);
        vinFace.setPresentationName("Vin_b21e");
        ioutFace.setPresentationName("Iout_b21e");
        List<Boundary> sides = otherFaces(r, vinFace, ioutFace);
        sim.println(">> Vin=" + vinFace.getPresentationName() + " Iout=" + ioutFace.getPresentationName());

        PhysicsContinuum pc = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pc.setPresentationName("b21e-filleted-corner");
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

        star.common.StepStoppingCriterion maxSteps = (star.common.StepStoppingCriterion)
                sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
        maxSteps.setMaximumNumberSteps(3000);
        sim.getSimulationIterator().run(3000);
        sim.println(">> iters " + sim.getSimulationIterator().getCurrentIteration());

        UserFieldFunction jDotE = sim.getFieldFunctionManager().createFieldFunction();
        jDotE.setPresentationName("JdotE_b21e");
        jDotE.setFunctionName("JdotE_b21e");
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
        double R_ohm = V_drop / CURRENT_A;

        VolumeIntegralReport pIntReport = sim.getReportManager().createReport(VolumeIntegralReport.class);
        pIntReport.setPresentationName("P_JdotE_b21e");
        pIntReport.getParts().setObjects(Arrays.asList(r));
        pIntReport.setFieldFunction(jDotE);
        double P_JdotE = pIntReport.getReportMonitorValue();
        double P_IV = CURRENT_A * V_drop;
        double powerMismatchPct = 100.0 * Math.abs(P_JdotE - P_IV) / Math.abs(P_IV);

        FieldFunction wallHeatFlux = ff("BoundaryHeatFlux");
        double Q_sides = surfaceIntegralAll(sides, wallHeatFlux);
        double heatBalancePct = 100.0 * Math.abs(Q_sides - P_JdotE) / P_JdotE;

        FieldFunction jMagAll = ff("ElectricCurrentDensity").getMagnitudeFunction();
        MaxReport jmx = sim.getReportManager().createReport(MaxReport.class);
        jmx.setPresentationName("Jmax_b21e"); jmx.getParts().setObjects(Arrays.asList(r)); jmx.setFieldFunction(jMagAll);
        double Jmax = jmx.getReportMonitorValue();

        FieldFunction T_ff = ff("Temperature");
        MaxReport tmx = sim.getReportManager().createReport(MaxReport.class);
        tmx.setPresentationName("Tmax_b21e"); tmx.getParts().setObjects(Arrays.asList(r)); tmx.setFieldFunction(T_ff);
        double Tmax_K = tmx.getReportMonitorValue();
        VolumeAverageReport tavg = sim.getReportManager().createReport(VolumeAverageReport.class);
        tavg.setPresentationName("Tavg_b21e"); tavg.getParts().setObjects(Arrays.asList(r)); tavg.setFieldFunction(T_ff);
        double Tavg_K = tavg.getReportMonitorValue();

        boolean gateCurrent = currentImbalancePct < 0.5;
        boolean gatePower = powerMismatchPct < 1.0;
        boolean gateHeat = heatBalancePct < 2.0;
        String gateStatus = (gateCurrent && gatePower && gateHeat) ? "PASSED" : "FAILED";

        sim.println("");
        sim.println("=== B21-E RESULTS ===");
        sim.println("I_iout=" + I_iout + " I_vin=" + I_vin + " imbalance=" + currentImbalancePct + "%");
        sim.println("V_drop=" + V_drop + " V, R=" + R_ohm + " ohm");
        sim.println("P_JdotE=" + P_JdotE + " W, P_IV=" + P_IV + " W, mismatch=" + powerMismatchPct + "%");
        sim.println("Q_sides=" + Q_sides + " W, heat balance vs P_JdotE=" + heatBalancePct + "%");
        sim.println("Jmax=" + Jmax + " A/m^2");
        sim.println("Tmax=" + Tmax_K + " K (" + (Tmax_K - 273.15) + " C), Tavg=" + Tavg_K + " K");
        sim.println("GATE current(<0.5%)=" + gateCurrent + " power(<1%)=" + gatePower
                + " heat(<2%)=" + gateHeat + " -> " + gateStatus);

        StringBuilder csv = new StringBuilder();
        csv.append("variant,current_A,I_iout_A,I_vin_A,current_imbalance_pct,V_drop_V,R_ohm,"
                + "P_JdotE_W,P_IV_W,power_mismatch_pct,Q_sides_W,heat_balance_pct,Jmax_A_m2,Tmax_K,Tavg_K,gate_status\n");
        csv.append(String.format("b21e_filleted_corner,%.2f,%.6f,%.6f,%.6f,%.8f,%.10f,%.6f,%.6f,%.6f,%.6f,%.6f,%.3f,%.4f,%.4f,%s%n",
                CURRENT_A, I_iout, I_vin, currentImbalancePct, V_drop, R_ohm, P_JdotE, P_IV,
                powerMismatchPct, Q_sides, heatBalancePct, Jmax, Tmax_K, Tavg_K, gateStatus));
        write(OUT_PATH, csv.toString());

        exportField(r, jMagAll, T_ff);

        sim.println("=== b21e_filleted_corner done ===");
    }

    /** Same two legs as B21-D's drawSharpPath, but with a fillet at their shared corner. */
    private void drawFilletedPath(Sketch s) {
        LineSketchPrimitive leg1 = s.createLine(xy(0.0, 0.0), xy(L1, 0.0));
        LineSketchPrimitive leg2 = s.createLine(xy(L1, 0.0), xy(L1, L2));
        s.createIncidenceConstraint(leg1.getEndPoint(), leg2.getStartPoint());
        s.createHorizontalConstraint(leg1);
        s.createVerticalConstraint(leg2);
        s.createFixationConstraint(leg1.getStartPoint());
        s.createSketchFillet(leg1, leg2, FILLET_R, m);
    }

    void exportField(Region r, FieldFunction jMag, FieldFunction tField) {
        XyzInternalTable table = sim.getTableManager().createTable(XyzInternalTable.class);
        table.setPresentationName("b21e_field_export");
        table.getParts().setObjects(Arrays.asList(r));
        PrimitiveFieldFunction pos = (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Position");
        table.setFieldFunctions(new NeoObjectVector(new Object[]{
                pos.getComponentFunction(0), pos.getComponentFunction(1), pos.getComponentFunction(2),
                jMag, tField}));
        table.extract();
        table.export(TABLE_PATH, ",");
        sim.println(">> exported field table to " + TABLE_PATH);
    }

    // ---- helpers (identical to B21-D) ----
    private static DoubleVector xy(double a, double b) { return new DoubleVector(new double[]{a, b}); }
    private CanonicalSketchPlane plane(CadModel cad, String name) {
        Feature ft = cad.getFeature(name);
        if (ft instanceof CanonicalSketchPlane) return (CanonicalSketchPlane) ft;
        throw new IllegalStateException("No datum plane named '" + name + "'");
    }
    void splitAll(GeometryPart p, double ang) {
        Collection<PartSurface> surfs = p.getPartSurfaces();
        Vector<PartSurface> v = new Vector<PartSurface>(surfs);
        ((MeshPart) p).getPartSurfaceManager().splitPartSurfacesByAngle(v, ang);
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
    Boundary extremeYFace(Region r, boolean wantMin) {
        PrimitiveFieldFunction pos = (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Position");
        Boundary best = null; double bestY = wantMin ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (Boundary bd : r.getBoundaryManager().getBoundaries()) {
            double yc = aaC(bd, pos.getComponentFunction(1));
            if ((wantMin && yc < bestY) || (!wantMin && yc > bestY)) { bestY = yc; best = bd; }
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
