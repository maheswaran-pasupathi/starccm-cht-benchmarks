// ============================================================================
//  b30_fix_heat_balance.java
//  Recovers B30's Q_cht/heat-balance metric from the ALREADY-CONVERGED
//  saved sim (star@03000.sim) without re-running the ~14-hour solve.
//  The original run's Q_cht read NaN because it used a stale pre-
//  createDirectInterface() Boundary reference (same lesson as B22 --
//  see docs/discrepancy_log.md). This macro re-fetches barMid's 4 CHT
//  interface boundaries FRESH from the loaded (already-solved) state and
//  recomputes the heat-flux surface integral correctly.
//
//  Run: starccmw.bat star@03000.sim -batch b30_fix_heat_balance.java
//  (load the existing sim, do NOT use -new)
// ============================================================================
import java.io.*;
import java.util.*;
import star.base.report.*;
import star.common.*;

public class b30_fix_heat_balance extends StarMacro {

    static final double P_TOTAL_W = 4.0319999999999885;      // from the original run's own log
    static final double Q_LEADCONV_W = 0.5321756243990245;   // from the original run's own log
    static final double CURRENT_A = 400.0;
    static final double I_IOUT_A = -399.99999999999704;
    static final double I_VIN_A = 399.99999999999994;
    static final double TMAX_SOLID_K = 308.80755875628535;
    static final double TMAX_AIR_K = 303.7010771429767;

    static final String OUT_PATH = "results/processed/b30_results.csv";
    Simulation sim;

    public void execute() {
        sim = getActiveSimulation();
        sim.println("=== b30_fix_heat_balance start (operating on already-converged loaded sim) ===");

        Region rMid = sim.getRegionManager().getRegion("barMid");
        List<Boundary> midCht = new ArrayList<Boundary>();
        for (Boundary bd : rMid.getBoundaryManager().getBoundaries()) {
            if (bd instanceof InterfaceBoundary && bd.getPresentationName().contains("CHT_")) {
                midCht.add(bd);
                sim.println("   found live CHT boundary: " + bd.getPresentationName());
            }
        }
        if (midCht.size() != 4) {
            throw new IllegalStateException("Expected 4 CHT boundaries on barMid, found " + midCht.size());
        }

        FieldFunction wallHeatFlux = ff("BoundaryHeatFlux");
        double Q_cht = surfaceIntegralAll(midCht, wallHeatFlux);
        double Q_totalOut = Q_LEADCONV_W + Q_cht;
        double heatBalancePct = 100.0 * Math.abs(Q_totalOut - P_TOTAL_W) / P_TOTAL_W;

        double currentImbalancePct = 100.0 * Math.abs(Math.abs(I_IOUT_A) - Math.abs(I_VIN_A)) / Math.abs(I_IOUT_A);
        boolean gateCurrent = currentImbalancePct < 0.5;
        boolean gateHeat = heatBalancePct < 5.0;
        String gateStatus = (gateCurrent && gateHeat) ? "PASSED" : "FAILED";

        sim.println("");
        sim.println("=== B30 RESULTS (recomputed Q_cht from live interface refs) ===");
        sim.println("Q_cht(barMid->air)=" + Q_cht + " W (was NaN in the original run due to a stale reference)");
        sim.println("Q_totalOut=" + Q_totalOut + " W vs P_total=" + P_TOTAL_W + " W, heat balance=" + heatBalancePct + "%");
        sim.println("GATE current(<0.5%)=" + gateCurrent + " heat(<5%)=" + gateHeat + " -> " + gateStatus);

        StringBuilder csv = new StringBuilder();
        csv.append("case_id,current_A,I_iout_A,I_vin_A,current_imbalance_pct,P_total_W,Q_leadConv_W,Q_cht_W,"
                + "Q_totalOut_W,heat_balance_pct,Tmax_solid_K,Tmax_air_K,gate_status\n");
        csv.append(String.format("b30_natural_convection_no_radiation,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.4f,%s%n",
                CURRENT_A, I_IOUT_A, I_VIN_A, currentImbalancePct, P_TOTAL_W, Q_LEADCONV_W, Q_cht, Q_totalOut,
                heatBalancePct, TMAX_SOLID_K, TMAX_AIR_K, gateStatus));
        write(OUT_PATH, csv.toString());
        sim.println("=== b30_fix_heat_balance done ===");
    }

    double surfaceIntegralAll(List<Boundary> bds, FieldFunction fn) {
        SurfaceIntegralReport r = sim.getReportManager().createReport(SurfaceIntegralReport.class);
        r.setPresentationName("sia_fix" + System.nanoTime()); r.getParts().setObjects(bds); r.setFieldFunction(fn);
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
