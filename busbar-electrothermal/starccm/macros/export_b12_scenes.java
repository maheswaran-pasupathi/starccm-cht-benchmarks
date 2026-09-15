import star.base.neo.DoubleVector;
import star.common.*;
import star.vis.*;
import java.util.*;

public class export_b12_scenes extends StarMacro {
    static final double CX = 0.15, CY = 0.0, CZ = 0.0025;

    public void execute() {
        Simulation sim = getActiveSimulation();
        exportFor(sim, "bar_v1_constant_properties", "v1");
        exportFor(sim, "bar_v2_temperature_dependent_resistivity", "v2");
        sim.println(">> exported B12 scenes");
    }

    void exportFor(Simulation sim, String regionName, String tag) {
        Region r = sim.getRegionManager().getRegion(regionName);
        List<Boundary> allWalls = new ArrayList<Boundary>(r.getBoundaryManager().getBoundaries());
        FieldFunction T = sim.getFieldFunctionManager().getFunction("Temperature");
        exportScalarScene(sim, T, allWalls, "results/figures/b12_" + tag + "_temperature.png");
        FieldFunction J = null;
        for (Object o : sim.getFieldFunctionManager().getObjects()) {
            FieldFunction f = (FieldFunction) o;
            if (f.getFunctionName().equalsIgnoreCase("ElectricCurrentDensity")) J = f;
        }
        exportScalarScene(sim, J.getMagnitudeFunction(), allWalls, "results/figures/b12_" + tag + "_current_density.png");
    }

    void exportScalarScene(Simulation sim, FieldFunction field, List<Boundary> parts, String outPath) {
        Scene scene = sim.getSceneManager().createScene();
        scene.setPresentationName(outPath);
        ScalarDisplayer sd = scene.getDisplayerManager().createScalarDisplayer("Scalar");
        sd.getParts().setObjects(parts);
        sd.getScalarDisplayQuantity().setFieldFunction(field);
        scene.open(true);
        VisView view = scene.getCurrentView();
        view.setProjectionModeToParallel();
        view.setInput(new DoubleVector(new double[]{CX, CY, CZ}), new DoubleVector(new double[]{CX, CY, CZ + 0.5}),
                new DoubleVector(new double[]{0, 1, 0}), 0.09, VisProjectionMode.PARALLEL.getValue(),
                sim.getCoordinateSystemManager().getLabCoordinateSystem(), true);
        scene.printAndWait(outPath, 1, 1400, 900);
        sim.println(">> wrote " + outPath);
    }
}
