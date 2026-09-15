// ============================================================================
//  b30_cht_natural_convection.java
//  B30 -- CHT natural-convection air domain around the baseline busbar
//  (radiation added in a SECOND stage once this is confirmed converging,
//  same staged-build approach used for B22's interface debugging -- see
//  starccm/case_setup_checklists/b30_cht_natural_convection_radiation.md).
//
//  Geometry: bar split into barLeft (outside the air box, Vin terminal),
//  barMid (inside the box, the real CHT region), barRight (outside the
//  box, Iout terminal) -- same "split at the exact interface boundary"
//  technique as B22, because electrical terminals cannot sit on a CHT
//  interface shared with a non-electrical fluid (air has no
//  ElectrodynamicsPotentialModel).
//
//  Flow regime: LAMINAR, justified analytically BEFORE this macro was
//  written (src/b30_rayleigh_estimate.py: Ra=7.5e3, 3-4 orders of
//  magnitude below the ~1e7-1e8 horizontal-plate turbulent transition).
//
//  Run: starccm+ -new -batch b30_cht_natural_convection.java
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
import star.segregatedflow.SegregatedFlowModel;
import star.energy.*;
import star.segregatedenergy.SegregatedSolidEnergyModel;
import star.segregatedenergy.SegregatedFluidTemperatureModel;
import star.electromagnetism.electricpotential.*;
import star.electromagnetism.ohmicheating.*;
import star.electromagnetism.common.*;

public class b30_cht_natural_convection extends StarMacro {

    static final double W = 0.040, T = 0.005;
    static final double MID_LEN = 0.26, END_LEN = 0.02;  // total bar length = 0.30m, matches baseline
    static final double BOX_W = 0.30, BOX_H = 0.40;      // box y/z extent (box x = MID_LEN exactly)
    static final double SMALL_AREA_THRESHOLD = 0.02;     // m^2 -- separates cavity walls from box outer walls

    static final double RHO_E_REF = 1.68e-8;
    static final double SIGMA_REF = 1.0 / RHO_E_REF;
    static final double K_CU = 401.0, RHO_CU = 8933.0, CP_CU = 385.0;
    static final double CURRENT_A = 400.0;
    static final double H_CONV_LEADS = 10.0;  // barLeft/barRight's own exterior faces, same fixed-h as B12/B21/B22
    static final double T_AMBIENT_K = 293.15;

    // Air properties, all from data/materials.csv (Incropera & Bergman Table A.4, 300K)
    static final double AIR_RHO = 1.1614, AIR_CP = 1007.0, AIR_K = 0.0263;
    static final double AIR_MU = 184.6e-7, AIR_BETA = 3.333e-3;

    static final String OUT_PATH = "results/processed/b30_results.csv";
    Simulation sim; Units m;

    public void execute() {
        sim = getActiveSimulation();
        m = sim.getUnitsManager().getUnits("m");
        sim.println("=== b30_cht_natural_convection start ===");

        MeshPartFactory f = sim.get(MeshPartFactory.class);

        SimpleBlockPart barLeft = block(f, "barLeft", -END_LEN, 0.0, -W / 2.0, W / 2.0, -T / 2.0, T / 2.0);
        SimpleBlockPart barMid = block(f, "barMid", 0.0, MID_LEN, -W / 2.0, W / 2.0, -T / 2.0, T / 2.0);
        SimpleBlockPart barMidTool = block(f, "barMidTool", 0.0, MID_LEN, -W / 2.0, W / 2.0, -T / 2.0, T / 2.0);
        SimpleBlockPart barRight = block(f, "barRight", MID_LEN, MID_LEN + END_LEN, -W / 2.0, W / 2.0, -T / 2.0, T / 2.0);
        SimpleBlockPart box = block(f, "airBoxRaw", 0.0, MID_LEN, -BOX_W / 2.0, BOX_W / 2.0, -BOX_H / 2.0, BOX_H / 2.0);

        List<GeometryPart> subtractInputs = new ArrayList<GeometryPart>();
        subtractInputs.add(box);
        subtractInputs.add(barMidTool);
        SubtractPartsOperation subtract = (SubtractPartsOperation)
                sim.get(MeshOperationManager.class).createSubtractPartsOperation(subtractInputs);
        subtract.setTargetPart((MeshPart) box);
        subtract.execute();

        GeometryPart airPart = null;
        for (GeometryPart gp : sim.get(SimulationPartManager.class).getParts()) {
            sim.println("     part after subtract: " + gp.getPresentationName());
            if (gp.getPresentationName().equals("Subtract")) airPart = gp;
        }
        if (airPart == null) throw new IllegalStateException("Subtract result part not found");
        airPart.setPresentationName("airBox");
        splitAll(airPart, 40.0);

        Region rLeft = mkRegion(barLeft, "barLeft");
        Region rMid = mkRegion(barMid, "barMid");
        Region rRight = mkRegion(barRight, "barRight");
        Region rAir = mkRegion(airPart, "airBox");

        // Each region meshed SEPARATELY -- avoids "Surface intersects
        // self" on the coincident faces (same lesson as B22).
        meshAll(Arrays.asList((GeometryPart) barLeft), 0.0025);
        meshAll(Arrays.asList((GeometryPart) barMid), 0.0025);
        meshAll(Arrays.asList((GeometryPart) barRight), 0.0025);
        meshAll(Arrays.asList((GeometryPart) airPart), 0.010);  // coarser -- much bigger domain

        Boundary vinTerm = extremeXFace(rLeft, true);
        Boundary leftStitch = extremeXFace(rLeft, false);
        Boundary midStitchL = extremeXFace(rMid, true);
        Boundary midStitchR = extremeXFace(rMid, false);
        Boundary rightStitch = extremeXFace(rRight, true);
        Boundary ioutTerm = extremeXFace(rRight, false);
        vinTerm.setPresentationName("Vin");
        ioutTerm.setPresentationName("Iout");

        // barMid's 4 side faces (the real CHT surface) -- everything on
        // barMid except the two x-stitch faces already identified.
        List<Boundary> midSides = otherFaces(rMid, midStitchL, midStitchR);
        sim.println(">> barMid side faces (CHT candidates): " + midSides.size());

        // Classify airBox's boundaries into "cavity walls" (small area,
        // match barMid's 4 sides) vs "outer box walls" (large area,
        // ambient opening) -- found live to be the robust way to tell
        // them apart after a boolean subtract regenerates the boundary
        // set (see docs/discrepancy_log.md).
        List<Boundary> cavityWalls = new ArrayList<Boundary>();
        List<Boundary> outerWalls = new ArrayList<Boundary>();
        for (Boundary bd : rAir.getBoundaryManager().getBoundaries()) {
            double area = area(bd);
            sim.println("   airBox boundary '" + bd.getPresentationName() + "' area=" + area);
            if (area < SMALL_AREA_THRESHOLD) cavityWalls.add(bd); else outerWalls.add(bd);
        }
        sim.println(">> airBox cavity walls (small area): " + cavityWalls.size()
                + ", outer walls (large area): " + outerWalls.size());

        // Physics: solid copper (barLeft/barMid/barRight)
        PhysicsContinuum pcSolid = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pcSolid.setPresentationName("b30-solid-copper");
        pcSolid.enable(ThreeDimensionalModel.class);
        pcSolid.enable(SteadyModel.class);
        pcSolid.enable(SolidModel.class);
        pcSolid.enable(SegregatedSolidEnergyModel.class);
        pcSolid.enable(ConstantDensityModel.class);
        pcSolid.enable(ElectromagnetismModel.class);
        pcSolid.enable(ElectrodynamicsPotentialModel.class);
        pcSolid.enable(OhmicHeatingModel.class);
        rLeft.setPhysicsContinuum(pcSolid);
        rMid.setPhysicsContinuum(pcSolid);
        rRight.setPhysicsContinuum(pcSolid);

        Material cu = pcSolid.getModelManager().getModel(SolidModel.class).getMaterial();
        MaterialPropertyManager cuProps = cu.getMaterialProperties();
        cuProps.getMaterialProperty(ConstantDensityProperty.class).setConstant(RHO_CU);
        cuProps.getMaterialProperty(SpecificHeatProperty.class).setConstant(CP_CU);
        cuProps.getMaterialProperty(ThermalConductivityProperty.class).setConstant(K_CU);
        cuProps.getMaterialProperty(ElectricalConductivityProperty.class).setConstant(SIGMA_REF);

        // Physics: air, laminar natural convection (Boussinesq)
        PhysicsContinuum pcAir = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pcAir.setPresentationName("b30-air-natural-convection");
        pcAir.enable(ThreeDimensionalModel.class);
        pcAir.enable(SteadyModel.class);
        pcAir.enable(SingleComponentGasModel.class);
        pcAir.enable(SegregatedFlowModel.class);
        pcAir.enable(ConstantDensityModel.class);
        pcAir.enable(LaminarModel.class);
        pcAir.enable(SegregatedFluidTemperatureModel.class);
        pcAir.enable(GravityModel.class);
        pcAir.enable(BoussinesqModel.class);
        rAir.setPhysicsContinuum(pcAir);

        Material air = pcAir.getModelManager().getModel(SingleComponentGasModel.class).getMaterial();
        MaterialPropertyManager airProps = air.getMaterialProperties();
        airProps.getMaterialProperty(ConstantDensityProperty.class).setConstant(AIR_RHO);
        airProps.getMaterialProperty(SpecificHeatProperty.class).setConstant(AIR_CP);
        airProps.getMaterialProperty(ThermalConductivityProperty.class).setConstant(AIR_K);
        airProps.getMaterialProperty(DynamicViscosityProperty.class).setConstant(AIR_MU);
        airProps.getMaterialProperty(ThermalExpansionProperty.class).setConstant(AIR_BETA);

        pcAir.getReferenceValues().get(Gravity.class).setComponents(0.0, 0.0, -9.81);
        sim.println(">> gravity set to (0,0,-9.81)");

        // Electrical terminals
        vinTerm.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_POTENTIAL);
        scal(vinTerm, ElectricPotentialProfile.class, 0.0);
        ioutTerm.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_CURRENT);
        scal(ioutTerm, ElectricCurrentProfile.class, CURRENT_A);

        // barLeft/barRight's own outer (non-stitch, non-terminal) faces:
        // same fixed h=10 convection as every other case in this repo --
        // these small lead-in segments are outside the modeled air box.
        for (Boundary bd : otherFaces(rLeft, vinTerm, leftStitch)) {
            bd.getConditions().get(WallThermalOption.class).setSelected(WallThermalOption.Type.CONVECTION);
            scal(bd, HeatTransferCoefficientProfile.class, H_CONV_LEADS);
            scal(bd, AmbientTemperatureProfile.class, T_AMBIENT_K);
        }
        for (Boundary bd : otherFaces(rRight, ioutTerm, rightStitch)) {
            bd.getConditions().get(WallThermalOption.class).setSelected(WallThermalOption.Type.CONVECTION);
            scal(bd, HeatTransferCoefficientProfile.class, H_CONV_LEADS);
            scal(bd, AmbientTemperatureProfile.class, T_AMBIENT_K);
        }

        // Perfect (zero-resistance) internal stitches, barLeft<->barMid<->barRight
        BoundaryInterface leftMidIface = sim.getInterfaceManager().createDirectInterface(leftStitch, midStitchL, "Left_Mid_internal");
        BoundaryInterface midRightIface = sim.getInterfaceManager().createDirectInterface(midStitchR, rightStitch, "Mid_Right_internal");
        for (BoundaryInterface bi : Arrays.asList(leftMidIface, midRightIface)) {
            bi.getConditions().get(ElectricalResistanceOption.class)
                    .setSelected(ElectricalResistanceOption.Type.PERFECT_CONDUCTOR);
        }

        // The real CHT interface: match each of barMid's 4 side faces to
        // the corresponding airBox cavity wall by centroid Y/Z sign.
        //
        // CRITICAL (found live, same lesson as B22): createDirectInterface()
        // creates NEW InterfaceBoundary objects -- it does NOT mutate its
        // two input Boundary references in place. Continuing to use the
        // pre-creation `midSides` list for later field-value/heat-flux
        // reports silently gives NaN. Re-point to the freshly-created
        // interface's own getInterfaceBoundary0() immediately.
        List<BoundaryInterface> chtIfaces = new ArrayList<BoundaryInterface>();
        List<Boundary> midSidesLive = new ArrayList<Boundary>();
        List<Boundary> remainingCavity = new ArrayList<Boundary>(cavityWalls);
        for (Boundary midFace : midSides) {
            double my = aaC(midFace, posComp(1)), mz = aaC(midFace, posComp(2));
            Boundary bestMatch = null; double bestDist = Double.MAX_VALUE;
            for (Boundary cav : remainingCavity) {
                double cy = aaC(cav, posComp(1)), cz = aaC(cav, posComp(2));
                double dist = Math.abs(cy - my) + Math.abs(cz - mz);
                if (dist < bestDist) { bestDist = dist; bestMatch = cav; }
            }
            if (bestMatch == null) throw new IllegalStateException("No matching cavity wall found for barMid face " + midFace.getPresentationName());
            remainingCavity.remove(bestMatch);
            sim.println(">> pairing barMid face '" + midFace.getPresentationName() + "' with airBox cavity face '"
                    + bestMatch.getPresentationName() + "' (dist=" + bestDist + ")");
            BoundaryInterface iface = sim.getInterfaceManager().createDirectInterface(midFace, bestMatch, "CHT_" + midFace.getPresentationName());
            chtIfaces.add(iface);
            midSidesLive.add(iface.getInterfaceBoundary0());
        }
        midSides = midSidesLive;

        // airBox outer walls: open ambient boundary (pressure, 0 gauge,
        // 293.15K backflow temperature) -- "open natural convection",
        // not a sealed enclosure.
        // Found live (diagnostic dump): PressureBoundary directly exposes
        // star.energy.StaticTemperatureProfile and
        // star.flow.StaticPressureProfile as Values -- no separate option
        // toggle needed first. StaticPressureProfile defaults to 0 (gauge),
        // which is what we want (open ambient).
        for (Boundary bd : outerWalls) {
            bd.setBoundaryType(PressureBoundary.class);
            scal(bd, StaticTemperatureProfile.class, T_AMBIENT_K);
        }

        star.common.StepStoppingCriterion maxSteps = (star.common.StepStoppingCriterion)
                sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
        maxSteps.setMaximumNumberSteps(3000);
        sim.getSimulationIterator().run(3000);
        sim.println(">> iters " + sim.getSimulationIterator().getCurrentIteration());

        // -----------------------------------------------------------------
        // Reports
        // -----------------------------------------------------------------
        FieldFunction boundarySpecificCurrent = ff("BoundarySpecificElectricCurrent");
        double I_iout = surfaceIntegral(ioutTerm, boundarySpecificCurrent);
        double I_vin = surfaceIntegral(vinTerm, boundarySpecificCurrent);
        double currentImbalancePct = 100.0 * Math.abs(Math.abs(I_iout) - Math.abs(I_vin)) / Math.abs(I_iout);

        UserFieldFunction jDotE = sim.getFieldFunctionManager().createFieldFunction();
        jDotE.setPresentationName("JdotE_b30");
        jDotE.setFunctionName("JdotE_b30");
        jDotE.setDefinition(
                "$$ElectricCurrentDensity[0]*$$ElectricField[0]"
                + "+$$ElectricCurrentDensity[1]*$$ElectricField[1]"
                + "+$$ElectricCurrentDensity[2]*$$ElectricField[2]");
        VolumeIntegralReport pTotalReport = sim.getReportManager().createReport(VolumeIntegralReport.class);
        pTotalReport.setPresentationName("P_JdotE_total");
        pTotalReport.getParts().setObjects(Arrays.asList(rLeft, rMid, rRight));
        pTotalReport.setFieldFunction(jDotE);
        double P_total = pTotalReport.getReportMonitorValue();

        FieldFunction wallHeatFlux = ff("BoundaryHeatFlux");
        double Q_leadConv = surfaceIntegralAll(otherFaces(rLeft, vinTerm, leftStitch), wallHeatFlux)
                + surfaceIntegralAll(otherFaces(rRight, ioutTerm, rightStitch), wallHeatFlux);
        double Q_cht = surfaceIntegralAll(midSides, wallHeatFlux);
        double Q_totalOut = Q_leadConv + Q_cht;
        double heatBalancePct = 100.0 * Math.abs(Q_totalOut - P_total) / P_total;

        FieldFunction T_ff = ff("Temperature");
        double Tmax_solid = Math.max(Math.max(maxOf(rLeft, T_ff), maxOf(rMid, T_ff)), maxOf(rRight, T_ff));
        double Tmax_air = maxOf(rAir, T_ff);

        boolean gateCurrent = currentImbalancePct < 0.5;
        boolean gateHeat = heatBalancePct < 5.0;  // wider than solid-only cases -- CFD heat-balance closure is noisier; stated here, not silently tightened
        String gateStatus = (gateCurrent && gateHeat) ? "PASSED" : "FAILED";

        sim.println("");
        sim.println("=== B30 RESULTS (natural convection, no radiation yet) ===");
        sim.println("I_iout=" + I_iout + " I_vin=" + I_vin + " imbalance=" + currentImbalancePct + "%");
        sim.println("P_total(Joule)=" + P_total + " W");
        sim.println("Q_leadConv(barLeft/barRight fixed-h)=" + Q_leadConv + " W, Q_cht(barMid->air)=" + Q_cht
                + " W, Q_totalOut=" + Q_totalOut + " W, heat balance=" + heatBalancePct + "%");
        sim.println("Tmax_solid=" + Tmax_solid + " K (" + (Tmax_solid - 273.15) + " C), Tmax_air=" + Tmax_air + " K");
        sim.println("GATE current(<0.5%)=" + gateCurrent + " heat(<5%)=" + gateHeat + " -> " + gateStatus);

        StringBuilder csv = new StringBuilder();
        csv.append("case_id,current_A,I_iout_A,I_vin_A,current_imbalance_pct,P_total_W,Q_leadConv_W,Q_cht_W,"
                + "Q_totalOut_W,heat_balance_pct,Tmax_solid_K,Tmax_air_K,gate_status\n");
        csv.append(String.format("b30_natural_convection_no_radiation,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.4f,%s%n",
                CURRENT_A, I_iout, I_vin, currentImbalancePct, P_total, Q_leadConv, Q_cht, Q_totalOut,
                heatBalancePct, Tmax_solid, Tmax_air, gateStatus));
        write(OUT_PATH, csv.toString());
        sim.println("=== b30_cht_natural_convection done ===");
    }

    // ---- helpers ----
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
    double area(Boundary bd) {
        UserFieldFunction one1 = sim.getFieldFunctionManager().createFieldFunction();
        one1.setPresentationName("one" + System.nanoTime());
        one1.setFunctionName("one" + System.nanoTime());
        one1.setDefinition("1.0");
        SurfaceIntegralReport ai = sim.getReportManager().createReport(SurfaceIntegralReport.class);
        ai.setPresentationName("areaInt" + System.nanoTime());
        ai.getParts().setObjects(bd);
        ai.setFieldFunction(one1);
        return ai.getReportMonitorValue();
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
    double maxOf(Region r, FieldFunction ff) {
        MaxReport mr = sim.getReportManager().createReport(MaxReport.class);
        mr.setPresentationName("max" + System.nanoTime());
        mr.getParts().setObjects(Arrays.asList(r));
        mr.setFieldFunction(ff);
        return mr.getReportMonitorValue();
    }
    FieldFunction posComp(int i) {
        PrimitiveFieldFunction pos = (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Position");
        return pos.getComponentFunction(i);
    }
    Boundary extremeXFace(Region r, boolean wantMin) {
        Boundary best = null; double bestX = wantMin ? Double.MAX_VALUE : -Double.MAX_VALUE;
        for (Boundary bd : r.getBoundaryManager().getBoundaries()) {
            double xc = aaC(bd, posComp(0));
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
