// ============================================================================
//  b30_add_radiation.java
//  B30 stage 2 -- adds S2S radiation to the ALREADY-CONVERGED natural-
//  convection CHT case (star@03000.sim), reusing the existing mesh and
//  warm-started flow/temperature fields rather than rebuilding from
//  scratch (~14 hours) or re-solving from a cold start.
//
//  Real finding (see docs/discrepancy_log.md): S2sModel/GrayThermal
//  RadiationModel could NOT be added to an already-built continuum whose
//  model list already includes Electromagnetism -- "no registration
//  found" regardless of what else was tried. Root cause: radiation must
//  be enabled in a specific slot in the model chain (right after the
//  energy/density models, BEFORE electromagnetism), found live via a
//  minimal single-block test. Since models can't be "inserted" into an
//  already-built continuum's history, this macro builds TWO NEW
//  continua with the correct order and reassigns the existing regions
//  to them (`ContinuumManager.setContinuum`), re-applying all boundary
//  conditions and material properties fresh (they do not carry over
//  automatically across a continuum reassignment).
//
//  Emissivity: `star.material.SurfaceMaterialSpecification` (a boundary
//  Value) -> `getMaterialManager().getSurfaceMaterials()` -> each
//  `SurfaceMaterial` (extends `Material`) has an ordinary
//  `MaterialProperties` group, same pattern as any bulk material, with
//  `star.radiation.common.EmissivitySurfaceProperty` -- found live via
//  javap, not guessed.
//
//  Run: starccmw.bat star@03000.sim -batch b30_add_radiation.java
// ============================================================================
import java.io.*;
import java.util.*;
import star.base.neo.*;
import star.base.report.*;
import star.common.*;
import star.material.*;
import star.flow.*;
import star.segregatedflow.SegregatedFlowModel;
import star.energy.*;
import star.segregatedenergy.SegregatedSolidEnergyModel;
import star.segregatedenergy.SegregatedFluidTemperatureModel;
import star.radiation.common.RadiationModel;
import star.radiation.s2s.S2sModel;
import star.radiation.common.GrayThermalRadiationModel;
import star.viewfactors.ViewfactorsCalculatorModel;
import star.viewfactors.PatchGeneratorModel;
import star.electromagnetism.electricpotential.*;
import star.electromagnetism.ohmicheating.*;
import star.electromagnetism.common.*;

public class b30_add_radiation extends StarMacro {

    static final double RHO_E_REF = 1.68e-8;
    static final double SIGMA_REF = 1.0 / RHO_E_REF;
    static final double K_CU = 401.0, RHO_CU = 8933.0, CP_CU = 385.0;
    static final double EMISSIVITY_CU = 0.78;   // oxidized copper, data/materials.csv, from B02

    static final double CURRENT_A = 400.0;
    static final double H_CONV_LEADS = 10.0;
    static final double T_AMBIENT_K = 293.15;

    static final double AIR_RHO = 1.1614, AIR_CP = 1007.0, AIR_K = 0.0263;
    static final double AIR_MU = 184.6e-7, AIR_BETA = 3.333e-3;

    static final String OUT_PATH = "results/processed/b30_results.csv";
    Simulation sim;

    public void execute() {
        sim = getActiveSimulation();
        sim.println("=== b30_add_radiation start (warm start from converged natural-convection sim) ===");

        // Found live: the first attempt's server connection was reset
        // right at iteration 3600 (a second-client "peek" connection
        // attempted mid-run may have destabilized the primary session --
        // see docs/discrepancy_log.md), losing ~9 hours of converged
        // progress since no autosave checkpoint existed near that
        // iteration. Force frequent autosave checkpoints now so a future
        // crash loses at most ~150 iterations, not the whole run.
        star.common.AutoSave autoSave = sim.getSimulationIterator().getAutoSave();
        autoSave.setAutoSaveBatch(true);
        autoSave.getStarUpdate().setUpdateFrequency(150);
        sim.println(">> autosave configured: batch=true, every 150 iterations");

        Region rLeft = sim.getRegionManager().getRegion("barLeft");
        Region rMid = sim.getRegionManager().getRegion("barMid");
        Region rRight = sim.getRegionManager().getRegion("barRight");
        Region rAir = sim.getRegionManager().getRegion("airBox");

        Boundary vinTerm = sim.getRegionManager().getRegion("barLeft").getBoundaryManager().getBoundary("Vin");
        Boundary ioutTerm = sim.getRegionManager().getRegion("barRight").getBoundaryManager().getBoundary("Iout");

        // ---- new continua, radiation in the correct slot ----
        PhysicsContinuum pcSolid = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pcSolid.setPresentationName("b30-solid-copper-rad");
        pcSolid.enable(star.metrics.ThreeDimensionalModel.class);
        pcSolid.enable(SteadyModel.class);
        pcSolid.enable(SolidModel.class);
        pcSolid.enable(SegregatedSolidEnergyModel.class);
        pcSolid.enable(ConstantDensityModel.class);
        pcSolid.enable(RadiationModel.class);
        pcSolid.enable(S2sModel.class);
        pcSolid.enable(GrayThermalRadiationModel.class);
        pcSolid.enable(ViewfactorsCalculatorModel.class);
        pcSolid.enable(PatchGeneratorModel.class);
        pcSolid.enable(ElectromagnetismModel.class);
        pcSolid.enable(ElectrodynamicsPotentialModel.class);
        pcSolid.enable(OhmicHeatingModel.class);
        sim.println(">> solid continuum (with radiation) built OK");

        PhysicsContinuum pcAir = sim.getContinuumManager().createContinuum(PhysicsContinuum.class);
        pcAir.setPresentationName("b30-air-natural-convection-rad");
        pcAir.enable(star.metrics.ThreeDimensionalModel.class);
        pcAir.enable(SteadyModel.class);
        pcAir.enable(SingleComponentGasModel.class);
        pcAir.enable(SegregatedFlowModel.class);
        pcAir.enable(ConstantDensityModel.class);
        pcAir.enable(LaminarModel.class);
        pcAir.enable(SegregatedFluidTemperatureModel.class);
        pcAir.enable(GravityModel.class);
        pcAir.enable(BoussinesqModel.class);
        pcAir.enable(RadiationModel.class);
        pcAir.enable(S2sModel.class);
        pcAir.enable(GrayThermalRadiationModel.class);
        pcAir.enable(ViewfactorsCalculatorModel.class);
        pcAir.enable(PatchGeneratorModel.class);
        sim.println(">> air continuum (with radiation) built OK");

        sim.getContinuumManager().setContinuum(Arrays.asList(rLeft, rMid, rRight), pcSolid);
        sim.getContinuumManager().setContinuum(Arrays.asList(rAir), pcAir);
        sim.println(">> regions reassigned to new continua");

        // ---- re-apply material properties (fresh Material objects on the new continua) ----
        Material cu = pcSolid.getModelManager().getModel(SolidModel.class).getMaterial();
        MaterialPropertyManager cuProps = cu.getMaterialProperties();
        cuProps.getMaterialProperty(ConstantDensityProperty.class).setConstant(RHO_CU);
        cuProps.getMaterialProperty(SpecificHeatProperty.class).setConstant(CP_CU);
        cuProps.getMaterialProperty(ThermalConductivityProperty.class).setConstant(K_CU);
        cuProps.getMaterialProperty(ElectricalConductivityProperty.class).setConstant(SIGMA_REF);

        Material air = pcAir.getModelManager().getModel(SingleComponentGasModel.class).getMaterial();
        MaterialPropertyManager airProps = air.getMaterialProperties();
        airProps.getMaterialProperty(ConstantDensityProperty.class).setConstant(AIR_RHO);
        airProps.getMaterialProperty(SpecificHeatProperty.class).setConstant(AIR_CP);
        airProps.getMaterialProperty(ThermalConductivityProperty.class).setConstant(AIR_K);
        airProps.getMaterialProperty(DynamicViscosityProperty.class).setConstant(AIR_MU);
        airProps.getMaterialProperty(ThermalExpansionProperty.class).setConstant(AIR_BETA);
        pcAir.getReferenceValues().get(Gravity.class).setComponents(0.0, 0.0, -9.81);
        sim.println(">> material properties + gravity re-applied");

        // ---- re-apply electrical terminals ----
        vinTerm.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_POTENTIAL);
        scal(vinTerm, ElectricPotentialProfile.class, 0.0);
        ioutTerm.getConditions().get(ElectrodynamicsPotentialWallOption.class)
                .setSelected(ElectrodynamicsPotentialWallOption.Type.ELECTRIC_CURRENT);
        scal(ioutTerm, ElectricCurrentProfile.class, CURRENT_A);

        // ---- re-apply barLeft/barRight fixed-h convection on their non-terminal, non-interface faces ----
        List<Boundary> leadFaces = new ArrayList<Boundary>();
        for (Boundary bd : rLeft.getBoundaryManager().getBoundaries())
            if (bd != vinTerm && !(bd instanceof InterfaceBoundary)) leadFaces.add(bd);
        for (Boundary bd : rRight.getBoundaryManager().getBoundaries())
            if (bd != ioutTerm && !(bd instanceof InterfaceBoundary)) leadFaces.add(bd);
        for (Boundary bd : leadFaces) {
            bd.getConditions().get(WallThermalOption.class).setSelected(WallThermalOption.Type.CONVECTION);
            scal(bd, HeatTransferCoefficientProfile.class, H_CONV_LEADS);
            scal(bd, AmbientTemperatureProfile.class, T_AMBIENT_K);
        }

        // ---- re-apply airBox outer-wall ambient BC ----
        List<Boundary> outerWalls = new ArrayList<Boundary>();
        for (Boundary bd : rAir.getBoundaryManager().getBoundaries())
            if (!(bd instanceof InterfaceBoundary)) outerWalls.add(bd);
        for (Boundary bd : outerWalls) {
            bd.setBoundaryType(PressureBoundary.class);
            scal(bd, StaticTemperatureProfile.class, T_AMBIENT_K);
        }
        sim.println(">> boundary conditions re-applied (" + leadFaces.size() + " lead faces, "
                + outerWalls.size() + " outer air walls)");

        // ---- emissivity on barMid's 4 CHT interface faces ----
        List<Boundary> midCht = new ArrayList<Boundary>();
        for (Boundary bd : rMid.getBoundaryManager().getBoundaries())
            if (bd instanceof InterfaceBoundary) midCht.add(bd);
        sim.println(">> found " + midCht.size() + " CHT boundaries on barMid for emissivity assignment");
        for (Boundary bd : midCht) {
            try {
                SurfaceMaterialSpecification sms = (SurfaceMaterialSpecification) bd.getValues().get(SurfaceMaterialSpecification.class);
                SurfaceMaterialManager smm = sms.getMaterialManager();
                Collection<SurfaceMaterial> surfMats = smm.getSurfaceMaterials();
                sim.println("   boundary '" + bd.getPresentationName() + "': " + surfMats.size() + " surface materials found");
                for (SurfaceMaterial sm : surfMats) {
                    for (Object o : sm.getMaterialProperties().getObjects()) {
                        sim.println("      surface material property: " + o.getClass().getName());
                    }
                    sm.getMaterialProperties().getMaterialProperty(star.radiation.common.EmissivitySurfaceProperty.class)
                            .setConstant(EMISSIVITY_CU);
                    sim.println("   set emissivity=" + EMISSIVITY_CU + " on surface material '" + sm.getPresentationName() + "'");
                }
            } catch (Exception e) {
                sim.println("   EMISSIVITY SETUP FAILED on '" + bd.getPresentationName() + "': " + e);
            }
        }

        // ---- solve: continue from the existing (warm) iteration count ----
        // NOTE: radiation's per-iteration cost turned out much higher than
        // the natural-convection-only stage (~0.5-1 iter/min vs ~2-6
        // iter/min) -- a fixed distant target (4500) was found live to
        // project 40+ hours. Using a smaller initial extension (600
        // iterations past the warm-started 3000) and checking the gate
        // before deciding whether to extend further, same iterative
        // pattern as B21-C's convergence fix.
        star.common.StepStoppingCriterion maxSteps = (star.common.StepStoppingCriterion)
                sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
        maxSteps.setMaximumNumberSteps(3650);
        sim.getSimulationIterator().run(3650);
        sim.println(">> iters " + sim.getSimulationIterator().getCurrentIteration());

        // ---- reports (fresh live references throughout -- no stale-reference bug this time) ----
        FieldFunction boundarySpecificCurrent = ff("BoundarySpecificElectricCurrent");
        double I_iout = surfaceIntegral(ioutTerm, boundarySpecificCurrent);
        double I_vin = surfaceIntegral(vinTerm, boundarySpecificCurrent);
        double currentImbalancePct = 100.0 * Math.abs(Math.abs(I_iout) - Math.abs(I_vin)) / Math.abs(I_iout);

        UserFieldFunction jDotE = sim.getFieldFunctionManager().createFieldFunction();
        jDotE.setPresentationName("JdotE_b30rad");
        jDotE.setFunctionName("JdotE_b30rad");
        jDotE.setDefinition(
                "$$ElectricCurrentDensity[0]*$$ElectricField[0]"
                + "+$$ElectricCurrentDensity[1]*$$ElectricField[1]"
                + "+$$ElectricCurrentDensity[2]*$$ElectricField[2]");
        VolumeIntegralReport pTotalReport = sim.getReportManager().createReport(VolumeIntegralReport.class);
        pTotalReport.setPresentationName("P_JdotE_total_rad");
        pTotalReport.getParts().setObjects(Arrays.asList(rLeft, rMid, rRight));
        pTotalReport.setFieldFunction(jDotE);
        double P_total = pTotalReport.getReportMonitorValue();

        FieldFunction wallHeatFlux = ff("BoundaryHeatFlux");
        double Q_leadConv = surfaceIntegralAll(leadFaces, wallHeatFlux);
        double Q_cht = surfaceIntegralAll(midCht, wallHeatFlux);
        double Q_totalOut = Q_leadConv + Q_cht;
        double heatBalancePct = 100.0 * Math.abs(Q_totalOut - P_total) / P_total;

        FieldFunction T_ff = ff("Temperature");
        double Tmax_solid = Math.max(Math.max(maxOf(rLeft, T_ff), maxOf(rMid, T_ff)), maxOf(rRight, T_ff));
        double Tmax_air = maxOf(rAir, T_ff);

        // Radiation-specific: net radiative heat leaving barMid's CHT
        // surfaces, via the field function created by GrayThermalRadiationModel
        // once S2S is active. Name discovered via a diagnostic dump if not
        // found under an expected name -- do not guess silently.
        Double Q_radiation = null;
        FieldFunction radFlux = ffOrNull("S2sRadiationHeatFlux");
        if (radFlux == null) radFlux = ffOrNull("RadiationHeatFlux");
        if (radFlux == null) radFlux = ffOrNull("S2SFlux");
        if (radFlux != null) {
            Q_radiation = surfaceIntegralAll(midCht, radFlux);
            sim.println(">> Q_radiation (net, via '" + radFlux.getFunctionName() + "') = " + Q_radiation + " W");
        } else {
            sim.println(">> WARNING: no radiation heat-flux field function found under any tried name -- "
                    + "dumping all field functions containing 'radiat' or 's2s' for manual identification:");
            for (Object o : sim.getFieldFunctionManager().getObjects()) {
                FieldFunction fo = (FieldFunction) o;
                String n = fo.getFunctionName().toLowerCase();
                if (n.contains("radiat") || n.contains("s2s")) sim.println("   candidate: " + fo.getFunctionName());
            }
        }

        boolean gateCurrent = currentImbalancePct < 0.5;
        boolean gateHeat = heatBalancePct < 5.0;
        String gateStatus = (gateCurrent && gateHeat) ? "PASSED" : "FAILED";

        sim.println("");
        sim.println("=== B30 RESULTS (natural convection + radiation) ===");
        sim.println("I_iout=" + I_iout + " I_vin=" + I_vin + " imbalance=" + currentImbalancePct + "%");
        sim.println("P_total(Joule)=" + P_total + " W");
        sim.println("Q_leadConv=" + Q_leadConv + " W, Q_cht(barMid->air, includes radiation implicitly via energy eqn)="
                + Q_cht + " W, Q_totalOut=" + Q_totalOut + " W, heat balance=" + heatBalancePct + "%");
        if (Q_radiation != null) sim.println("Q_radiation(explicit, subset of Q_cht)=" + Q_radiation + " W");
        sim.println("Tmax_solid=" + Tmax_solid + " K (" + (Tmax_solid - 273.15) + " C), Tmax_air=" + Tmax_air + " K");
        sim.println("GATE current(<0.5%)=" + gateCurrent + " heat(<5%)=" + gateHeat + " -> " + gateStatus);

        StringBuilder csv = new StringBuilder();
        csv.append("case_id,current_A,I_iout_A,I_vin_A,current_imbalance_pct,P_total_W,Q_leadConv_W,Q_cht_W,"
                + "Q_radiation_W,Q_totalOut_W,heat_balance_pct,Tmax_solid_K,Tmax_air_K,gate_status\n");
        csv.append(String.format("b30_natural_convection_with_radiation,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%.6f,%.4f,%.4f,%.4f,%s%n",
                CURRENT_A, I_iout, I_vin, currentImbalancePct, P_total, Q_leadConv, Q_cht,
                (Q_radiation == null ? "PENDING" : String.format("%.6f", Q_radiation)),
                Q_totalOut, heatBalancePct, Tmax_solid, Tmax_air, gateStatus));
        appendCsv(OUT_PATH, csv.toString());
        sim.println("=== b30_add_radiation done ===");
    }

    double maxOf(Region r, FieldFunction ff) {
        MaxReport mr = sim.getReportManager().createReport(MaxReport.class);
        mr.setPresentationName("max" + System.nanoTime());
        mr.getParts().setObjects(Arrays.asList(r));
        mr.setFieldFunction(ff);
        return mr.getReportMonitorValue();
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
    FieldFunction ffOrNull(String n) {
        for (Object o : sim.getFieldFunctionManager().getObjects()) {
            FieldFunction ffo = (FieldFunction) o;
            if (ffo.getFunctionName().equalsIgnoreCase(n)) return ffo;
        }
        return null;
    }
    void appendCsv(String p, String s) {
        try {
            new File(p).getParentFile().mkdirs();
            FileWriter w = new FileWriter(p); w.write(s); w.close();
            sim.println(">> wrote " + p);
        } catch (IOException e) { sim.println("!! " + e); }
    }
}
