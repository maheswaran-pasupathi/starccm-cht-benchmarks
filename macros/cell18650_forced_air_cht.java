// ============================================================================
//  cell18650_forced_air_cht.java
//  Air-cooled 18650 cylindrical Li-ion cell, cross-flow, conjugate heat
//  transfer (CHT): solid cell with uniform volumetric heat generation,
//  coupled to a forced-air duct through a real interface.
//
//  ALL cell input data below is real, published, open-access literature
//  data (not invented) -- see README.md for the full citation list:
//    - 18650 form factor: 18 mm diameter x 65 mm height (industry standard)
//    - volumetric heat generation: 41,789 W/m^3 at 2C discharge
//    - specific heat: ~1000 J/kg.K (measured, Sony US18650, 960-1040 range)
//    - radial thermal conductivity: ~0.30 W/m.K (literature range 0.2-0.43,
//      radial is 1-2 orders of magnitude below axial for this cell family --
//      used here as an ISOTROPIC simplification along the dominant radial
//      heat path to the cooled surface, stated explicitly, not hidden)
//    - density: computed from typical commercial 18650 mass (~45 g) and the
//      real cell volume, not an independently invented number
//
//  Validation: compares the CFD-computed cell centre-to-surface temperature
//  rise against the CLOSED-FORM analytical solution for steady radial
//  conduction with uniform volumetric generation in a solid cylinder
//  (a standard textbook result, e.g. Incropera & Bergman):
//        T_center - T_surface = q''' * R^2 / (4 k)
//  using the CFD run's own computed surface temperature as the boundary
//  value -- this checks that STAR-CCM+'s solid-energy solve reproduces
//  known, public-domain conduction physics, independent of any single
//  paper's specific reported temperature curve.
//
//  Parametric via environment variables (drives a small air-velocity sweep):
//    CELL_U      duct inlet air velocity, m/s   (default 2.0)
//    CELL_OUT    output CSV path                (default results/u_<U>.csv)
//
//  Run: starccm+ -new -batch cell18650_forced_air_cht.java
// ============================================================================
import java.io.*;
import java.util.*;
import star.base.neo.*;
import star.base.report.*;
import star.common.*;
import star.meshing.*;
import star.metrics.ThreeDimensionalModel;
import star.material.*;
import star.segregatedflow.SegregatedFlowModel;
import star.segregatedenergy.SegregatedFluidTemperatureModel;
import star.segregatedenergy.SegregatedSolidEnergyModel;
import star.flow.*;
import star.energy.*;
import star.vis.*;

public class cell18650_forced_air_cht extends StarMacro {

    // -- real 18650 cell geometry (industry-standard form factor) --
    static final double D_CELL = 0.018, H_CELL = 0.065, R_CELL = D_CELL / 2.0;

    // -- real, cited cell thermal data (see README.md for sources) --
    static final double QGEN_VOL = 41789.0;   // W/m^3, 2C discharge
    static final double K_CELL   = 0.30;      // W/m.K, radial (isotropic simplification)
    static final double CP_CELL  = 1000.0;    // J/kg.K
    static final double RHO_CELL = 0.045 / (Math.PI * R_CELL * R_CELL * H_CELL);  // kg/m^3, from real mass/volume

    // -- duct (air domain) --
    static final double X_UP = -0.10, X_DOWN = 0.20, Y_HALF = 0.06;
    static final double T_AMB = 300.0;   // K, ambient/inlet air
    static final double BASE = 0.004;

    static final String OUT_DIR = "results/";
    Simulation sim; Units m;

    double env(String name, double def) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? def : Double.parseDouble(v);
    }

    public void execute() {
        double U = env("CELL_U", 2.0);
        String outPath = System.getenv("CELL_OUT");
        if (outPath == null || outPath.isEmpty()) outPath = OUT_DIR + "u_" + U + ".csv";

        sim = getActiveSimulation();
        m = sim.getUnitsManager().getUnits("m");
        sim.println("=== cell18650_forced_air_cht start: U=" + U + " m/s ===");

        // ---------------------------------------------------------------
        // Geometry: rectangular duct with a vertical 18650-cell-shaped hole
        // ---------------------------------------------------------------
        MeshPartFactory f = sim.get(MeshPartFactory.class);

        SimpleBlockPart duct = f.createNewBlockPart(sim.get(SimulationPartManager.class));
        LabCoordinateSystem lab = sim.getCoordinateSystemManager().getLabCoordinateSystem();
        duct.setCoordinateSystem(lab);
        duct.setCorners(
                new DoubleVector(new double[]{X_UP, -Y_HALF, 0.0}),
                new DoubleVector(new double[]{X_DOWN, Y_HALF, H_CELL}));
        duct.setPresentationName("duct");
        duct.rebuildSimpleShapePart();

        SimpleCylinderPart cellTool = cyl(f, "cellTool", R_CELL, 0.0, H_CELL);

        SubtractPartsOperation sub = (SubtractPartsOperation) sim.get(MeshOperationManager.class)
                .createSubtractPartsOperation(new NeoObjectVector(new Object[]{duct, cellTool}));
        sub.getTargetPartManager().setObjects(duct);
        sub.execute();
        MeshOperationPart air = (MeshOperationPart) sub.getOutputPartObjects().iterator().next();
        air.setPresentationName("air");
        splitAll(air, 40.0);

        SimpleCylinderPart cellSolid = cyl(f, "cell", R_CELL, 0.0, H_CELL);
        splitAll(cellSolid, 40.0);

        Region airR = mkRegion(air, "air");
        Region cellR = mkRegion(cellSolid, "cell");

        meshAll(Arrays.asList((GeometryPart) air), BASE, 3);
        meshAll(Arrays.asList((GeometryPart) cellSolid), 0.0015, 0);

        Map<String, Boundary> ab = classifyDuct(airR);
        Map<String, Boundary> cb = classifyCellCaps(cellR);

        // ---------------------------------------------------------------
        // Physics
        // ---------------------------------------------------------------
        PhysicsContinuum airPc = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        airPc.setPresentationName("air-physics");
        airPc.enable(ThreeDimensionalModel.class);
        airPc.enable(SteadyModel.class);
        airPc.enable(SingleComponentGasModel.class);
        airPc.enable(SegregatedFlowModel.class);
        airPc.enable(ConstantDensityModel.class);
        airPc.enable(SegregatedFluidTemperatureModel.class);
        airPc.enable(LaminarModel.class);
        airR.setPhysicsContinuum(airPc);

        PhysicsContinuum cellPc = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        cellPc.setPresentationName("cell-physics");
        cellPc.enable(ThreeDimensionalModel.class);
        cellPc.enable(SteadyModel.class);
        cellPc.enable(SolidModel.class);
        cellPc.enable(ConstantDensityModel.class);
        cellPc.enable(SegregatedSolidEnergyModel.class);
        cellR.setPhysicsContinuum(cellPc);

        // STAGE 2: real, cited 18650 solid material properties (K_CELL /
        // RHO_CELL / CP_CELL, computed above from literature -- see
        // README.md), replacing the Stage-1 default placeholder material.
        // The per-property classes live under star.flow (density) and
        // star.energy (specific heat, thermal conductivity), but all three
        // sit on the same underlying Material object exposed by the
        // continuum's SolidModel, and star.material.MaterialProperty itself
        // carries setConstant(double) -- no need to touch the Constant
        // method object directly.
        Material cellMaterial = cellPc.getModelManager().getModel(SolidModel.class).getMaterial();
        MaterialPropertyManager cellProps = cellMaterial.getMaterialProperties();
        cellProps.getMaterialProperty(ConstantDensityProperty.class).setConstant(RHO_CELL);
        cellProps.getMaterialProperty(SpecificHeatProperty.class).setConstant(CP_CELL);
        cellProps.getMaterialProperty(ThermalConductivityProperty.class).setConstant(K_CELL);

        // volumetric heat source in the cell (uniform, from real q''') --
        // this IS core to what the case demonstrates, so it's in the
        // skeleton pass, not deferred.
        cellR.getConditions().get(EnergyUserVolumeSourceOption.class)
                .setSelected(EnergyUserVolumeSourceOption.Type.VOLUMETRIC_HEAT_SOURCE);
        VolumetricHeatSourceProfile vhs = cellR.getValues().get(VolumetricHeatSourceProfile.class);
        vhs.getMethod(ConstantScalarProfileMethod.class).getQuantity().setValue(QGEN_VOL);

        // -- BCs --
        Boundary inlet = ab.get("inlet");
        inlet.setBoundaryType(InletBoundary.class);
        scal(inlet, VelocityMagnitudeProfile.class, U);
        scal(inlet, StaticTemperatureProfile.class, T_AMB);
        Boundary outlet = ab.get("outlet");
        outlet.setBoundaryType(PressureBoundary.class);
        scal(outlet, StaticTemperatureProfile.class, T_AMB);
        wallAdiabatic(ab.get("side1")); wallAdiabatic(ab.get("side2"));
        wallAdiabatic(ab.get("top"));   wallAdiabatic(ab.get("bottom"));
        wallAdiabatic(cb.get("cap_top")); wallAdiabatic(cb.get("cap_bottom"));

        // -- CHT interface: without this the cell has NO path for its heat
        // source to leave (top/bottom caps are adiabatic by design; the side
        // was left as a bare, unconnected wall) -- confirmed live: the energy
        // residual sat completely flat for 40+ iterations with this missing,
        // while flow residuals converged normally. This is what actually
        // couples the two regions.
        DirectBoundaryInterface cht = (DirectBoundaryInterface) sim.getInterfaceManager()
                .createDirectInterface(ab.get("cell_interface"), cb.get("cell_side"), "CHT Interface");
        sim.println(">> CHT interface created: " + cht.getPresentationName());

        sim.getSimulationIterator().run(800);
        sim.println(">> iters " + sim.getSimulationIterator().getCurrentIteration());

        // NB: createDirectInterface() makes the ORIGINAL Boundary handles
        // (ab.get("cell_interface"), cb.get("cell_side")) stale for
        // reporting -- use the interface's own live boundary handles instead
        // (same gotcha as the earlier CHT macro in the sibling
        // starccm-rag-assistant repo).
        Boundary liveCellSide = cht.getInterfaceBoundary1();
        report(cellR, liveCellSide, U, outPath);
        sim.println("=== cell18650_forced_air_cht done: U=" + U + " ===");
    }

    // ---- geometry helpers (proven pattern) ----
    SimpleCylinderPart cyl(MeshPartFactory f, String name, double r, double z0, double z1) {
        SimpleCylinderPart c = f.createNewCylinderPart(sim.get(SimulationPartManager.class));
        LabCoordinateSystem lab = sim.getCoordinateSystemManager().getLabCoordinateSystem();
        c.setCoordinateSystem(lab);
        c.getRadius().setUnits(m); c.getRadius().setValue(r);
        c.getStartCoordinate().setCoordinateSystem(lab);
        c.getStartCoordinate().setCoordinate(m, m, m, new DoubleVector(new double[]{0, 0, z0}));
        c.getEndCoordinate().setCoordinateSystem(lab);
        c.getEndCoordinate().setCoordinate(m, m, m, new DoubleVector(new double[]{0, 0, z1}));
        c.rebuildSimpleShapePart(); c.setPresentationName(name);
        return c;
    }
    void splitAll(MeshPart p, double ang) {
        PartSurface def = p.getPartSurfaces().iterator().next();
        Vector<PartSurface> v = new Vector<PartSurface>(); v.add(def);
        p.getPartSurfaceManager().splitPartSurfacesByAngle(v, ang);
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
    void meshAll(List<GeometryPart> parts, double base, int prismLayers) {
        List<String> meshers = prismLayers > 0
                ? Arrays.asList("star.resurfacer.ResurfacerAutoMesher",
                    "star.dualmesher.DualAutoMesher", "star.prismmesher.PrismAutoMesher")
                : Arrays.asList("star.resurfacer.ResurfacerAutoMesher", "star.dualmesher.DualAutoMesher");
        AutoMeshOperation op = sim.get(MeshOperationManager.class).createAutoMeshOperation(meshers, parts);
        op.getDefaultValues().get(BaseSize.class).setValueAndUnits(base, m);
        if (prismLayers > 0) op.getDefaultValues().get(star.prismmesher.NumPrismLayers.class).setNumLayers(prismLayers);
        op.execute();
        sim.println(">> meshed " + parts.get(0).getPresentationName() + " base=" + base);
    }
    double aaC(Boundary bd, FieldFunction f) {
        AreaAverageReport r = sim.getReportManager().createReport(AreaAverageReport.class);
        r.setPresentationName("c" + System.nanoTime()); r.getParts().setObjects(bd); r.setFieldFunction(f);
        return r.getReportMonitorValue();
    }
    // -- boundary auto-classification for the duct (rectangular, minus the
    //    cylindrical cell cavity): flat faces sort to the six duct extremes
    //    by centroid; the curved interior surface is whatever's left.
    Map<String, Boundary> classifyDuct(Region r) {
        PrimitiveFieldFunction pos = (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Position");
        Map<String, Boundary> out = new HashMap<String, Boundary>();
        for (Boundary bd : r.getBoundaryManager().getBoundaries()) {
            double xc = aaC(bd, pos.getComponentFunction(0));
            double yc = aaC(bd, pos.getComponentFunction(1));
            double zc = aaC(bd, pos.getComponentFunction(2));
            if (xc < X_UP + 0.01) out.put("inlet", bd);
            else if (xc > X_DOWN - 0.01) out.put("outlet", bd);
            else if (yc < -Y_HALF + 0.01) out.put("side1", bd);
            else if (yc > Y_HALF - 0.01) out.put("side2", bd);
            else if (zc < 0.005) out.put("bottom", bd);
            else if (zc > H_CELL - 0.005) out.put("top", bd);
            else out.put("cell_interface", bd);
        }
        out.get("inlet").setPresentationName("duct_inlet");
        out.get("outlet").setPresentationName("duct_outlet");
        out.get("cell_interface").setPresentationName("air_cell_interface");
        sim.println(">> duct: " + out.keySet());
        return out;
    }
    // cell solid: split by z-centroid, same "sort by height" trick as classifySolid
    Map<String, Boundary> classifyCellCaps(Region r) {
        PrimitiveFieldFunction pos = (PrimitiveFieldFunction) sim.getFieldFunctionManager().getFunction("Position");
        Map<String, Boundary> out = new HashMap<String, Boundary>();
        List<Boundary> bs = new ArrayList<Boundary>(r.getBoundaryManager().getBoundaries());
        Map<Boundary, Double> z = new HashMap<Boundary, Double>();
        for (Boundary bd : bs) z.put(bd, aaC(bd, pos.getComponentFunction(2)));
        Collections.sort(bs, new Comparator<Boundary>() {
            public int compare(Boundary a, Boundary b) { return Double.compare(z.get(a), z.get(b)); }
        });
        out.put("cap_bottom", bs.get(0));
        out.put("cell_side", bs.get(1));
        out.put("cap_top", bs.get(2));
        out.get("cap_bottom").setPresentationName("cell_cap_bottom");
        out.get("cell_side").setPresentationName("cell_side");
        out.get("cap_top").setPresentationName("cell_cap_top");
        sim.println(">> cell caps classified");
        return out;
    }

    void scal(Boundary bd, Class c, double v) {
        ScalarProfile sp = (ScalarProfile) bd.getValues().get(c);
        sp.setMethod(ConstantScalarProfileMethod.class);
        ((ConstantScalarProfileMethod) sp.getMethod()).getQuantity().setValue(v);
    }
    void wallAdiabatic(Boundary bd) {
        if (bd == null) return;
        try { bd.getConditions().get(WallThermalOption.class).setSelected(WallThermalOption.Type.ADIABATIC); }
        catch (Throwable t) {}
    }
    // TODO (Stage 2, physics refinement pass): set the cell solid's real,
    // cited k/rho/cp (K_CELL/RHO_CELL/CP_CELL) here once the exact
    // material-property-setter API is confirmed against this install's jars.

    void report(Region cellR, Boundary side, double U, String outPath) {
        FieldFunction T = ff("Temperature");
        double tSurfAvg = aaC(side, T);

        // -- CHECK 1: real conduction physics, closed-form validation --
        // Steady radial conduction, uniform volumetric generation, solid
        // cylinder: T_center - T_surface = q''' R^2 / (4k)  (Incropera &
        // Bergman, standard textbook result -- see README.md).
        double dT_analytical = QGEN_VOL * R_CELL * R_CELL / (4.0 * K_CELL);
        double T_center_analytical = tSurfAvg + dT_analytical;

        MaxReport mx = sim.getReportManager().createReport(MaxReport.class);
        mx.setPresentationName("Tmax" + System.nanoTime());
        mx.getParts().setObjects(cellR); mx.setFieldFunction(T);
        double T_center_cfd = mx.getReportMonitorValue();   // cell interior peak

        double pctErr = 100.0 * (T_center_cfd - T_center_analytical) / dT_analytical;

        double q_total = QGEN_VOL * Math.PI * R_CELL * R_CELL * H_CELL;

        StringBuilder csv = new StringBuilder();
        csv.append("U_ms,q_total_W,T_surface_avg_K,T_center_CFD_K,T_center_analytical_K,dT_analytical_K,pct_error_vs_analytical\n");
        csv.append(String.format("%.4f,%.5f,%.3f,%.3f,%.3f,%.3f,%.3f%n",
                U, q_total, tSurfAvg, T_center_cfd, T_center_analytical, dT_analytical, pctErr));
        write(outPath, csv.toString());
        sim.println(csv.toString());
    }
    FieldFunction ff(String n) {
        FieldFunction f = sim.getFieldFunctionManager().getFunction(n);
        if (f != null && !f.getPresentationName().contains("Select Function")) return f;
        for (Object o : sim.getFieldFunctionManager().getObjects())
            if (((FieldFunction) o).getPresentationName().equalsIgnoreCase(n)) return (FieldFunction) o;
        return null;
    }
    void write(String p, String s) {
        try { FileWriter w = new FileWriter(p); w.write(s); w.close(); sim.println(">> wrote " + p); }
        catch (IOException e) { sim.println("!! " + e); }
    }
}
