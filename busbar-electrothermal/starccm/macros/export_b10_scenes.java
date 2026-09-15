import star.base.neo.DoubleVector;
import star.common.*;
import star.vis.*;
import java.util.*;

public class export_b10_scenes extends StarMacro {
    static final double CX = 0.15, CY = 0.0, CZ = 0.0025;  // bar center: L=0.3,W=0.04,T=0.005

    public void execute() {
        Simulation sim = getActiveSimulation();
        Region r = sim.getRegionManager().getRegion("straightBar");
        List<Boundary> allWalls = new ArrayList<Boundary>(r.getBoundaryManager().getBoundaries());

        exportScalarScene(sim, sim.getFieldFunctionManager().getFunction("ElectricPotential"),
                allWalls, "results/figures/b10_potential.png");

        FieldFunction J = null;
        for (Object o : sim.getFieldFunctionManager().getObjects()) {
            FieldFunction f = (FieldFunction) o;
            if (f.getFunctionName().equalsIgnoreCase("ElectricCurrentDensity")) J = f;
        }
        exportScalarScene(sim, J.getMagnitudeFunction(), allWalls, "results/figures/b10_current_density.png");

        exportGeometryScene(sim, allWalls, "results/figures/b10_geometry.png");

        sim.println(">> exported B10 scenes");
    }

    void exportGeometryScene(Simulation sim, List<Boundary> parts, String outPath) {
        Scene scene = sim.getSceneManager().createScene();
        scene.setPresentationName(outPath);
        PartDisplayer pd = scene.getDisplayerManager().createPartDisplayer("Geometry");
        pd.getParts().setObjects(parts);
        pd.setColorMode(PartColorMode.PART);
        scene.open(true);
        frameCamera(sim, scene);
        scene.printAndWait(outPath, 1, 1400, 900);
        sim.println(">> wrote " + outPath);
    }

    void exportScalarScene(Simulation sim, FieldFunction field, List<Boundary> parts, String outPath) {
        Scene scene = sim.getSceneManager().createScene();
        scene.setPresentationName(outPath);
        ScalarDisplayer sd = scene.getDisplayerManager().createScalarDisplayer("Scalar");
        sd.getParts().setObjects(parts);
        sd.getScalarDisplayQuantity().setFieldFunction(field);
        scene.open(true);
        frameCamera(sim, scene);
        scene.printAndWait(outPath, 1, 1400, 900);
        sim.println(">> wrote " + outPath);
    }

    void frameCamera(Simulation sim, Scene scene) {
        VisView view = scene.getCurrentView();
        view.setProjectionModeToParallel();
        DoubleVector focal = new DoubleVector(new double[]{CX, CY, CZ});
        DoubleVector pos = new DoubleVector(new double[]{CX, CY, CZ + 0.5});
        DoubleVector up = new DoubleVector(new double[]{0, 1, 0});
        view.setInput(focal, pos, up, 0.09, VisProjectionMode.PARALLEL.getValue(),
                sim.getCoordinateSystemManager().getLabCoordinateSystem(), true);
    }
}
