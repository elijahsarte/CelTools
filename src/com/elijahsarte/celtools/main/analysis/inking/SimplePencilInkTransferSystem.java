package com.elijahsarte.celtools.main.analysis.inking;

import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Compares matching pencil and ink scans without training a regression model.
 *
 * <p>Each scan is independently registered into the same square coordinate
 * space. Measurements therefore describe the drawing material, rather than
 * canvas size or translation. The comparison uses a robust dark core for line
 * thickness, darkness outside that core for border blend, and core darkness
 * statistics for opacity and pressure.</p>
 */
public final class SimplePencilInkTransferSystem {

    public enum Characteristic {
        THICKNESS,
        BORDER_BLEND,
        OPACITY
    }

    /** Relative contributions to the appearance score. */
    public static final class Weights {
        private final EnumMap<Characteristic, Double> values =
                new EnumMap<>(Characteristic.class);

        public Weights() {
            values.put(Characteristic.THICKNESS, 1.0);
            values.put(Characteristic.BORDER_BLEND, 1.5);
            values.put(Characteristic.OPACITY, 1.0);
        }

        public Weights set(Characteristic characteristic, double weight) {
            Objects.requireNonNull(characteristic, "characteristic");
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("Weights must be finite and non-negative");
            }
            values.put(characteristic, weight);
            return this;
        }

        public double get(Characteristic characteristic) {
            return values.getOrDefault(characteristic, 0.0);
        }

        public Weights copy() {
            Weights copy = new Weights();
            copy.values.clear();
            copy.values.putAll(values);
            return copy;
        }

        private double total() {
            double total = 0.0;
            for (double value : values.values()) {
                total += value;
            }
            return total;
        }
    }

    /** Controls registration and measurement resolution. */
    public static final class Options {
        public int normalizedSize = 256;
        public int borderProbeRadius = 12;
        public int registrationMargin = 20;
        public double minimumVisibleDarkness = 0.012;
        public double minimumDrawingSimilarity = 0.18;
        public boolean rejectDifferentDrawings = true;

        public Options copy() {
            Options copy = new Options();
            copy.normalizedSize = normalizedSize;
            copy.borderProbeRadius = borderProbeRadius;
            copy.registrationMargin = registrationMargin;
            copy.minimumVisibleDarkness = minimumVisibleDarkness;
            copy.minimumDrawingSimilarity = minimumDrawingSimilarity;
            copy.rejectDifferentDrawings = rejectDifferentDrawings;
            return copy;
        }

        private void validate() {
            if (normalizedSize < 64) {
                throw new IllegalArgumentException("normalizedSize must be at least 64");
            }
            if (borderProbeRadius < 1 || borderProbeRadius >= normalizedSize / 4) {
                throw new IllegalArgumentException("borderProbeRadius is outside the useful range");
            }
            if (registrationMargin <= borderProbeRadius
                    || registrationMargin * 2 >= normalizedSize) {
                throw new IllegalArgumentException(
                        "registrationMargin must contain the border probe and leave drawing space"
                );
            }
            if (!inUnitRange(minimumVisibleDarkness)
                    || !inUnitRange(minimumDrawingSimilarity)) {
                throw new IllegalArgumentException("Darkness and similarity limits must be in [0, 1]");
            }
        }
    }

    /**
     * Strength of each calibrated ink characteristic in the generated image.
     * A value of 0 keeps the pencil characteristic, 1 uses the measured ink
     * characteristic, and values above 1 exaggerate the transfer.
     */
    public static final class RenderSettings {
        public double thicknessWeight = 1.0;
        public double borderBlendWeight = 1.0;
        public double opacityWeight = 1.0;
        public double pressureWeight = 1.0;

        public RenderSettings copy() {
            RenderSettings copy = new RenderSettings();
            copy.thicknessWeight = thicknessWeight;
            copy.borderBlendWeight = borderBlendWeight;
            copy.opacityWeight = opacityWeight;
            copy.pressureWeight = pressureWeight;
            return copy;
        }

        private void validate() {
            validateRenderWeight("thicknessWeight", thicknessWeight);
            validateRenderWeight("borderBlendWeight", borderBlendWeight);
            validateRenderWeight("opacityWeight", opacityWeight);
            validateRenderWeight("pressureWeight", pressureWeight);
        }

        private static void validateRenderWeight(String name, double value) {
            if (!Double.isFinite(value) || value < 0.0 || value > 4.0) {
                throw new IllegalArgumentException(name + " must be in [0, 4]");
            }
        }
    }

    /** Material measurements in normalized-image pixels and unit darkness. */
    public static final class ScanAppearance {
        private final double thickness;
        private final double borderBlend;
        private final double opacity;
        private final double pressureVariation;
        private final double coreThreshold;

        private ScanAppearance(
                double thickness,
                double borderBlend,
                double opacity,
                double pressureVariation,
                double coreThreshold
        ) {
            this.thickness = thickness;
            this.borderBlend = borderBlend;
            this.opacity = opacity;
            this.pressureVariation = pressureVariation;
            this.coreThreshold = coreThreshold;
        }

        public double thickness() {
            return thickness;
        }

        public double borderBlend() {
            return borderBlend;
        }

        public double opacity() {
            return opacity;
        }

        public double pressureVariation() {
            return pressureVariation;
        }

        public double coreThreshold() {
            return coreThreshold;
        }
    }

    /** Multipliers/delta needed to move pencil appearance toward ink appearance. */
    public static final class TransferProfile {
        private final double thicknessScale;
        private final double borderBlendScale;
        private final double borderBlendDelta;
        private final double opacityScale;
        private final double pressureVariationScale;

        private TransferProfile(ScanAppearance pencil, ScanAppearance ink) {
            thicknessScale = safeRatio(ink.thickness, pencil.thickness);
            borderBlendScale = safeRatio(ink.borderBlend, pencil.borderBlend);
            borderBlendDelta = ink.borderBlend - pencil.borderBlend;
            opacityScale = safeRatio(ink.opacity, pencil.opacity);
            pressureVariationScale = safeRatio(
                    ink.pressureVariation,
                    pencil.pressureVariation
            );
        }

        public double thicknessScale() {
            return thicknessScale;
        }

        public double borderBlendScale() {
            return borderBlendScale;
        }

        public double borderBlendDelta() {
            return borderBlendDelta;
        }

        public double opacityScale() {
            return opacityScale;
        }

        public double pressureVariationScale() {
            return pressureVariationScale;
        }
    }

    public static final class Comparison {
        private final ScanAppearance pencil;
        private final ScanAppearance ink;
        private final TransferProfile transfer;
        private final double shapeSimilarity;
        private final double appearanceSimilarity;
        private final double overallSimilarity;

        private Comparison(
                ScanAppearance pencil,
                ScanAppearance ink,
                double shapeSimilarity,
                double appearanceSimilarity
        ) {
            this.pencil = pencil;
            this.ink = ink;
            this.transfer = new TransferProfile(pencil, ink);
            this.shapeSimilarity = shapeSimilarity;
            this.appearanceSimilarity = appearanceSimilarity;
            this.overallSimilarity = shapeSimilarity * appearanceSimilarity;
        }

        public ScanAppearance pencil() {
            return pencil;
        }

        public ScanAppearance ink() {
            return ink;
        }

        public TransferProfile transfer() {
            return transfer;
        }

        public double shapeSimilarity() {
            return shapeSimilarity;
        }

        public double appearanceSimilarity() {
            return appearanceSimilarity;
        }

        public double overallSimilarity() {
            return overallSimilarity;
        }
    }

    private static final int CHAMFER_STRAIGHT = 3;
    private static final int CHAMFER_DIAGONAL = 4;
    private static final int DISTANCE_INFINITY = 1_000_000;

    private final Weights weights;
    private final Options options;

    public SimplePencilInkTransferSystem() {
        this(new Weights(), new Options());
    }

    public SimplePencilInkTransferSystem(Weights weights) {
        this(weights, new Options());
    }

    public SimplePencilInkTransferSystem(Weights weights, Options options) {
        this.weights = Objects.requireNonNull(weights, "weights").copy();
        this.options = Objects.requireNonNull(options, "options").copy();
        this.options.validate();
        if (!(this.weights.total() > 0.0)) {
            throw new IllegalArgumentException("At least one comparison weight must be positive");
        }
    }

    public Comparison compare(FastRGB pencilScan, FastRGB inkScan) {
        return compare(null, null, pencilScan, inkScan);
    }

    public Comparison compare(
            PointCollection pencilDrawing,
            PointCollection inkDrawing,
            FastRGB pencilScan,
            FastRGB inkScan
    ) {
        Objects.requireNonNull(pencilScan, "pencilScan");
        Objects.requireNonNull(inkScan, "inkScan");

        RegisteredScan pencil = register(pencilScan, "pencilScan", pencilDrawing);
        RegisteredScan ink = register(inkScan, "inkScan", inkDrawing);
        double shapeSimilarity = shapeSimilarity(pencil, ink);

        if (options.rejectDifferentDrawings
                && shapeSimilarity < options.minimumDrawingSimilarity) {
            throw new IllegalArgumentException(
                    "The scans do not appear to contain the same drawing (shape similarity "
                            + roundForMessage(shapeSimilarity) + ")"
            );
        }

        double appearanceSimilarity = weightedAppearanceSimilarity(
                pencil.appearance,
                ink.appearance
        );
        return new Comparison(
                pencil.appearance,
                ink.appearance,
                shapeSimilarity,
                appearanceSimilarity
        );
    }

    /** Generates an ink-style rendering while retaining the pencil drawing's geometry. */
    public FastRGB generate(
            FastRGB pencilScan,
            FastRGB inkScan,
            RenderSettings settings
    ) {
        return generate(null, null, pencilScan, inkScan, settings);
    }

    public FastRGB generate(
            PointCollection pencilDrawing,
            PointCollection inkDrawing,
            FastRGB pencilScan,
            FastRGB inkScan,
            RenderSettings settings
    ) {
        Objects.requireNonNull(pencilScan, "pencilScan");
        Objects.requireNonNull(inkScan, "inkScan");
        RenderSettings useSettings = settings == null
                ? new RenderSettings()
                : settings.copy();
        useSettings.validate();

        RegisteredScan pencilRegistered = register(
                pencilScan,
                "pencilScan",
                pencilDrawing
        );
        RegisteredScan inkRegistered = register(inkScan, "inkScan", inkDrawing);
        double drawingSimilarity = shapeSimilarity(pencilRegistered, inkRegistered);
        if (options.rejectDifferentDrawings
                && drawingSimilarity < options.minimumDrawingSimilarity) {
            throw new IllegalArgumentException(
                    "The scans do not appear to contain the same drawing (shape similarity "
                            + roundForMessage(drawingSimilarity) + ")"
            );
        }

        SourceRaster source = analyzeSourceRaster(
                pencilScan,
                "pencilScan",
                pencilDrawing
        );
        SourceRaster inkMaterial = analyzeSourceRaster(
                inkScan,
                "inkScan",
                inkDrawing
        );
        boolean[] targetCore = new boolean[source.core.length];
        if (pencilDrawing != null) {
            double pencilThicknessPixels =
                    pencilRegistered.appearance.thickness / source.normalizationScale;
            int closingRadius = Math.max(
                    1,
                    Math.min(6, (int) Math.ceil(pencilThicknessPixels * 0.75))
            );
            boolean[] continuousPencil = closeMask(
                    source.core,
                    source.width,
                    source.height,
                    closingRadius
            );
            boolean[] centerline = thinMask(
                    continuousPencil,
                    source.width,
                    source.height
            );
            int[] distanceToCenterline = chamferDistance(
                    centerline,
                    source.width,
                    source.height,
                    true
            );
            double desiredNormalizedThickness = Math.max(
                    0.75,
                    lerp(
                            pencilRegistered.appearance.thickness,
                            inkRegistered.appearance.thickness,
                            useSettings.thicknessWeight
                    )
            );
            double targetRadius = Math.max(
                    0.5,
                    desiredNormalizedThickness * 0.5 / source.normalizationScale
            );
            for (int i = 0; i < targetCore.length; i++) {
                targetCore[i] = distanceToCenterline[i]
                        / (double) CHAMFER_STRAIGHT <= targetRadius;
            }
        } else {
            // Legacy whole-image mode preserves the original threshold-derived
            // geometry and applies only the measured thickness delta.
            double normalizedThicknessOffset =
                    (inkRegistered.appearance.thickness
                            - pencilRegistered.appearance.thickness)
                            * 0.5
                            * useSettings.thicknessWeight;
            double pixelThicknessOffset = normalizedThicknessOffset / source.normalizationScale;
            int[] distanceToPencil = chamferDistance(
                    source.core,
                    source.width,
                    source.height,
                    true
            );
            int[] distanceToBackground = chamferDistance(
                    source.core,
                    source.width,
                    source.height,
                    false
            );
            for (int i = 0; i < targetCore.length; i++) {
                if (pixelThicknessOffset >= 0.0) {
                    targetCore[i] = source.core[i]
                            || distanceToPencil[i] / (double) CHAMFER_STRAIGHT
                            <= pixelThicknessOffset;
                } else {
                    targetCore[i] = source.core[i]
                            && distanceToBackground[i] / (double) CHAMFER_STRAIGHT
                            > -pixelThicknessOffset;
                }
            }
        }

        // Final one-pixel closing removes raster pinholes while preserving the
        // large intentional interior of a closed outline such as a heart.
        targetCore = closeMask(targetCore, source.width, source.height, 1);

        int[] distanceToTarget = chamferDistance(
                targetCore,
                source.width,
                source.height,
                true
        );
        double sourceBlackPoint = source.quantile(0.90);
        double inkBlackPoint = inkMaterial.quantile(0.90);
        double currentBlackPoint = lerp(
                sourceBlackPoint,
                inkBlackPoint,
                useSettings.pressureWeight
        );
        double desiredBlackPoint = lerp(
                sourceBlackPoint,
                inkBlackPoint,
                useSettings.opacityWeight
        );
        double opacityScale = desiredBlackPoint / Math.max(currentBlackPoint, 1.0e-8);
        double coreDarknessFloor = clamp01(lerp(
                source.quantile(0.50),
                inkMaterial.quantile(0.50),
                useSettings.opacityWeight
        ));
        double borderCoreOpacity = clamp01(lerp(
                source.meanOpacity,
                inkMaterial.meanOpacity,
                useSettings.opacityWeight
        ));
        double[] copiedOutsideInk = pencilDrawing == null
                ? null
                : warpOutsideInkPixelsByContour(
                        targetCore,
                        distanceToTarget,
                        source,
                        inkMaterial,
                        useSettings.borderBlendWeight
                );

        FastRGB output = new FastRGB(source.width, source.height, true);
        output.fill(0xffffffff);
        for (int i = 0; i < source.darkness.length; i++) {
            int x = i % source.width;
            int y = i / source.width;
            double renderedDarkness;
            if (targetCore[i]) {
                double sourceTone = source.core[i]
                        ? source.darkness[i]
                        : source.quantile(0.50);
                double inkTone = inkMaterial.quantile(source.percentile(sourceTone));
                double pressureMappedTone = lerp(
                        sourceTone,
                        inkTone,
                        useSettings.pressureWeight
                );
                renderedDarkness = Math.max(
                        coreDarknessFloor,
                        clamp01(pressureMappedTone * opacityScale)
                );
            } else {
                if (copiedOutsideInk != null) {
                    renderedDarkness = copiedOutsideInk[i];
                } else {
                double normalizedDistance =
                        distanceToTarget[i]
                                / (double) CHAMFER_STRAIGHT
                                * source.normalizationScale;
                double radialInkBorder = profileAt(
                        inkRegistered.borderProfile,
                        normalizedDistance
                );
                double relativeBorder = clamp01(
                        radialInkBorder * useSettings.borderBlendWeight
                );
                renderedDarkness = borderCoreOpacity * relativeBorder;
                }
            }

            int gray = (int) Math.round(255.0 * (1.0 - renderedDarkness));
            output.setRGB(x, y, new int[] {gray, gray, gray, 255});
        }
        return output;
    }

    public FastRGB generate(FastRGB pencilScan, FastRGB inkScan) {
        return generate(pencilScan, inkScan, new RenderSettings());
    }

    /** Opens the calibrated pencil-to-ink generation workbench. */
    public static Workbench launch(FastRGB pencilScan, FastRGB inkScan) {
        return launch(null, null, pencilScan, inkScan);
    }

    public static Workbench launch(
            PointCollection pencilDrawing,
            PointCollection inkDrawing,
            FastRGB pencilScan,
            FastRGB inkScan
    ) {
        Objects.requireNonNull(pencilScan, "pencilScan");
        Objects.requireNonNull(inkScan, "inkScan");
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("The transfer workbench requires a graphical environment");
        }
        Options permissiveOptions = new Options();
        permissiveOptions.rejectDifferentDrawings = false;
        SimplePencilInkTransferSystem permissiveSystem =
                new SimplePencilInkTransferSystem(new Weights(), permissiveOptions);
        SimplePencilInkTransferSystem strictSystem =
                new SimplePencilInkTransferSystem();
        Comparison calibration = permissiveSystem.compare(
                pencilDrawing,
                inkDrawing,
                pencilScan,
                inkScan
        );
        return onEventThread(() -> {
            Workbench workbench = new Workbench(
                    strictSystem,
                    permissiveSystem,
                    pencilDrawing,
                    inkDrawing,
                    pencilScan,
                    inkScan,
                    calibration
            );
            workbench.setVisible(true);
            return workbench;
        });
    }

    /**
     * Live preview and configuration window for the generated ink drawing.
     * The sliders are multipliers around the scan-calibrated values.
     */
    public static final class Workbench extends JFrame {
        private final SimplePencilInkTransferSystem strictSystem;
        private final SimplePencilInkTransferSystem permissiveSystem;
        private final PointCollection pencilDrawing;
        private final PointCollection inkDrawing;
        private final FastRGB pencilScan;
        private final FastRGB inkScan;
        private final Comparison calibration;
        private final JLabel pencilPreview = previewLabel();
        private final JLabel inkPreview = previewLabel();
        private final JLabel simulatedPreview = previewLabel();
        private final JLabel status = new JLabel("Calibrated from the supplied ink scan.");
        private final JSlider thickness = weightSlider();
        private final JSlider borderBlend = weightSlider();
        private final JSlider opacity = weightSlider();
        private final JSlider pressure = weightSlider();
        private final JSlider borderRadius = new JSlider(1, 48, 12);
        private final JSlider zoom = new JSlider(25, 400, 100);
        private final JCheckBox requireMatchingDrawing = new JCheckBox(
                "Require scans to contain the same drawing",
                true
        );
        private final JCheckBox useWholeImageMode = new JCheckBox(
                "Use legacy whole-image transfer",
                false
        );
        private final javax.swing.Timer refreshTimer;
        private SwingWorker<FastRGB, Void> renderer;
        private volatile FastRGB simulatedInk;

        private Workbench(
                SimplePencilInkTransferSystem strictSystem,
                SimplePencilInkTransferSystem permissiveSystem,
                PointCollection pencilDrawing,
                PointCollection inkDrawing,
                FastRGB pencilScan,
                FastRGB inkScan,
                Comparison calibration
        ) {
            super("Simple Pencil → Ink Transfer Workbench");
            this.strictSystem = strictSystem;
            this.permissiveSystem = permissiveSystem;
            this.pencilDrawing = pencilDrawing;
            this.inkDrawing = inkDrawing;
            this.pencilScan = pencilScan;
            this.inkScan = inkScan;
            this.calibration = calibration;
            this.refreshTimer = new javax.swing.Timer(100, event -> refreshPreview());
            refreshTimer.setRepeats(false);

            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout(8, 8));
            add(buildPreviewPanel(), BorderLayout.CENTER);
            add(buildControls(), BorderLayout.EAST);
            add(buildFooter(), BorderLayout.SOUTH);
            setPreferredSize(new Dimension(1500, 850));
            pack();
            setLocationRelativeTo(null);

            pencilPreview.setIcon(previewIcon(pencilScan, zoom.getValue()));
            inkPreview.setIcon(previewIcon(inkScan, zoom.getValue()));
            attachListeners();
            refreshPreview();
        }

        public FastRGB simulatedInk() {
            return simulatedInk == null ? null : simulatedInk.copy();
        }

        public RenderSettings renderSettings() {
            RenderSettings settings = new RenderSettings();
            settings.thicknessWeight = thickness.getValue() / 100.0;
            settings.borderBlendWeight = borderBlend.getValue() / 100.0;
            settings.opacityWeight = opacity.getValue() / 100.0;
            settings.pressureWeight = pressure.getValue() / 100.0;
            return settings;
        }

        public Comparison calibration() {
            return calibration;
        }

        private JPanel buildPreviewPanel() {
            JPanel previews = new JPanel(new GridLayout(1, 3, 6, 6));
            previews.add(imagePanel("Original Pencil", pencilPreview));
            previews.add(imagePanel("Actual Ink Calibration", inkPreview));
            previews.add(imagePanel("Generated Simulated Ink", simulatedPreview));
            return previews;
        }

        private JPanel buildControls() {
            JPanel controls = new JPanel();
            controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
            controls.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            controls.setPreferredSize(new Dimension(330, 700));

            JLabel heading = new JLabel("Calibrated transfer weights");
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 15f));
            controls.add(heading);
            controls.add(new JLabel("100% = measured ink characteristic"));
            controls.add(sliderPanel("Thickness", thickness));
            controls.add(sliderPanel("Outward border blend", borderBlend));
            borderRadius.setMajorTickSpacing(12);
            borderRadius.setMinorTickSpacing(2);
            borderRadius.setPaintTicks(true);
            borderRadius.setPaintLabels(true);
            controls.add(sliderPanel("Outside scan radius", borderRadius, " px"));
            controls.add(sliderPanel("Opacity", opacity));
            controls.add(sliderPanel("Pressure variation", pressure));
            zoom.setMajorTickSpacing(75);
            zoom.setMinorTickSpacing(25);
            zoom.setPaintTicks(true);
            zoom.setPaintLabels(true);
            controls.add(sliderPanel("Preview zoom", zoom));
            requireMatchingDrawing.setToolTipText(
                    "Disable this to generate even when geometric correspondence is low."
            );
            controls.add(requireMatchingDrawing);
            useWholeImageMode.setToolTipText(
                    "Ignore PointCollections and derive the transfer from all scan pixels."
            );
            controls.add(useWholeImageMode);

            TransferProfile transfer = calibration.transfer();
            JTextArea measured = new JTextArea(String.format(
                    Locale.ROOT,
                    "Initial calibration%n%n"
                            + "Thickness scale: %.3f%n"
                            + "Border blend scale: %.3f%n"
                            + "Border blend delta: %+.3f%n"
                            + "Opacity scale: %.3f%n"
                            + "Pressure scale: %.3f%n%n"
                            + "Shape correspondence: %.3f",
                    transfer.thicknessScale(),
                    transfer.borderBlendScale(),
                    transfer.borderBlendDelta(),
                    transfer.opacityScale(),
                    transfer.pressureVariationScale(),
                    calibration.shapeSimilarity()
            ));
            measured.setEditable(false);
            measured.setOpaque(false);
            measured.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            controls.add(measured);
            return controls;
        }

        private JPanel buildFooter() {
            JPanel footer = new JPanel(new BorderLayout());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton reset = new JButton("Reset to calibrated weights");
            JButton save = new JButton("Save simulated ink...");
            reset.addActionListener(event -> {
                thickness.setValue(100);
                borderBlend.setValue(100);
                opacity.setValue(100);
                pressure.setValue(100);
                scheduleRefresh();
            });
            save.addActionListener(event -> saveSimulatedInk());
            buttons.add(reset);
            buttons.add(save);
            footer.add(buttons, BorderLayout.WEST);
            footer.add(status, BorderLayout.CENTER);
            return footer;
        }

        private void attachListeners() {
            javax.swing.event.ChangeListener listener = event -> scheduleRefresh();
            thickness.addChangeListener(listener);
            borderBlend.addChangeListener(listener);
            borderRadius.addChangeListener(listener);
            opacity.addChangeListener(listener);
            pressure.addChangeListener(listener);
            requireMatchingDrawing.addActionListener(event -> scheduleRefresh());
            useWholeImageMode.addActionListener(event -> scheduleRefresh());
            zoom.addChangeListener(event -> refreshZoomedPreviews());
        }

        private void scheduleRefresh() {
            status.setText("Rendering simulated ink...");
            refreshTimer.restart();
        }

        private void refreshPreview() {
            if (renderer != null && !renderer.isDone()) {
                renderer.cancel(true);
            }
            RenderSettings settings = renderSettings();
            boolean validateDrawing = requireMatchingDrawing.isSelected();
            boolean wholeImageMode = useWholeImageMode.isSelected();
            int useBorderRadius = borderRadius.getValue();
            renderer = new SwingWorker<>() {
                @Override
                protected FastRGB doInBackground() {
                    SimplePencilInkTransferSystem renderer = validateDrawing
                            ? strictSystem
                            : permissiveSystem;
                    Options renderOptions = renderer.options();
                    renderOptions.borderProbeRadius = useBorderRadius;
                    renderOptions.registrationMargin = Math.max(
                            renderOptions.registrationMargin,
                            useBorderRadius + 4
                    );
                    renderer = new SimplePencilInkTransferSystem(
                            renderer.weights(),
                            renderOptions
                    );
                    return wholeImageMode
                            ? renderer.generate(pencilScan, inkScan, settings)
                            : renderer.generate(
                                    pencilDrawing,
                                    inkDrawing,
                                    pencilScan,
                                    inkScan,
                                    settings
                            );
                }

                @Override
                protected void done() {
                    if (isCancelled()) {
                        return;
                    }
                    try {
                        simulatedInk = get();
                        simulatedPreview.setIcon(previewIcon(simulatedInk, zoom.getValue()));
                        status.setText(String.format(
                                Locale.ROOT,
                                "Rendered — thickness %.0f%%, border %.0f%%, opacity %.0f%%, pressure %.0f%%",
                                settings.thicknessWeight * 100.0,
                                settings.borderBlendWeight * 100.0,
                                settings.opacityWeight * 100.0,
                                settings.pressureWeight * 100.0
                        ));
                    } catch (Exception exception) {
                        status.setText("Render failed: " + exception.getMessage());
                    }
                }
            };
            renderer.execute();
        }

        private void saveSimulatedInk() {
            FastRGB image = simulatedInk;
            if (image == null) {
                JOptionPane.showMessageDialog(this, "The simulated image is still rendering.");
                return;
            }
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("simulated-ink.png"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File destination = chooser.getSelectedFile();
            try {
                ImageIO.write(image.getImage(), "png", destination);
                status.setText("Saved " + destination.getAbsolutePath());
            } catch (IOException exception) {
                JOptionPane.showMessageDialog(
                        this,
                        exception.getMessage(),
                        "Could Not Save Simulated Ink",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }

        private void refreshZoomedPreviews() {
            pencilPreview.setIcon(previewIcon(pencilScan, zoom.getValue()));
            inkPreview.setIcon(previewIcon(inkScan, zoom.getValue()));
            FastRGB generated = simulatedInk;
            if (generated != null) {
                simulatedPreview.setIcon(previewIcon(generated, zoom.getValue()));
            }
        }

        private static JPanel imagePanel(String title, JLabel image) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createTitledBorder(title));
            JScrollPane scroll = new JScrollPane(image);
            scroll.getViewport().setBackground(Color.WHITE);
            panel.add(scroll, BorderLayout.CENTER);
            return panel;
        }

        private static JPanel sliderPanel(String title, JSlider slider) {
            return sliderPanel(title, slider, "%");
        }

        private static JPanel sliderPanel(String title, JSlider slider, String suffix) {
            JPanel panel = new JPanel(new BorderLayout(4, 4));
            JLabel value = new JLabel(slider.getValue() + suffix, SwingConstants.RIGHT);
            slider.addChangeListener(event -> value.setText(slider.getValue() + suffix));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 0, 4, 0));
            panel.add(new JLabel(title), BorderLayout.NORTH);
            panel.add(slider, BorderLayout.CENTER);
            panel.add(value, BorderLayout.EAST);
            return panel;
        }

        private static JLabel previewLabel() {
            JLabel label = new JLabel("Rendering...", SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(Color.WHITE);
            return label;
        }

        private static JSlider weightSlider() {
            JSlider slider = new JSlider(0, 200, 100);
            slider.setMajorTickSpacing(50);
            slider.setMinorTickSpacing(10);
            slider.setPaintTicks(true);
            slider.setPaintLabels(true);
            return slider;
        }

        private static ImageIcon previewIcon(FastRGB source, int zoomPercent) {
            return new ScaledImageIcon(source.getImage(), zoomPercent / 100.0);
        }

        private static final class ScaledImageIcon extends ImageIcon {
            private final BufferedImage source;
            private final double scale;

            private ScaledImageIcon(BufferedImage source, double scale) {
                this.source = source;
                this.scale = scale;
            }

            @Override
            public int getIconWidth() {
                return Math.max(1, (int) Math.round(source.getWidth() * scale));
            }

            @Override
            public int getIconHeight() {
                return Math.max(1, (int) Math.round(source.getHeight() * scale));
            }

            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                graphics.drawImage(
                        source,
                        x,
                        y,
                        getIconWidth(),
                        getIconHeight(),
                        component
                );
            }
        }
    }

    /**
     * Opens the older measurement-only dialog. Prefer {@link #launch(FastRGB, FastRGB)}
     * when a generated image is required.
     */
    public static Comparison compareWithDialog(FastRGB pencilScan, FastRGB inkScan) {
        return compareWithDialog(null, pencilScan, inkScan);
    }

    public static Comparison compareWithDialog(
            Component parent,
            FastRGB pencilScan,
            FastRGB inkScan
    ) {
        Objects.requireNonNull(pencilScan, "pencilScan");
        Objects.requireNonNull(inkScan, "inkScan");
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Manual configuration requires a graphical environment");
        }

        Configuration configuration = onEventThread(
                () -> showConfigurationDialog(parent)
        );
        if (configuration == null) {
            return null;
        }

        SimplePencilInkTransferSystem system = new SimplePencilInkTransferSystem(
                configuration.weights,
                configuration.options
        );
        Comparison comparison;
        try {
            comparison = system.compare(pencilScan, inkScan);
        } catch (IllegalArgumentException exception) {
            onEventThread(() -> {
                JOptionPane.showMessageDialog(
                        parent,
                        exception.getMessage(),
                        "Pencil/Ink Comparison Failed",
                        JOptionPane.ERROR_MESSAGE
                );
                return null;
            });
            throw exception;
        }

        Comparison finalComparison = comparison;
        onEventThread(() -> {
            showComparisonDialog(parent, finalComparison);
            return null;
        });
        return comparison;
    }

    public Weights weights() {
        return weights.copy();
    }

    public Options options() {
        return options.copy();
    }

    private SourceRaster analyzeSourceRaster(FastRGB scan, String argumentName) {
        return analyzeSourceRaster(scan, argumentName, null);
    }

    private SourceRaster analyzeSourceRaster(
            FastRGB scan,
            String argumentName,
            PointCollection drawingPoints
    ) {
        int width = scan.getWidth();
        int height = scan.getHeight();
        double[] values = new double[width * height];
        double peak = 0.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double value = darkness(scan.getRGBRaw(x, y), scan.hasAlphaChannel());
                values[y * width + x] = value;
                peak = Math.max(peak, value);
            }
        }
        boolean[] suppliedPointMask = drawingPoints == null
                ? null
                : pointMask(drawingPoints, width, height);
        Bounds bounds = suppliedPointMask == null
                ? findBounds(
                        values,
                        width,
                        height,
                        Math.max(options.minimumVisibleDarkness, peak * 0.22)
                )
                : findBounds(suppliedPointMask, width, height);
        if (bounds == null) {
            throw new IllegalArgumentException(argumentName + " contains no measurable drawing");
        }
        double threshold = Math.max(
                options.minimumVisibleDarkness,
                otsuThreshold(values, options.minimumVisibleDarkness)
        );
        boolean[] core = suppliedPointMask == null
                ? new boolean[values.length]
                : suppliedPointMask;
        double sum = 0.0;
        double squareSum = 0.0;
        int count = 0;
        int[] coreHistogram = new int[256];
        for (int i = 0; i < values.length; i++) {
            if (suppliedPointMask == null) {
                core[i] = values[i] >= threshold;
            }
            if (core[i]) {
                sum += values[i];
                squareSum += values[i] * values[i];
                coreHistogram[(int) Math.round(clamp01(values[i]) * 255.0)]++;
                count++;
            }
        }
        if (count < 4) {
            throw new IllegalArgumentException(argumentName + " has too little structural detail");
        }
        double mean = sum / count;
        double deviation = Math.sqrt(Math.max(0.0, squareSum / count - mean * mean));
        double available = options.normalizedSize - options.registrationMargin * 2.0;
        double normalizationScale = available / Math.max(bounds.width(), bounds.height());
        return new SourceRaster(
                values,
                core,
                width,
                height,
                mean,
                deviation,
                coreHistogram,
                count,
                normalizationScale,
                bounds,
                options.normalizedSize
        );
    }

    private static double profileAt(double[] profile, double distance) {
        if (!(distance >= 0.5) || profile.length < 2) {
            return 0.0;
        }
        if (distance <= 1.0) {
            return profile[1];
        }
        if (distance >= profile.length - 1) {
            return 0.0;
        }
        int lower = Math.max(1, (int) Math.floor(distance));
        int upper = Math.min(profile.length - 1, lower + 1);
        return lerp(profile[lower], profile[upper], distance - lower);
    }

    private double[] warpOutsideInkPixelsByContour(
            boolean[] targetCore,
            int[] distanceFromTargetCore,
            SourceRaster pencil,
            SourceRaster ink,
            double strength
    ) {
        double[] halo = new double[targetCore.length];
        if (!(strength > 0.0)) {
            return halo;
        }
        ShapeContour targetContour = new ShapeContour(
                maskToPointCollection(targetCore, pencil.width, pencil.height)
        );
        ShapeContour inkContour = new ShapeContour(
                maskToPointCollection(ink.core, ink.width, ink.height)
        );
        if (targetContour.isEmpty() || inkContour.isEmpty()) {
            return halo;
        }
        Bounds targetBounds = findBounds(targetCore, pencil.width, pencil.height);
        Bounds inkBounds = findBounds(ink.core, ink.width, ink.height);
        if (targetBounds == null || inkBounds == null) {
            return halo;
        }
        double targetWidth = Math.max(1.0, targetBounds.width());
        double targetHeight = Math.max(1.0, targetBounds.height());
        double inkWidth = Math.max(1.0, inkBounds.width());
        double inkHeight = Math.max(1.0, inkBounds.height());
        ContourField targetField = buildContourField(
                targetContour,
                pencil.width,
                pencil.height
        );
        int[] distanceFromInkCore = chamferDistance(
                ink.core,
                ink.width,
                ink.height,
                true
        );

        for (int y = 0; y < pencil.height; y++) {
            for (int x = 0; x < pencil.width; x++) {
                int targetIndex = y * pencil.width + x;
                if (targetCore[targetIndex]) {
                    continue;
                }
                double targetDistance = distanceFromTargetCore[targetIndex]
                        / (double) CHAMFER_STRAIGHT
                        * pencil.normalizationScale;
                if (targetDistance > options.borderProbeRadius) {
                    continue;
                }

                int targetContourIndex = targetField.nearestContourIndex[targetIndex];
                if (targetContourIndex < 0) {
                    continue;
                }
                Point targetPoint = targetContour.get(targetContourIndex);
                ContourFrame targetFrame = contourFrame(
                        targetContour,
                        targetContourIndex,
                        targetCore,
                        pencil.width,
                        pencil.height
                );
                double offsetX = x - targetPoint.x;
                double offsetY = y - targetPoint.y;
                double normalOffset = offsetX * targetFrame.normal.x
                        + offsetY * targetFrame.normal.y;
                if (normalOffset < -0.25) {
                    continue;
                }
                double tangentOffset = offsetX * targetFrame.tangent.x
                        + offsetY * targetFrame.tangent.y;
                double location = targetContour.normalizedArcLengthTo(targetContourIndex);
                int inkContourIndex = Math.max(
                        0,
                        Math.min(
                                inkContour.size() - 1,
                                inkContour.indexByNormalizedArc(location)
                        )
                );
                Point inkPoint = inkContour.get(inkContourIndex);
                ContourFrame inkFrame = contourFrame(
                        inkContour,
                        inkContourIndex,
                        ink.core,
                        ink.width,
                        ink.height
                );
                double targetNormalUnit = normalizedBasisLength(
                        targetFrame.normal,
                        targetWidth,
                        targetHeight
                );
                double inkNormalUnit = normalizedBasisLength(
                        inkFrame.normal,
                        inkWidth,
                        inkHeight
                );
                double mappedNormalOffset = Math.max(0.0, normalOffset)
                        * targetNormalUnit / Math.max(inkNormalUnit, 1.0e-9);
                double mappedTangentOffset = tangentOffset
                        * inkFrame.localStep / Math.max(targetFrame.localStep, 1.0e-9);
                double inkX = inkPoint.x
                        + inkFrame.normal.x * mappedNormalOffset
                        + inkFrame.tangent.x * mappedTangentOffset;
                double inkY = inkPoint.y
                        + inkFrame.normal.y * mappedNormalOffset
                        + inkFrame.tangent.y * mappedTangentOffset;
                int nearestInkX = (int) Math.round(inkX);
                int nearestInkY = (int) Math.round(inkY);
                if (nearestInkX < 0 || nearestInkY < 0
                        || nearestInkX >= ink.width || nearestInkY >= ink.height) {
                    continue;
                }
                int inkIndex = nearestInkY * ink.width + nearestInkX;
                if (ink.core[inkIndex]) {
                    continue;
                }
                double inkDistance = distanceFromInkCore[inkIndex]
                        / (double) CHAMFER_STRAIGHT
                        * ink.normalizationScale;
                if (inkDistance > options.borderProbeRadius) {
                    continue;
                }
                halo[targetIndex] = clamp01(bilinear(
                        ink.darkness,
                        ink.width,
                        ink.height,
                        inkX,
                        inkY
                ) * strength);
            }
        }
        return halo;
    }

    private static PointCollection maskToPointCollection(
            boolean[] mask,
            int width,
            int height
    ) {
        PointCollection points = new PointCollection();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x]) {
                    points.addAtX(x, y);
                }
            }
        }
        return points;
    }

    private static ContourFrame contourFrame(
            ShapeContour contour,
            int index,
            boolean[] core,
            int width,
            int height
    ) {
        int size = contour.size();
        int window = Math.max(2, Math.min(10, size / 300));
        Point previous = contour.get(Math.floorMod(index - window, size));
        Point next = contour.get((index + window) % size);
        double tangentX = next.x - previous.x;
        double tangentY = next.y - previous.y;
        double length = Math.hypot(tangentX, tangentY);
        if (!(length > 1.0e-8)) {
            tangentX = 1.0;
            tangentY = 0.0;
            length = 1.0;
        }
        Point2D.Double tangent = new Point2D.Double(
                tangentX / length,
                tangentY / length
        );
        Point2D.Double firstNormal = new Point2D.Double(-tangent.y, tangent.x);
        Point2D.Double secondNormal = new Point2D.Double(-firstNormal.x, -firstNormal.y);
        Point point = contour.get(index);
        boolean firstInside = maskContains(
                core,
                width,
                height,
                point.x + firstNormal.x * 1.5,
                point.y + firstNormal.y * 1.5
        );
        boolean secondInside = maskContains(
                core,
                width,
                height,
                point.x + secondNormal.x * 1.5,
                point.y + secondNormal.y * 1.5
        );
        Point2D.Double normal;
        if (firstInside != secondInside) {
            normal = firstInside ? secondNormal : firstNormal;
        } else {
            Point2D.Double center = contour.centroid();
            double centerX = point.x - center.x;
            double centerY = point.y - center.y;
            normal = firstNormal.x * centerX + firstNormal.y * centerY >= 0.0
                    ? firstNormal
                    : secondNormal;
        }
        return new ContourFrame(
                tangent,
                normal,
                length / Math.max(1.0, window * 2.0)
        );
    }

    private static double normalizedBasisLength(
            Point2D.Double basis,
            double width,
            double height
    ) {
        return Math.hypot(basis.x / width, basis.y / height);
    }

    private static boolean maskContains(
            boolean[] mask,
            int width,
            int height,
            double x,
            double y
    ) {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        return ix >= 0 && iy >= 0 && ix < width && iy < height
                && mask[iy * width + ix];
    }

    private static ContourField buildContourField(
            ShapeContour contour,
            int width,
            int height
    ) {
        int[] distance = new int[width * height];
        int[] nearest = new int[width * height];
        java.util.Arrays.fill(distance, DISTANCE_INFINITY);
        java.util.Arrays.fill(nearest, -1);
        for (int contourIndex = 0; contourIndex < contour.size(); contourIndex++) {
            Point point = contour.get(contourIndex);
            if (point.x < 0 || point.y < 0 || point.x >= width || point.y >= height) {
                continue;
            }
            int imageIndex = point.y * width + point.x;
            distance[imageIndex] = 0;
            nearest[imageIndex] = contourIndex;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (x > 0) copyNearest(distance, nearest, index, index - 1, CHAMFER_STRAIGHT);
                if (y > 0) copyNearest(distance, nearest, index, index - width, CHAMFER_STRAIGHT);
                if (x > 0 && y > 0) copyNearest(distance, nearest, index, index - width - 1, CHAMFER_DIAGONAL);
                if (x + 1 < width && y > 0) copyNearest(distance, nearest, index, index - width + 1, CHAMFER_DIAGONAL);
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int index = y * width + x;
                if (x + 1 < width) copyNearest(distance, nearest, index, index + 1, CHAMFER_STRAIGHT);
                if (y + 1 < height) copyNearest(distance, nearest, index, index + width, CHAMFER_STRAIGHT);
                if (x + 1 < width && y + 1 < height) copyNearest(distance, nearest, index, index + width + 1, CHAMFER_DIAGONAL);
                if (x > 0 && y + 1 < height) copyNearest(distance, nearest, index, index + width - 1, CHAMFER_DIAGONAL);
            }
        }
        return new ContourField(nearest);
    }

    private static void copyNearest(
            int[] distance,
            int[] nearest,
            int destination,
            int source,
            int step
    ) {
        if (nearest[source] < 0) {
            return;
        }
        int candidate = distance[source] + step;
        if (candidate < distance[destination]) {
            distance[destination] = candidate;
            nearest[destination] = nearest[source];
        }
    }

    private static Configuration showConfigurationDialog(Component parent) {
        Weights defaults = new Weights();
        Options optionDefaults = new Options();
        JSpinner thicknessWeight = decimalSpinner(
                defaults.get(Characteristic.THICKNESS),
                0.0,
                10.0,
                0.1
        );
        JSpinner borderWeight = decimalSpinner(
                defaults.get(Characteristic.BORDER_BLEND),
                0.0,
                10.0,
                0.1
        );
        JSpinner opacityWeight = decimalSpinner(
                defaults.get(Characteristic.OPACITY),
                0.0,
                10.0,
                0.1
        );
        JSpinner normalizedSize = integerSpinner(optionDefaults.normalizedSize, 64, 1024, 32);
        JSpinner borderRadius = integerSpinner(optionDefaults.borderProbeRadius, 1, 128, 1);
        JSpinner margin = integerSpinner(optionDefaults.registrationMargin, 2, 256, 1);
        JSpinner visibleDarkness = decimalSpinner(
                optionDefaults.minimumVisibleDarkness,
                0.0,
                1.0,
                0.005
        );
        JSpinner drawingSimilarity = decimalSpinner(
                optionDefaults.minimumDrawingSimilarity,
                0.0,
                1.0,
                0.01
        );
        JCheckBox rejectDifferent = new JCheckBox(
                "Reject scans of different drawings",
                optionDefaults.rejectDifferentDrawings
        );

        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        addSetting(panel, "Thickness weight", thicknessWeight);
        addSetting(panel, "Border blend weight", borderWeight);
        addSetting(panel, "Opacity/pressure weight", opacityWeight);
        addSetting(panel, "Analysis resolution", normalizedSize);
        addSetting(panel, "Outward border radius", borderRadius);
        addSetting(panel, "Registration margin", margin);
        addSetting(panel, "Minimum visible darkness", visibleDarkness);
        addSetting(panel, "Minimum drawing similarity", drawingSimilarity);
        panel.add(new JLabel("Drawing validation"));
        panel.add(rejectDifferent);

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "Simple Pencil/Ink Transfer Configuration",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        Weights weights = new Weights()
                .set(Characteristic.THICKNESS, number(thicknessWeight))
                .set(Characteristic.BORDER_BLEND, number(borderWeight))
                .set(Characteristic.OPACITY, number(opacityWeight));
        Options options = new Options();
        options.normalizedSize = integer(normalizedSize);
        options.borderProbeRadius = integer(borderRadius);
        options.registrationMargin = integer(margin);
        options.minimumVisibleDarkness = number(visibleDarkness);
        options.minimumDrawingSimilarity = number(drawingSimilarity);
        options.rejectDifferentDrawings = rejectDifferent.isSelected();

        try {
            options.validate();
            if (!(weights.total() > 0.0)) {
                throw new IllegalArgumentException(
                        "At least one comparison weight must be positive"
                );
            }
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    parent,
                    exception.getMessage(),
                    "Invalid Transfer Configuration",
                    JOptionPane.ERROR_MESSAGE
            );
            return showConfigurationDialog(parent);
        }
        return new Configuration(weights, options);
    }

    private static void showComparisonDialog(Component parent, Comparison comparison) {
        ScanAppearance pencil = comparison.pencil();
        ScanAppearance ink = comparison.ink();
        TransferProfile transfer = comparison.transfer();
        String text = String.format(
                Locale.ROOT,
                "Shape similarity: %.3f%n"
                        + "Appearance similarity: %.3f%n%n"
                        + "                         Pencil       Ink%n"
                        + "Thickness:              %8.3f  %8.3f%n"
                        + "Border blend:            %8.3f  %8.3f%n"
                        + "Opacity:                 %8.3f  %8.3f%n"
                        + "Pressure variation:      %8.3f  %8.3f%n%n"
                        + "Recommended ink transfer%n"
                        + "Thickness scale:         %.3f%n"
                        + "Border blend scale:      %.3f%n"
                        + "Border blend delta:      %+.3f%n"
                        + "Opacity scale:           %.3f%n"
                        + "Pressure variation scale: %.3f",
                comparison.shapeSimilarity(),
                comparison.appearanceSimilarity(),
                pencil.thickness(), ink.thickness(),
                pencil.borderBlend(), ink.borderBlend(),
                pencil.opacity(), ink.opacity(),
                pencil.pressureVariation(), ink.pressureVariation(),
                transfer.thicknessScale(),
                transfer.borderBlendScale(),
                transfer.borderBlendDelta(),
                transfer.opacityScale(),
                transfer.pressureVariationScale()
        );
        JTextArea output = new JTextArea(text);
        output.setEditable(false);
        output.setOpaque(false);
        output.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JOptionPane.showMessageDialog(
                parent,
                output,
                "Pencil/Ink Transfer Results",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static void addSetting(JPanel panel, String label, JSpinner input) {
        panel.add(new JLabel(label));
        panel.add(input);
    }

    private static JSpinner decimalSpinner(
            double value,
            double minimum,
            double maximum,
            double step
    ) {
        return new JSpinner(new SpinnerNumberModel(value, minimum, maximum, step));
    }

    private static JSpinner integerSpinner(int value, int minimum, int maximum, int step) {
        return new JSpinner(new SpinnerNumberModel(value, minimum, maximum, step));
    }

    private static double number(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue();
    }

    private static int integer(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static <T> T onEventThread(Supplier<T> action) {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.get();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(action.get());
                } catch (RuntimeException exception) {
                    failure.set(exception);
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while opening configuration", exception);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw new IllegalStateException("Could not open configuration", exception.getCause());
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    private RegisteredScan register(FastRGB scan, String argumentName) {
        return register(scan, argumentName, null);
    }

    private RegisteredScan register(
            FastRGB scan,
            String argumentName,
            PointCollection drawingPoints
    ) {
        int width = scan.getWidth();
        int height = scan.getHeight();
        double[] source = new double[width * height];
        boolean[] pointMask = drawingPoints == null
                ? null
                : pointMask(drawingPoints, width, height);
        double peak = 0.0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double darkness = darkness(
                        scan.getRGBRaw(x, y),
                        scan.hasAlphaChannel()
                );
                source[y * width + x] = darkness;
                peak = Math.max(peak, darkness);
            }
        }

        if (peak < options.minimumVisibleDarkness) {
            throw new IllegalArgumentException(argumentName + " contains no visible drawing");
        }

        // A relative threshold finds the structural drawing bounds while excluding
        // the soft material halo that border-blend measurement needs to preserve.
        double boundsThreshold = Math.max(
                options.minimumVisibleDarkness,
                peak * 0.22
        );
        Bounds bounds = pointMask == null
                ? findBounds(source, width, height, boundsThreshold)
                : findBounds(pointMask, width, height);
        if (bounds == null) {
            throw new IllegalArgumentException(argumentName + " contains no measurable drawing");
        }

        int size = options.normalizedSize;
        double[] normalized = new double[size * size];
        boolean[] normalizedPointMask = pointMask == null
                ? null
                : new boolean[size * size];
        double available = size - options.registrationMargin * 2.0;
        double scale = available / Math.max(bounds.width(), bounds.height());
        double drawnWidth = bounds.width() * scale;
        double drawnHeight = bounds.height() * scale;
        double left = (size - drawnWidth) * 0.5;
        double top = (size - drawnHeight) * 0.5;

        for (int y = 0; y < size; y++) {
            double sourceY = bounds.minY + ((y - top + 0.5) / scale) - 0.5;
            for (int x = 0; x < size; x++) {
                double sourceX = bounds.minX + ((x - left + 0.5) / scale) - 0.5;
                normalized[y * size + x] = bilinear(
                        source,
                        width,
                        height,
                        sourceX,
                        sourceY
                );
                if (normalizedPointMask != null) {
                    normalizedPointMask[y * size + x] = maskAt(
                            pointMask,
                            width,
                            height,
                            sourceX,
                            sourceY
                    );
                }
            }
        }

        double threshold = otsuThreshold(normalized, options.minimumVisibleDarkness);
        threshold = Math.max(options.minimumVisibleDarkness, threshold);
        boolean[] core = new boolean[normalized.length];
        for (int i = 0; i < normalized.length; i++) {
            core[i] = normalizedPointMask == null
                    ? normalized[i] >= threshold
                    : normalizedPointMask[i];
        }

        return measure(normalized, core, threshold, size, argumentName);
    }

    private RegisteredScan measure(
            double[] darkness,
            boolean[] core,
            double threshold,
            int size,
            String argumentName
    ) {
        int area = 0;
        int perimeterEdges = 0;
        double opacitySum = 0.0;
        double opacitySquareSum = 0.0;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                if (!core[index]) {
                    continue;
                }
                area++;
                opacitySum += darkness[index];
                opacitySquareSum += darkness[index] * darkness[index];
                if (x == 0 || !core[index - 1]) perimeterEdges++;
                if (x == size - 1 || !core[index + 1]) perimeterEdges++;
                if (y == 0 || !core[index - size]) perimeterEdges++;
                if (y == size - 1 || !core[index + size]) perimeterEdges++;
            }
        }

        if (area < 4 || perimeterEdges == 0) {
            throw new IllegalArgumentException(argumentName + " has too little structural detail");
        }

        double opacity = opacitySum / area;
        double variance = Math.max(0.0, opacitySquareSum / area - opacity * opacity);
        double pressureVariation = clamp01(Math.sqrt(variance) / Math.max(opacity, 1.0e-9));

        // For a long stroke, 2 * area / perimeter converges to stroke width.
        // Unlike scan-line probing, this remains stable at diagonal and curved edges.
        double thickness = 2.0 * area / perimeterEdges;
        int[] distanceFromCore = chamferDistance(core, size, true);
        BorderMeasurement border = measureBorder(
                darkness,
                core,
                distanceFromCore,
                opacity,
                size
        );

        ScanAppearance appearance = new ScanAppearance(
                thickness,
                border.score,
                opacity,
                pressureVariation,
                threshold
        );
        return new RegisteredScan(
                darkness,
                core,
                distanceFromCore,
                appearance,
                border.profile,
                size
        );
    }

    private BorderMeasurement measureBorder(
            double[] darkness,
            boolean[] core,
            int[] distanceFromCore,
            double coreOpacity,
            int size
    ) {
        double[] profile = new double[options.borderProbeRadius + 1];
        double weightedProfile = 0.0;
        double profileWeight = 0.0;

        for (int ring = 1; ring <= options.borderProbeRadius; ring++) {
            double ringDarkness = 0.0;
            int ringPixels = 0;
            double lower = ring - 0.5;
            double upper = ring + 0.5;

            for (int i = 0; i < size * size; i++) {
                if (core[i]) {
                    continue;
                }
                double distance = distanceFromCore[i] / (double) CHAMFER_STRAIGHT;
                if (distance >= lower && distance < upper) {
                    ringDarkness += darkness[i];
                    ringPixels++;
                }
            }

            if (ringPixels == 0) {
                continue;
            }
            double relativeDarkness = clamp01((ringDarkness / ringPixels) / coreOpacity);
            profile[ring] = relativeDarkness;
            double weight = 1.0 - ((ring - 1.0) / options.borderProbeRadius);
            weightedProfile += relativeDarkness * weight;
            profileWeight += weight;
        }

        double score = profileWeight == 0.0
                ? 0.0
                : clamp01(weightedProfile / profileWeight);
        profile[options.borderProbeRadius] = 0.0;
        for (int radius = options.borderProbeRadius - 1; radius >= 1; radius--) {
            profile[radius] = Math.max(profile[radius], profile[radius + 1]);
        }
        return new BorderMeasurement(score, profile);
    }

    private double shapeSimilarity(RegisteredScan a, RegisteredScan b) {
        int[] distanceToA = chamferDistance(a.core, a.size, true);
        int[] distanceToB = chamferDistance(b.core, b.size, true);
        double aToB = meanMaskDistance(a.core, distanceToB);
        double bToA = meanMaskDistance(b.core, distanceToA);
        double meanDistance = (aToB + bToA) * 0.5;
        double tolerance = Math.max(
                1.5,
                (a.appearance.thickness + b.appearance.thickness) * 0.75
        );
        return clamp01(Math.exp(-meanDistance / tolerance));
    }

    private double weightedAppearanceSimilarity(ScanAppearance pencil, ScanAppearance ink) {
        double thickness = ratioSimilarity(pencil.thickness, ink.thickness);
        double borderBlend = ratioSimilarity(pencil.borderBlend, ink.borderBlend);
        double opacity = ratioSimilarity(pencil.opacity, ink.opacity);
        double pressure = ratioSimilarity(
                pencil.pressureVariation,
                ink.pressureVariation
        );
        double opacityAndPressure = opacity * 0.65 + pressure * 0.35;

        double weighted =
                thickness * weights.get(Characteristic.THICKNESS)
                        + borderBlend * weights.get(Characteristic.BORDER_BLEND)
                        + opacityAndPressure * weights.get(Characteristic.OPACITY);
        return clamp01(weighted / weights.total());
    }

    private static Bounds findBounds(
            double[] darkness,
            int width,
            int height,
            double threshold
    ) {
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (darkness[y * width + x] < threshold) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < minX ? null : new Bounds(minX, minY, maxX, maxY);
    }

    private static boolean[] pointMask(
            PointCollection points,
            int width,
            int height
    ) {
        Objects.requireNonNull(points, "drawingPoints");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("drawingPoints must not be empty");
        }
        boolean[] mask = new boolean[width * height];
        for (Point point : points) {
            if (point.x >= 0 && point.y >= 0 && point.x < width && point.y < height) {
                mask[point.y * width + point.x] = true;
            }
        }
        return mask;
    }

    private static Bounds findBounds(boolean[] mask, int width, int height) {
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y * width + x]) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < minX ? null : new Bounds(minX, minY, maxX, maxY);
    }

    private static boolean maskAt(
            boolean[] mask,
            int width,
            int height,
            double x,
            double y
    ) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        for (int sampleY = y0; sampleY <= y0 + 1; sampleY++) {
            for (int sampleX = x0; sampleX <= x0 + 1; sampleX++) {
                if (sampleX >= 0 && sampleY >= 0
                        && sampleX < width && sampleY < height
                        && mask[sampleY * width + sampleX]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int[] chamferDistance(boolean[] mask, int size, boolean distanceToTrue) {
        return chamferDistance(mask, size, size, distanceToTrue);
    }

    private static boolean[] closeMask(
            boolean[] mask,
            int width,
            int height,
            int radius
    ) {
        int limit = Math.max(1, radius) * CHAMFER_STRAIGHT;
        int[] toInk = chamferDistance(mask, width, height, true);
        boolean[] dilated = new boolean[mask.length];
        for (int i = 0; i < mask.length; i++) {
            dilated[i] = toInk[i] <= limit;
        }
        int[] toBackground = chamferDistance(dilated, width, height, false);
        boolean[] closed = new boolean[mask.length];
        for (int i = 0; i < mask.length; i++) {
            closed[i] = dilated[i] && toBackground[i] > limit;
        }
        return closed;
    }

    /** Zhang-Suen thinning produces a continuous one-pixel stroke centerline. */
    private static boolean[] thinMask(boolean[] source, int width, int height) {
        boolean[] image = source.clone();
        boolean[] remove = new boolean[image.length];
        boolean changed = true;
        int iterations = 0;
        while (changed && iterations++ < 128) {
            changed = thinningPass(image, remove, width, height, true);
            changed |= thinningPass(image, remove, width, height, false);
        }
        return image;
    }

    private static boolean thinningPass(
            boolean[] image,
            boolean[] remove,
            int width,
            int height,
            boolean firstPass
    ) {
        java.util.Arrays.fill(remove, false);
        boolean changed = false;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int i = y * width + x;
                if (!image[i]) {
                    continue;
                }
                boolean p2 = image[i - width];
                boolean p3 = image[i - width + 1];
                boolean p4 = image[i + 1];
                boolean p5 = image[i + width + 1];
                boolean p6 = image[i + width];
                boolean p7 = image[i + width - 1];
                boolean p8 = image[i - 1];
                boolean p9 = image[i - width - 1];
                int neighbors = bool(p2) + bool(p3) + bool(p4) + bool(p5)
                        + bool(p6) + bool(p7) + bool(p8) + bool(p9);
                if (neighbors < 2 || neighbors > 6) {
                    continue;
                }
                int transitions = transition(p2, p3) + transition(p3, p4)
                        + transition(p4, p5) + transition(p5, p6)
                        + transition(p6, p7) + transition(p7, p8)
                        + transition(p8, p9) + transition(p9, p2);
                if (transitions != 1) {
                    continue;
                }
                boolean conditionA = firstPass
                        ? !(p2 && p4 && p6)
                        : !(p2 && p4 && p8);
                boolean conditionB = firstPass
                        ? !(p4 && p6 && p8)
                        : !(p2 && p6 && p8);
                if (conditionA && conditionB) {
                    remove[i] = true;
                    changed = true;
                }
            }
        }
        if (changed) {
            for (int i = 0; i < image.length; i++) {
                if (remove[i]) {
                    image[i] = false;
                }
            }
        }
        return changed;
    }

    private static int bool(boolean value) {
        return value ? 1 : 0;
    }

    private static int transition(boolean from, boolean to) {
        return !from && to ? 1 : 0;
    }

    private static int[] chamferDistance(
            boolean[] mask,
            int width,
            int height,
            boolean distanceToTrue
    ) {
        int[] distance = new int[mask.length];
        for (int i = 0; i < mask.length; i++) {
            distance[i] = mask[i] == distanceToTrue ? 0 : DISTANCE_INFINITY;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                int best = distance[i];
                if (x > 0) best = Math.min(best, distance[i - 1] + CHAMFER_STRAIGHT);
                if (y > 0) best = Math.min(best, distance[i - width] + CHAMFER_STRAIGHT);
                if (x > 0 && y > 0) best = Math.min(best, distance[i - width - 1] + CHAMFER_DIAGONAL);
                if (x + 1 < width && y > 0) best = Math.min(best, distance[i - width + 1] + CHAMFER_DIAGONAL);
                distance[i] = best;
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int i = y * width + x;
                int best = distance[i];
                if (x + 1 < width) best = Math.min(best, distance[i + 1] + CHAMFER_STRAIGHT);
                if (y + 1 < height) best = Math.min(best, distance[i + width] + CHAMFER_STRAIGHT);
                if (x + 1 < width && y + 1 < height) best = Math.min(best, distance[i + width + 1] + CHAMFER_DIAGONAL);
                if (x > 0 && y + 1 < height) best = Math.min(best, distance[i + width - 1] + CHAMFER_DIAGONAL);
                distance[i] = best;
            }
        }
        return distance;
    }

    private static double meanMaskDistance(boolean[] mask, int[] distance) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                sum += distance[i] / (double) CHAMFER_STRAIGHT;
                count++;
            }
        }
        return count == 0 ? Double.POSITIVE_INFINITY : sum / count;
    }

    private static double otsuThreshold(double[] values, double noiseFloor) {
        int[] histogram = new int[256];
        int count = 0;
        for (double value : values) {
            if (value >= noiseFloor) {
                histogram[(int) Math.round(clamp01(value) * 255.0)]++;
                count++;
            }
        }
        if (count == 0) {
            return noiseFloor;
        }

        double totalMoment = 0.0;
        for (int i = 0; i < histogram.length; i++) {
            totalMoment += i * histogram[i];
        }
        int backgroundCount = 0;
        double backgroundMoment = 0.0;
        double bestVariance = -1.0;
        int best = Math.max(1, (int) Math.ceil(noiseFloor * 255.0));

        for (int i = best; i < histogram.length; i++) {
            backgroundCount += histogram[i];
            if (backgroundCount == 0) continue;
            int foregroundCount = count - backgroundCount;
            if (foregroundCount == 0) break;
            backgroundMoment += i * histogram[i];
            double backgroundMean = backgroundMoment / backgroundCount;
            double foregroundMean = (totalMoment - backgroundMoment) / foregroundCount;
            double difference = backgroundMean - foregroundMean;
            double variance = (double) backgroundCount * foregroundCount * difference * difference;
            if (variance > bestVariance) {
                bestVariance = variance;
                best = i;
            }
        }
        return best / 255.0;
    }

    private static double bilinear(
            double[] values,
            int width,
            int height,
            double x,
            double y
    ) {
        if (x < 0.0 || y < 0.0 || x > width - 1.0 || y > height - 1.0) {
            return 0.0;
        }
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = Math.min(width - 1, x0 + 1);
        int y1 = Math.min(height - 1, y0 + 1);
        double tx = x - x0;
        double ty = y - y0;
        double top = lerp(values[y0 * width + x0], values[y0 * width + x1], tx);
        double bottom = lerp(values[y1 * width + x0], values[y1 * width + x1], tx);
        return lerp(top, bottom, ty);
    }

    private static double darkness(int argb, boolean hasAlphaChannel) {
        double alpha = hasAlphaChannel ? FastRGB.alpha(argb) / 255.0 : 1.0;
        double red = linearChannel(FastRGB.red(argb) / 255.0);
        double green = linearChannel(FastRGB.green(argb) / 255.0);
        double blue = linearChannel(FastRGB.blue(argb) / 255.0);
        double luminance = red * 0.2126 + green * 0.7152 + blue * 0.0722;
        return clamp01((1.0 - luminance) * alpha);
    }

    private static double linearChannel(double channel) {
        return channel <= 0.04045
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static double ratioSimilarity(double a, double b) {
        if (a < 1.0e-8 && b < 1.0e-8) {
            return 1.0;
        }
        return clamp01(Math.exp(-Math.abs(Math.log((b + 1.0e-6) / (a + 1.0e-6)))));
    }

    private static double safeRatio(double numerator, double denominator) {
        if (numerator < 1.0e-8 && denominator < 1.0e-8) {
            return 1.0;
        }
        return clamp(numerator / Math.max(denominator, 1.0e-8), 0.05, 20.0);
    }

    private static String roundForMessage(double value) {
        return String.format("%.3f", value);
    }

    private static boolean inUnitRange(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class RegisteredScan {
        private final double[] darkness;
        private final boolean[] core;
        private final int[] distanceFromCore;
        private final ScanAppearance appearance;
        private final double[] borderProfile;
        private final int size;

        private RegisteredScan(
                double[] darkness,
                boolean[] core,
                int[] distanceFromCore,
                ScanAppearance appearance,
                double[] borderProfile,
                int size
        ) {
            this.darkness = darkness;
            this.core = core;
            this.distanceFromCore = distanceFromCore;
            this.appearance = appearance;
            this.borderProfile = borderProfile;
            this.size = size;
        }
    }

    private static final class BorderMeasurement {
        private final double score;
        private final double[] profile;

        private BorderMeasurement(double score, double[] profile) {
            this.score = score;
            this.profile = profile;
        }
    }

    private static final class ContourFrame {
        private final Point2D.Double tangent;
        private final Point2D.Double normal;
        private final double localStep;

        private ContourFrame(
                Point2D.Double tangent,
                Point2D.Double normal,
                double localStep
        ) {
            this.tangent = tangent;
            this.normal = normal;
            this.localStep = localStep;
        }
    }

    private static final class ContourField {
        private final int[] nearestContourIndex;

        private ContourField(int[] nearestContourIndex) {
            this.nearestContourIndex = nearestContourIndex;
        }
    }

    private static final class Configuration {
        private final Weights weights;
        private final Options options;

        private Configuration(Weights weights, Options options) {
            this.weights = weights;
            this.options = options;
        }
    }

    private static final class SourceRaster {
        private final double[] darkness;
        private final boolean[] core;
        private final int width;
        private final int height;
        private final double meanOpacity;
        private final double opacityDeviation;
        private final int[] coreHistogram;
        private final int corePixelCount;
        private final double normalizationScale;
        private final Bounds bounds;
        private final int normalizedSize;

        private SourceRaster(
                double[] darkness,
                boolean[] core,
                int width,
                int height,
                double meanOpacity,
                double opacityDeviation,
                int[] coreHistogram,
                int corePixelCount,
                double normalizationScale,
                Bounds bounds,
                int normalizedSize
        ) {
            this.darkness = darkness;
            this.core = core;
            this.width = width;
            this.height = height;
            this.meanOpacity = meanOpacity;
            this.opacityDeviation = opacityDeviation;
            this.coreHistogram = coreHistogram;
            this.corePixelCount = corePixelCount;
            this.normalizationScale = normalizationScale;
            this.bounds = bounds;
            this.normalizedSize = normalizedSize;
        }

        private double normalizedX(int x) {
            double drawnWidth = bounds.width() * normalizationScale;
            double left = (normalizedSize - drawnWidth) * 0.5;
            return left + (x - bounds.minX + 0.5) * normalizationScale - 0.5;
        }

        private double normalizedY(int y) {
            double drawnHeight = bounds.height() * normalizationScale;
            double top = (normalizedSize - drawnHeight) * 0.5;
            return top + (y - bounds.minY + 0.5) * normalizationScale - 0.5;
        }

        private double percentile(double darkness) {
            int bin = (int) Math.round(clamp01(darkness) * 255.0);
            int cumulative = 0;
            for (int i = 0; i <= bin; i++) {
                cumulative += coreHistogram[i];
            }
            return cumulative / (double) Math.max(1, corePixelCount);
        }

        private double quantile(double percentile) {
            double bounded = clamp01(percentile);
            int target = Math.max(1, (int) Math.ceil(bounded * corePixelCount));
            int cumulative = 0;
            for (int i = 0; i < coreHistogram.length; i++) {
                cumulative += coreHistogram[i];
                if (cumulative >= target) {
                    return i / 255.0;
                }
            }
            return 1.0;
        }
    }

    private static final class Bounds {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;

        private Bounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }
    }
}
