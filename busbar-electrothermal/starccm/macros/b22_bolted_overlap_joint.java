// ============================================================================
//  b22_bolted_overlap_joint.java
//  B22 -- bolted overlap joint with prescribed electrical contact
//  resistance. Model ladder (day-3 core scope): perfect contact, low,
//  nominal, high -- all four run in ONE macro invocation, same geometry
//  and mesh reused across variants (only the JointContact interface's
//  electrical-resistance condition/value changes).
//
//  READ starccm/case_setup_checklists/b22_bolted_overlap_joint.md FIRST.
//  Geometry is FOUR blocks (barA_free, barA_overlap, barB_overlap,
//  barB_free), not two -- found live that createDirectInterface() needs
//  exactly-matching faces, not a partial-overlap pair (see
//  docs/discrepancy_log.md). barA_free<->barA_overlap and
//  barB_overlap<->barB_free are PERFECT (zero-resistance) stitching
//  interfaces; barA_overlap<->barB_overlap ("JointContact") is the real
//  bolted joint, where the resistance ladder is applied.
//
//  Contact resistance values (area-normalized, contact area = W*OVERLAP
//  = 8.0e-4 m^2): see data/source_traceability.csv for full citations.
//    low     = 3 uOhm (Storm Power Components, silver-plated joint)
//    nominal = 20 uOhm (PEM, contact-enhanced bolted joint)
//    high    = 105 uOhm (PEM, traditional/untreated bolted joint)
//  Thermal contact resistance is DEFERRED this increment (left at
//  default/perfect) -- no credible ROOM-TEMPERATURE source was found;
//  see the checklist doc for why the one detailed source located
//  (cryogenic SuperCDMS study) was not used.
//
//  Run: starccm+ -new -batch b22_bolted_overlap_joint.java
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

public class b22_bolted_overlap_joint extends StarMacro {

    static final double W = 0.040, T = 0.005;
    static final double L1 = 0.150, L2 = 0.150, OVERLAP = 0.020;
    static final double CONTACT_AREA = W * OVERLAP;   // 8.0e-4 m^2, confirmed live

    static final double RHO_E_REF = 1.68e-8;
    static final double SIGMA_REF = 1.0 / RHO_E_REF;
    static final double K_CU = 401.0, RHO_MASS = 8933.0, CP = 385.0;

    static final double CURRENT_A = 400.0;
    static final double H_CONV = 10.0, T_INF_K = 293.15;

    // Area-normalized electrical contact resistance, ohm.m^2 -- see
    // data/source_traceability.csv for the absolute-uOhm citations this
    // was derived from.
    static final double RC_LOW = 2.4e-9;
    static final double RC_NOMINAL = 1.6e-8;
    static final double RC_HIGH = 8.4e-8;

    static final String OUT_PATH = "results/processed/b22_results.csv";
    Simulation sim; Units m;
    StringBuilder csv = new StringBuilder(
        "variant,current_A,contact_R_ohm_m2,I_measured_A,current_imbalance_pct,"
        + "V_drop_total_V,V_drop_joint_V,R_total_ohm,"
        + "P_bulk_W,P_interface_analytical_W,P_total_check_W,P_IV_W,power_mismatch_pct,"
        + "interface_loss_fraction_pct,T_jump_K,Tmax_freeA_K,Tmax_ovA_K,Tmax_ovB_K,Tmax_freeB_K,"
        + "Tmax_overall_K,gate_status\n");

    Region rAf, rAo, rBo, rBf;
    Boundary vinTerm, ioutTerm, aOvTop, bOvBot;
    BoundaryInterface jointIface;
    UserFieldFunction jDotE;

    public void execute() {
        sim = getActiveSimulation();
        m = sim.getUnitsManager().getUnits("m");
        sim.println("=== b22_bolted_overlap_joint start ===");

        buildGeometryAndPhysics();

        runVariant("perfect_contact", 0.0, false, 3000);
        runVariant("low_contact_R", RC_LOW, true, 6000);
        runVariant("nominal_contact_R", RC_NOMINAL, true, 9000);
        runVariant("high_contact_R", RC_HIGH, true, 12000);

        write(OUT_PATH, csv.toString());
        sim.println("=== b22_bolted_overlap_joint done ===");
    }

    void buildGeometryAndPhysics() {
        MeshPartFactory f = sim.get(MeshPartFactory.class);
        double barBStart = L1 - OVERLAP;

        SimpleBlockPart barA_free = block(f, "barA_free", 0.0, barBStart, -W / 2.0, W / 2.0, 0.0, T);
        SimpleBlockPart barA_ov   = block(f, "barA_overlap", barBStart, L1, -W / 2.0, W / 2.0, 0.0, T);
        SimpleBlockPart barB_ov   = block(f, "barB_overlap", barBStart, L1, -W / 2.0, W / 2.0, T, 2 * T);
        SimpleBlockPart barB_free = block(f, "barB_free", L1, barBStart + L2, -W / 2.0, W / 2.0, T, 2 * T);

        rAf = mkRegion(barA_free, "barA_free");
        rAo = mkRegion(barA_ov, "barA_overlap");
        rBo = mkRegion(barB_ov, "barB_overlap");
        rBf = mkRegion(barB_free, "barB_free");

        // Each block meshed SEPARATELY -- avoids "Surface intersects self"
        // (found live, see docs/discrepancy_log.md).
        meshAll(Arrays.asList((GeometryPart) barA_free), 0.0025);
        meshAll(Arrays.asList((GeometryPart) barA_ov), 0.0025);
        meshAll(Arrays.asList((GeometryPart) barB_ov), 0.0025);
        meshAll(Arrays.asList((GeometryPart) barB_free), 0.0025);

        vinTerm = extremeXFace(rAf, true);          // barA_free far end, x=0
        Boundary aFreeMaxX = extremeXFace(rAf, false);
        Boundary aOvMinX = extremeXFace(rAo, true);
        aOvTop = extremeZFace(rAo, false);
        bOvBot = extremeZFace(rBo, true);
        Boundary bOvMaxX = extremeXFace(rBo, false);
        Boundary bFreeMinX = extremeXFace(rBf, true);
        ioutTerm = extremeXFace(rBf, false);        // barB_free far end

        vinTerm.setPresentationName("Vin");
        ioutTerm.setPresentationName("Iout");
        sim.println(">> Vin=" + vinTerm.getPresentationName() + " Iout=" + ioutTerm.getPresentationName());

        // Physics MUST be assigned to the regions BEFORE the interfaces are
        // created -- found live: an interface's condition set (e.g.
        // ElectricalResistanceOption) is attached based on the physics
        // active on its two connected regions AT CREATION TIME, not
        // retroactively when physics is added afterward. A first attempt
        // created the interfaces first and got "Condition not found in
        // ConditionManager" when trying to set ElectricalResistanceOption
        // on them. See docs/discrepancy_log.md.
        PhysicsContinuum pc = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pc.setPresentationName("b22-bolted-overlap-joint");
        pc.enable(ThreeDimensionalModel.class);
        pc.enable(SteadyModel.class);
        pc.enable(SolidModel.class);
        pc.enable(SegregatedSolidEnergyModel.class);
        pc.enable(ConstantDensityModel.class);
        pc.enable(ElectromagnetismModel.class);
        pc.enable(ElectrodynamicsPotentialModel.class);
        pc.enable(OhmicHeatingModel.class);
        rAf.setPhysicsContinuum(pc); rAo.setPhysicsContinuum(pc);
        rBo.setPhysicsContinuum(pc); rBf.setPhysicsContinuum(pc);

        jointIface = sim.getInterfaceManager().createDirectInterface(aOvTop, bOvBot, "JointContact");
        BoundaryInterface aStitch = sim.getInterfaceManager().createDirectInterface(aFreeMaxX, aOvMinX, "A_internal");
        BoundaryInterface bStitch = sim.getInterfaceManager().createDirectInterface(bOvMaxX, bFreeMinX, "B_internal");
        sim.println(">> JointContact/A_internal/B_internal interfaces created. Contact area (design) = "
                + CONTACT_AREA + " m^2");

        // CRITICAL: createDirectInterface() returns/creates NEW
        // InterfaceBoundary objects -- it does NOT mutate aOvTop/bOvBot in
        // place. Continuing to use the pre-creation aOvTop/bOvBot
        // references for field-value reports (V, T) silently gives NaN
        // (found live: side0==aOvTop was `false`; AreaAverageReport on the
        // stale object returns NaN rather than throwing). Re-point the
        // fields to the REAL, freshly-created interface boundaries.
        aOvTop = jointIface.getInterfaceBoundary0();
        bOvBot = jointIface.getInterfaceBoundary1();

        Material cu = pc.getModelManager().getModel(SolidModel.class).getMaterial();
        MaterialPropertyManager cuProps = cu.getMaterialProperties();
        cuProps.getMaterialProperty(ConstantDensityProperty.class).setConstant(RHO_MASS);
        cuProps.getMaterialProperty(SpecificHeatProperty.class).setConstant(CP);
        cuProps.getMaterialProperty(ThermalConductivityProperty.class).setConstant(K_CU);
        cuProps.getMaterialProperty(ElectricalConductivityProperty.class).setConstant(SIGMA_REF);

        vinTerm.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_POTENTIAL);
        scal(vinTerm, ElectricPotentialProfile.class, 0.0);
        ioutTerm.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_CURRENT);
        scal(ioutTerm, ElectricCurrentProfile.class, CURRENT_A);

        // Convection on every outer face except the two terminals -- the
        // interface faces (A_internal/B_internal/JointContact) are
        // automatically excluded since they are no longer plain
        // convecting Boundary objects once turned into InterfaceBoundary.
        for (Region r : Arrays.asList(rAf, rAo, rBo, rBf)) {
            for (Boundary bd : r.getBoundaryManager().getBoundaries()) {
                if (bd == vinTerm || bd == ioutTerm) continue;
                if (bd instanceof InterfaceBoundary) continue;
                bd.getConditions().get(WallThermalOption.class).setSelected(WallThermalOption.Type.CONVECTION);
                scal(bd, HeatTransferCoefficientProfile.class, H_CONV);
                scal(bd, AmbientTemperatureProfile.class, T_INF_K);
            }
        }

        // Explicit PERFECT electrical contact for the two stitching
        // interfaces (should be the default, set explicitly for clarity
        // and to guard against a future STAR-CCM+ default change).
        for (BoundaryInterface bi : Arrays.asList(aStitch, bStitch)) {
            bi.getConditions().get(ElectricalResistanceOption.class)
                    .setSelected(ElectricalResistanceOption.Type.PERFECT_CONDUCTOR);
        }

        jDotE = sim.getFieldFunctionManager().createFieldFunction();
        jDotE.setPresentationName("JdotE_b22");
        jDotE.setFunctionName("JdotE_b22");
        jDotE.setDefinition(
                "$$ElectricCurrentDensity[0]*$$ElectricField[0]"
                + "+$$ElectricCurrentDensity[1]*$$ElectricField[1]"
                + "+$$ElectricCurrentDensity[2]*$$ElectricField[2]");
    }

    void runVariant(String tag, double rContact, boolean resistive, int cumulativeTargetIteration) {
        sim.println("");
        sim.println(">>> VARIANT: " + tag + " (R_contact=" + rContact + " ohm.m^2)");

        if (resistive) {
            jointIface.getConditions().get(ElectricalResistanceOption.class)
                    .setSelected(ElectricalResistanceOption.Type.SPECIFIC_ELECTRICAL_RESISTANCE);
            scalIface(jointIface, ElectricalResistanceAreaProfile.class, rContact);
        } else {
            jointIface.getConditions().get(ElectricalResistanceOption.class)
                    .setSelected(ElectricalResistanceOption.Type.PERFECT_CONDUCTOR);
        }

        // Cumulative "Maximum Steps" counter across the whole simulation --
        // increasing absolute target per variant, same fix as B12/B21-C.
        star.common.StepStoppingCriterion maxSteps = (star.common.StepStoppingCriterion)
                sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
        maxSteps.setMaximumNumberSteps(cumulativeTargetIteration);
        sim.getSimulationIterator().run(cumulativeTargetIteration);
        sim.println("   iters " + sim.getSimulationIterator().getCurrentIteration());

        FieldFunction boundarySpecificCurrent = ff("BoundarySpecificElectricCurrent");
        double I_iout = surfaceIntegral(ioutTerm, boundarySpecificCurrent);
        double I_vin = surfaceIntegral(vinTerm, boundarySpecificCurrent);
        double currentImbalancePct = 100.0 * Math.abs(Math.abs(I_iout) - Math.abs(I_vin)) / Math.abs(I_iout);
        double I_measured = Math.abs(I_vin);

        FieldFunction phi = ff("ElectricPotential");
        double V_vin = aaC(vinTerm, phi);
        double V_iout = aaC(ioutTerm, phi);
        double V_drop_total = V_iout - V_vin;
        double R_total = V_drop_total / I_measured;

        double V_aOv = aaC(aOvTop, phi);
        double V_bOv = aaC(bOvBot, phi);
        double V_drop_joint = Math.abs(V_aOv - V_bOv);

        VolumeIntegralReport pBulkReport = sim.getReportManager().createReport(VolumeIntegralReport.class);
        pBulkReport.setPresentationName("P_bulk_" + tag);
        pBulkReport.getParts().setObjects(Arrays.asList(rAf, rAo, rBo, rBf));
        pBulkReport.setFieldFunction(jDotE);
        double P_bulk = pBulkReport.getReportMonitorValue();

        double P_interface_analytical = I_measured * I_measured * rContact / CONTACT_AREA;
        double P_total_check = P_bulk + P_interface_analytical;
        double P_IV = I_measured * V_drop_total;
        double powerMismatchPct = 100.0 * Math.abs(P_total_check - P_IV) / Math.abs(P_IV);
        double interfaceLossFractionPct = 100.0 * P_interface_analytical / P_total_check;

        FieldFunction T_ff = ff("Temperature");
        double T_aOv = aaC(aOvTop, T_ff);
        double T_bOv = aaC(bOvBot, T_ff);
        double T_jump = Math.abs(T_aOv - T_bOv);

        FieldFunction jMagAll = ff("ElectricCurrentDensity").getMagnitudeFunction();
        double Jmax_freeA = maxOf(rAf, jMagAll), Jmax_ovA = maxOf(rAo, jMagAll);
        double Jmax_ovB = maxOf(rBo, jMagAll), Jmax_freeB = maxOf(rBf, jMagAll);

        double Tmax_overall = Math.max(Math.max(maxOf(rAf, T_ff), maxOf(rAo, T_ff)),
                Math.max(maxOf(rBo, T_ff), maxOf(rBf, T_ff)));

        boolean gateCurrent = currentImbalancePct < 0.5;
        boolean gatePower = powerMismatchPct < 2.0;
        String gateStatus = (gateCurrent && gatePower) ? "PASSED" : "FAILED";

        sim.println("   I_measured=" + I_measured + " A, imbalance=" + currentImbalancePct + "%");
        sim.println("   V_drop_total=" + V_drop_total + " V, V_drop_joint=" + V_drop_joint
                + " V, R_total=" + R_total + " ohm");
        sim.println("   P_bulk=" + P_bulk + " W, P_interface(analytical)=" + P_interface_analytical
                + " W, P_total_check=" + P_total_check + " W, P_IV=" + P_IV
                + " W, mismatch=" + powerMismatchPct + "%");
        sim.println("   interface_loss_fraction=" + interfaceLossFractionPct + "%, T_jump=" + T_jump + " K");
        sim.println("   Tmax: freeA=" + Jmax_freeA + " ovA=" + Jmax_ovA + " ovB=" + Jmax_ovB
                + " freeB=" + Jmax_freeB + " (J, A/m^2); Tmax_overall=" + Tmax_overall + " K");
        sim.println("   GATE current(<0.5%)=" + gateCurrent + " power(<2%)=" + gatePower + " -> " + gateStatus);

        csv.append(String.format("%s,%.2f,%.4e,%.6f,%.6f,%.8f,%.8f,%.8e,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.4f,%.3f,%.3f,%.3f,%.3f,%.4f,%s%n",
                tag, CURRENT_A, rContact, I_measured, currentImbalancePct, V_drop_total, V_drop_joint, R_total,
                P_bulk, P_interface_analytical, P_total_check, P_IV, powerMismatchPct,
                interfaceLossFractionPct, T_jump, Jmax_freeA, Jmax_ovA, Jmax_ovB, Jmax_freeB,
                Tmax_overall, gateStatus));
    }

    double maxOf(Region r, FieldFunction ff) {
        MaxReport mr = sim.getReportManager().createReport(MaxReport.class);
        mr.setPresentationName("max" + System.nanoTime());
        mr.getParts().setObjects(Arrays.asList(r));
        mr.setFieldFunction(ff);
        return mr.getReportMonitorValue();
    }

    SimpleBlockPart block(MeshPartFactory f, String name, double x0, double x1, double y0, double y1, double z0, double z1) {
        SimpleBlockPart b = f.createNewBlockPart(sim.get(SimulationPartManager.class));
        b.setCoordinateSystem(sim.getCoordinateSystemManager().getLabCoordinateSystem());
        b.setCorners(new DoubleVector(new double[]{x0, y0, z0}), new DoubleVector(new double[]{x1, y1, z1}));
        b.setPresentationName(name);
        b.rebuildSimpleShapePart();
        splitAll(b, 40.0);
        return b;
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
    void splitAll(GeometryPart p, double ang) {
        Collection<PartSurface> surfs = p.getPartSurfaces();
        Vector<PartSurface> v = new Vector<PartSurface>(surfs);
        ((MeshPart) p).getPartSurfaceManager().splitPartSurfacesByAngle(v, ang);
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
    Boundary extremeXFace(Region r, boolean wantMin) {
        PrimitiveFieldFunction pos = (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Position");
        Boundary best = null; double bestX = wantMin ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (Boundary bd : r.getBoundaryManager().getBoundaries()) {
            double xc = aaC(bd, pos.getComponentFunction(0));
            if ((wantMin && xc < bestX) || (!wantMin && xc > bestX)) { bestX = xc; best = bd; }
        }
        return best;
    }
    Boundary extremeZFace(Region r, boolean wantMin) {
        PrimitiveFieldFunction pos = (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Position");
        Boundary best = null; double bestZ = wantMin ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (Boundary bd : r.getBoundaryManager().getBoundaries()) {
            double zc = aaC(bd, pos.getComponentFunction(2));
            if ((wantMin && zc < bestZ) || (!wantMin && zc > bestZ)) { bestZ = zc; best = bd; }
        }
        return best;
    }
    void scal(Boundary bd, Class c, double val) {
        ScalarProfile sp = (ScalarProfile) bd.getValues().get(c);
        sp.setMethod(ConstantScalarProfileMethod.class);
        ((ConstantScalarProfileMethod) sp.getMethod()).getQuantity().setValue(val);
    }
    void scalIface(BoundaryInterface iface, Class c, double val) {
        ScalarProfile sp = (ScalarProfile) iface.getValues().get(c);
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
