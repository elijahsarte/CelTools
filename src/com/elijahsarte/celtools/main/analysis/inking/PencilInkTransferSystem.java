package com.elijahsarte.celtools.main.analysis.inking;

import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.util.MathEx;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;
import com.elijahsarte.celtools.mainex.DebuggerEx;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class PencilInkTransferSystem {

    private static final boolean START_IN_SIMPLE_MODE = true;
    private static final boolean USE_STATIC_OUTPUT_STRENGTHS = false;
    private static final boolean USE_STATIC_LOSS_WEIGHTS = false;


    /*
     * How strongly each learned delta should actually affect the synthesized result.
     * Lower blotch heavily, raise border blend most.
     */
    private static final double THICKNESS_STRENGTH = 1.10;
    private static final double BORDER_BLEND_STRENGTH = 1.85;
    private static final double OPACITY_STRENGTH = 1.20;
    private static final double BLOTCH_STRENGTH = 0.18;

    /*
     * How strongly each output contributes to fitting / calibration loss.
     * Border blend is intentionally weighted highest.
     */
    private static final double THICKNESS_LOSS_WEIGHT = 1.10;
    private static final double BORDER_BLEND_LOSS_WEIGHT = 2.20;
    private static final double OPACITY_LOSS_WEIGHT = 1.25;
    private static final double BLOTCH_LOSS_WEIGHT = 0.15;

    private static final double THICKNESS_ESTIMATE_SCALE = 0.82;
    private static final double THICKNESS_RENDER_RADIUS_SCALE = 0.42;
    private static final double BORDER_BLEND_RENDER_FEATHER_SCALE = 0.9;

    private static final double BORDER_BLEND_MIN_OPACITY_THRESHOLD = 0.02;
    private static final double BORDER_BLEND_MIN_EDGE_DARKNESS_THRESHOLD = 1.0e-6;
    private static final double BORDER_BLEND_OUTSIDE_DARKNESS_SCALE = 1.0;
    private static final int DEFAULT_BORDER_BLEND_OUTWARD_SCAN_DISTANCE = 15;

    private static final boolean BORDER_BLEND_SAMPLE_FROM_INK_ONLY = true;
    private static final double BORDER_BLEND_WHITE_BACKGROUND_THRESHOLD = 0.018;
    private static final double BORDER_BLEND_MIN_EDGE_DARKNESS = 0.010;
    private static final int BORDER_BLEND_REQUIRED_WHITE_RUN = 2;
    private static final int BORDER_BLEND_EXTRA_SCAN_STEPS = 4;
    private static final double BORDER_BLEND_NEAR_EDGE_WEIGHT = 0.55;
    private static final double BORDER_BLEND_WIDTH_WEIGHT = 0.45;

    private static final boolean BORDER_BLEND_SCAN_BOTH_SIDES = true;
    private static final boolean BORDER_BLEND_COMBINE_BY_MAX = true;

    /*
     * Which features appear in SIMPLE mode.
     * SIMPLE also includes a few strongest cross terms automatically.
     */
    private static final int SIMPLE_EXTRA_CROSS_TERMS = 6;
    private static final Set<String> SIMPLE_FEATURES = new LinkedHashSet<>(Arrays.asList(
            "Bias",
            "Location Along Contour",
            "Orientation",
            "Derivative",
            "End Stop",
            "Current Thickness",
            "Current Border Blend",
            "Current Opacity",
            "Current Blotch"
    ));

    private static final boolean STRICT_SCAN_CROP_TO_POINT_BOUNDS = true;
    private static final boolean SHOW_SIMULATED_BUSY_INDICATOR = true;
    private static final boolean DISABLE_INTER_FACTOR_INFLUENCE = true;

    private static final boolean ENABLE_DEBUG_VISUALIZATION = true;
    private static final int DEBUG_MAX_SAMPLES = 120;
    private static final boolean DEBUG_USE_NON_BLOCKING_WINDOWS = true;


    /*
     * Alignment mode for offset correction between pencil and ink canvases.
     * CENTER is usually best if the same drawing is shifted on the canvas.
     */
    private enum AlignmentMode {
        TOP_LEFT,
        CENTER
    }

    private static final AlignmentMode LOCAL_ALIGNMENT_MODE = AlignmentMode.CENTER;


    private PencilInkTransferSystem() {
    }

    public enum InputVariable {
        LOCATION("Location Along Contour"),
        ORIENTATION("Orientation"),
        DERIVATIVE("Derivative"),
        END_STOP("End Stop");

        private final String display;

        InputVariable(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }
    }

    public enum OutputVariable {
        THICKNESS("Thickness"),
        BORDER_BLEND("Border Blend"),
        OPACITY("Opacity"),
        BLOTCH("Blotch");

        private final String display;

        OutputVariable(String display) {
            this.display = display;
        }

        public String display() {
            return display;
        }
    }

    public static final class VariableToggles {
        private final EnumMap<InputVariable, Boolean> inputEnabled = new EnumMap<>(InputVariable.class);
        private final EnumMap<OutputVariable, Boolean> outputEnabled = new EnumMap<>(OutputVariable.class);

        public boolean includeCrossTerms = true;
        public boolean includeCurrentOutputsAsFeatures = true;

        public VariableToggles() {
            for (InputVariable variable : InputVariable.values()) {
                inputEnabled.put(variable, true);
            }
            for (OutputVariable variable : OutputVariable.values()) {
                outputEnabled.put(variable, true);
            }
        }

        public static VariableToggles allEnabled() {
            return new VariableToggles();
        }

        public VariableToggles set(InputVariable variable, boolean enabled) {
            inputEnabled.put(variable, enabled);
            return this;
        }

        public VariableToggles set(OutputVariable variable, boolean enabled) {
            outputEnabled.put(variable, enabled);
            return this;
        }

        public boolean enabled(InputVariable variable) {
            return inputEnabled.getOrDefault(variable, false);
        }

        public boolean enabled(OutputVariable variable) {
            return outputEnabled.getOrDefault(variable, false);
        }

        public List<InputVariable> activeInputs() {
            ArrayList<InputVariable> out = new ArrayList<>();
            for (InputVariable variable : InputVariable.values()) {
                if (enabled(variable)) {
                    out.add(variable);
                }
            }
            return out;
        }

        public List<OutputVariable> activeOutputs() {
            ArrayList<OutputVariable> out = new ArrayList<>();
            for (OutputVariable variable : OutputVariable.values()) {
                if (enabled(variable)) {
                    out.add(variable);
                }
            }
            return out;
        }

        public VariableToggles copy() {
            VariableToggles copy = new VariableToggles();
            copy.inputEnabled.clear();
            copy.outputEnabled.clear();
            copy.inputEnabled.putAll(this.inputEnabled);
            copy.outputEnabled.putAll(this.outputEnabled);
            copy.includeCrossTerms = this.includeCrossTerms;
            copy.includeCurrentOutputsAsFeatures = this.includeCurrentOutputsAsFeatures;
            return copy;
        }
    }

    public static final class BuildOptions {
        public int sampleCount = 0;
        public int contourStride = 1;
        public int opacityProbeRadius = 2;
        public int outwardBlendProbe = DEFAULT_BORDER_BLEND_OUTWARD_SCAN_DISTANCE;
        public int localAverageRadius = 4;
        public int maxThickness = 64;
        public double maxBlotchMagnitude = 16.0;

        public boolean showLaunchConfigurationDialog = true;

        public boolean useSmoothedPencilAnchors = true;
        public int pencilAnchorAveragingRadius = 6;
        public double pencilAnchorBlend = 0.82;

        public boolean useSmoothedPencilContourPoint = true;
        public int pencilContourAveragingRadius = 3;

        public boolean averageThicknessAndOpacityMeasurements = true;
        public int measurementAveragingRadius = 4;

        public BuildOptions copy() {
            BuildOptions copy = new BuildOptions();
            copy.sampleCount = this.sampleCount;
            copy.contourStride = this.contourStride;
            copy.opacityProbeRadius = this.opacityProbeRadius;
            copy.outwardBlendProbe = this.outwardBlendProbe;
            copy.localAverageRadius = this.localAverageRadius;
            copy.maxThickness = this.maxThickness;
            copy.maxBlotchMagnitude = this.maxBlotchMagnitude;

            copy.showLaunchConfigurationDialog = this.showLaunchConfigurationDialog;

            copy.useSmoothedPencilAnchors = this.useSmoothedPencilAnchors;
            copy.pencilAnchorAveragingRadius = this.pencilAnchorAveragingRadius;
            copy.pencilAnchorBlend = this.pencilAnchorBlend;

            copy.useSmoothedPencilContourPoint = this.useSmoothedPencilContourPoint;
            copy.pencilContourAveragingRadius = this.pencilContourAveragingRadius;

            copy.averageThicknessAndOpacityMeasurements = this.averageThicknessAndOpacityMeasurements;
            copy.measurementAveragingRadius = this.measurementAveragingRadius;
            return copy;
        }
    }
    public static final class TrainingOptions {
        public int epochs = 1400;
        public double learningRate = 0.0125;
        public double regularization = 0.0004;

        public TrainingOptions copy() {
            TrainingOptions copy = new TrainingOptions();
            copy.epochs = this.epochs;
            copy.learningRate = this.learningRate;
            copy.regularization = this.regularization;
            return copy;
        }
    }

    public static final class FeatureSample {
        public final int contourIndex;
        public final double location;
        public final Point contourPoint;
        public final Point anchorPoint;
        public final Point2D.Double tangent;
        public final Point2D.Double inwardNormal;
        public final Point2D.Double outwardNormal;

        private static final int IN_LOCATION = 0;
        private static final int IN_ORIENTATION = 1;
        private static final int IN_DERIVATIVE = 2;
        private static final int IN_END_STOP = 3;

        private static final int OUT_THICKNESS = 0;
        private static final int OUT_BORDER_BLEND = 1;
        private static final int OUT_OPACITY = 2;
        private static final int OUT_BLOTCH = 3;

        private final double[] inputs = new double[4];
        private final double[] outputs = new double[4];
        private final Map<String, Double> diagnostics = new HashMap<>();

        public FeatureSample(
                int contourIndex,
                double location,
                Point contourPoint,
                Point anchorPoint,
                Point2D.Double tangent,
                Point2D.Double inwardNormal,
                Point2D.Double outwardNormal
        ) {
            this.contourIndex = contourIndex;
            this.location = location;
            this.contourPoint = new Point(contourPoint);
            this.anchorPoint = new Point(anchorPoint);
            this.tangent = new Point2D.Double(tangent.x, tangent.y);
            this.inwardNormal = new Point2D.Double(inwardNormal.x, inwardNormal.y);
            this.outwardNormal = new Point2D.Double(outwardNormal.x, outwardNormal.y);
        }

        private static int inputIndex(InputVariable variable) {
            return switch (variable) {
                case LOCATION -> IN_LOCATION;
                case ORIENTATION -> IN_ORIENTATION;
                case DERIVATIVE -> IN_DERIVATIVE;
                case END_STOP -> IN_END_STOP;
            };
        }

        private static int outputIndex(OutputVariable variable) {
            return switch (variable) {
                case THICKNESS -> OUT_THICKNESS;
                case BORDER_BLEND -> OUT_BORDER_BLEND;
                case OPACITY -> OUT_OPACITY;
                case BLOTCH -> OUT_BLOTCH;
            };
        }

        public FeatureSample setInput(InputVariable variable, double value) {
            inputs[inputIndex(variable)] = finite(value);
            return this;
        }

        public FeatureSample setOutput(OutputVariable variable, double value) {
            outputs[outputIndex(variable)] = finite(value);
            return this;
        }

        public FeatureSample setDiagnostic(String key, double value) {
            diagnostics.put(key, finite(value));
            return this;
        }

        public double input(InputVariable variable) {
            return inputs[inputIndex(variable)];
        }

        public double output(OutputVariable variable) {
            return outputs[outputIndex(variable)];
        }

        public double diagnostic(String key) {
            return diagnostics.getOrDefault(key, 0.0);
        }

        public double thickness() {
            return outputs[OUT_THICKNESS];
        }

        public double borderBlend() {
            return outputs[OUT_BORDER_BLEND];
        }

        public double opacity() {
            return outputs[OUT_OPACITY];
        }

        public double blotch() {
            return outputs[OUT_BLOTCH];
        }

        public FeatureSample copy() {
            FeatureSample copy = new FeatureSample(
                    contourIndex,
                    location,
                    contourPoint,
                    anchorPoint,
                    tangent,
                    inwardNormal,
                    outwardNormal
            );
            System.arraycopy(this.inputs, 0, copy.inputs, 0, this.inputs.length);
            System.arraycopy(this.outputs, 0, copy.outputs, 0, this.outputs.length);
            copy.diagnostics.putAll(this.diagnostics);
            return copy;
        }
    }

    public static final class AppearanceMap {
        private final String name;
        private final ShapeContour contour;
        private final List<FeatureSample> samples;
        private final VariableToggles toggles;
        private final NavigableMap<Double, FeatureSample> byLocation = new TreeMap<>();
        private final EnumMap<OutputVariable, Double> averages = new EnumMap<>(OutputVariable.class);

        public AppearanceMap(String name, ShapeContour contour, List<FeatureSample> samples, VariableToggles toggles) {
            this.name = name;
            this.contour = contour;
            this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
            this.toggles = toggles.copy();

            for (FeatureSample sample : this.samples) {
                double key = sample.location + (sample.contourIndex * 1.0e-12);
                byLocation.put(key, sample);
            }

            recomputeAverages();
        }

        public String name() {
            return name;
        }

        public ShapeContour contour() {
            return contour;
        }

        public List<FeatureSample> samples() {
            return samples;
        }

        public int size() {
            return samples.size();
        }

        public VariableToggles toggles() {
            return toggles.copy();
        }

        public double average(OutputVariable variable) {
            return averages.getOrDefault(variable, 0.0);
        }

        public FeatureSample nearestByLocation(double location) {
            Map.Entry<Double, FeatureSample> floor = byLocation.floorEntry(location);
            Map.Entry<Double, FeatureSample> ceil = byLocation.ceilingEntry(location);

            if (floor == null) {
                return ceil == null ? null : ceil.getValue();
            }
            if (ceil == null) {
                return floor.getValue();
            }

            double df = Math.abs(location - floor.getKey());
            double dc = Math.abs(location - ceil.getKey());
            return df <= dc ? floor.getValue() : ceil.getValue();
        }

        private void recomputeAverages() {
            double thicknessSum = 0.0;
            double borderBlendSum = 0.0;
            double opacitySum = 0.0;
            double blotchSum = 0.0;
            int n = samples.size();

            if (n == 0) {
                averages.put(OutputVariable.THICKNESS, 0.0);
                averages.put(OutputVariable.BORDER_BLEND, 0.0);
                averages.put(OutputVariable.OPACITY, 0.0);
                averages.put(OutputVariable.BLOTCH, 0.0);
                return;
            }

            for (FeatureSample sample : samples) {
                thicknessSum += sample.thickness();
                borderBlendSum += sample.borderBlend();
                opacitySum += sample.opacity();
                blotchSum += sample.blotch();
            }

            double avgThickness = thicknessSum / n;
            averages.put(OutputVariable.THICKNESS, avgThickness);
            averages.put(OutputVariable.BORDER_BLEND, borderBlendSum / n);
            averages.put(OutputVariable.OPACITY, opacitySum / n);
            averages.put(OutputVariable.BLOTCH, blotchSum / n);

            for (FeatureSample sample : samples) {
                sample.setDiagnostic("thicknessDeviation", sample.thickness() - avgThickness);
            }
        }
    }

    private static final class FeatureVectorizer {
        private final VariableToggles toggles;
        private final List<String> baseNames = new ArrayList<>();
        private final List<ToDoubleFunction<FeatureSample>> baseExtractors = new ArrayList<>();
        private final List<int[]> crossTerms = new ArrayList<>();
        private final List<String> names = new ArrayList<>();

        private double[] means;
        private double[] stds;

        private FeatureVectorizer(VariableToggles toggles) {
            this.toggles = toggles.copy();
            buildBaseFeatures();
            buildCrossTerms();
            buildNames();
            this.means = new double[featureCount()];
            this.stds = new double[featureCount()];
            Arrays.fill(stds, 1.0);
        }

        private void buildBaseFeatures() {
            if (toggles.enabled(InputVariable.LOCATION)) {
                baseNames.add(InputVariable.LOCATION.display());
                baseExtractors.add(sample -> sample.input(InputVariable.LOCATION));
            }
            if (toggles.enabled(InputVariable.ORIENTATION)) {
                baseNames.add(InputVariable.ORIENTATION.display());
                baseExtractors.add(sample -> sample.input(InputVariable.ORIENTATION) / 360.0);
            }
            if (toggles.enabled(InputVariable.DERIVATIVE)) {
                baseNames.add(InputVariable.DERIVATIVE.display());
                baseExtractors.add(sample -> sample.input(InputVariable.DERIVATIVE));
            }
            if (toggles.enabled(InputVariable.END_STOP)) {
                baseNames.add(InputVariable.END_STOP.display());
                baseExtractors.add(sample -> sample.input(InputVariable.END_STOP));
            }

            if (toggles.includeCurrentOutputsAsFeatures) {
                for (OutputVariable output : OutputVariable.values()) {
                    if (toggles.enabled(output)) {
                        baseNames.add("Current " + output.display());
                        baseExtractors.add(sample -> sample.output(output));
                    }
                }
            }
        }

        private void buildCrossTerms() {
            if (!toggles.includeCrossTerms) {
                return;
            }
            for (int i = 0; i < baseNames.size(); i++) {
                for (int j = i + 1; j < baseNames.size(); j++) {
                    crossTerms.add(new int[] {i, j});
                }
            }
        }

        private void buildNames() {
            names.clear();
            names.add("Bias");
            names.addAll(baseNames);
            for (int[] pair : crossTerms) {
                names.add(baseNames.get(pair[0]) + " × " + baseNames.get(pair[1]));
            }
        }

        public int featureCount() {
            return 1 + baseNames.size() + crossTerms.size();
        }

        public List<String> featureNames() {
            return Collections.unmodifiableList(names);
        }

        public String featureName(int index) {
            return names.get(index);
        }

        public void fit(List<FeatureSample> samples) {
            means = new double[featureCount()];
            stds = new double[featureCount()];
            Arrays.fill(stds, 1.0);

            if (samples.isEmpty()) {
                return;
            }

            List<double[]> raw = new ArrayList<>(samples.size());
            for (FeatureSample sample : samples) {
                raw.add(rawVector(sample));
            }

            for (int j = 1; j < featureCount(); j++) {
                double sum = 0.0;
                for (double[] row : raw) {
                    sum += row[j];
                }
                means[j] = sum / raw.size();
            }

            for (int j = 1; j < featureCount(); j++) {
                double var = 0.0;
                for (double[] row : raw) {
                    double d = row[j] - means[j];
                    var += d * d;
                }
                double std = Math.sqrt(var / raw.size());
                stds[j] = std < 1.0e-9 ? 1.0 : std;
            }
        }

        public double[] vectorize(FeatureSample sample) {
            double[] raw = rawVector(sample);
            double[] out = new double[raw.length];
            out[0] = 1.0;
            for (int i = 1; i < raw.length; i++) {
                out[i] = (raw[i] - means[i]) / stds[i];
            }
            return out;
        }

        private double[] rawVector(FeatureSample sample) {
            double[] out = new double[featureCount()];
            out[0] = 1.0;

            for (int i = 0; i < baseExtractors.size(); i++) {
                out[1 + i] = finite(baseExtractors.get(i).applyAsDouble(sample));
            }

            int idx = 1 + baseExtractors.size();
            for (int[] pair : crossTerms) {
                double a = out[1 + pair[0]];
                double b = out[1 + pair[1]];
                out[idx++] = finite(a * b);
            }

            return out;
        }
    }



    public static final class CorrespondenceOptions {
        public int searchStepsPerSide = 12;
        public double searchRadiusNormalized = 0.025;

        public double positionWeight = 1.00;
        public double orientationWeight = 0.60;
        public double derivativeWeight = 0.45;
        public double radialWeight = 0.30;
        public double arcPenaltyWeight = 0.40;

        public double reverseMismatchPenalty = 0.35;
        public double minimumConfidence = 0.10;

        public CorrespondenceOptions copy() {
            CorrespondenceOptions copy = new CorrespondenceOptions();
            copy.searchStepsPerSide = this.searchStepsPerSide;
            copy.searchRadiusNormalized = this.searchRadiusNormalized;
            copy.positionWeight = this.positionWeight;
            copy.orientationWeight = this.orientationWeight;
            copy.derivativeWeight = this.derivativeWeight;
            copy.radialWeight = this.radialWeight;
            copy.arcPenaltyWeight = this.arcPenaltyWeight;
            copy.reverseMismatchPenalty = this.reverseMismatchPenalty;
            copy.minimumConfidence = this.minimumConfidence;
            return copy;
        }
    }


    public static final class ContourCorrespondenceSample {
        private final double sharedLocation;
        private final FeatureSample pencilSample;
        private final FeatureSample inkSample;
        private final double confidence;

        private ContourCorrespondenceSample(
                double sharedLocation,
                FeatureSample pencilSample,
                FeatureSample inkSample,
                double confidence
        ) {
            this.sharedLocation = sharedLocation;
            this.pencilSample = pencilSample;
            this.inkSample = inkSample;
            this.confidence = confidence;
        }

        public double sharedLocation() {
            return sharedLocation;
        }

        public FeatureSample pencilSample() {
            return pencilSample;
        }

        public FeatureSample inkSample() {
            return inkSample;
        }

        public double confidence() {
            return confidence;
        }
    }



    private static final class LocalFrame {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;
        private final int width;
        private final int height;

        private LocalFrame(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.width = Math.max(1, (maxX - minX) + 1);
            this.height = Math.max(1, (maxY - minY) + 1);
        }

        private static LocalFrame from(ShapeContour contour, PointCollection points) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;

            if (contour != null && !contour.isEmpty()) {
                minX = Math.min(minX, contour.firstX());
                maxX = Math.max(maxX, contour.lastX());
                minY = Math.min(minY, contour.bottomY());
                maxY = Math.max(maxY, contour.topY());
            }

            if (points != null && !points.isEmpty()) {
                minX = Math.min(minX, points.firstX());
                maxX = Math.max(maxX, points.lastX());
                minY = Math.min(minY, points.bottomY());
                maxY = Math.max(maxY, points.topY());
            }

            if (minX == Integer.MAX_VALUE) {
                return new LocalFrame(0, 0, 0, 0);
            }

            return new LocalFrame(minX, minY, maxX, maxY);
        }

        private static LocalFrame fromPointsOnly(PointCollection points) {
            if (points == null || points.isEmpty()) {
                return new LocalFrame(0, 0, 0, 0);
            }
            return new LocalFrame(
                    points.firstX(),
                    points.bottomY(),
                    points.lastX(),
                    points.topY()
            );
        }

        private static LocalFrame fromContourOnly(ShapeContour contour) {
            if (contour == null || contour.isEmpty()) {
                return new LocalFrame(0, 0, 0, 0);
            }
            return new LocalFrame(
                    contour.firstX(),
                    contour.bottomY(),
                    contour.lastX(),
                    contour.topY()
            );
        }
    }

    private static final class LocalDrawing {
        private final ShapeContour contour;
        private final PointCollection points;
        private final FastRGB scan;
        private final LocalFrame frame;

        private LocalDrawing(ShapeContour contour, PointCollection points, FastRGB scan, LocalFrame frame) {
            this.contour = contour;
            this.points = points;
            this.scan = scan;
            this.frame = frame;
        }
    }

    private static BuildOptions defaultBuildOptions() {
        return new BuildOptions();
    }

    private static LocalDrawing normalizeToLocalFrame(
            ShapeContour contour,
            PointCollection points,
            FastRGB scan
    ) {
        LocalFrame pointFrame;
        if (STRICT_SCAN_CROP_TO_POINT_BOUNDS) {
            pointFrame = (points != null && !points.isEmpty())
                    ? LocalFrame.fromPointsOnly(points)
                    : LocalFrame.fromContourOnly(contour);
        } else {
            pointFrame = LocalFrame.from(contour, points);
        }

        ShapeContour localContour = contour == null || contour.isEmpty()
                ? ShapeContour.empty()
                : contour.translate(-pointFrame.minX, -pointFrame.minY);

        PointCollection localPoints;
        if (points == null) {
            localPoints = new PointCollection();
        } else {
            localPoints = new PointCollection(points);
            if (!localPoints.isEmpty()) {
                localPoints.translate(-pointFrame.minX, -pointFrame.minY);
            }
        }

        FastRGB localScan = cropToLocalFrame(scan, pointFrame);
        return new LocalDrawing(localContour, localPoints, localScan, pointFrame);
    }


    private static FastRGB cropToLocalFrame(FastRGB scan, LocalFrame frame) {
        if (scan == null) {
            return null;
        }

        FastRGB source = scan.hasAlphaChannel() ? scan : scan.withAlphaChannel();
        FastRGB out = new FastRGB(frame.width, frame.height, true);
        out.fill(0xFFFFFFFF);

        for (int y = 0; y < frame.height; y++) {
            for (int x = 0; x < frame.width; x++) {
                int srcX = frame.minX + x;
                int srcY = frame.minY + y;
                if (source.contains(srcX, srcY)) {
                    out.setRGB(x, y, source.getRGBA(srcX, srcY));
                }
            }
        }

        return out;
    }


    private static double outputStrength(OutputVariable output) {
        if (!USE_STATIC_OUTPUT_STRENGTHS) {
            return 1.0;
        }
        return switch (output) {
            case THICKNESS -> THICKNESS_STRENGTH;
            case BORDER_BLEND -> BORDER_BLEND_STRENGTH;
            case OPACITY -> OPACITY_STRENGTH;
            case BLOTCH -> BLOTCH_STRENGTH;
        };
    }

    private static double outputLossWeight(OutputVariable output) {
        if (!USE_STATIC_LOSS_WEIGHTS) {
            return 1.0;
        }
        return switch (output) {
            case THICKNESS -> THICKNESS_LOSS_WEIGHT;
            case BORDER_BLEND -> BORDER_BLEND_LOSS_WEIGHT;
            case OPACITY -> OPACITY_LOSS_WEIGHT;
            case BLOTCH -> BLOTCH_LOSS_WEIGHT;
        };
    }


    public static final class LinearTransferModel {
        private final VariableToggles toggles;
        private final FeatureVectorizer vectorizer;
        private final EnumMap<OutputVariable, double[]> weights = new EnumMap<>(OutputVariable.class);
        private final List<ContourCorrespondenceSample> correspondence;

        public LinearTransferModel(
                VariableToggles toggles,
                AppearanceMap pencilMap,
                AppearanceMap inkMap
        ) {
            this(toggles, pencilMap, inkMap, buildNaiveCorrespondence(pencilMap, inkMap));
        }

        public LinearTransferModel(
                VariableToggles toggles,
                AppearanceMap pencilMap,
                AppearanceMap inkMap,
                List<ContourCorrespondenceSample> correspondence
        ) {
            this.toggles = toggles.copy();
            this.vectorizer = new FeatureVectorizer(this.toggles);
            this.vectorizer.fit(pencilMap.samples());
            this.correspondence = correspondence == null || correspondence.isEmpty()
                    ? buildNaiveCorrespondence(pencilMap, inkMap)
                    : Collections.unmodifiableList(new ArrayList<>(correspondence));

            for (OutputVariable output : OutputVariable.values()) {
                weights.put(output, new double[vectorizer.featureCount()]);
            }

            initializeFromPair(pencilMap, inkMap);
        }

        public int featureCount() {
            return vectorizer.featureCount();
        }

        public String featureName(int index) {
            return vectorizer.featureName(index);
        }

        public List<String> featureNames() {
            return vectorizer.featureNames();
        }

        public double weight(OutputVariable output, int index) {
            return weights.get(output)[index];
        }

        public void setWeight(OutputVariable output, int index, double value) {
            weights.get(output)[index] = finite(value);
        }

        public double[] weights(OutputVariable output) {
            return Arrays.copyOf(weights.get(output), weights.get(output).length);
        }

        public List<ContourCorrespondenceSample> correspondence() {
            return correspondence;
        }

        private void initializeFromPair(AppearanceMap pencilMap, AppearanceMap inkMap) {
            if (correspondence.isEmpty()) {
                return;
            }

            vectorizer.fit(pencilMap.samples());

            int n = correspondence.size();
            double[][] x = new double[n][];
            double[] c = new double[n];

            for (int i = 0; i < n; i++) {
                ContourCorrespondenceSample pair = correspondence.get(i);
                x[i] = vectorizer.vectorize(pair.pencilSample());
                c[i] = pair.confidence();
            }

            double confSum = Arrays.stream(c).sum();
            if (!(confSum > 1.0e-9)) {
                confSum = n;
                Arrays.fill(c, 1.0);
            }

            for (OutputVariable output : OutputVariable.values()) {
                if (!toggles.enabled(output)) {
                    continue;
                }

                double[] y = new double[n];
                for (int i = 0; i < n; i++) {
                    ContourCorrespondenceSample pair = correspondence.get(i);
                    y[i] = pair.inkSample().output(output) - pair.pencilSample().output(output);
                }

                double mean = 0.0;
                for (int i = 0; i < n; i++) {
                    mean += c[i] * y[i];
                }
                mean /= confSum;

                double[] w = weights.get(output);
                w[0] = mean;

                for (int j = 1; j < w.length; j++) {
                    double cov = 0.0;
                    double var = 0.0;
                    for (int i = 0; i < n; i++) {
                        cov += c[i] * x[i][j] * (y[i] - mean);
                        var += c[i] * x[i][j] * x[i][j];
                    }
                    cov /= confSum;
                    var /= confSum;
                    w[j] = var < 1.0e-9 ? 0.0 : 0.15 * (cov / var);
                }
            }
        }

        public void fit(AppearanceMap pencilMap, AppearanceMap inkMap, TrainingOptions options) {
            if (correspondence.isEmpty()) {
                return;
            }

            TrainingOptions opts = options == null ? new TrainingOptions() : options.copy();
            vectorizer.fit(pencilMap.samples());

            int n = correspondence.size();
            int featureCount = vectorizer.featureCount();
            double[][] x = new double[n][featureCount];
            double[] c = new double[n];

            for (int i = 0; i < n; i++) {
                ContourCorrespondenceSample pair = correspondence.get(i);
                x[i] = vectorizer.vectorize(pair.pencilSample());
                c[i] = pair.confidence();
            }

            double confSum = Arrays.stream(c).sum();
            if (!(confSum > 1.0e-9)) {
                confSum = n;
                Arrays.fill(c, 1.0);
            }

            for (OutputVariable output : OutputVariable.values()) {
                if (!toggles.enabled(output)) {
                    continue;
                }

                double[] y = new double[n];
                for (int i = 0; i < n; i++) {
                    ContourCorrespondenceSample pair = correspondence.get(i);
                    y[i] = pair.inkSample().output(output) - pair.pencilSample().output(output);
                }

                double[] w = weights.get(output);

                for (int epoch = 0; epoch < opts.epochs; epoch++) {
                    double[] grad = new double[featureCount];

                    for (int i = 0; i < n; i++) {
                        double prediction = dot(w, x[i]);
                        double error = prediction - y[i];
                        double lossWeight = outputLossWeight(output);
                        double conf = c[i];

                        for (int j = 0; j < featureCount; j++) {
                            grad[j] += lossWeight * conf * error * x[i][j];
                        }
                    }

                    for (int j = 0; j < featureCount; j++) {
                        grad[j] /= confSum;
                        if (j != 0) {
                            grad[j] += opts.regularization * w[j];
                        }
                        w[j] -= opts.learningRate * grad[j];
                    }
                }
            }
        }

        // IF your LinearTransferModel cannot access BuildOptions, then REPLACE the synthesize signature and calls instead:

        public AppearanceMap synthesize(AppearanceMap pencilMap, BuildOptions options) {
            List<FeatureSample> out = new ArrayList<>(pencilMap.size());

            BuildOptions useOptions = options == null ? new BuildOptions() : options;

            for (FeatureSample pencil : pencilMap.samples()) {
                FeatureSample copy = pencil.copy();
                double[] features = vectorizer.vectorize(pencil);

                for (OutputVariable output : OutputVariable.values()) {
                    if (!toggles.enabled(output)) {
                        copy.setOutput(output, pencil.output(output));
                        continue;
                    }

                    double delta = dot(weights.get(output), features);
                    double predicted = boundOutput(
                            output,
                            pencil.output(output) + (delta * outputStrength(output)),
                            useOptions
                    );
                    copy.setOutput(output, predicted);
                }

                out.add(copy);
            }

            return new AppearanceMap("Simulated Ink", pencilMap.contour(), out, pencilMap.toggles());
        }

        public AppearanceMap synthesize(AppearanceMap pencilMap) {
            return synthesize(pencilMap, new BuildOptions());
        }

        public double rmse(AppearanceMap pencilMap, AppearanceMap actualInkMap, BuildOptions options) {
            if (correspondence.isEmpty()) {
                return 0.0;
            }

            AppearanceMap predicted = synthesize(pencilMap, options);

            double sumSq = 0.0;
            double weightSum = 0.0;

            for (ContourCorrespondenceSample pair : correspondence) {
                FeatureSample predictedSample = predicted.nearestByLocation(pair.pencilSample().location);
                if (predictedSample == null) {
                    continue;
                }

                for (OutputVariable output : OutputVariable.values()) {
                    if (!toggles.enabled(output)) {
                        continue;
                    }

                    double diff = predictedSample.output(output) - pair.inkSample().output(output);
                    double lw = outputLossWeight(output);
                    sumSq += lw * pair.confidence() * diff * diff;
                    weightSum += lw * pair.confidence();
                }
            }

            return weightSum <= 1.0e-9 ? 0.0 : Math.sqrt(sumSq / weightSum);
        }

        public double rmse(AppearanceMap pencilMap, AppearanceMap actualInkMap) {
            return rmse(pencilMap, actualInkMap, new BuildOptions());
        }    }

    public static final class Session {
        private final AppearanceMap pencilMap;
        private final AppearanceMap inkMap;
        private final List<ContourCorrespondenceSample> correspondence;
        private final LinearTransferModel model;

        private Session(
                AppearanceMap pencilMap,
                AppearanceMap inkMap,
                List<ContourCorrespondenceSample> correspondence,
                LinearTransferModel model
        ) {
            this.pencilMap = pencilMap;
            this.inkMap = inkMap;
            this.correspondence = Collections.unmodifiableList(new ArrayList<>(correspondence));
            this.model = model;
        }

        public AppearanceMap pencilMap() {
            return pencilMap;
        }

        public AppearanceMap inkMap() {
            return inkMap;
        }

        public List<ContourCorrespondenceSample> correspondence() {
            return correspondence;
        }

        public LinearTransferModel model() {
            return model;
        }
    }/*
    public static Session build(
            ShapeContour contour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            BuildOptions options
    ) {
        Objects.requireNonNull(contour, "contour");
        Objects.requireNonNull(pencilPoints, "pencilPoints");
        Objects.requireNonNull(inkPoints, "inkPoints");

        VariableToggles useToggles = toggles == null ? VariableToggles.allEnabled() : toggles.copy();
        BuildOptions buildOptions = options == null ? new BuildOptions() : options.copy();

        AppearanceMap pencilMap = buildAppearanceMap("Pencil", contour, pencilPoints, pencilScan, useToggles, buildOptions);
        AppearanceMap inkMap = buildAppearanceMap("Ink", contour, inkPoints, inkScan, useToggles, buildOptions);
        LinearTransferModel model = new LinearTransferModel(useToggles, pencilMap, inkMap);

        return new Session(pencilMap, inkMap, model);
    }*/



    public static Session build(
            ShapeContour pencilContour,
            ShapeContour inkContour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            BuildOptions options,
            CorrespondenceOptions correspondenceOptions
    ) {
        Objects.requireNonNull(pencilContour, "pencilContour");
        Objects.requireNonNull(inkContour, "inkContour");
        Objects.requireNonNull(pencilPoints, "pencilPoints");
        Objects.requireNonNull(inkPoints, "inkPoints");

        VariableToggles useToggles = applyFactorInfluenceMode(toggles);
        BuildOptions buildOptions = options == null ? new BuildOptions() : options.copy();
        CorrespondenceOptions useCorrespondenceOptions = correspondenceOptions == null
                ? new CorrespondenceOptions()
                : correspondenceOptions.copy();

        LocalDrawing pencilLocal = normalizeToLocalFrame(pencilContour, pencilPoints, pencilScan);
        LocalDrawing inkLocal = normalizeToLocalFrame(inkContour, inkPoints, inkScan);

        AppearanceMap pencilMap = buildAppearanceMap(
                "Pencil",
                pencilLocal.contour,
                pencilLocal.points,
                pencilLocal.scan,
                useToggles,
                buildOptions
        );

        AppearanceMap inkMap = buildAppearanceMap(
                "Ink",
                inkLocal.contour,
                inkLocal.points,
                inkLocal.scan,
                useToggles,
                buildOptions
        );

        List<ContourCorrespondenceSample> correspondence = buildCorrespondence(
                pencilMap,
                inkMap,
                useCorrespondenceOptions
        );

        LinearTransferModel model = new LinearTransferModel(
                useToggles,
                pencilMap,
                inkMap,
                correspondence
        );

        return new Session(pencilMap, inkMap, correspondence, model);
    }


    public static Session build(
            ShapeContour pencilContour,
            ShapeContour inkContour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            BuildOptions options
    ) {
        return build(
                pencilContour,
                inkContour,
                pencilPoints,
                inkPoints,
                pencilScan,
                inkScan,
                toggles,
                options,
                new CorrespondenceOptions()
        );
    }

    public static Session build(
            ShapeContour pencilContour,
            ShapeContour inkContour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan
    ) {
        return build(
                pencilContour,
                inkContour,
                pencilPoints,
                inkPoints,
                pencilScan,
                inkScan,
                VariableToggles.allEnabled(),
                new BuildOptions(),
                new CorrespondenceOptions()
        );
    }

    public static Session build(
            ShapeContour pencilContour,
            ShapeContour inkContour,
            PointCollection pencilPoints,
            PointCollection inkPoints
    ) {
        return build(
                pencilContour,
                inkContour,
                pencilPoints,
                inkPoints,
                null,
                null,
                VariableToggles.allEnabled(),
                new BuildOptions(),
                new CorrespondenceOptions()
        );
    }

    public static Session build(
            ShapeContour contour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            BuildOptions options
    ) {
        return build(
                contour,
                contour,
                pencilPoints,
                inkPoints,
                pencilScan,
                inkScan,
                toggles,
                options,
                new CorrespondenceOptions()
        );
    }

    public static Session build(
            ShapeContour contour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            int sampleCount
    ) {
        BuildOptions buildOptions = new BuildOptions();
        buildOptions.sampleCount = sampleCount;
        return build(
                contour,
                contour,
                pencilPoints,
                inkPoints,
                pencilScan,
                inkScan,
                toggles,
                buildOptions,
                new CorrespondenceOptions()
        );
    }

    public static Session build(
            ShapeContour contour,
            PointCollection pencilPoints,
            PointCollection inkPoints
    ) {
        return build(contour, pencilPoints, inkPoints, null, null, VariableToggles.allEnabled(), new BuildOptions());
    }
/*
    public static Workbench launch(
            ShapeContour contour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            BuildOptions buildOptions,
            TrainingOptions trainingOptions
    ) {
        Session session = build(contour, pencilPoints, inkPoints, pencilScan, inkScan, toggles, buildOptions);
        Workbench frame = new Workbench(
                session.pencilMap(),
                session.inkMap(),
                pencilScan,
                inkScan,
                session.model(),
                trainingOptions
        );
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
        return frame;
    }*/



    public static Workbench launch(
            ShapeContour pencilContour,
            ShapeContour inkContour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            BuildOptions buildOptions,
            TrainingOptions trainingOptions,
            CorrespondenceOptions correspondenceOptions
    ) {
        BuildOptions useBuildOptions = buildOptions == null ? new BuildOptions() : buildOptions.copy();
        useBuildOptions = configureBuildOptionsOnLaunch(useBuildOptions);

        Session session = build(
                pencilContour,
                inkContour,
                pencilPoints,
                inkPoints,
                pencilScan,
                inkScan,
                toggles,
                useBuildOptions,
                correspondenceOptions
        );

        LocalDrawing pencilLocal = normalizeToLocalFrame(pencilContour, pencilPoints, pencilScan);
        LocalDrawing inkLocal = normalizeToLocalFrame(inkContour, inkPoints, inkScan);

        Workbench frame = new Workbench(
                session.pencilMap(),
                session.inkMap(),
                pencilLocal.scan,
                inkLocal.scan,
                session.model(),
                trainingOptions,
                useBuildOptions
        );

        SwingUtilities.invokeLater(() -> frame.setVisible(true));
        return frame;
    }
    public static Workbench launch(
            ShapeContour pencilContour,
            ShapeContour inkContour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            BuildOptions buildOptions,
            TrainingOptions trainingOptions
    ) {
        return launch(
                pencilContour,
                inkContour,
                pencilPoints,
                inkPoints,
                pencilScan,
                inkScan,
                toggles,
                buildOptions,
                trainingOptions,
                new CorrespondenceOptions()
        );
    }

    public static Workbench launch(
            ShapeContour pencilContour,
            ShapeContour inkContour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan
    ) {
        return launch(
                pencilContour,
                inkContour,
                pencilPoints,
                inkPoints,
                pencilScan,
                inkScan,
                VariableToggles.allEnabled(),
                new BuildOptions(),
                new TrainingOptions(),
                new CorrespondenceOptions()
        );
    }

    public static Workbench launch(
            ShapeContour pencilContour,
            ShapeContour inkContour,
            PointCollection pencilPoints,
            PointCollection inkPoints
    ) {
        return launch(
                pencilContour,
                inkContour,
                pencilPoints,
                inkPoints,
                null,
                null,
                VariableToggles.allEnabled(),
                new BuildOptions(),
                new TrainingOptions(),
                new CorrespondenceOptions()
        );
    }


    public static Workbench launch(
            ShapeContour contour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            BuildOptions buildOptions,
            TrainingOptions trainingOptions
    ) {
        return launch(
                contour,
                contour,
                pencilPoints,
                inkPoints,
                pencilScan,
                inkScan,
                toggles,
                buildOptions,
                trainingOptions,
                new CorrespondenceOptions()
        );
    }

    public static Workbench launch(
            ShapeContour contour,
            PointCollection pencilPoints,
            PointCollection inkPoints,
            FastRGB pencilScan,
            FastRGB inkScan,
            VariableToggles toggles,
            int sampleCount
    ) {
        BuildOptions options = new BuildOptions();
        options.sampleCount = sampleCount;
        return launch(
                contour,
                contour,
                pencilPoints,
                inkPoints,
                pencilScan,
                inkScan,
                toggles,
                options,
                new TrainingOptions(),
                new CorrespondenceOptions()
        );
    }

    public static Workbench launch(
            ShapeContour contour,
            PointCollection pencilPoints,
            PointCollection inkPoints
    ) {
        return launch(
                contour,
                contour,
                pencilPoints,
                inkPoints,
                null,
                null,
                VariableToggles.allEnabled(),
                new BuildOptions(),
                new TrainingOptions(),
                new CorrespondenceOptions()
        );
    }

    private static BuildOptions thisBuildOptionsOrFallback(BuildOptions options) {
        return options == null ? new BuildOptions() : options;
    }
    private static BuildOptions thisBuildOptionsOrFallback() {
        return new BuildOptions();
    }

    public static AppearanceMap buildAppearanceMap(
            String name,
            ShapeContour contour,
            PointCollection points,
            FastRGB scan,
            VariableToggles toggles,
            BuildOptions options
    ) {
        VariableToggles useToggles = applyFactorInfluenceMode(toggles);
        BuildOptions buildOptions = options == null ? new BuildOptions() : options.copy();

        if (contour == null || contour.isEmpty()) {
            return new AppearanceMap(name, ShapeContour.empty(), Collections.<FeatureSample>emptyList(), useToggles);
        }

        ToggleMask mask = new ToggleMask(useToggles);
        ContourGeometryCache cache = buildContourGeometryCache(contour);
        boolean smoothPencilPointCloud = "Pencil".equalsIgnoreCase(name);
        boolean isInkMap = "Ink".equalsIgnoreCase(name);

        List<FeatureSample> samples = new ArrayList<>();

        if (buildOptions.sampleCount > 0) {
            int desired = Math.max(1, Math.min(buildOptions.sampleCount, contour.size()));
            Set<Integer> usedIndices = new HashSet<>();

            for (int i = 0; i < desired; i++) {
                double t = desired == 1 ? 0.0 : ((double) i / desired);
                int index = contour.indexByNormalizedArc(t);

                while (usedIndices.contains(index) && usedIndices.size() < contour.size()) {
                    index = (index + 1) % contour.size();
                }

                usedIndices.add(index);
                samples.add(analyzeAtIndexFast(
                        cache,
                        points,
                        scan,
                        mask,
                        buildOptions,
                        index,
                        smoothPencilPointCloud,
                        isInkMap
                ));
            }
        } else {
            int stride = Math.max(1, buildOptions.contourStride);
            for (int i = 0; i < contour.size(); i += stride) {
                samples.add(analyzeAtIndexFast(
                        cache,
                        points,
                        scan,
                        mask,
                        buildOptions,
                        i,
                        smoothPencilPointCloud,
                        isInkMap
                ));
            }
        }

        samples.sort(
                Comparator.comparingDouble((FeatureSample sample) -> sample.location)
                        .thenComparingInt(sample -> sample.contourIndex)
        );

        return new AppearanceMap(name, contour, samples, useToggles);
    }

    private static FeatureSample analyzeAtIndexFast(
            ContourGeometryCache cache,
            PointCollection points,
            FastRGB scan,
            ToggleMask mask,
            BuildOptions options,
            int contourIndex,
            boolean smoothPencilPointCloud,
            boolean isInkMap
    ) {
        Point curr = cache.points[contourIndex];
        PointCollection cloud = points == null ? new PointCollection() : points;

        Point analysisContourPoint = smoothPencilPointCloud && options.useSmoothedPencilContourPoint
                ? averageContourPoint(cache, contourIndex, options.pencilContourAveragingRadius)
                : curr;

        Point anchor;
        if (cloud.isEmpty()) {
            anchor = analysisContourPoint;
        } else if (smoothPencilPointCloud) {
            anchor = computeRobustPencilAnchor(cache, cloud, contourIndex, options);
        } else {
            anchor = safePoint(cloud.closest(curr), curr);
        }

        Point2D.Double tangent = cache.tangents[contourIndex];
        Point2D.Double inward = cache.inwardNormals[contourIndex];
        Point2D.Double outward = cache.outwardNormals[contourIndex];

        double location = cache.locations[contourIndex];
        double orientationDegrees = cache.orientations[contourIndex];
        double derivative = cache.derivatives[contourIndex];

        double thickness = 0.0;
        if (mask.outThickness) {
            thickness = (smoothPencilPointCloud && options.averageThicknessAndOpacityMeasurements)
                    ? estimateAveragedThickness(cache, cloud, contourIndex, inward, outward, options)
                    : estimateThicknessFromAnchor(cloud, anchor, inward, outward, options);
        }

        double opacity = 0.0;
        if (mask.outOpacity) {
            opacity = (smoothPencilPointCloud && options.averageThicknessAndOpacityMeasurements)
                    ? estimateAveragedOpacity(cache, cloud, scan, contourIndex, inward, options)
                    : estimateOpacityFromAnchor(scan, anchor, inward, options);
        }

        double borderBlend = 0.0;
        if (mask.outBorderBlend) {
            if (BORDER_BLEND_SAMPLE_FROM_INK_ONLY) {
                borderBlend = isInkMap
                        ? estimateBorderBlend(scan, analysisContourPoint, outward, opacity, options)
                        : 0.0;
            } else {
                borderBlend = estimateBorderBlend(scan, analysisContourPoint, outward, opacity, options);
            }
        }

        double blotch = mask.outBlotch
                ? estimateBlotch(cache.contour, contourIndex, anchor, outward, options)
                : 0.0;

        double endStop = mask.inEndStop
                ? estimateEndStop(cloud, anchor, orientationDegrees, thickness <= 0.0 ? 1.0 : thickness)
                : 0.0;

        FeatureSample sample = new FeatureSample(
                contourIndex,
                location,
                curr,
                anchor,
                tangent,
                inward,
                outward
        );

        if (mask.inLocation) sample.setInput(InputVariable.LOCATION, location);
        if (mask.inOrientation) sample.setInput(InputVariable.ORIENTATION, orientationDegrees);
        if (mask.inDerivative) sample.setInput(InputVariable.DERIVATIVE, derivative);
        if (mask.inEndStop) sample.setInput(InputVariable.END_STOP, endStop);

        if (mask.outThickness) sample.setOutput(OutputVariable.THICKNESS, thickness);
        if (mask.outBorderBlend) sample.setOutput(OutputVariable.BORDER_BLEND, borderBlend);
        if (mask.outOpacity) sample.setOutput(OutputVariable.OPACITY, opacity);
        if (mask.outBlotch) sample.setOutput(OutputVariable.BLOTCH, blotch);

        sample.setDiagnostic("orientationDegrees", orientationDegrees);
        sample.setDiagnostic("derivativeRaw", derivative);
        sample.setDiagnostic("endStopScore", endStop);
        sample.setDiagnostic("anchorDistance", anchor.distance(curr));

        return sample;
    }

    private static FeatureSample analyzeAtIndex(
            ShapeContour contour,
            PointCollection points,
            FastRGB scan,
            VariableToggles toggles,
            BuildOptions options,
            int contourIndex
    ) {
        ContourGeometryCache cache = buildContourGeometryCache(contour);
        ToggleMask mask = new ToggleMask(toggles == null ? VariableToggles.allEnabled() : toggles);
        boolean smoothPencilPointCloud = false;
        boolean isInkMap = false;

        return analyzeAtIndexFast(
                cache,
                points,
                scan,
                mask,
                options == null ? new BuildOptions() : options,
                contourIndex,
                smoothPencilPointCloud,
                isInkMap
        );
    }

    private static VariableToggles applyFactorInfluenceMode(VariableToggles toggles) {
        VariableToggles out = toggles == null ? VariableToggles.allEnabled() : toggles.copy();

        if (DISABLE_INTER_FACTOR_INFLUENCE) {
            out.includeCrossTerms = false;
            out.includeCurrentOutputsAsFeatures = false;
        }

        return out;
    }

    public static final class Workbench extends JFrame {
        private static final double SLIDER_SCALE = 1000.0;
        private static final int SLIDER_MIN = -8000;
        private static final int SLIDER_MAX = 8000;

        private final AppearanceMap pencilMap;
        private final AppearanceMap actualInkMap;
        private final FastRGB pencilScan;
        private final FastRGB actualInkScan;
        private final LinearTransferModel model;
        private final TrainingOptions trainingOptions;
        private final BuildOptions buildOptions;

        private final JLabel actualLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel simulatedLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel infoLabel = new JLabel("", SwingConstants.CENTER);
        private final JLabel statusLabel = new JLabel("Ready.", SwingConstants.LEFT);

        private final ViewportPane pencilViewport = new ViewportPane("Pencil Source");
        private final ViewportPane actualViewport = new ViewportPane("Actual Ink");
        private final ViewportPane simulatedViewport = new ViewportPane("Simulated Ink");

        private final JLabel pencilLabel = new JLabel("", SwingConstants.CENTER);
        private final javax.swing.Timer previewTimer = new javax.swing.Timer(60, e -> requestPreviewRefresh());

        private final JPanel previewRoot = new JPanel(new BorderLayout(4, 4));
        private final JPanel minimizedTabsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        private final JPanel previewDesktop = new JPanel(new BorderLayout());
        private final List<ViewportPane> viewportOrder = new ArrayList<>();

        private SwingWorker<PreviewBundle, Void> previewWorker;
        private long previewGeneration = 0L;

        private final JComboBox<DebugFactor> debugFactorBox = new JComboBox<>(DebugFactor.values());


        private enum SliderMode {
            SIMPLE,
            ADVANCED
        }

        private enum DebugFactor {
            NORMALS("Normals"),
            THICKNESS("Thickness"),
            OPACITY("Opacity"),
            BORDER_BLEND("Border Blend"),
            BLOTCH("Blotch"),
            END_STOP("End Stop");

            private final String display;

            DebugFactor(String display) {
                this.display = display;
            }

            @Override
            public String toString() {
                return display;
            }
        }

        private enum DebugSource {
            PENCIL("Pencil"),
            INK("Ink");

            private final String display;

            DebugSource(String display) {
                this.display = display;
            }

            @Override
            public String toString() {
                return display;
            }
        }

        private final JComboBox<DebugSource> debugSourceBox = new JComboBox<>(DebugSource.values());

        private SliderMode sliderMode = START_IN_SIMPLE_MODE ? SliderMode.SIMPLE : SliderMode.ADVANCED;
        private final JPanel weightEditorHost = new JPanel(new BorderLayout());

        private final List<WeightBinding> bindings = new ArrayList<>();
        private boolean syncing = false;

        public Workbench(
                AppearanceMap pencilMap,
                AppearanceMap actualInkMap,
                FastRGB pencilScan,
                FastRGB actualInkScan,
                LinearTransferModel model,
                TrainingOptions trainingOptions,
                BuildOptions buildOptions
        ) {
            super("Pencil \u2192 Ink Transfer Workbench");

            this.pencilMap = pencilMap;
            this.actualInkMap = actualInkMap;
            this.pencilScan = pencilScan;
            this.actualInkScan = actualInkScan;
            this.model = model;
            this.trainingOptions = trainingOptions == null ? new TrainingOptions() : trainingOptions.copy();
            this.buildOptions = buildOptions == null ? new BuildOptions() : buildOptions.copy();
            previewTimer.setRepeats(false);

            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout(8, 8));

            add(buildHeader(), BorderLayout.NORTH);

//            JScrollPane weightScroll = new JScrollPane(buildWeightTabs());
//            weightScroll.setPreferredSize(new Dimension(460, 900));
            weightEditorHost.add(buildWeightTabs(), BorderLayout.CENTER);
            JScrollPane weightScroll = new JScrollPane(weightEditorHost);
            weightScroll.setPreferredSize(new Dimension(460, 900));


            JSplitPane mainSplit = new JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    buildPreviewPanel(),
                    weightScroll
            );
            mainSplit.setResizeWeight(0.72);

            add(mainSplit, BorderLayout.CENTER);
            add(buildButtonBar(), BorderLayout.SOUTH);

            setPreferredSize(new Dimension(1700, 980));
            pack();
            setLocationRelativeTo(null);

            refreshPreview();
        }

        private JComponent buildHeader() {
            JPanel panel = new JPanel(new BorderLayout());
            infoLabel.setFont(infoLabel.getFont().deriveFont(Font.BOLD, 14f));
            panel.add(infoLabel, BorderLayout.CENTER);
            return panel;
        }
/*
private JComponent buildPreviewPanel() {
    JPanel pencilPanel = new JPanel(new BorderLayout());
    pencilPanel.setBorder(new TitledBorder("Pencil Source"));
    pencilPanel.add(new JScrollPane(pencilLabel), BorderLayout.CENTER);

    JPanel actualPanel = new JPanel(new BorderLayout());
    actualPanel.setBorder(new TitledBorder("Actual Ink"));
    actualPanel.add(new JScrollPane(actualLabel), BorderLayout.CENTER);

    JPanel simulatedPanel = new JPanel(new BorderLayout());
    simulatedPanel.setBorder(new TitledBorder("Simulated Ink"));
    simulatedPanel.add(new JScrollPane(simulatedLabel), BorderLayout.CENTER);

    JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pencilPanel, actualPanel);
    leftSplit.setResizeWeight(0.5);

    JSplitPane fullSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, simulatedPanel);
    fullSplit.setResizeWeight(0.66);

    return fullSplit;
}*/


        private JComponent buildPreviewPanel() {
            viewportOrder.clear();
            viewportOrder.add(pencilViewport);
            viewportOrder.add(actualViewport);
            viewportOrder.add(simulatedViewport);

            for (ViewportPane pane : viewportOrder) {
                pane.setMinimizeHandler(this::onViewportMinimizeChanged);
            }

            previewRoot.removeAll();

            minimizedTabsBar.setBorder(BorderFactory.createTitledBorder("Minimized"));
            minimizedTabsBar.setVisible(false);

            previewRoot.add(minimizedTabsBar, BorderLayout.NORTH);
            previewRoot.add(previewDesktop, BorderLayout.CENTER);

            rebuildMinimizedTabs();
            rebuildPreviewDesktop();

            return previewRoot;
        }


        private JComponent buildButtonBar() {
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));

            JButton calibrate = new JButton("Auto Calibrate");
            JButton reset = new JButton("Reset To Initial Heuristic");
            JButton savePreset = new JButton("Save Settings");
            JButton loadPreset = new JButton("Restore Settings");
            JButton saveFull = new JButton("Save Full State");
            JButton loadFull = new JButton("Restore Full State");
            JButton debugView = new JButton("Visualize Factor");

            calibrate.addActionListener(e -> autoCalibrate(calibrate));

            reset.addActionListener(e -> {
                LinearTransferModel resetModel = new LinearTransferModel(actualInkMap.toggles(), pencilMap, actualInkMap);
                for (OutputVariable output : OutputVariable.values()) {
                    double[] w = resetModel.weights(output);
                    for (int i = 0; i < w.length; i++) {
                        model.setWeight(output, i, w[i]);
                    }
                }
                syncSlidersFromModel();
                refreshPreview();
                statusLabel.setText("Reset to heuristic initialization.");
            });

            savePreset.addActionListener(e -> savePresetDumpDialog());
            loadPreset.addActionListener(e -> loadPresetDumpDialog());
            saveFull.addActionListener(e -> saveFullStateDumpDialog());
            loadFull.addActionListener(e -> loadFullStateDumpDialog());

            debugView.addActionListener(e -> {
                if (ENABLE_DEBUG_VISUALIZATION) {
                    visualizeSelectedFactor();
                }
            });

            debugSourceBox.setSelectedItem(DebugSource.INK);

            buttons.add(calibrate);
            buttons.add(reset);
            buttons.add(savePreset);
            buttons.add(loadPreset);
            buttons.add(saveFull);
            buttons.add(loadFull);
            buttons.add(new JLabel("Debug Source:"));
            buttons.add(debugSourceBox);
            buttons.add(new JLabel("Factor:"));
            buttons.add(debugFactorBox);
            buttons.add(debugView);
            buttons.add(statusLabel);

            return buttons;
        }
        private JComponent buildControls() {
            JPanel root = new JPanel(new BorderLayout(6, 6));

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton calibrate = new JButton("Auto Calibrate");
            JButton reset = new JButton("Reset To Initial Heuristic");

            calibrate.addActionListener(e -> autoCalibrate(calibrate));
            reset.addActionListener(e -> {
                LinearTransferModel resetModel = new LinearTransferModel(actualInkMap.toggles(), pencilMap, actualInkMap);
                for (OutputVariable output : OutputVariable.values()) {
                    double[] w = resetModel.weights(output);
                    for (int i = 0; i < w.length; i++) {
                        model.setWeight(output, i, w[i]);
                    }
                }
                syncSlidersFromModel();
                refreshPreview();
                statusLabel.setText("Reset to heuristic initialization.");
            });

            buttons.add(calibrate);
            buttons.add(reset);
            buttons.add(statusLabel);

            root.add(buttons, BorderLayout.NORTH);
            root.add(buildWeightTabs(), BorderLayout.CENTER);

            return root;
        }

        private String sliderManual(OutputVariable tab, String featureName) {
            String outputMeaning = switch (tab) {
                case THICKNESS -> "line thickness";
                case BORDER_BLEND -> "edge softness / outer blend";
                case OPACITY -> "opacity / darkness";
                case BLOTCH -> "outward contour drift / bulging";
            };

            return switch (featureName) {
                case "Bias" ->
                        "Adds a global baseline shift to " + outputMeaning + " across the whole drawing.";
                case "Location Along Contour" ->
                        "Changes " + outputMeaning + " according to where the point lies along the outline.";
                case "Orientation" ->
                        "Changes " + outputMeaning + " according to the local direction of the line.";
                case "Derivative" ->
                        "Changes " + outputMeaning + " according to how sharply the contour bends here.";
                case "End Stop" ->
                        "Changes " + outputMeaning + " near regions interpreted as stroke endings.";
                case "Current Thickness" ->
                        "Uses the pencil drawing's measured thickness here to influence " + outputMeaning + ".";
                case "Current Border Blend" ->
                        "Uses the pencil drawing's measured edge softness here to influence " + outputMeaning + ".";
                case "Current Opacity" ->
                        "Uses the pencil drawing's measured darkness here to influence " + outputMeaning + ".";
                case "Current Blotch" ->
                        "Uses the pencil drawing's measured bulge / irregularity here to influence " + outputMeaning + ".";
                default -> {
                    if (featureName.contains("×")) {
                        yield "Changes " + outputMeaning + " when both parts of this interaction occur together.";
                    }
                    yield "Controls how this feature contributes to " + outputMeaning + ".";
                }
            };
        }

        private JComponent buildWeightTabs() {
            JTabbedPane tabs = new JTabbedPane();
            VariableToggles mapToggles = actualInkMap.toggles();

            for (OutputVariable output : OutputVariable.values()) {
                if (!mapToggles.enabled(output)) {
                    continue;
                }

                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

                for (int i = 0; i < model.featureCount(); i++) {
                    if (!shouldShowFeature(output, i)) {
                        continue;
                    }
                    JPanel row = new JPanel(new BorderLayout(8, 4));
                    row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));

                    JLabel name = new JLabel(model.featureName(i));
                    JLabel value = new JLabel(formatWeight(model.weight(output, i)), SwingConstants.RIGHT);
                    JSlider slider = new JSlider(SLIDER_MIN, SLIDER_MAX, sliderValue(model.weight(output, i)));
                    JButton zero = new JButton("0");
                    zero.setMargin(new Insets(0, 8, 0, 8));
                    zero.setFocusable(false);
                    zero.setToolTipText("Zero this weight so this feature no longer influences this tab.");

                    final int featureIndex = i;

                    slider.addChangeListener(e -> {
                        if (syncing) return;

                        double weight = slider.getValue() / SLIDER_SCALE;
                        model.setWeight(output, featureIndex, weight);
                        value.setText(formatWeight(weight));

                        if (slider.getValueIsAdjusting()) {
                            previewTimer.restart();
                        } else {
                            refreshPreview();
                        }
                    });

                    zero.addActionListener(e -> {
                        if (syncing) return;
                        model.setWeight(output, featureIndex, 0.0);
                        slider.setValue(sliderValue(0.0));
                        value.setText(formatWeight(0.0));
                        refreshPreview();
                    });

                    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
                    right.add(value);
                    right.add(zero);

                    row.add(name, BorderLayout.WEST);
                    row.add(slider, BorderLayout.CENTER);
                    row.add(right, BorderLayout.EAST);

                    panel.add(row);
                    bindings.add(new WeightBinding(output, featureIndex, slider, value));
                }

                tabs.addTab(output.display(), new JScrollPane(panel));
            }

            return tabs;
        }


        private AppearanceMap activeDebugMap() {
            DebugSource source = (DebugSource) debugSourceBox.getSelectedItem();
            return source == DebugSource.PENCIL ? pencilMap : actualInkMap;
        }

        private FastRGB activeDebugScan() {
            DebugSource source = (DebugSource) debugSourceBox.getSelectedItem();
            return source == DebugSource.PENCIL ? pencilScan : actualInkScan;
        }

        private PointCollection circlePoints(Point center, int radius) {
            PointCollection out = new PointCollection();
            radius = Math.max(1, radius);

            int steps = Math.max(24, radius * 10);
            for (int i = 0; i < steps; i++) {
                double t = (Math.PI * 2.0 * i) / steps;
                int x = MathEx.roundInt(center.x + Math.cos(t) * radius);
                int y = MathEx.roundInt(center.y + Math.sin(t) * radius);
                out.add(new Point(x, y));
            }

            return out;
        }

        private PointCollection offsetContourPoints(AppearanceMap map, double distance) {
            PointCollection out = new PointCollection();
            for (FeatureSample sample : map.samples()) {
                out.add(offset(sample.contourPoint, sample.outwardNormal, distance));
            }
            return out;
        }

        private PointCollection movingAverageContourPoints(AppearanceMap map, int radius) {
            PointCollection out = new PointCollection();
            List<FeatureSample> samples = map.samples();
            int n = samples.size();
            int r = Math.max(1, radius);

            for (int i = 0; i < n; i++) {
                double sx = 0.0;
                double sy = 0.0;
                int count = 0;

                for (int d = -r; d <= r; d++) {
                    FeatureSample s = samples.get(Math.floorMod(i + d, n));
                    sx += s.contourPoint.x;
                    sy += s.contourPoint.y;
                    count++;
                }

                out.add(new Point(
                        MathEx.roundInt(sx / count),
                        MathEx.roundInt(sy / count)
                ));
            }

            return out;
        }

        private PointCollection contourPoints(AppearanceMap map) {
            PointCollection out = new PointCollection();
            for (FeatureSample sample : map.samples()) {
                out.add(sample.contourPoint);
            }
            return out;
        }

        private PointCollection anchors(AppearanceMap map) {
            PointCollection out = new PointCollection();
            for (FeatureSample sample : map.samples()) {
                out.add(sample.anchorPoint);
            }
            return out;
        }

        private double averageOutput(AppearanceMap map, OutputVariable output) {
            return map.average(output);
        }




        private List<FeatureSample> debugSamples(AppearanceMap map) {
            List<FeatureSample> all = map.samples();
            if (all.isEmpty()) {
                return List.of();
            }

            int stride = Math.max(1, all.size() / Math.max(1, DEBUG_MAX_SAMPLES));
            ArrayList<FeatureSample> out = new ArrayList<>();

            for (int i = 0; i < all.size(); i += stride) {
                out.add(all.get(i));
            }

            if (out.isEmpty()) {
                out.add(all.get(0));
            }

            return out;
        }

        private FastRGB debugBackground(FastRGB preferred, AppearanceMap fallbackMap) {
            if (preferred != null) {
                return preferred;
            }
            RenderBounds bounds = RenderBounds.from(null, null, fallbackMap);
            return Rasterizer.render(fallbackMap, bounds, buildOptions);
        }
        private void showDebugWindow(String name, Object... items) {
            if (DEBUG_USE_NON_BLOCKING_WINDOWS) {
                DebuggerEx.visN(name, items);
            } else {
                DebuggerEx.vis(name, items);
            }
        }

        private PointCollection rayPoints(Point origin, Point2D.Double dir, int distance) {
            PointCollection out = new PointCollection();
            for (int i = 0; i <= Math.max(0, distance); i++) {
                out.add(offset(origin, dir, i));
            }
            return out;
        }

        private PointCollection segmentPoints(Point a, Point b) {
            PointCollection out = new PointCollection();
            int steps = Math.max(1, MathEx.ceilInt(a.distance(b)));
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                int x = MathEx.roundInt(lerp(a.x, b.x, t));
                int y = MathEx.roundInt(lerp(a.y, b.y, t));
                out.add(new Point(x, y));
            }
            return out;
        }


        private void visualizeSelectedFactor() {
            DebugFactor factor = (DebugFactor) debugFactorBox.getSelectedItem();
            if (factor == null) {
                return;
            }

            switch (factor) {
                case NORMALS -> visualizeNormals();
                case THICKNESS -> visualizeThickness();
                case OPACITY -> visualizeOpacity();
                case BORDER_BLEND -> visualizeBorderBlend();
                case BLOTCH -> visualizeBlotch();
                case END_STOP -> visualizeEndStop();
            }
        }


        private void visualizeEndStop() {
            AppearanceMap map = activeDebugMap();
            FastRGB scan = activeDebugScan();

            List<PointCollection> overlays = new ArrayList<>();
            PointCollection anchorPts = new PointCollection();

            for (FeatureSample sample : debugSamples(map)) {
                anchorPts.add(sample.anchorPoint);

                double orientation = sample.input(InputVariable.ORIENTATION);
                Point2D.Double forward = unit(Math.cos(Math.toRadians(orientation)), Math.sin(Math.toRadians(orientation)));
                Point2D.Double backward = new Point2D.Double(-forward.x, -forward.y);

                overlays.add(rayPoints(sample.anchorPoint, forward, 16));
                overlays.add(rayPoints(sample.anchorPoint, backward, 16));
            }

            overlays.add(anchorPts);

            showDebugWindow(
                    "End Stop Debug - " + ((DebugSource) debugSourceBox.getSelectedItem()),
                    debugBackground(scan, map),
                    map.contour(),
                    overlays
            );
        }


        private void visualizeNormals() {
            AppearanceMap map = activeDebugMap();
            FastRGB scan = activeDebugScan();

            List<PointCollection> overlays = new ArrayList<>();
            PointCollection contourPts = new PointCollection();

            for (FeatureSample sample : debugSamples(map)) {
                contourPts.add(sample.contourPoint);
                overlays.add(rayPoints(sample.contourPoint, sample.inwardNormal, 12));
                overlays.add(rayPoints(sample.contourPoint, sample.outwardNormal, 12));
            }

            overlays.add(contourPts);

            showDebugWindow(
                    "Normals Debug - " + ((DebugSource) debugSourceBox.getSelectedItem()),
                    debugBackground(scan, map),
                    map.contour(),
                    overlays
            );
        }


        private void visualizeThickness() {
            AppearanceMap map = activeDebugMap();
            FastRGB scan = activeDebugScan();

            List<PointCollection> overlays = new ArrayList<>();

            PointCollection contourPts = contourPoints(map);
            PointCollection avgContourPts = movingAverageContourPoints(map, Math.max(1, buildOptions.localAverageRadius));

            PointCollection thickHigh = new PointCollection();
            PointCollection thickLow = new PointCollection();
            PointCollection opacityHigh = new PointCollection();
            PointCollection opacityLow = new PointCollection();

            double avgThickness = averageOutput(map, OutputVariable.THICKNESS);
            double avgOpacity = averageOutput(map, OutputVariable.OPACITY);

            for (FeatureSample sample : debugSamples(map)) {
                double thickness = sample.output(OutputVariable.THICKNESS);
                double opacity = sample.output(OutputVariable.OPACITY);

                if (thickness >= avgThickness) {
                    thickHigh.add(sample.contourPoint);
                } else {
                    thickLow.add(sample.contourPoint);
                }

                if (opacity >= avgOpacity) {
                    opacityHigh.add(sample.contourPoint);
                } else {
                    opacityLow.add(sample.contourPoint);
                }

                int radius = Math.max(1, MathEx.roundInt(thickness * 0.5));
                overlays.add(circlePoints(sample.contourPoint, radius));
            }

            overlays.add(contourPts);
            overlays.add(avgContourPts);
            overlays.add(thickHigh);
            overlays.add(thickLow);
            overlays.add(opacityHigh);
            overlays.add(opacityLow);

            showDebugWindow(
                    "Thickness Debug - " + ((DebugSource) debugSourceBox.getSelectedItem()),
                    debugBackground(scan, map),
                    map.contour(),
                    overlays
            );
        }

        private void visualizeOpacity() {
            AppearanceMap map = activeDebugMap();
            FastRGB scan = activeDebugScan();

            List<PointCollection> overlays = new ArrayList<>();
            PointCollection anchorPts = new PointCollection();

            int radius = Math.max(1, buildOptions.opacityProbeRadius);

            for (FeatureSample sample : debugSamples(map)) {
                anchorPts.add(sample.anchorPoint);

                PointCollection localProbe = new PointCollection();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        if ((dx * dx) + (dy * dy) <= radius * radius) {
                            localProbe.add(new Point(sample.anchorPoint.x + dx, sample.anchorPoint.y + dy));
                        }
                    }
                }
                overlays.add(localProbe);
                overlays.add(rayPoints(sample.anchorPoint, sample.inwardNormal, radius));
            }

            overlays.add(anchorPts);

            showDebugWindow(
                    "Opacity Debug - " + ((DebugSource) debugSourceBox.getSelectedItem()),
                    debugBackground(scan, map),
                    map.contour(),
                    overlays
            );
        }

// REPLACE this inside visualizeBorderBlend()

        private void visualizeBorderBlend() {
            AppearanceMap map = activeDebugMap();
            FastRGB scan = activeDebugScan();

            List<PointCollection> overlays = new ArrayList<>();
            PointCollection contourPts = contourPoints(map);
            PointCollection outwardEndpoints = new PointCollection();
            PointCollection inwardEndpoints = new PointCollection();

            int probe = Math.max(1, buildOptions.outwardBlendProbe);

            for (FeatureSample sample : debugSamples(map)) {
                Point2D.Double outward = sample.outwardNormal;
                Point2D.Double inward = new Point2D.Double(-outward.x, -outward.y);

                overlays.add(rayPoints(sample.contourPoint, outward, probe));
                overlays.add(rayPoints(sample.contourPoint, inward, probe));

                Point outwardEnd = offset(sample.contourPoint, outward, probe);
                Point inwardEnd = offset(sample.contourPoint, inward, probe);

                outwardEndpoints.add(outwardEnd);
                inwardEndpoints.add(inwardEnd);

                overlays.add(circlePoints(outwardEnd, 2));
                overlays.add(circlePoints(inwardEnd, 2));
            }

            overlays.add(contourPts);
            overlays.add(outwardEndpoints);
            overlays.add(inwardEndpoints);

            showDebugWindow(
                    "Border Blend Debug - " + ((DebugSource) debugSourceBox.getSelectedItem()),
                    debugBackground(scan, map),
                    map.contour(),
                    overlays
            );
        }

        private void visualizeBlotch() {
            AppearanceMap map = activeDebugMap();
            FastRGB scan = activeDebugScan();

            List<PointCollection> overlays = new ArrayList<>();
            PointCollection contourPts = contourPoints(map);
            PointCollection positiveCenters = new PointCollection();
            PointCollection negativeCenters = new PointCollection();

            double avgAbsBlotch = 0.0;
            for (FeatureSample sample : map.samples()) {
                avgAbsBlotch += Math.abs(sample.output(OutputVariable.BLOTCH));
            }
            avgAbsBlotch /= Math.max(1, map.size());

            double threshold = Math.max(0.75, avgAbsBlotch);

            for (FeatureSample sample : debugSamples(map)) {
                double blotch = sample.output(OutputVariable.BLOTCH);
                if (Math.abs(blotch) < threshold) {
                    continue;
                }

                Point translated = offset(sample.contourPoint, sample.outwardNormal, blotch);
                int radius = Math.max(2, MathEx.roundInt(Math.abs(blotch)));

                if (blotch >= 0.0) {
                    positiveCenters.add(translated);
                } else {
                    negativeCenters.add(translated);
                }

                overlays.add(circlePoints(translated, radius));
            }

            overlays.add(contourPts);
            overlays.add(positiveCenters);
            overlays.add(negativeCenters);

            showDebugWindow(
                    "Blotch Debug - " + ((DebugSource) debugSourceBox.getSelectedItem()),
                    debugBackground(scan, map),
                    map.contour(),
                    overlays
            );
        }

        private void rebuildWeightEditor() {
            syncing = true;
            try {
                bindings.clear();
                weightEditorHost.removeAll();
                weightEditorHost.add(buildWeightTabs(), BorderLayout.CENTER);
                weightEditorHost.revalidate();
                weightEditorHost.repaint();
            } finally {
                syncing = false;
            }
        }


        private void onViewportMinimizeChanged(ViewportPane pane) {
            rebuildMinimizedTabs();
            rebuildPreviewDesktop();
        }

        private void rebuildMinimizedTabs() {
            minimizedTabsBar.removeAll();

            for (ViewportPane pane : viewportOrder) {
                if (!pane.isMinimized()) {
                    continue;
                }

                JButton restoreButton = new JButton(pane.title());
                restoreButton.addActionListener(e -> pane.restore());
                minimizedTabsBar.add(restoreButton);
            }

            minimizedTabsBar.setVisible(minimizedTabsBar.getComponentCount() > 0);
            minimizedTabsBar.revalidate();
            minimizedTabsBar.repaint();
        }

        private void rebuildPreviewDesktop() {
            previewDesktop.removeAll();

            List<ViewportPane> active = new ArrayList<>();
            for (ViewportPane pane : viewportOrder) {
                if (!pane.isMinimized()) {
                    active.add(pane);
                }
            }

            if (active.isEmpty()) {
                previewDesktop.add(new JLabel("All viewports minimized", SwingConstants.CENTER), BorderLayout.CENTER);
            } else if (active.size() == 1) {
                previewDesktop.add(active.get(0), BorderLayout.CENTER);
            } else if (active.size() == 2) {
                JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, active.get(0), active.get(1));
                split.setResizeWeight(0.5);
                previewDesktop.add(split, BorderLayout.CENTER);
            } else {
                JSplitPane leftPair = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, active.get(0), active.get(1));
                leftPair.setResizeWeight(0.5);

                JSplitPane allThree = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPair, active.get(2));
                allThree.setResizeWeight(0.66);

                previewDesktop.add(allThree, BorderLayout.CENTER);
            }

            previewDesktop.revalidate();
            previewDesktop.repaint();
        }


        private void requestPreviewRefresh() {
            final long generation = ++previewGeneration;

            if (previewWorker != null && !previewWorker.isDone()) {
                previewWorker.cancel(true);
            }

            if (SHOW_SIMULATED_BUSY_INDICATOR) {
                simulatedViewport.setBusy(true);
            }

            previewWorker = new SwingWorker<>() {
                @Override
                protected PreviewBundle doInBackground() {
                    AppearanceMap simulated = model.synthesize(pencilMap, buildOptions);

                    RenderBounds simulatedBounds = RenderBounds.from(null, null, actualInkMap, simulated);
                    RenderBounds pencilBounds = (pencilScan != null)
                            ? RenderBounds.from(pencilScan, null)
                            : RenderBounds.from(null, null, pencilMap);
                    RenderBounds actualBounds = (actualInkScan != null)
                            ? RenderBounds.from(actualInkScan, null)
                            : RenderBounds.from(null, null, actualInkMap);

                    BufferedImage pencil = pencilScan != null
                            ? pencilScan.getImage()
                            : Rasterizer.render(pencilMap, pencilBounds, buildOptions).getImage();

                    BufferedImage actual = actualInkScan != null
                            ? actualInkScan.getImage()
                            : Rasterizer.render(actualInkMap, actualBounds, buildOptions).getImage();

                    BufferedImage fake = Rasterizer.render(simulated, simulatedBounds, buildOptions).getImage();

                    double rmse = model.rmse(pencilMap, actualInkMap, buildOptions);

                    return new PreviewBundle(pencil, actual, fake, rmse);
                }

                @Override
                protected void done() {
                    if (generation != previewGeneration || isCancelled()) {
                        return;
                    }

                    try {
                        PreviewBundle bundle = get();

                        pencilViewport.setImage(bundle.pencil);
                        actualViewport.setImage(bundle.actual);
                        simulatedViewport.setImage(bundle.simulated);

                        infoLabel.setText(
                                "Pencil Samples: " + pencilMap.size()
                                        + " | Ink Samples: " + actualInkMap.size()
                                        + " | Active Features: " + model.featureCount()
                                        + " | Output RMSE: " + formatWeight(bundle.rmse)
                        );
                    } catch (Exception ignored) {
                    } finally {
                        if (generation == previewGeneration && SHOW_SIMULATED_BUSY_INDICATOR) {
                            simulatedViewport.setBusy(false);
                        }
                    }
                }
            };

            previewWorker.execute();
        }



        // add these methods inside Workbench:
        private boolean shouldShowFeature(OutputVariable output, int featureIndex) {
            if (sliderMode == SliderMode.ADVANCED) {
                return true;
            }

            String name = model.featureName(featureIndex);
            if (SIMPLE_FEATURES.contains(name)) {
                return true;
            }

            if (!name.contains("×")) {
                return false;
            }

            return isTopCrossTermForOutput(output, featureIndex, SIMPLE_EXTRA_CROSS_TERMS);
        }

        private boolean isTopCrossTermForOutput(OutputVariable output, int featureIndex, int limit) {
            List<Integer> crossIndices = new ArrayList<>();

            for (int i = 0; i < model.featureCount(); i++) {
                if (model.featureName(i).contains("×")) {
                    crossIndices.add(i);
                }
            }

            crossIndices.sort((a, b) -> Double.compare(
                    Math.abs(model.weight(output, b)),
                    Math.abs(model.weight(output, a))
            ));

            int max = Math.min(limit, crossIndices.size());
            for (int i = 0; i < max; i++) {
                if (crossIndices.get(i) == featureIndex) {
                    return true;
                }
            }

            return false;
        }

        private void autoCalibrate(JButton sourceButton) {
            sourceButton.setEnabled(false);
            statusLabel.setText("Calibrating...");

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    model.fit(pencilMap, actualInkMap, trainingOptions);
                    return null;
                }

                @Override
                protected void done() {
                    syncSlidersFromModel();
                    refreshPreview();
                    statusLabel.setText("Calibration complete.");
                    sourceButton.setEnabled(true);
                }
            }.execute();
        }

        private void syncSlidersFromModel() {
            syncing = true;
            try {
                for (WeightBinding binding : bindings) {
                    double w = model.weight(binding.output, binding.featureIndex);
                    binding.slider.setValue(sliderValue(w));
                    binding.value.setText(formatWeight(w));
                }
            } finally {
                syncing = false;
            }
        }
/*

        private void refreshPreview() {
            AppearanceMap simulated = model.synthesize(pencilMap);

            RenderBounds simulatedBounds = RenderBounds.from(null, null, actualInkMap, simulated);
            RenderBounds pencilBounds = (pencilScan != null)
                    ? RenderBounds.from(pencilScan, null)
                    : RenderBounds.from(null, null, pencilMap);
            RenderBounds inkBounds = (actualInkScan != null)
                    ? RenderBounds.from(actualInkScan, null)
                    : RenderBounds.from(null, null, actualInkMap);

            BufferedImage pencil = pencilScan != null
                    ? pencilScan.getImage()
                    : Rasterizer.render(pencilMap, pencilBounds).getImage();

            BufferedImage actual = actualInkScan != null
                    ? actualInkScan.getImage()
                    : Rasterizer.render(actualInkMap, inkBounds).getImage();

            BufferedImage fake = Rasterizer.render(simulated, simulatedBounds).getImage();

            pencilViewport.setImage(pencil);
            actualViewport.setImage(actual);
            simulatedViewport.setImage(fake);

            double rmse = model.rmse(pencilMap, actualInkMap);
            infoLabel.setText(
                    "Pencil Samples: " + pencilMap.size()
                            + " | Ink Samples: " + actualInkMap.size()
                            + " | Active Features: " + model.featureCount()
                            + " | Output RMSE: " + formatWeight(rmse)
            );
        }*/
        private void refreshPreview() {
            requestPreviewRefresh();
        }


        private int sliderValue(double weight) {
            return MathEx.bound((int) Math.round(weight * SLIDER_SCALE), SLIDER_MIN, SLIDER_MAX);
        }

        private File chooseDumpFile(boolean save, String extension, String description) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter(description, extension));

            int result = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
            if (result != JFileChooser.APPROVE_OPTION) {
                return null;
            }

            File file = chooser.getSelectedFile();
            if (save && !file.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
                file = new File(file.getParentFile(), file.getName() + "." + extension);
            }
            return file;
        }

        private void savePresetDumpDialog() {
            File file = chooseDumpFile(true, "pitpreset", "Pencil Ink Transfer Preset (*.pitpreset)");
            if (file == null) {
                return;
            }

            try {
                writeDump(file, snapshotPresetDump(this));
                statusLabel.setText("Preset dump saved: " + file.getName());
            } catch (IOException ex) {
                statusLabel.setText("Failed to save preset dump.");
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Save Preset Failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void loadPresetDumpDialog() {
            File file = chooseDumpFile(false, "pitpreset", "Pencil Ink Transfer Preset (*.pitpreset)");
            if (file == null) {
                return;
            }

            try {
                Object loaded = readDump(file);
                if (!(loaded instanceof PresetDump dump)) {
                    throw new IOException("File is not a valid preset dump.");
                }

                VariableToggles loadedToggles = restoreToggles(dump.toggles);
                TrainingOptions loadedTraining = restoreTraining(dump.training);
                BuildOptions loadedBuildOptions = restoreBuildOptions(dump.buildOptions);

                LinearTransferModel loadedModel = new LinearTransferModel(
                        loadedToggles,
                        pencilMap,
                        actualInkMap
                );
                applyModelState(loadedModel, dump.model);

                Workbench reopened = new Workbench(
                        pencilMap,
                        actualInkMap,
                        pencilScan,
                        actualInkScan,
                        loadedModel,
                        loadedTraining,
                        loadedBuildOptions
                );
                reopened.setVisible(true);

                statusLabel.setText("Preset dump loaded into new window: " + file.getName());
            } catch (IOException | ClassNotFoundException ex) {
                statusLabel.setText("Failed to load preset dump.");
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Load Preset Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
        private void saveFullStateDumpDialog() {
            File file = chooseDumpFile(true, "pitstate", "Pencil Ink Transfer Full State (*.pitstate)");
            if (file == null) {
                return;
            }

            try {
                writeDump(file, snapshotFullStateDump(this));
                statusLabel.setText("Full state dump saved: " + file.getName());
            } catch (IOException ex) {
                statusLabel.setText("Failed to save full state dump.");
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Save Full State Failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void loadFullStateDumpDialog() {
            File file = chooseDumpFile(false, "pitstate", "Pencil Ink Transfer Full State (*.pitstate)");
            if (file == null) {
                return;
            }

            try {
                Object loaded = readDump(file);
                if (!(loaded instanceof FullStateDump dump)) {
                    throw new IOException("File is not a valid full state dump.");
                }

                AppearanceMap loadedPencilMap = restoreAppearanceMap(dump.pencilMap);
                AppearanceMap loadedInkMap = restoreAppearanceMap(dump.inkMap);

                VariableToggles loadedToggles = restoreToggles(dump.toggles);
                TrainingOptions loadedTraining = restoreTraining(dump.training);
                BuildOptions loadedBuildOptions = restoreBuildOptions(dump.buildOptions);

                LinearTransferModel loadedModel = new LinearTransferModel(
                        loadedToggles,
                        loadedPencilMap,
                        loadedInkMap
                );
                applyModelState(loadedModel, dump.model);

                Workbench reopened = new Workbench(
                        loadedPencilMap,
                        loadedInkMap,
                        null,
                        null,
                        loadedModel,
                        loadedTraining,
                        loadedBuildOptions
                );
                reopened.setVisible(true);

                statusLabel.setText("Full state dump loaded into new window: " + file.getName());
            } catch (IOException | ClassNotFoundException ex) {
                statusLabel.setText("Failed to load full state dump.");
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Load Full State Failed", JOptionPane.ERROR_MESSAGE);
            }
        }

        private String formatWeight(double value) {
            return String.format("%.4f", value);
        }

        private static final class WeightBinding {
            private final OutputVariable output;
            private final int featureIndex;
            private final JSlider slider;
            private final JLabel value;

            private WeightBinding(OutputVariable output, int featureIndex, JSlider slider, JLabel value) {
                this.output = output;
                this.featureIndex = featureIndex;
                this.slider = slider;
                this.value = value;
            }
        }


        private static final class PreviewBundle {
            private final BufferedImage pencil;
            private final BufferedImage actual;
            private final BufferedImage simulated;
            private final double rmse;

            private PreviewBundle(BufferedImage pencil, BufferedImage actual, BufferedImage simulated, double rmse) {
                this.pencil = pencil;
                this.actual = actual;
                this.simulated = simulated;
                this.rmse = rmse;
            }
        }



        private static final class ViewportPane extends JPanel {
            private final String title;
            private final JLabel imageLabel = new JLabel("", SwingConstants.CENTER);
            private final JScrollPane scrollPane = new JScrollPane(imageLabel);
            private final JLabel zoomLabel = new JLabel("100%");
            private final JButton minimizeButton = new JButton("–");
            private final JButton zoomOutButton = new JButton("-");
            private final JButton zoomResetButton = new JButton("1:1");
            private final JButton zoomInButton = new JButton("+");
            private final JLabel busyLabel = new JLabel(" ");
            private final javax.swing.Timer busyTimer;

            private BufferedImage baseImage;
            private double zoom = 1.0;
            private boolean minimized = false;
            private java.util.function.Consumer<ViewportPane> minimizeHandler;
            private int busyFrame = 0;

            private ViewportPane(String title) {
                super(new BorderLayout(4, 4));
                this.title = title;
                setBorder(new TitledBorder(title));

                String[] spinnerFrames = {"|", "/", "—", "\\"};
                busyTimer = new javax.swing.Timer(120, e -> {
                    busyLabel.setText(spinnerFrames[busyFrame % spinnerFrames.length]);
                    busyFrame++;
                });
                busyTimer.setRepeats(true);
                busyLabel.setVisible(false);

                JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));

                minimizeButton.addActionListener(e -> minimize());
                zoomOutButton.addActionListener(e -> setZoom(zoom / 1.25));
                zoomResetButton.addActionListener(e -> setZoom(1.0));
                zoomInButton.addActionListener(e -> setZoom(zoom * 1.25));

                header.add(minimizeButton);   // far-left minus = minimize
                header.add(zoomOutButton);    // this minus = zoom out
                header.add(zoomResetButton);
                header.add(zoomInButton);
                header.add(zoomLabel);
                header.add(busyLabel);

                add(header, BorderLayout.NORTH);
                add(scrollPane, BorderLayout.CENTER);
            }

            private String title() {
                return title;
            }

            private void setMinimizeHandler(java.util.function.Consumer<ViewportPane> handler) {
                this.minimizeHandler = handler;
            }

            private boolean isMinimized() {
                return minimized;
            }

            private void minimize() {
                if (minimized) {
                    return;
                }
                minimized = true;
                if (minimizeHandler != null) {
                    minimizeHandler.accept(this);
                }
            }

            private void restore() {
                if (!minimized) {
                    return;
                }
                minimized = false;
                if (minimizeHandler != null) {
                    minimizeHandler.accept(this);
                }
            }

            private void setBusy(boolean busy) {
                busyLabel.setVisible(busy);
                if (busy) {
                    busyFrame = 0;
                    busyLabel.setText("|");
                    busyTimer.start();
                } else {
                    busyTimer.stop();
                    busyLabel.setText(" ");
                }
            }

            private void setImage(BufferedImage image) {
                this.baseImage = image;
                refreshScaledImage();
            }

            private void setZoom(double zoom) {
                this.zoom = MathEx.bound(zoom, 0.10, 16.0);
                zoomLabel.setText((int) Math.round(this.zoom * 100.0) + "%");
                refreshScaledImage();
            }

            private void refreshScaledImage() {
                if (baseImage == null) {
                    imageLabel.setIcon(null);
                    imageLabel.setText("No image");
                    return;
                }

                imageLabel.setText(null);

                int w = Math.max(1, MathEx.roundInt(baseImage.getWidth() * zoom));
                int h = Math.max(1, MathEx.roundInt(baseImage.getHeight() * zoom));

                Image scaled = baseImage.getScaledInstance(w, h, Image.SCALE_FAST);
                imageLabel.setIcon(new ImageIcon(scaled));
                imageLabel.revalidate();
                imageLabel.repaint();
            }
        }
    }

    private static Point2D.Double simulatedStrokeCenter(
            FeatureSample sample,
            VariableToggles toggles,
            BuildOptions options
    ) {
        double thickness = toggles.enabled(OutputVariable.THICKNESS)
                ? Math.max(0.5, Math.min(sample.thickness(), Math.max(0.5, options.maxThickness)))
                : 1.0;

        double blotch = toggles.enabled(OutputVariable.BLOTCH)
                ? sample.blotch()
                : 0.0;

        double cx = sample.contourPoint.x
                + (sample.inwardNormal.x * (thickness * 0.5))
                + (sample.outwardNormal.x * blotch);

        double cy = sample.contourPoint.y
                + (sample.inwardNormal.y * (thickness * 0.5))
                + (sample.outwardNormal.y * blotch);

        return new Point2D.Double(cx, cy);
    }

    private static double simulatedStrokeRadius(
            FeatureSample sample,
            VariableToggles toggles,
            BuildOptions options
    ) {
        double thickness = toggles.enabled(OutputVariable.THICKNESS)
                ? Math.max(0.5, Math.min(sample.thickness(), Math.max(0.5, options.maxThickness)))
                : 1.0;

        return Math.max(0.5, thickness * THICKNESS_RENDER_RADIUS_SCALE);
    }

    private static double simulatedStrokeFeather(
            FeatureSample sample,
            VariableToggles toggles,
            BuildOptions options
    ) {
        double blend = toggles.enabled(OutputVariable.BORDER_BLEND)
                ? clamp01(sample.borderBlend())
                : 0.0;

        double feather = 0.20 + (blend * BORDER_BLEND_RENDER_FEATHER_SCALE);
        return Math.min(Math.max(0.20, feather), Math.max(0.5, options.maxThickness * 0.35));
    }

    private static double simulatedStrokeRadius(FeatureSample sample, VariableToggles toggles) {
        double thickness = toggles.enabled(OutputVariable.THICKNESS)
                ? Math.max(1.0, sample.thickness())
                : 1.0;
        return Math.max(0.5, thickness * 0.5);
    }

    private static double simulatedStrokeFeather(FeatureSample sample, VariableToggles toggles) {
        double blend = toggles.enabled(OutputVariable.BORDER_BLEND)
                ? clamp01(sample.borderBlend())
                : 0.0;
        return Math.max(0.20, 0.20 + (blend * BORDER_BLEND_RENDER_FEATHER_SCALE));
    }

    private static final class RenderBounds {
        private final int minX;
        private final int minY;
        private final int width;
        private final int height;

        private RenderBounds(int minX, int minY, int width, int height) {
            this.minX = minX;
            this.minY = minY;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }

        private int tx(double x) {
            return (int) Math.round(x - minX);
        }

        private int ty(double y) {
            return (int) Math.round(y - minY);
        }

        private static RenderBounds from(FastRGB preferred, FastRGB secondary, AppearanceMap... maps) {
            if (preferred != null) {
                return new RenderBounds(0, 0, preferred.getWidth(), preferred.getHeight());
            }
            if (secondary != null) {
                return new RenderBounds(0, 0, secondary.getWidth(), secondary.getHeight());
            }

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;

            BuildOptions defaultOptions = new BuildOptions();

            for (AppearanceMap map : maps) {
                if (map == null) {
                    continue;
                }

                VariableToggles toggles = map.toggles();

                for (FeatureSample sample : map.samples()) {
                    Point2D.Double center = simulatedStrokeCenter(sample, toggles, defaultOptions);
                    double radius = simulatedStrokeRadius(sample, toggles, defaultOptions);
                    double feather = simulatedStrokeFeather(sample, toggles, defaultOptions);

                    int pad = Math.max(4, MathEx.ceilInt(radius + feather + 3.0));

                    minX = Math.min(minX, MathEx.floorInt(center.x - pad));
                    minY = Math.min(minY, MathEx.floorInt(center.y - pad));
                    maxX = Math.max(maxX, MathEx.ceilInt(center.x + pad));
                    maxY = Math.max(maxY, MathEx.ceilInt(center.y + pad));
                }
            }

            if (minX == Integer.MAX_VALUE) {
                return new RenderBounds(0, 0, 512, 512);
            }

            int outerPad = 16;
            return new RenderBounds(
                    minX - outerPad,
                    minY - outerPad,
                    (maxX - minX) + (outerPad * 2) + 1,
                    (maxY - minY) + (outerPad * 2) + 1
            );
        }
    }


    private static final class Rasterizer {
        private Rasterizer() {
        }

        private static FastRGB renderInternal(
                AppearanceMap map,
                RenderBounds bounds,
                int sampleStride,
                BuildOptions options
        ) {
            FastRGB out = new FastRGB(bounds.width, bounds.height, true);
            out.fill(0xFFFFFFFF);

            if (map == null || map.size() == 0) {
                return out;
            }

            BuildOptions useOptions = options == null ? new BuildOptions() : options;
            VariableToggles toggles = map.toggles();
            boolean useOpacity = toggles.enabled(OutputVariable.OPACITY);

            int stride = Math.max(1, sampleStride);
            List<Point2D.Double> centers = new ArrayList<>((map.size() + stride - 1) / stride);
            List<FeatureSample> reduced = new ArrayList<>((map.size() + stride - 1) / stride);

            List<FeatureSample> samples = map.samples();
            for (int i = 0; i < map.size(); i += stride) {
                FeatureSample sample = samples.get(i);
                centers.add(simulatedStrokeCenter(sample, toggles, useOptions));
                reduced.add(sample);
            }

            int n = reduced.size();
            if (n == 1) {
                FeatureSample only = reduced.get(0);
                Point2D.Double c = centers.get(0);

                drawStamp(
                        out,
                        bounds.tx(c.x),
                        bounds.ty(c.y),
                        simulatedStrokeRadius(only, toggles, useOptions),
                        simulatedStrokeFeather(only, toggles, useOptions),
                        useOpacity ? clamp01(defaultIfZero(only.opacity(), 1.0)) : 1.0
                );
                return out;
            }

            for (int i = 0; i < n - 1; i++) {
                FeatureSample a = reduced.get(i);
                FeatureSample b = reduced.get(i + 1);

                Point2D.Double pa = centers.get(i);
                Point2D.Double pb = centers.get(i + 1);

                double aRadius = simulatedStrokeRadius(a, toggles, useOptions);
                double bRadius = simulatedStrokeRadius(b, toggles, useOptions);

                double aFeather = simulatedStrokeFeather(a, toggles, useOptions);
                double bFeather = simulatedStrokeFeather(b, toggles, useOptions);

                double aOpacity = useOpacity ? clamp01(defaultIfZero(a.opacity(), 1.0)) : 1.0;
                double bOpacity = useOpacity ? clamp01(defaultIfZero(b.opacity(), 1.0)) : 1.0;

                double len = pa.distance(pb);
                int steps = Math.max(1, MathEx.ceilInt(len * 6.0));

                for (int s = 0; s <= steps; s++) {
                    double t = steps == 0 ? 0.0 : (double) s / steps;

                    double x = lerp(pa.x, pb.x, t);
                    double y = lerp(pa.y, pb.y, t);

                    double radius = Math.max(0.5, lerp(aRadius, bRadius, t));
                    double feather = Math.max(0.20, lerp(aFeather, bFeather, t));
                    double opacity = clamp01(lerp(aOpacity, bOpacity, t));

                    drawStamp(
                            out,
                            bounds.tx(x),
                            bounds.ty(y),
                            radius,
                            feather,
                            opacity
                    );
                }
            }

            return out;
        }

        public static FastRGB render(AppearanceMap map, RenderBounds bounds, BuildOptions options) {
            return renderInternal(map, bounds, 1, options);
        }

        public static FastRGB render(AppearanceMap map, RenderBounds bounds, int sampleStride) {
            return renderInternal(map, bounds, sampleStride, null);
        }

        public static FastRGB render(AppearanceMap map, RenderBounds bounds) {
            return renderInternal(map, bounds, 1, null);
        }

        private static void drawStamp(FastRGB image, int cx, int cy, double radius, double feather, double opacity) {
            radius = Math.max(0.5, radius);
            feather = Math.max(0.2, feather);
            opacity = clamp01(opacity);

            int minX = Math.max(0, MathEx.floorInt(cx - radius - feather - 1));
            int maxX = Math.min(image.getWidth() - 1, MathEx.ceilInt(cx + radius + feather + 1));
            int minY = Math.max(0, MathEx.floorInt(cy - radius - feather - 1));
            int maxY = Math.min(image.getHeight() - 1, MathEx.ceilInt(cy + radius + feather + 1));

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    double dx = x - cx;
                    double dy = y - cy;
                    double dist = Math.hypot(dx, dy);

                    double alpha = coverage(dist, radius, feather) * opacity;
                    if (alpha <= 1.0e-6) {
                        continue;
                    }

                    int[] rgba = image.getRGBA(x, y);
                    int r = (int) Math.round(rgba[0] * (1.0 - alpha));
                    int g = (int) Math.round(rgba[1] * (1.0 - alpha));
                    int b = (int) Math.round(rgba[2] * (1.0 - alpha));

                    image.setRGB(x, y, new int[] {
                            MathEx.bound(r, 0, 255),
                            MathEx.bound(g, 0, 255),
                            MathEx.bound(b, 0, 255),
                            255
                    });
                }
            }
        }

        private static double coverage(double distance, double radius, double feather) {
            if (distance <= radius) {
                return 1.0;
            }
            if (distance >= radius + feather) {
                return 0.0;
            }
            double t = (distance - radius) / feather;
            return 1.0 - t;
        }
    }
    private static final class NormalPair {
        private final Point2D.Double inward;
        private final Point2D.Double outward;

        private NormalPair(Point2D.Double inward, Point2D.Double outward) {
            this.inward = inward;
            this.outward = outward;
        }
    }

    public interface ContourRebuilder {
        ShapeContour rebuild(List<Point> points);
    }

    private static final class ToggleMask {
        private final boolean inLocation;
        private final boolean inOrientation;
        private final boolean inDerivative;
        private final boolean inEndStop;

        private final boolean outThickness;
        private final boolean outBorderBlend;
        private final boolean outOpacity;
        private final boolean outBlotch;

        private ToggleMask(VariableToggles toggles) {
            this.inLocation = toggles.enabled(InputVariable.LOCATION);
            this.inOrientation = toggles.enabled(InputVariable.ORIENTATION);
            this.inDerivative = toggles.enabled(InputVariable.DERIVATIVE);
            this.inEndStop = toggles.enabled(InputVariable.END_STOP);

            this.outThickness = toggles.enabled(OutputVariable.THICKNESS);
            this.outBorderBlend = toggles.enabled(OutputVariable.BORDER_BLEND);
            this.outOpacity = toggles.enabled(OutputVariable.OPACITY);
            this.outBlotch = toggles.enabled(OutputVariable.BLOTCH);
        }
    }

    private static final class ContourGeometryCache {
        private final ShapeContour contour;
        private final Point[] points;
        private final double[] locations;
        private final double[] orientations;
        private final double[] derivatives;
        private final Point2D.Double[] tangents;
        private final Point2D.Double[] inwardNormals;
        private final Point2D.Double[] outwardNormals;
        private final boolean clockwise;

        private ContourGeometryCache(
                ShapeContour contour,
                Point[] points,
                double[] locations,
                double[] orientations,
                double[] derivatives,
                Point2D.Double[] tangents,
                Point2D.Double[] inwardNormals,
                Point2D.Double[] outwardNormals,
                boolean clockwise
        ) {
            this.contour = contour;
            this.points = points;
            this.locations = locations;
            this.orientations = orientations;
            this.derivatives = derivatives;
            this.tangents = tangents;
            this.inwardNormals = inwardNormals;
            this.outwardNormals = outwardNormals;
            this.clockwise = clockwise;
        }
    }

    private static ContourGeometryCache buildContourGeometryCache(ShapeContour contour) {
        if (contour == null || contour.isEmpty()) {
            return new ContourGeometryCache(
                    ShapeContour.empty(),
                    new Point[0],
                    new double[0],
                    new double[0],
                    new double[0],
                    new Point2D.Double[0],
                    new Point2D.Double[0],
                    new Point2D.Double[0],
                    false
            );
        }

        int n = contour.size();
        Point[] points = new Point[n];
        double[] locations = new double[n];
        double[] orientations = new double[n];
        double[] derivatives = new double[n];
        Point2D.Double[] tangents = new Point2D.Double[n];
        Point2D.Double[] inwardNormals = new Point2D.Double[n];
        Point2D.Double[] outwardNormals = new Point2D.Double[n];

        boolean clockwise = isContourClockwise(contour);

        for (int i = 0; i < n; i++) {
            Point prev = contour.get((i - 1 + n) % n);
            Point curr = contour.get(i);
            Point next = contour.get((i + 1) % n);

            points[i] = curr;

            Point2D.Double tangent = unit(next.x - prev.x, next.y - prev.y);
            tangents[i] = tangent;

            Point2D.Double[] normals = chooseNormalsFast(tangent, clockwise);
            inwardNormals[i] = normals[0];
            outwardNormals[i] = normals[1];

            locations[i] = contour.normalizedArcLengthTo(i);
            orientations[i] = normalizeDegrees(Math.toDegrees(Math.atan2(tangent.y, tangent.x)));
            derivatives[i] = finite(contour.laplacianAt(curr));
        }

        return new ContourGeometryCache(
                contour,
                points,
                locations,
                orientations,
                derivatives,
                tangents,
                inwardNormals,
                outwardNormals,
                clockwise
        );
    }

    /*
     * Default fallback keeps loadable full dumps compile-safe even if your local
     * ShapeContour constructor/factory is not visible here.
     *
     * In your app startup, wire this once if you have a real contour constructor:
     * PencilInkTransferSystem.setContourRebuilder(points -> new ShapeContour(points));
     */
    private static ContourRebuilder contourRebuilder = points -> ShapeContour.empty();

    public static void setContourRebuilder(ContourRebuilder rebuilder) {
        contourRebuilder = rebuilder == null ? (points -> ShapeContour.empty()) : rebuilder;
    }


    private String sliderManual(OutputVariable tab, String featureName) {
        String outputMeaning = switch (tab) {
            case THICKNESS -> "line thickness";
            case BORDER_BLEND -> "edge softness / outer blend";
            case OPACITY -> "opacity / darkness";
            case BLOTCH -> "outward contour drift / bulging";
        };

        return switch (featureName) {
            case "Bias" ->
                    "Adds a global baseline shift to " + outputMeaning + " across the whole drawing.";
            case "Location Along Contour" ->
                    "Changes " + outputMeaning + " according to where the point lies along the outline.";
            case "Orientation" ->
                    "Changes " + outputMeaning + " according to the local direction of the line.";
            case "Derivative" ->
                    "Changes " + outputMeaning + " according to how sharply the contour bends here.";
            case "End Stop" ->
                    "Changes " + outputMeaning + " near regions interpreted as stroke endings.";
            case "Current Thickness" ->
                    "Uses the pencil drawing's measured thickness here to influence " + outputMeaning + ".";
            case "Current Border Blend" ->
                    "Uses the pencil drawing's measured edge softness here to influence " + outputMeaning + ".";
            case "Current Opacity" ->
                    "Uses the pencil drawing's measured darkness here to influence " + outputMeaning + ".";
            case "Current Blotch" ->
                    "Uses the pencil drawing's measured bulge / irregularity here to influence " + outputMeaning + ".";
            default -> {
                if (featureName.contains("×")) {
                    yield "Changes " + outputMeaning + " when both parts of this interaction occur together.";
                }
                yield "Controls how this feature contributes to " + outputMeaning + ".";
            }
        };
    }

    private static final long DUMP_VERSION = 1L;

    private static final class ToggleState implements Serializable {
        private static final long serialVersionUID = 1L;

        private final EnumMap<InputVariable, Boolean> inputEnabled = new EnumMap<>(InputVariable.class);
        private final EnumMap<OutputVariable, Boolean> outputEnabled = new EnumMap<>(OutputVariable.class);
        private boolean includeCrossTerms;
        private boolean includeCurrentOutputsAsFeatures;
    }

    private static final class TrainingOptionsState implements Serializable {
        private static final long serialVersionUID = 1L;

        private int epochs;
        private double learningRate;
        private double regularization;
    }

    private static final class BuildOptionsState implements Serializable {
        private static final long serialVersionUID = 1L;

        private int sampleCount;
        private int contourStride;
        private int opacityProbeRadius;
        private int outwardBlendProbe;
        private int localAverageRadius;
        private int maxThickness;
        private double maxBlotchMagnitude;

        private boolean showLaunchConfigurationDialog;

        private boolean useSmoothedPencilAnchors;
        private int pencilAnchorAveragingRadius;
        private double pencilAnchorBlend;

        private boolean useSmoothedPencilContourPoint;
        private int pencilContourAveragingRadius;

        private boolean averageThicknessAndOpacityMeasurements;
        private int measurementAveragingRadius;
    }
    private static final class ModelState implements Serializable {
        private static final long serialVersionUID = 1L;

        private final EnumMap<OutputVariable, LinkedHashMap<String, Double>> weightsByFeature =
                new EnumMap<>(OutputVariable.class);
    }

    private static class PresetDump implements Serializable {
        private static final long serialVersionUID = 1L;

        long version = DUMP_VERSION;
        ToggleState toggles;
        TrainingOptionsState training;
        BuildOptionsState buildOptions;
        ModelState model;
        String note = "Preset dump";
    }



    private static final class PointState implements Serializable {
        private static final long serialVersionUID = 1L;

        private int x;
        private int y;

        private PointState() {
        }

        private PointState(Point p) {
            this.x = p.x;
            this.y = p.y;
        }

        private Point toPoint() {
            return new Point(x, y);
        }
    }

    private static final class VecState implements Serializable {
        private static final long serialVersionUID = 1L;

        private double x;
        private double y;

        private VecState() {
        }

        private VecState(Point2D.Double v) {
            this.x = v.x;
            this.y = v.y;
        }

        private Point2D.Double toVec() {
            return new Point2D.Double(x, y);
        }
    }

    private static final class FeatureSampleState implements Serializable {
        private static final long serialVersionUID = 1L;

        private int contourIndex;
        private double location;
        private PointState contourPoint;
        private PointState anchorPoint;
        private VecState tangent;
        private VecState inwardNormal;
        private VecState outwardNormal;

        private final EnumMap<InputVariable, Double> inputs = new EnumMap<>(InputVariable.class);
        private final EnumMap<OutputVariable, Double> outputs = new EnumMap<>(OutputVariable.class);
        private final HashMap<String, Double> diagnostics = new HashMap<>();
    }

    private static final class ContourState implements Serializable {
        private static final long serialVersionUID = 1L;

        private final ArrayList<PointState> points = new ArrayList<>();
    }

    private static final class AppearanceMapState implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private ToggleState toggles;
        private ContourState contour;
        private ArrayList<FeatureSampleState> samples = new ArrayList<>();
    }

    private static final class FullStateDump extends PresetDump {
        private static final long serialVersionUID = 1L;

        private AppearanceMapState pencilMap;
        private AppearanceMapState inkMap;
        private String note = "Full state dump";
    }

    private static BuildOptionsState snapshotBuildOptions(BuildOptions options) {
        BuildOptionsState state = new BuildOptionsState();
        if (options == null) {
            options = new BuildOptions();
        }

        state.sampleCount = options.sampleCount;
        state.contourStride = options.contourStride;
        state.opacityProbeRadius = options.opacityProbeRadius;
        state.outwardBlendProbe = options.outwardBlendProbe;
        state.localAverageRadius = options.localAverageRadius;
        state.maxThickness = options.maxThickness;
        state.maxBlotchMagnitude = options.maxBlotchMagnitude;

        state.showLaunchConfigurationDialog = options.showLaunchConfigurationDialog;

        state.useSmoothedPencilAnchors = options.useSmoothedPencilAnchors;
        state.pencilAnchorAveragingRadius = options.pencilAnchorAveragingRadius;
        state.pencilAnchorBlend = options.pencilAnchorBlend;

        state.useSmoothedPencilContourPoint = options.useSmoothedPencilContourPoint;
        state.pencilContourAveragingRadius = options.pencilContourAveragingRadius;

        state.averageThicknessAndOpacityMeasurements = options.averageThicknessAndOpacityMeasurements;
        state.measurementAveragingRadius = options.measurementAveragingRadius;

        return state;
    }
    private static BuildOptions restoreBuildOptions(BuildOptionsState state) {
        BuildOptions options = new BuildOptions();
        if (state == null) {
            return options;
        }

        options.sampleCount = state.sampleCount;
        options.contourStride = state.contourStride;
        options.opacityProbeRadius = state.opacityProbeRadius;
        options.outwardBlendProbe = state.outwardBlendProbe;
        options.localAverageRadius = state.localAverageRadius;
        options.maxThickness = state.maxThickness;
        options.maxBlotchMagnitude = state.maxBlotchMagnitude;

        options.showLaunchConfigurationDialog = state.showLaunchConfigurationDialog;

        options.useSmoothedPencilAnchors = state.useSmoothedPencilAnchors;
        options.pencilAnchorAveragingRadius = state.pencilAnchorAveragingRadius;
        options.pencilAnchorBlend = state.pencilAnchorBlend;

        options.useSmoothedPencilContourPoint = state.useSmoothedPencilContourPoint;
        options.pencilContourAveragingRadius = state.pencilContourAveragingRadius;

        options.averageThicknessAndOpacityMeasurements = state.averageThicknessAndOpacityMeasurements;
        options.measurementAveragingRadius = state.measurementAveragingRadius;

        return options;
    }
    private static Point averageContourPoint(ContourGeometryCache cache, int contourIndex, int radius) {
        if (cache == null || cache.points.length == 0) {
            return new Point();
        }

        int n = cache.points.length;
        int r = Math.max(0, radius);

        if (r == 0) {
            return new Point(cache.points[contourIndex]);
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumW = 0.0;

        for (int d = -r; d <= r; d++) {
            int idx = Math.floorMod(contourIndex + d, n);
            Point p = cache.points[idx];
            double w = (r + 1) - Math.abs(d);

            sumX += p.x * w;
            sumY += p.y * w;
            sumW += w;
        }

        if (!(sumW > 1.0e-9)) {
            return new Point(cache.points[contourIndex]);
        }

        return new Point(
                MathEx.roundInt(sumX / sumW),
                MathEx.roundInt(sumY / sumW)
        );
    }
    private static Point robustClosestPoint(PointCollection cloud, Point target) {
        if (cloud == null || cloud.isEmpty()) {
            return new Point(target);
        }
        return safePoint(cloud.closest(target), target);
    }

    private static Point computeRobustPencilAnchor(
            ContourGeometryCache cache,
            PointCollection cloud,
            int contourIndex,
            BuildOptions options
    ) {
        Point baseContourPoint = options.useSmoothedPencilContourPoint
                ? averageContourPoint(cache, contourIndex, options.pencilContourAveragingRadius)
                : new Point(cache.points[contourIndex]);

        Point baseAnchor = robustClosestPoint(cloud, baseContourPoint);

        if (!options.useSmoothedPencilAnchors || options.pencilAnchorAveragingRadius <= 0) {
            return baseAnchor;
        }

        int radius = Math.max(1, options.pencilAnchorAveragingRadius);
        int n = cache.points.length;

        double sumX = 0.0;
        double sumY = 0.0;
        double sumW = 0.0;

        for (int d = -radius; d <= radius; d++) {
            int idx = Math.floorMod(contourIndex + d, n);

            Point target = options.useSmoothedPencilContourPoint
                    ? averageContourPoint(cache, idx, options.pencilContourAveragingRadius)
                    : cache.points[idx];

            Point closest = robustClosestPoint(cloud, target);
            double w = (radius + 1) - Math.abs(d);

            sumX += closest.x * w;
            sumY += closest.y * w;
            sumW += w;
        }

        if (!(sumW > 1.0e-9)) {
            return baseAnchor;
        }

        Point averagedAnchor = new Point(
                MathEx.roundInt(sumX / sumW),
                MathEx.roundInt(sumY / sumW)
        );

        double blend = MathEx.bound(options.pencilAnchorBlend, 0.0, 1.0);

        return new Point(
                MathEx.roundInt(lerp(baseAnchor.x, averagedAnchor.x, blend)),
                MathEx.roundInt(lerp(baseAnchor.y, averagedAnchor.y, blend))
        );
    }

    private static BuildOptions configureBuildOptionsOnLaunch(BuildOptions initial) {
        BuildOptions configured = initial == null ? new BuildOptions() : initial.copy();

        if (!configured.showLaunchConfigurationDialog || GraphicsEnvironment.isHeadless()) {
            return configured;
        }

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));

        JSpinner sampleCount = new JSpinner(new SpinnerNumberModel(configured.sampleCount, 0, 500000, 1));
        JSpinner contourStride = new JSpinner(new SpinnerNumberModel(configured.contourStride, 1, 1024, 1));
        JSpinner opacityProbeRadius = new JSpinner(new SpinnerNumberModel(configured.opacityProbeRadius, 1, 128, 1));
        JSpinner outwardBlendProbe = new JSpinner(new SpinnerNumberModel(configured.outwardBlendProbe, 1, 256, 1));
        JSpinner localAverageRadius = new JSpinner(new SpinnerNumberModel(configured.localAverageRadius, 1, 256, 1));
        JSpinner maxThickness = new JSpinner(new SpinnerNumberModel(configured.maxThickness, 1, 4096, 1));
        JSpinner maxBlotchMagnitude = new JSpinner(new SpinnerNumberModel(configured.maxBlotchMagnitude, 0.0, 1024.0, 0.5));

        JCheckBox useSmoothedPencilAnchors = new JCheckBox("Use Smoothed Pencil Anchors", configured.useSmoothedPencilAnchors);
        JSpinner pencilAnchorAveragingRadius = new JSpinner(new SpinnerNumberModel(configured.pencilAnchorAveragingRadius, 0, 256, 1));
        JSpinner pencilAnchorBlend = new JSpinner(new SpinnerNumberModel(configured.pencilAnchorBlend, 0.0, 1.0, 0.01));

        JCheckBox useSmoothedPencilContourPoint = new JCheckBox("Use Smoothed Pencil Contour Point", configured.useSmoothedPencilContourPoint);
        JSpinner pencilContourAveragingRadius = new JSpinner(new SpinnerNumberModel(configured.pencilContourAveragingRadius, 0, 256, 1));

        JCheckBox averageThicknessAndOpacityMeasurements = new JCheckBox(
                "Average Thickness / Opacity Measurements",
                configured.averageThicknessAndOpacityMeasurements
        );
        JSpinner measurementAveragingRadius = new JSpinner(new SpinnerNumberModel(configured.measurementAveragingRadius, 0, 256, 1));

        panel.add(new JLabel("Sample Count"));
        panel.add(sampleCount);

        panel.add(new JLabel("Contour Stride"));
        panel.add(contourStride);

        panel.add(new JLabel("Opacity Probe Radius"));
        panel.add(opacityProbeRadius);

        panel.add(new JLabel("Outward Blend Probe"));
        panel.add(outwardBlendProbe);

        panel.add(new JLabel("Local Average Radius"));
        panel.add(localAverageRadius);

        panel.add(new JLabel("Max Thickness"));
        panel.add(maxThickness);

        panel.add(new JLabel("Max Blotch Magnitude"));
        panel.add(maxBlotchMagnitude);

        panel.add(useSmoothedPencilAnchors);
        panel.add(new JLabel(""));

        panel.add(new JLabel("Pencil Anchor Averaging Radius"));
        panel.add(pencilAnchorAveragingRadius);

        panel.add(new JLabel("Pencil Anchor Blend"));
        panel.add(pencilAnchorBlend);

        panel.add(useSmoothedPencilContourPoint);
        panel.add(new JLabel(""));

        panel.add(new JLabel("Pencil Contour Averaging Radius"));
        panel.add(pencilContourAveragingRadius);

        panel.add(averageThicknessAndOpacityMeasurements);
        panel.add(new JLabel(""));

        panel.add(new JLabel("Measurement Averaging Radius"));
        panel.add(measurementAveragingRadius);

        int result = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Pencil → Ink Launch Configuration",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return configured;
        }

        configured.sampleCount = (Integer) sampleCount.getValue();
        configured.contourStride = (Integer) contourStride.getValue();
        configured.opacityProbeRadius = (Integer) opacityProbeRadius.getValue();
        configured.outwardBlendProbe = (Integer) outwardBlendProbe.getValue();
        configured.localAverageRadius = (Integer) localAverageRadius.getValue();
        configured.maxThickness = (Integer) maxThickness.getValue();
        configured.maxBlotchMagnitude = ((Number) maxBlotchMagnitude.getValue()).doubleValue();

        configured.useSmoothedPencilAnchors = useSmoothedPencilAnchors.isSelected();
        configured.pencilAnchorAveragingRadius = (Integer) pencilAnchorAveragingRadius.getValue();
        configured.pencilAnchorBlend = ((Number) pencilAnchorBlend.getValue()).doubleValue();

        configured.useSmoothedPencilContourPoint = useSmoothedPencilContourPoint.isSelected();
        configured.pencilContourAveragingRadius = (Integer) pencilContourAveragingRadius.getValue();

        configured.averageThicknessAndOpacityMeasurements = averageThicknessAndOpacityMeasurements.isSelected();
        configured.measurementAveragingRadius = (Integer) measurementAveragingRadius.getValue();

        return configured;
    }
    private static ToggleState snapshotToggles(VariableToggles toggles) {
        ToggleState state = new ToggleState();
        for (InputVariable variable : InputVariable.values()) {
            state.inputEnabled.put(variable, toggles.enabled(variable));
        }
        for (OutputVariable variable : OutputVariable.values()) {
            state.outputEnabled.put(variable, toggles.enabled(variable));
        }
        state.includeCrossTerms = toggles.includeCrossTerms;
        state.includeCurrentOutputsAsFeatures = toggles.includeCurrentOutputsAsFeatures;
        return state;
    }

    private static VariableToggles restoreToggles(ToggleState state) {
        VariableToggles toggles = VariableToggles.allEnabled();
        if (state != null) {
            for (InputVariable variable : InputVariable.values()) {
                toggles.set(variable, state.inputEnabled.getOrDefault(variable, true));
            }
            for (OutputVariable variable : OutputVariable.values()) {
                toggles.set(variable, state.outputEnabled.getOrDefault(variable, true));
            }
            toggles.includeCrossTerms = state.includeCrossTerms;
            toggles.includeCurrentOutputsAsFeatures = state.includeCurrentOutputsAsFeatures;
        }

        return applyFactorInfluenceMode(toggles);
    }

    private static TrainingOptionsState snapshotTraining(TrainingOptions options) {
        TrainingOptionsState state = new TrainingOptionsState();
        state.epochs = options.epochs;
        state.learningRate = options.learningRate;
        state.regularization = options.regularization;
        return state;
    }

    private static TrainingOptions restoreTraining(TrainingOptionsState state) {
        TrainingOptions options = new TrainingOptions();
        if (state == null) {
            return options;
        }
        options.epochs = state.epochs;
        options.learningRate = state.learningRate;
        options.regularization = state.regularization;
        return options;
    }

    private static ModelState snapshotModel(LinearTransferModel model) {
        ModelState state = new ModelState();

        for (OutputVariable output : OutputVariable.values()) {
            LinkedHashMap<String, Double> featureWeights = new LinkedHashMap<>();
            for (int i = 0; i < model.featureCount(); i++) {
                featureWeights.put(model.featureName(i), model.weight(output, i));
            }
            state.weightsByFeature.put(output, featureWeights);
        }

        return state;
    }

    private static void applyModelState(LinearTransferModel model, ModelState state) {
        if (state == null) {
            return;
        }

        for (OutputVariable output : OutputVariable.values()) {
            Map<String, Double> saved = state.weightsByFeature.get(output);
            if (saved == null) {
                continue;
            }

            for (int i = 0; i < model.featureCount(); i++) {
                String name = model.featureName(i);
                if (saved.containsKey(name)) {
                    model.setWeight(output, i, saved.get(name));
                }
            }
        }
    }

    private static FeatureSampleState snapshotFeatureSample(FeatureSample sample) {
        FeatureSampleState state = new FeatureSampleState();
        state.contourIndex = sample.contourIndex;
        state.location = sample.location;
        state.contourPoint = new PointState(sample.contourPoint);
        state.anchorPoint = new PointState(sample.anchorPoint);
        state.tangent = new VecState(sample.tangent);
        state.inwardNormal = new VecState(sample.inwardNormal);
        state.outwardNormal = new VecState(sample.outwardNormal);

        for (InputVariable variable : InputVariable.values()) {
            state.inputs.put(variable, sample.input(variable));
        }
        for (OutputVariable variable : OutputVariable.values()) {
            state.outputs.put(variable, sample.output(variable));
        }

        state.diagnostics.put("orientationDegrees", sample.diagnostic("orientationDegrees"));
        state.diagnostics.put("derivativeRaw", sample.diagnostic("derivativeRaw"));
        state.diagnostics.put("endStopScore", sample.diagnostic("endStopScore"));
        state.diagnostics.put("anchorDistance", sample.diagnostic("anchorDistance"));
        state.diagnostics.put("thicknessDeviation", sample.diagnostic("thicknessDeviation"));

        return state;
    }

    private static FeatureSample restoreFeatureSample(FeatureSampleState state) {
        FeatureSample sample = new FeatureSample(
                state.contourIndex,
                state.location,
                state.contourPoint.toPoint(),
                state.anchorPoint.toPoint(),
                state.tangent.toVec(),
                state.inwardNormal.toVec(),
                state.outwardNormal.toVec()
        );

        for (InputVariable variable : InputVariable.values()) {
            sample.setInput(variable, state.inputs.getOrDefault(variable, 0.0));
        }
        for (OutputVariable variable : OutputVariable.values()) {
            sample.setOutput(variable, state.outputs.getOrDefault(variable, 0.0));
        }
        for (Map.Entry<String, Double> entry : state.diagnostics.entrySet()) {
            sample.setDiagnostic(entry.getKey(), entry.getValue());
        }

        return sample;
    }

    private static ContourState snapshotContour(ShapeContour contour) {
        ContourState state = new ContourState();
        if (contour == null || contour.isEmpty()) {
            return state;
        }

        for (int i = 0; i < contour.size(); i++) {
            state.points.add(new PointState(contour.get(i)));
        }

        return state;
    }

    private static ShapeContour restoreContour(ContourState state) {
        if (state == null || state.points.isEmpty()) {
            return ShapeContour.empty();
        }

        ArrayList<Point> points = new ArrayList<>(state.points.size());
        for (PointState pointState : state.points) {
            points.add(pointState.toPoint());
        }

        return contourRebuilder.rebuild(points);
    }

    private static AppearanceMapState snapshotAppearanceMap(AppearanceMap map) {
        AppearanceMapState state = new AppearanceMapState();
        state.name = map.name();
        state.toggles = snapshotToggles(map.toggles());
        state.contour = snapshotContour(map.contour());

        for (FeatureSample sample : map.samples()) {
            state.samples.add(snapshotFeatureSample(sample));
        }

        return state;
    }

    private static AppearanceMap restoreAppearanceMap(AppearanceMapState state) {
        VariableToggles toggles = restoreToggles(state.toggles);
        ShapeContour contour = restoreContour(state.contour);
        ArrayList<FeatureSample> samples = new ArrayList<>(state.samples.size());

        for (FeatureSampleState sampleState : state.samples) {
            samples.add(restoreFeatureSample(sampleState));
        }

        return new AppearanceMap(state.name, contour, samples, toggles);
    }

    private static PresetDump snapshotPresetDump(Workbench workbench) {
        PresetDump dump = new PresetDump();
        dump.toggles = snapshotToggles(workbench.actualInkMap.toggles());
        dump.training = snapshotTraining(workbench.trainingOptions);
        dump.buildOptions = snapshotBuildOptions(workbench.buildOptions);
        dump.model = snapshotModel(workbench.model);
        return dump;
    }

    private static FullStateDump snapshotFullStateDump(Workbench workbench) {
        FullStateDump dump = new FullStateDump();
        dump.toggles = snapshotToggles(workbench.actualInkMap.toggles());
        dump.training = snapshotTraining(workbench.trainingOptions);
        dump.buildOptions = snapshotBuildOptions(workbench.buildOptions);
        dump.model = snapshotModel(workbench.model);
        dump.pencilMap = snapshotAppearanceMap(workbench.pencilMap);
        dump.inkMap = snapshotAppearanceMap(workbench.actualInkMap);
        return dump;
    }

    private static void writeDump(File file, Serializable dump) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeObject(dump);
        }
    }

    private static Object readDump(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            return in.readObject();
        }
    }


    private static double normalizedBoundValue(double value, double a, double b) {
        double min = Math.min(a, b);
        double max = Math.max(a, b);
        if (Math.abs(max - min) < 1.0e-9) {
            return 0.5;
        }
        return (value - min) / (max - min);
    }

    private static double circular01Distance(double a, double b) {
        double da = Math.abs(a - b);
        return Math.min(da, 1.0 - da);
    }

    private static double orientationDistanceDegrees(double a, double b) {
        double da = Math.abs(normalizeDegrees(a) - normalizeDegrees(b));
        da = Math.min(da, 360.0 - da);
        return da / 180.0;
    }

    private static double tangentDegrees(FeatureSample sample) {
        return normalizeDegrees(Math.toDegrees(Math.atan2(sample.tangent.y, sample.tangent.x)));
    }

    private static double rawDerivative(FeatureSample sample) {
        return finite(sample.diagnostic("derivativeRaw"));
    }

    private static double normalizedRadius(ShapeContour contour, Point point) {
        if (contour == null || contour.isEmpty() || point == null) {
            return 0.0;
        }

        Point2D.Double c = contour.centroid();
        double dx = point.x - c.x;
        double dy = point.y - c.y;
        double r = Math.hypot(dx, dy);

        double spanX = Math.abs(contour.lastX() - contour.firstX());
        double spanY = Math.abs(contour.topY() - contour.bottomY());
        double scale = Math.max(1.0, Math.hypot(spanX, spanY));

        return r / scale;
    }

    private static double normalizedPointDelta(
            ShapeContour contourA,
            Point pointA,
            ShapeContour contourB,
            Point pointB
    ) {
        if (contourA == null || contourA.isEmpty() || contourB == null || contourB.isEmpty() || pointA == null || pointB == null) {
            return 0.0;
        }

        double ax = normalizedBoundValue(pointA.x, contourA.firstX(), contourA.lastX());
        double ay = normalizedBoundValue(pointA.y, contourA.bottomY(), contourA.topY());

        double bx = normalizedBoundValue(pointB.x, contourB.firstX(), contourB.lastX());
        double by = normalizedBoundValue(pointB.y, contourB.bottomY(), contourB.topY());

        return Math.hypot(ax - bx, ay - by);
    }

    private static int nearestSampleIndexByLocation(List<FeatureSample> samples, double location) {
        if (samples == null || samples.isEmpty()) {
            return -1;
        }

        int low = 0;
        int high = samples.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            double here = samples.get(mid).location;
            if (here < location) {
                low = mid + 1;
            } else if (here > location) {
                high = mid - 1;
            } else {
                return mid;
            }
        }

        if (low >= samples.size()) {
            return samples.size() - 1;
        }
        if (high < 0) {
            return 0;
        }

        double dLow = Math.abs(samples.get(low).location - location);
        double dHigh = Math.abs(samples.get(high).location - location);
        return dLow <= dHigh ? low : high;
    }

    private static double correspondenceScore(
            ShapeContour pencilContour,
            FeatureSample pencilSample,
            ShapeContour inkContour,
            FeatureSample inkSample,
            CorrespondenceOptions options
    ) {
        double arcDelta = circular01Distance(pencilSample.location, inkSample.location);

        double positionDelta = normalizedPointDelta(
                pencilContour,
                pencilSample.contourPoint,
                inkContour,
                inkSample.contourPoint
        );

        double orientationDelta = orientationDistanceDegrees(
                tangentDegrees(pencilSample),
                tangentDegrees(inkSample)
        );

        double derivativeDelta = Math.abs(rawDerivative(pencilSample) - rawDerivative(inkSample));
        derivativeDelta = Math.min(1.0, derivativeDelta);

        double radialDelta = Math.abs(
                normalizedRadius(pencilContour, pencilSample.contourPoint) -
                        normalizedRadius(inkContour, inkSample.contourPoint)
        );

        return
                (options.positionWeight * positionDelta) +
                        (options.orientationWeight * orientationDelta) +
                        (options.derivativeWeight * derivativeDelta) +
                        (options.radialWeight * radialDelta) +
                        (options.arcPenaltyWeight * arcDelta);
    }

    private static int bestInkIndexForPencil(
            AppearanceMap pencilMap,
            FeatureSample pencilSample,
            AppearanceMap inkMap,
            List<FeatureSample> inkSamples,
            int seedIndex,
            CorrespondenceOptions options
    ) {
        if (inkSamples.isEmpty()) {
            return -1;
        }

        int n = inkSamples.size();
        int bestIndex = Math.max(0, Math.min(seedIndex, n - 1));
        double bestScore = Double.POSITIVE_INFINITY;
        boolean foundLocal = false;

        for (int offset = -options.searchStepsPerSide; offset <= options.searchStepsPerSide; offset++) {
            int idx = Math.floorMod(seedIndex + offset, n);
            FeatureSample candidate = inkSamples.get(idx);
            double arcDelta = circular01Distance(pencilSample.location, candidate.location);
            if (arcDelta > options.searchRadiusNormalized) {
                continue;
            }

            double score = correspondenceScore(pencilMap.contour(), pencilSample, inkMap.contour(), candidate, options);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = idx;
                foundLocal = true;
            }
        }

        if (foundLocal) {
            return bestIndex;
        }

        for (int idx = 0; idx < n; idx++) {
            FeatureSample candidate = inkSamples.get(idx);
            double score = correspondenceScore(pencilMap.contour(), pencilSample, inkMap.contour(), candidate, options);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = idx;
            }
        }

        return bestIndex;
    }

    private static int bestPencilIndexForInk(
            AppearanceMap pencilMap,
            List<FeatureSample> pencilSamples,
            AppearanceMap inkMap,
            FeatureSample inkSample,
            int seedIndex,
            CorrespondenceOptions options
    ) {
        if (pencilSamples.isEmpty()) {
            return -1;
        }

        int n = pencilSamples.size();
        int bestIndex = Math.max(0, Math.min(seedIndex, n - 1));
        double bestScore = Double.POSITIVE_INFINITY;
        boolean foundLocal = false;

        for (int offset = -options.searchStepsPerSide; offset <= options.searchStepsPerSide; offset++) {
            int idx = Math.floorMod(seedIndex + offset, n);
            FeatureSample candidate = pencilSamples.get(idx);
            double arcDelta = circular01Distance(candidate.location, inkSample.location);
            if (arcDelta > options.searchRadiusNormalized) {
                continue;
            }

            double score = correspondenceScore(pencilMap.contour(), candidate, inkMap.contour(), inkSample, options);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = idx;
                foundLocal = true;
            }
        }

        if (foundLocal) {
            return bestIndex;
        }

        for (int idx = 0; idx < n; idx++) {
            FeatureSample candidate = pencilSamples.get(idx);
            double score = correspondenceScore(pencilMap.contour(), candidate, inkMap.contour(), inkSample, options);
            if (score < bestScore) {
                bestScore = score;
                bestIndex = idx;
            }
        }

        return bestIndex;
    }

    private static List<ContourCorrespondenceSample> buildNaiveCorrespondence(
            AppearanceMap pencilMap,
            AppearanceMap inkMap
    ) {
        List<ContourCorrespondenceSample> out = new ArrayList<>();
        for (FeatureSample pencilSample : pencilMap.samples()) {
            FeatureSample inkSample = inkMap.nearestByLocation(pencilSample.location);
            if (inkSample == null) {
                continue;
            }
            out.add(new ContourCorrespondenceSample(
                    0.5 * (pencilSample.location + inkSample.location),
                    pencilSample,
                    inkSample,
                    1.0
            ));
        }
        return out;
    }

    public static List<ContourCorrespondenceSample> buildCorrespondence(
            AppearanceMap pencilMap,
            AppearanceMap inkMap,
            CorrespondenceOptions options
    ) {
        if (pencilMap == null || inkMap == null || pencilMap.size() == 0 || inkMap.size() == 0) {
            return Collections.emptyList();
        }

        CorrespondenceOptions useOptions = options == null ? new CorrespondenceOptions() : options.copy();

        List<FeatureSample> pencilSamples = pencilMap.samples();
        List<FeatureSample> inkSamples = inkMap.samples();
        List<ContourCorrespondenceSample> out = new ArrayList<>(pencilSamples.size());

        for (int i = 0; i < pencilSamples.size(); i++) {
            FeatureSample pencilSample = pencilSamples.get(i);
            int inkSeed = nearestSampleIndexByLocation(inkSamples, pencilSample.location);
            int bestInkIndex = bestInkIndexForPencil(
                    pencilMap,
                    pencilSample,
                    inkMap,
                    inkSamples,
                    inkSeed,
                    useOptions
            );

            if (bestInkIndex < 0) {
                continue;
            }

            FeatureSample bestInk = inkSamples.get(bestInkIndex);

            double score = correspondenceScore(
                    pencilMap.contour(),
                    pencilSample,
                    inkMap.contour(),
                    bestInk,
                    useOptions
            );

            int reverseSeed = nearestSampleIndexByLocation(pencilSamples, bestInk.location);
            int reverseBestPencilIndex = bestPencilIndexForInk(
                    pencilMap,
                    pencilSamples,
                    inkMap,
                    bestInk,
                    reverseSeed,
                    useOptions
            );

            double confidence = 1.0 / (1.0 + score);
            if (reverseBestPencilIndex != i) {
                confidence *= (1.0 - useOptions.reverseMismatchPenalty);
            }
            confidence = MathEx.bound(confidence, useOptions.minimumConfidence, 1.0);

            out.add(new ContourCorrespondenceSample(
                    0.5 * (pencilSample.location + bestInk.location),
                    pencilSample,
                    bestInk,
                    confidence
            ));
        }

        out.sort(Comparator.comparingDouble(ContourCorrespondenceSample::sharedLocation));
        return out;
    }

    private static Point safePoint(Point point, Point fallback) {
        return point == null ? new Point(fallback) : new Point(point);
    }

    private static NormalPair chooseNormals(ShapeContour contour, Point contourPoint, Point2D.Double tangent) {
        Point2D.Double candidate = unit(-tangent.y, tangent.x);
        Point probe = offset(contourPoint, candidate, 2.0);

        Point2D.Double inward;
        Point2D.Double outward;

        if (contour.inside(probe)) {
            inward = candidate;
            outward = new Point2D.Double(-candidate.x, -candidate.y);
        } else {
            outward = candidate;
            inward = new Point2D.Double(-candidate.x, -candidate.y);
        }

        return new NormalPair(inward, outward);
    }
private static boolean isContourClockwise(ShapeContour contour) {
    if (contour == null || contour.isEmpty() || contour.size() < 3) {
        return false;
    }

    double twiceArea = 0.0;
    int n = contour.size();

    for (int i = 0; i < n; i++) {
        Point a = contour.get(i);
        Point b = contour.get((i + 1) % n);
        twiceArea += ((double) a.x * b.y) - ((double) b.x * a.y);
    }

    return twiceArea < 0.0;
}

    private static Point2D.Double[] chooseNormalsFast(Point2D.Double tangent, boolean clockwise) {
        Point2D.Double left = new Point2D.Double(-tangent.y, tangent.x);
        Point2D.Double right = new Point2D.Double(tangent.y, -tangent.x);

        Point2D.Double inward;
        Point2D.Double outward;

        if (clockwise) {
            inward = left;
            outward = right;
        } else {
            inward = right;
            outward = left;
        }

        return new Point2D.Double[] {inward, outward};
    }

    private static double estimateAveragedThickness(
            ContourGeometryCache cache,
            PointCollection points,
            int contourIndex,
            Point2D.Double inward,
            Point2D.Double outward,
            BuildOptions options
    ) {
        if (points == null || points.isEmpty()) {
            return 1.0;
        }

        int radius = Math.max(0, options.measurementAveragingRadius);
        int n = cache.points.length;

        double sum = 0.0;
        double sumW = 0.0;

        for (int d = -radius; d <= radius; d++) {
            int idx = Math.floorMod(contourIndex + d, n);
            Point anchor = computeRobustPencilAnchor(cache, points, idx, options);
            double value = estimateThicknessFromAnchor(points, anchor, inward, outward, options);
            double w = (radius + 1) - Math.abs(d);

            sum += value * w;
            sumW += w;
        }

        if (!(sumW > 1.0e-9)) {
            Point anchor = computeRobustPencilAnchor(cache, points, contourIndex, options);
            return estimateThicknessFromAnchor(points, anchor, inward, outward, options);
        }

        return MathEx.bound(sum / sumW, 0.5, Math.max(0.5, options.maxThickness));
    }

    private static double estimateThicknessFromAnchor(
            PointCollection points,
            Point anchor,
            Point2D.Double inward,
            Point2D.Double outward,
            BuildOptions options
    ) {
        if (points == null || points.isEmpty()) {
            return 1.0;
        }

        Point start = points.contains(anchor) ? new Point(anchor) : robustClosestPoint(points, anchor);

        double inwardDegrees = normalizeDegrees(Math.toDegrees(Math.atan2(inward.y, inward.x)));
        double outwardDegrees = normalizeDegrees(Math.toDegrees(Math.atan2(outward.y, outward.x)));

        Point inProbe = points.probeDegree(start, inwardDegrees);
        Point outProbe = points.probeDegree(start, outwardDegrees);

        double inDist = start.distance(inProbe);
        double outDist = start.distance(outProbe);
        double thickness = 0.5 + ((inDist + outDist) * THICKNESS_ESTIMATE_SCALE);

        return MathEx.bound(thickness, 0.5, Math.max(0.5, options.maxThickness));
    }

    private static double estimateThickness(
            PointCollection points,
            Point anchor,
            Point2D.Double inward,
            Point2D.Double outward,
            BuildOptions options
    ) {
        return estimateThicknessFromAnchor(points, anchor, inward, outward, options);
    }

    private static double estimateOpacityFromAnchor(
            FastRGB scan,
            Point anchor,
            Point2D.Double inward,
            BuildOptions options
    ) {
        if (scan == null) {
            return 1.0;
        }

        int radius = Math.max(1, options.opacityProbeRadius);
        double sum = 0.0;
        int count = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if ((dx * dx) + (dy * dy) > radius * radius) {
                    continue;
                }
                sum += darknessAt(scan, anchor.x + dx, anchor.y + dy);
                count++;
            }
        }

        for (int i = 1; i <= radius; i++) {
            sum += darknessAt(scan, anchor.x + inward.x * i, anchor.y + inward.y * i);
            count++;
        }

        return clamp01(count == 0 ? 0.0 : sum / count);
    }

    private static double estimateAveragedOpacity(
            ContourGeometryCache cache,
            PointCollection points,
            FastRGB scan,
            int contourIndex,
            Point2D.Double inward,
            BuildOptions options
    ) {
        if (scan == null) {
            return 1.0;
        }

        int radius = Math.max(0, options.measurementAveragingRadius);
        int n = cache.points.length;

        double sum = 0.0;
        double sumW = 0.0;

        for (int d = -radius; d <= radius; d++) {
            int idx = Math.floorMod(contourIndex + d, n);
            Point anchor = points == null || points.isEmpty()
                    ? averageContourPoint(cache, idx, options.pencilContourAveragingRadius)
                    : computeRobustPencilAnchor(cache, points, idx, options);

            double value = estimateOpacityFromAnchor(scan, anchor, inward, options);
            double w = (radius + 1) - Math.abs(d);

            sum += value * w;
            sumW += w;
        }

        if (!(sumW > 1.0e-9)) {
            Point anchor = points == null || points.isEmpty()
                    ? averageContourPoint(cache, contourIndex, options.pencilContourAveragingRadius)
                    : computeRobustPencilAnchor(cache, points, contourIndex, options);
            return estimateOpacityFromAnchor(scan, anchor, inward, options);
        }

        return clamp01(sum / sumW);
    }

    private static double estimateOpacity(
            FastRGB scan,
            Point anchor,
            Point2D.Double inward,
            BuildOptions options
    ) {
        if (scan == null) {
            return 1.0;
        }

        int radius = Math.max(1, options.opacityProbeRadius);
        double sum = 0.0;
        int count = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if ((dx * dx) + (dy * dy) > radius * radius) {
                    continue;
                }
                sum += darknessAt(scan, anchor.x + dx, anchor.y + dy);
                count++;
            }
        }

        for (int i = 1; i <= radius; i++) {
            sum += darknessAt(scan, anchor.x + inward.x * i, anchor.y + inward.y * i);
            count++;
        }

        return clamp01(count == 0 ? 0.0 : sum / count);
    }

    private static double estimateBorderBlendOneSide(
            FastRGB scan,
            Point contourPoint,
            Point2D.Double direction,
            double edgeDarkness,
            BuildOptions options
    ) {
        int radius = Math.max(1, options.outwardBlendProbe);
        double weightedDarkness = 0.0;
        double weightSum = 0.0;

        for (int step = 1; step <= radius; step++) {
            double darkness = darknessAt(
                    scan,
                    contourPoint.x + direction.x * step,
                    contourPoint.y + direction.y * step
            );

            double weight = 1.0 - ((step - 1.0) / Math.max(1.0, radius));
            weightedDarkness += darkness * weight;
            weightSum += weight;
        }

        if (!(weightSum > 1.0e-9) || edgeDarkness <= BORDER_BLEND_MIN_EDGE_DARKNESS_THRESHOLD) {
            return 0.0;
        }

        return clamp01(((weightedDarkness / weightSum) * BORDER_BLEND_OUTSIDE_DARKNESS_SCALE) / edgeDarkness);
    }

    private static double estimateBorderBlend(
            FastRGB scan,
            Point contourPoint,
            Point2D.Double outward,
            double opacity,
            BuildOptions options
    ) {
        if (scan == null || opacity <= BORDER_BLEND_MIN_OPACITY_THRESHOLD) {
            return 0.0;
        }

        double edgeDarkness = darknessAt(scan, contourPoint.x, contourPoint.y);
        if (edgeDarkness <= BORDER_BLEND_MIN_EDGE_DARKNESS_THRESHOLD) {
            return 0.0;
        }

        int radius = Math.max(1, options.outwardBlendProbe);

        double outwardBlend = estimateBorderBlendOneSide(
                scan,
                contourPoint,
                outward,
                edgeDarkness,
                options
        );

        if (!BORDER_BLEND_SCAN_BOTH_SIDES) {
            return outwardBlend;
        }

        Point2D.Double inward = new Point2D.Double(-outward.x, -outward.y);
        double inwardBlend = estimateBorderBlendOneSide(
                scan,
                contourPoint,
                inward,
                edgeDarkness,
                options
        );

        return BORDER_BLEND_COMBINE_BY_MAX
                ? Math.max(outwardBlend, inwardBlend)
                : clamp01((outwardBlend + inwardBlend) * 0.5);
    }

    private static double estimateBlotch(
            ShapeContour contour,
            int contourIndex,
            Point anchor,
            Point2D.Double outwardNormal,
            BuildOptions options
    ) {
        Point here = contour.get(contourIndex);

        double anchorDx = anchor.x - here.x;
        double anchorDy = anchor.y - here.y;
        double anchorSigned = (anchorDx * outwardNormal.x) + (anchorDy * outwardNormal.y);

        int n = contour.size();
        int radius = Math.max(1, options.localAverageRadius);

        double avgX = 0.0;
        double avgY = 0.0;
        int count = 0;

        for (int d = -radius; d <= radius; d++) {
            if (d == 0) {
                continue;
            }
            Point q = contour.get((contourIndex + d + n) % n);
            avgX += q.x;
            avgY += q.y;
            count++;
        }

        double localSigned = 0.0;
        if (count > 0) {
            avgX /= count;
            avgY /= count;
            double localDx = here.x - avgX;
            double localDy = here.y - avgY;
            localSigned = (localDx * outwardNormal.x) + (localDy * outwardNormal.y);
        }

        double blotch = (anchorSigned * 0.5) + (localSigned * 0.5);
        return MathEx.bound(blotch, -options.maxBlotchMagnitude, options.maxBlotchMagnitude);
    }

    private static double estimateEndStop(
            PointCollection points,
            Point anchor,
            double orientationDegrees,
            double thickness
    ) {
        if (points == null || points.isEmpty()) {
            return 0.0;
        }

        Point start = points.contains(anchor) ? new Point(anchor) : safePoint(points.closest(anchor), anchor);
        Point forward = points.probeDegree(start, orientationDegrees);
        Point backward = points.probeDegree(start, normalizeDegrees(orientationDegrees + 180.0));

        double fd = start.distance(forward);
        double bd = start.distance(backward);
        double span = fd + bd;

        double compactness = 1.0 - clamp01(span / Math.max(2.0, thickness * 2.75));
        double asymmetry = span <= 1.0e-9 ? 0.0 : Math.abs(fd - bd) / span;

        return clamp01((compactness * 0.55) + (asymmetry * 0.45));
    }

    private static Point2D.Double unit(double x, double y) {
        double len = Math.hypot(x, y);
        if (!(len > 1.0e-9)) {
            return new Point2D.Double(1.0, 0.0);
        }
        return new Point2D.Double(x / len, y / len);
    }

    private static Point offset(Point p, Point2D.Double dir, double distance) {
        return new Point(
                MathEx.roundInt(p.x + dir.x * distance),
                MathEx.roundInt(p.y + dir.y * distance)
        );
    }

    private static double darknessAt(FastRGB scan, double x, double y) {
        if (scan == null) {
            return 0.0;
        }

        int ix = MathEx.roundInt(x);
        int iy = MathEx.roundInt(y);
        if (!scan.contains(ix, iy)) {
            return 0.0;
        }

        int[] rgba = scan.getRGBA(ix, iy);
        double alpha = rgba[3] / 255.0;
        double luminance = (rgba[0] + rgba[1] + rgba[2]) / (3.0 * 255.0);
        return clamp01((1.0 - luminance) * alpha);
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static double boundOutput(OutputVariable output, double value, BuildOptions options) {
        return switch (output) {
            case THICKNESS -> MathEx.bound(finite(value), 0.5, Math.max(0.5, options.maxThickness));
            case BORDER_BLEND -> clamp01(value);
            case OPACITY -> clamp01(value);
            case BLOTCH -> MathEx.bound(finite(value), -64.0, 64.0);
        };
    }

    private static double boundOutput(OutputVariable output, double value) {
        return switch (output) {
            case THICKNESS -> MathEx.bound(finite(value), 0.5, 64.0);
            case BORDER_BLEND -> clamp01(value);
            case OPACITY -> clamp01(value);
            case BLOTCH -> MathEx.bound(finite(value), -64.0, 64.0);
        };
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp01(double value) {
        return MathEx.bound(finite(value), 0.0, 1.0);
    }

    private static double normalizeDegrees(double degrees) {
        return MathEx.normalize(degrees, 360.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + ((b - a) * t);
    }

    private static double defaultIfZero(double value, double fallback) {
        return Math.abs(value) < 1.0e-9 ? fallback : value;
    }
}
