// ============================================================================
//  b30_split_radiation_convection.java
//  Recovers the explicit radiation-vs-convection heat split from the
//  ALREADY-CONVERGED star@03650.sim (B30 with radiation), per the
//  roadmap's B30 "Outputs" requirement ("heat split among convection,
//  radiation, end conduction, and interfaces") -- without re-solving.
//
//  Run: starccmw.bat star@03650.sim -batch b30_split_radiation_convection.java
// ============================================================================
import java.io.*;
import java.util.*;
import star.base.report.*;
import star.common.*;

public class b30_split_radiation_convection extends StarMacro {

    static final double P_TOTAL_W = 4.031999999999993;
    static final double Q_LEADCONV_W = 0.49401236763239753;
    static final double Q_CHT_TOTAL_W = 3.72679802208823;

    Simulation sim;

    public void execute() {
        sim = getActiveSimulation();
        sim.println("=== b30_split_radiation_convection start ===");

        Region rMid = sim.getRegionManager().getRegion("barMid");
        List<Boundary> midCht = new ArrayList<Boundary>();
        for (Boundary bd : rMid.getBoundaryManager().getBoundaries())
            if (bd instanceof InterfaceBoundary && bd.getPresentationName().contains("CHT_")) midCht.add(bd);
        sim.println(">> found " + midCht.size() + " CHT boundaries");

        FieldFunction radFlux = ff("BoundaryRadiationHeatFlux");
        double Q_radiation = surfaceIntegralAll(midCht, radFlux);

        double Q_convection = Q_CHT_TOTAL_W - Q_radiation;
        double radiationFractionOfTotalPct = 100.0 * Q_radiation / P_TOTAL_W;
        double convectionFractionOfTotalPct = 100.0 * (Q_convection + Q_LEADCONV_W) / P_TOTAL_W;

        sim.println("Q_radiation (via BoundaryRadiationHeatFlux, barMid CHT faces) = " + Q_radiation + " W");
        sim.println("Q_convection (= Q_cht_total - Q_radiation) = " + Q_convection + " W");
        sim.println("Q_leadConv (barLeft/barRight fixed-h) = " + Q_LEADCONV_W + " W");
        sim.println("Split of P_total=" + P_TOTAL_W + " W: radiation=" + radiationFractionOfTotalPct
                + "%, convection(barMid+leads)=" + convectionFractionOfTotalPct + "%");

        StringBuilder csv = new StringBuilder();
        csv.append("Q_radiation_W,Q_convection_barMid_W,Q_leadConv_W,radiation_fraction_pct,convection_fraction_pct\n");
        csv.append(String.format("%.6f,%.6f,%.6f,%.4f,%.4f%n",
                Q_radiation, Q_convection, Q_LEADCONV_W, radiationFractionOfTotalPct, convectionFractionOfTotalPct));
        write("results/processed/b30_radiation_split.csv", csv.toString());
        sim.println("=== b30_split_radiation_convection done ===");
    }

    double surfaceIntegralAll(List<Boundary> bds, FieldFunction fn) {
        SurfaceIntegralReport r = sim.getReportManager().createReport(SurfaceIntegralReport.class);
        r.setPresentationName("sia_split" + System.nanoTime()); r.getParts().setObjects(bds); r.setFieldFunction(fn);
        return r.getReportMonitorValue();
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
