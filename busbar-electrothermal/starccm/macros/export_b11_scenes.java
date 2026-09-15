import star.base.neo.DoubleVector;
import star.common.*;
import star.vis.*;
import java.util.*;

public class export_b11_scenes extends StarMacro {
    static final double CX = 0.15, CY = 0.0, CZ = 0.0025;

    public void execute() {
        Simulation sim = getActiveSimulation();
        Region r = sim.getRegionManager().getRegion("straightBar");
        List<Boundary> allWalls = new ArrayList<Boundary>(r.getBoundaryManager().getBoundaries());
        FieldFunction T = sim.getFieldFunctionManager().getFunction("Temperature");
        exportScalarScene(sim, T, allWalls, "results/figures/b11_temperature.png");
        sim.println(">> exported B11 scenes");
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
