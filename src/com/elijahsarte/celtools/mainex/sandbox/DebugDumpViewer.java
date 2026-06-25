package com.elijahsarte.celtools.mainex.sandbox;

import com.elijahsarte.celtools.gui.Application;
import com.elijahsarte.celtools.main.framefactory.FastRGB;
import com.elijahsarte.celtools.main.framefactory.ImageHandler;
import com.elijahsarte.celtools.main.util.structures.bounds.IntegerBounds;
import com.elijahsarte.celtools.main.util.structures.bounds.ShapeBounds;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.main.util.structures.shape.ShapeContour;
import com.elijahsarte.celtools.mainex.DebugDump;
import com.elijahsarte.celtools.mainex.DebuggerEx;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Array;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DebugDumpViewer extends JFrame {

    private static final String POINT_COLLECTION_SUFFIX = "_pointcollection";
    private static final String SHAPE_CONTOUR_SUFFIX = "_shapecontour";
    private static final String SHAPE_BOUNDS_SUFFIX = "_shapebounds";
    private static final String FAST_RGB_SUFFIX = "_fastrgb";

    private static final String EXPAND_PREF_PREFIX = "debug_dump_expand_rule_";

    /*
     * Easy-to-edit expansion defaults. The original counterpart rules are enabled by default.
     * The newly requested ShapeBounds derivations are available in the dialog, but are not
     * enabled by default unless these flags are changed or the user checks them once.
     */
    private static final boolean DEFAULT_EXPAND_INTEGER_LIST_MAP_TO_POINT_COLLECTION = true;
    private static final boolean DEFAULT_EXPAND_INTEGER_LIST_MAP_TO_SHAPE_CONTOUR = true;
    private static final boolean DEFAULT_EXPAND_INTEGER_LIST_MAP_TO_SHAPE_BOUNDS = false;

    private static final boolean DEFAULT_EXPAND_INTEGER_BOUNDS_MAP_TO_POINT_COLLECTION = true;
    private static final boolean DEFAULT_EXPAND_INTEGER_BOUNDS_MAP_TO_SHAPE_CONTOUR = true;
    private static final boolean DEFAULT_EXPAND_INTEGER_BOUNDS_MAP_TO_SHAPE_BOUNDS = false;

    private static final boolean DEFAULT_EXPAND_POINT_COLLECTION_TO_SHAPE_CONTOUR = true;
    private static final boolean DEFAULT_EXPAND_POINT_COLLECTION_TO_SHAPE_BOUNDS = false;
    private static final boolean DEFAULT_EXPAND_SHAPE_BOUNDS_TO_SHAPE_CONTOUR = true;
    private static final boolean DEFAULT_EXPAND_IMAGE_HANDLER_TO_FAST_RGB = true;

    private static final int PREVIEW_MAX_POINTLIKE_SIZE = 150_000;
    private static final int PREVIEW_SAMPLE_POINT_LIMIT = 65_000;
    private static final int POINTLIKE_PREVIEW_WIDTH = 260;
    private static final int POINTLIKE_PREVIEW_HEIGHT = 180;
    private static final int CARD_TEXT_MAX_CHARS = 12_000;

    /*
     * Programmer-facing behavior switches. Add new explicit object behavior in
     * typeBehaviors(), add new constructor rules in availableOperationsFor(),
     * and add new expansion rules in expandRules(). These three methods are the
     * intended extension points for custom DebugDumpViewer behavior.
     */
    private static final boolean ENABLE_POINTLIKE_THUMBNAILS = true;
    private static final boolean ENABLE_IMAGEHANDLER_LOAD_BUTTONS = true;
    private static final boolean LOAD_IMAGEHANDLER_BEFORE_DEBUGGER_VIEW = true;
    private static final String IMAGE_HANDLER_LOAD_BUTTON_TEXT = "Load";
    private static final String IMAGE_HANDLER_LOAD_FAILED_TEXT = "Failed to load";

    private static final String[] GENERATED_SUFFIXES = {
            POINT_COLLECTION_SUFFIX,
            SHAPE_CONTOUR_SUFFIX,
            SHAPE_BOUNDS_SUFFIX,
            FAST_RGB_SUFFIX
    };

    private static volatile DebugDumpViewer debuggerTarget;

    private final DefaultListModel<Resource> resourceModel = new DefaultListModel<>();
    private final JPanel resourcesPanel = new JPanel();

    private File currentFile;
    private String exceptionStackTrace = "";
    private JTextArea exceptionArea;
    private JLabel summaryLabel;
    private JLabel fileLabel;
    private JLabel loadStatusLabel;

    public DebugDumpViewer(File dumpFile) {
        super("Debug Dump Viewer" + (dumpFile == null ? "" : " - " + dumpFile.getName()));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        this.currentFile = dumpFile;
        debuggerTarget = this;

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildMainContent(), BorderLayout.CENTER);

        setSize(1180, 840);
        setMinimumSize(new Dimension(780, 520));
        setLocationRelativeTo(null);
        refreshResourceCards();
        updateHeader();

        if (dumpFile != null) {
            loadDumpAsync(dumpFile);
        } else {
            setLoadStatus("No dump loaded.");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Throwable ignored) {
            }

            File file = null;
            if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
                file = new File(args[0]);
            }

            if (file == null) {
                JFrame owner = new JFrame("Open Debug Dump Viewer");
                owner.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                owner.setUndecorated(true);
                owner.setSize(1, 1);
                owner.setLocationRelativeTo(null);
                owner.setVisible(true);

                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Open CTDEBUGDUMP");
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setFileFilter(new FileNameExtensionFilter("Cel Tools Debug Dump (*.ctdebugdump)", DebugDump.FILE_EXTENSION));
                int result = chooser.showOpenDialog(owner);
                if (result != JFileChooser.APPROVE_OPTION) {
                    owner.dispose();
                    System.exit(0);
                    return;
                }
                file = chooser.getSelectedFile();
                owner.dispose();
            }

            DebugDumpViewer viewer = new DebugDumpViewer(file);
            viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            viewer.setVisible(true);
        });
    }

    public static void open(File dumpFile) {
        SwingUtilities.invokeLater(() -> new DebugDumpViewer(dumpFile).setVisible(true));
    }

    public static void openWithChooser(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open CTDEBUGDUMP");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter("Cel Tools Debug Dump (*.ctdebugdump)", DebugDump.FILE_EXTENSION));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        open(chooser.getSelectedFile());
    }

    private void loadDumpAsync(File file) {
        setLoadStatus("Loading " + file.getAbsolutePath() + " ...");
        resourceModel.clear();
        refreshResourceCards();

        SwingWorker<DebugDump.DumpFile, Void> worker = new SwingWorker<>() {
            @Override
            protected DebugDump.DumpFile doInBackground() throws Exception {
                return DebugDump.read(file);
            }

            @Override
            protected void done() {
                try {
                    DebugDump.DumpFile dump = get();
                    finishLoadedDump(file, dump);
                } catch (Throwable ex) {
                    setLoadStatus("Failed to load dump: " + rootMessage(ex));
                    JOptionPane.showMessageDialog(
                            DebugDumpViewer.this,
                            "Unable to open debug dump:\n" + rootMessage(ex),
                            "Debug Dump Viewer",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        worker.execute();
    }

    private void finishLoadedDump(File file, DebugDump.DumpFile dump) {
        this.currentFile = file;
        this.exceptionStackTrace = dump.exceptionStackTrace() == null ? "" : dump.exceptionStackTrace();
        if (exceptionArea != null) exceptionArea.setText(exceptionStackTrace);

        resourceModel.clear();
        for (DebugDump.Entry entry : dump.entries()) {
            addResource(new Resource(entry.key(), entry.typeName(), entry.info(), entry.object(), false));
        }
        setLoadStatus("Loaded " + resourceModel.size() + " resource(s).");
        refreshResourceCards();
    }

    private JPanel buildToolbar() {
        JButton loadImageButton = new JButton("Load Image...");
        loadImageButton.addActionListener(e -> loadImageResource());

        JButton addConstructedButton = new JButton("Add Constructed...");
        addConstructedButton.addActionListener(e -> addConstructedResource());

        JButton renameButton = new JButton("Rename...");
        renameButton.addActionListener(e -> renameResource());

        JButton removeButton = new JButton("Remove...");
        removeButton.addActionListener(e -> removeResources());

        JButton expandButton = new JButton("Expand Dump");
        expandButton.addActionListener(e -> expandDump());

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveCurrentFile(true));

        JButton sizeReportButton = new JButton("Dump Size Report");
        sizeReportButton.addActionListener(e -> openDumpSizeReport());

        JButton debuggerViewButton = new JButton("Open Debugger View...");
        debuggerViewButton.addActionListener(e -> openDebuggerViewDialog());

        JButton triggerButton = new JButton("Trigger Debugger");
        triggerButton.addActionListener(e -> {
            debuggerTarget = this;
            triggerDebugger();
            refreshResourceCards();
        });

        fileLabel = new JLabel();

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(loadImageButton);
        buttons.add(addConstructedButton);
        buttons.add(renameButton);
        buttons.add(removeButton);
        buttons.add(expandButton);
        buttons.add(saveButton);
        buttons.add(sizeReportButton);
        buttons.add(debuggerViewButton);
        buttons.add(triggerButton);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        panel.add(buttons, BorderLayout.WEST);
        panel.add(fileLabel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildMainContent() {
        resourcesPanel.setLayout(new BoxLayout(resourcesPanel, BoxLayout.Y_AXIS));
        JScrollPane resourcesScroll = new JScrollPane(resourcesPanel);
        resourcesScroll.getVerticalScrollBar().setUnitIncrement(24);

        exceptionArea = new JTextArea("", 12, 80);
        exceptionArea.setEditable(true);
        exceptionArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane exceptionScroll = new JScrollPane(exceptionArea);

        JPanel resourcesWrapper = new JPanel(new BorderLayout());
        resourcesWrapper.add(buildSummary(), BorderLayout.NORTH);
        resourcesWrapper.add(resourcesScroll, BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Resources", resourcesWrapper);
        tabs.addTab("Exception Stack Trace", exceptionScroll);
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        return tabs;
    }

    private JComponent buildSummary() {
        JPanel summary = new JPanel(new BorderLayout(8, 0));
        summary.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        summaryLabel = new JLabel();
        loadStatusLabel = new JLabel();
        summary.add(summaryLabel, BorderLayout.WEST);
        summary.add(loadStatusLabel, BorderLayout.CENTER);
        return summary;
    }

    private void setLoadStatus(String text) {
        if (loadStatusLabel != null) loadStatusLabel.setText(text == null ? "" : text);
    }

    private void updateHeader() {
        if (fileLabel != null) fileLabel.setText(currentFile == null ? "" : currentFile.getAbsolutePath());
        if (summaryLabel != null) summaryLabel.setText("Resources: " + resourceModel.size());
        if (currentFile != null) setTitle("Debug Dump Viewer - " + currentFile.getName());
    }

    private void addResource(Resource resource) {
        resourceModel.addElement(resource);
    }

    private void refreshResourceCards() {
        resourcesPanel.removeAll();
        if (resourceModel.isEmpty()) {
            JLabel empty = new JLabel("No resources loaded.");
            empty.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            resourcesPanel.add(empty);
        } else {
            for (int i = 0; i < resourceModel.size(); i++) {
                resourcesPanel.add(resourceCard(resourceModel.getElementAt(i)));
                resourcesPanel.add(Box.createVerticalStrut(8));
            }
        }
        resourcesPanel.revalidate();
        resourcesPanel.repaint();
        updateHeader();
    }

    private JComponent resourceCard(Resource resource) {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(resource.name()),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea infoArea = new JTextArea(limitText(resourceInfo(resource), CARD_TEXT_MAX_CHARS));
        infoArea.setEditable(false);
        infoArea.setOpaque(false);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(infoArea, BorderLayout.CENTER);

        JComponent thumbnail = thumbnailComponent(resource);
        if (thumbnail != null) {
            panel.add(thumbnail, BorderLayout.EAST);
        }
        return panel;
    }

    private JComponent thumbnailComponent(Resource resource) {
        if (resource == null) return null;

        if (ENABLE_IMAGEHANDLER_LOAD_BUTTONS && isImageHandlerLike(resource.object())) {
            if (IMAGE_HANDLER_LOAD_FAILED_TEXT.equals(resource.previewStatus())) {
                return thumbnailText(IMAGE_HANDLER_LOAD_FAILED_TEXT);
            }
            if (!resource.loadedImage()) {
                JButton load = new JButton(IMAGE_HANDLER_LOAD_BUTTON_TEXT);
                load.setPreferredSize(new Dimension(160, 80));
                load.addActionListener(e -> loadImageHandlerThumbnail(resource));
                JPanel wrapper = new JPanel(new BorderLayout());
                wrapper.setPreferredSize(new Dimension(180, 110));
                wrapper.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                wrapper.add(load, BorderLayout.CENTER);
                return wrapper;
            }
        }

        BufferedImage preview = previewFor(resource.object());
        if (preview == null) return null;
        JLabel previewLabel = new JLabel(new ImageIcon(scalePreview(preview, 260, 180)));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setVerticalAlignment(SwingConstants.CENTER);
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return previewLabel;
    }

    private JComponent thumbnailText(String text) {
        JLabel label = new JLabel(text == null ? "" : text, SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(180, 110));
        label.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return label;
    }

    private String resourceInfo(Resource resource) {
        StringBuilder out = new StringBuilder();
        out.append("type: ").append(resource.type()).append('\n');
        if (resource.loadedImage()) out.append("source: loaded image\n");
        if (resource.info() != null && !resource.info().isBlank()) out.append(resource.info()).append('\n');

        Object object = resource.object();
        if (object instanceof PointCollection pc) {
            out.append("points: ").append(pc.size()).append('\n');
            if (!pc.isEmpty()) out.append("x: ").append(pc.firstX()).append("..").append(pc.lastX())
                    .append(", y: ").append(pc.bottomY()).append("..").append(pc.topY()).append('\n');
        } else if (object instanceof ShapeContour contour) {
            out.append("points: ").append(contour.size()).append('\n');
            if (!contour.isEmpty()) out.append("rect: ").append(contour.rect()).append('\n')
                    .append("perimeter: ").append(contour.perimeter()).append('\n')
                    .append("area: ").append(contour.area()).append('\n');
        } else if (object instanceof ShapeBounds bounds) {
            PointCollection pc = bounds.get();
            out.append("bounds points: ").append(pc == null ? 0 : pc.size()).append('\n');
            if (pc != null && !pc.isEmpty()) out.append("x: ").append(bounds.firstX()).append("..").append(bounds.lastX())
                    .append(", y: ").append(bounds.bottomY()).append("..").append(bounds.topY()).append('\n');
        } else if (object instanceof FastRGB rgb) {
            out.append("FastRGB: ").append(rgb.getWidth()).append('x').append(rgb.getHeight())
                    .append(", length=").append(rgb.getLength())
                    .append(", hasAlpha=").append(rgb.hasAlphaChannel()).append('\n');
        } else if (object instanceof BufferedImage image) {
            out.append("image: ").append(image.getWidth()).append('x').append(image.getHeight())
                    .append(", type=").append(image.getType())
                    .append(", hasAlpha=").append(image.getColorModel().hasAlpha()).append('\n');
        } else if (isIntegerListMap(object)) {
            out.append("Map<Integer,List<Integer>> columns: ").append(((Map<?, ?>) object).size()).append('\n');
            out.append("values: ").append(countIntegerListMapValues((Map<?, ?>) object)).append('\n');
            out.append(DebugDump.debugString(object));
        } else if (isIntegerBoundsMap(object)) {
            out.append("Map<Integer,IntegerBounds> columns: ").append(((Map<?, ?>) object).size()).append('\n');
            out.append(DebugDump.debugString(object));
        } else if (object instanceof DebugDump.MetadataObject metadata) {
            out.append(metadata).append('\n');
        } else if (object instanceof Collection<?> || object instanceof Map<?, ?>) {
            out.append(DebugDump.debugString(object));
        } else if (object != null && object.getClass().isArray()) {
            out.append("array length: ").append(Array.getLength(object)).append('\n');
            out.append(DebugDump.debugString(object));
        } else {
            out.append(String.valueOf(object));
        }
        return out.toString();
    }

    private BufferedImage previewFor(Object object) {
        try {
            TypeBehavior behavior = behaviorFor(object);
            if (behavior != null && behavior.previewFactory() != null) {
                BufferedImage preview = behavior.previewFactory().apply(object);
                if (preview != null) return preview;
            }
            if (!isVisCandidate(object) || !isPreviewSafe(object)) return null;
            return DebuggerEx.render("Debugger View", object);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private TypeBehavior behaviorFor(Object object) {
        for (TypeBehavior behavior : typeBehaviors()) {
            if (behavior.supports(object)) return behavior;
        }
        return null;
    }

    private List<TypeBehavior> typeBehaviors() {
        ArrayList<TypeBehavior> behaviors = new ArrayList<>();

        // Add new explicitly-coded viewer behavior here.
        behaviors.add(new TypeBehavior(
                "ImageHandler",
                this::isImageHandlerLike,
                object -> {
                    if (!(object instanceof ImageHandler handler)) return null;
                    try {
                        handler.loadImage();
                        handler.loadGuiImage();
                        return handler.getGuiImage() != null ? handler.getGuiImage() : handler.getImage();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                },
                object -> {
                    ImageHandler handler = imageHandlerFromObject(object);
                    if (LOAD_IMAGEHANDLER_BEFORE_DEBUGGER_VIEW) {
                        try {
                            handler.loadImage();
                            handler.loadGuiImage();
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    return handler;
                }
        ));
        behaviors.add(new TypeBehavior(
                "BufferedImage",
                object -> object instanceof BufferedImage,
                object -> (BufferedImage) object,
                Function.identity()
        ));
        behaviors.add(new TypeBehavior(
                "FastRGB",
                object -> object instanceof FastRGB,
                object -> ((FastRGB) object).getImage(),
                Function.identity()
        ));
        behaviors.add(new TypeBehavior(
                "Point-like structures",
                this::isPointlikePreviewCandidate,
                this::renderPointlikePreview,
                Function.identity()
        ));

        return Collections.unmodifiableList(behaviors);
    }

    private boolean isPreviewSafe(Object object) {
        if (object instanceof PointCollection || object instanceof ShapeContour || object instanceof ShapeBounds) return true;
        if (object instanceof Collection<?> collection) {
            int total = 0;
            for (Object item : collection) {
                total += pointlikeSize(item);
                if (total > PREVIEW_MAX_POINTLIKE_SIZE) return true; // sampled point-like preview is still allowed
            }
        }
        if (object != null && object.getClass().isArray()) {
            int total = 0;
            int len = Array.getLength(object);
            for (int i = 0; i < len; i++) {
                total += pointlikeSize(Array.get(object, i));
                if (total > PREVIEW_MAX_POINTLIKE_SIZE) return true;
            }
        }
        return true;
    }

    private int pointlikeSize(Object object) {
        if (object instanceof PointCollection pc) return pc.size();
        if (object instanceof ShapeContour contour) return contour.size();
        if (object instanceof ShapeBounds bounds) return bounds.get() == null ? 0 : bounds.get().size();
        return 0;
    }

    private boolean isPointlikePreviewCandidate(Object object) {
        if (!ENABLE_POINTLIKE_THUMBNAILS || object == null) return false;
        if (object instanceof PointCollection || object instanceof ShapeContour || object instanceof ShapeBounds) return true;
        if (object instanceof Collection<?> collection && !collection.isEmpty()) {
            for (Object item : collection) {
                if (!(item instanceof PointCollection || item instanceof ShapeContour || item instanceof ShapeBounds)) return false;
            }
            return true;
        }
        if (object.getClass().isArray() && Array.getLength(object) > 0) {
            for (int i = 0; i < Array.getLength(object); i++) {
                Object item = Array.get(object, i);
                if (!(item instanceof PointCollection || item instanceof ShapeContour || item instanceof ShapeBounds)) return false;
            }
            return true;
        }
        return false;
    }

    private BufferedImage renderPointlikePreview(Object object) {
        ArrayList<Object> items = new ArrayList<>();
        flattenPointlikeObjects(object, items);
        if (items.isEmpty()) return null;

        Rectangle bounds = combinedPointlikeBounds(items);
        if (bounds == null || bounds.width < 0 || bounds.height < 0) return null;

        BufferedImage image = new BufferedImage(POINTLIKE_PREVIEW_WIDTH, POINTLIKE_PREVIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(230, 230, 230));
        g.drawRect(0, 0, image.getWidth() - 1, image.getHeight() - 1);

        int pad = 10;
        double sx = (image.getWidth() - 2.0 * pad) / Math.max(1.0, bounds.width);
        double sy = (image.getHeight() - 2.0 * pad) / Math.max(1.0, bounds.height);
        double scale = Math.max(0.01, Math.min(sx, sy));
        double ox = pad - bounds.x * scale;
        double oy = pad - bounds.y * scale;

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            g.setColor(previewColor(i));
            drawPointlikeItem(g, item, ox, oy, scale);
        }
        g.dispose();
        return image;
    }

    private Color previewColor(int index) {
        Color[] colors = { Color.RED, Color.BLUE, Color.GREEN.darker(), Color.MAGENTA, Color.ORANGE.darker(), Color.CYAN.darker(), Color.BLACK };
        return colors[Math.floorMod(index, colors.length)];
    }

    private void flattenPointlikeObjects(Object object, List<Object> out) {
        if (object == null) return;
        if (object instanceof PointCollection || object instanceof ShapeContour || object instanceof ShapeBounds) {
            out.add(object);
            return;
        }
        if (object instanceof Collection<?> collection) {
            for (Object item : collection) flattenPointlikeObjects(item, out);
            return;
        }
        if (object.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(object); i++) flattenPointlikeObjects(Array.get(object, i), out);
        }
    }

    private Rectangle combinedPointlikeBounds(List<Object> items) {
        Rectangle combined = null;
        for (Object item : items) {
            Rectangle here = pointlikeBounds(item);
            if (here == null) continue;
            combined = combined == null ? here : combined.union(here);
        }
        return combined;
    }

    private Rectangle pointlikeBounds(Object item) {
        if (item instanceof PointCollection pc) {
            if (pc.isEmpty()) return null;
            return new Rectangle(pc.firstX(), pc.bottomY(), Math.max(1, pc.lastX() - pc.firstX() + 1), Math.max(1, pc.topY() - pc.bottomY() + 1));
        }
        if (item instanceof ShapeContour contour) {
            if (contour.isEmpty()) return null;
            Rectangle r = contour.rect();
            return new Rectangle(r.x, r.y, Math.max(1, r.width), Math.max(1, r.height));
        }
        if (item instanceof ShapeBounds bounds) {
            PointCollection pc = bounds.get();
            if (pc == null || pc.isEmpty()) return null;
            return new Rectangle(bounds.firstX(), bounds.bottomY(), Math.max(1, bounds.lastX() - bounds.firstX() + 1), Math.max(1, bounds.topY() - bounds.bottomY() + 1));
        }
        return null;
    }

    private void drawPointlikeItem(Graphics2D g, Object item, double ox, double oy, double scale) {
        if (item instanceof ShapeContour contour) {
            drawContourPreview(g, contour, ox, oy, scale);
            return;
        }
        if (item instanceof ShapeBounds bounds) {
            PointCollection pc = bounds.get();
            if (pc != null) drawPointCollectionPreview(g, pc, ox, oy, scale);
            return;
        }
        if (item instanceof PointCollection pc) {
            drawPointCollectionPreview(g, pc, ox, oy, scale);
        }
    }

    private void drawPointCollectionPreview(Graphics2D g, PointCollection points, double ox, double oy, double scale) {
        int stride = Math.max(1, points.size() / PREVIEW_SAMPLE_POINT_LIMIT);
        int i = 0;
        int dot = scale >= 2.0 ? 2 : 1;
        for (Point p : points) {
            if ((i++ % stride) != 0) continue;
            int x = (int) Math.round(ox + p.x * scale);
            int y = (int) Math.round(oy + p.y * scale);
            g.fillRect(x, y, dot, dot);
        }
    }

    private void drawContourPreview(Graphics2D g, ShapeContour contour, double ox, double oy, double scale) {
        Point first = null;
        Point previous = null;
        int stride = Math.max(1, contour.size() / PREVIEW_SAMPLE_POINT_LIMIT);
        int i = 0;
        for (Point p : contour) {
            if ((i++ % stride) != 0) continue;
            if (first == null) first = p;
            if (previous != null) drawPreviewLine(g, previous, p, ox, oy, scale);
            previous = p;
        }
        if (first != null && previous != null && first != previous) drawPreviewLine(g, previous, first, ox, oy, scale);
    }

    private void drawPreviewLine(Graphics2D g, Point a, Point b, double ox, double oy, double scale) {
        int x1 = (int) Math.round(ox + a.x * scale);
        int y1 = (int) Math.round(oy + a.y * scale);
        int x2 = (int) Math.round(ox + b.x * scale);
        int y2 = (int) Math.round(oy + b.y * scale);
        g.drawLine(x1, y1, x2, y2);
    }

    private Image scalePreview(BufferedImage image, int maxWidth, int maxHeight) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) return image;
        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        scale = Math.min(1.0, Math.max(0.01, scale));
        return image.getScaledInstance(Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale)), Image.SCALE_SMOOTH);
    }

    private void openDumpSizeReport() {
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this, "No current dump file is loaded.", "Dump Size Report", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            List<DebugDump.SizeEntry> entries = DebugDump.sizeReport(currentFile);
            StringBuilder out = new StringBuilder();
            long fileBytes = currentFile.length();
            out.append("file: ").append(currentFile.getAbsolutePath()).append('\n');
            out.append("file bytes: ").append(fileBytes).append("\n\n");
            out.append("Largest entries by encoded block size:\n");
            int rank = 1;
            for (DebugDump.SizeEntry entry : entries) {
                double pct = fileBytes <= 0 ? 0.0 : (100.0 * entry.entryBytes() / fileBytes);
                out.append(rank++).append(". ")
                        .append(entry.key()).append("  [").append(entry.typeName()).append("]\n")
                        .append("   encoding: ").append(entry.encoding()).append('\n')
                        .append("   entry bytes: ").append(entry.entryBytes())
                        .append(String.format("  (%.2f%% of file)", pct)).append('\n')
                        .append("   value chars: ").append(entry.valueChars()).append('\n')
                        .append("   info: ").append(entry.info()).append("\n\n");
                if (rank > 250) {
                    out.append("... truncated remaining entries for display\n");
                    break;
                }
            }
            JTextArea area = new JTextArea(out.toString(), 32, 110);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Dump Size Report", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Unable to build dump size report:\n" + ex.getMessage(), "Dump Size Report", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadImageHandlerThumbnail(Resource resource) {
        if (resource == null) return;
        resource.setPreviewStatus("Loading...");
        refreshResourceCards();
        SwingWorker<ImageHandler, Void> worker = new SwingWorker<>() {
            @Override
            protected ImageHandler doInBackground() throws Exception {
                ImageHandler handler = imageHandlerFromObject(resource.object());
                handler.loadImage();
                handler.loadGuiImage();
                return handler;
            }

            @Override
            protected void done() {
                try {
                    ImageHandler handler = get();
                    resource.setObject(handler);
                    resource.setType(ImageHandler.class.getName());
                    resource.setLoadedImage(true);
                    resource.setPreviewStatus("");
                } catch (Throwable ex) {
                    resource.setPreviewStatus(IMAGE_HANDLER_LOAD_FAILED_TEXT);
                }
                refreshResourceCards();
            }
        };
        worker.execute();
    }

    private void loadImageResource() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Image Resource");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) throw new IllegalArgumentException("Unsupported image file");
            addResource(new Resource(uniqueName(file.getName()), BufferedImage.class.getName(), "loaded image: " + image.getWidth() + "x" + image.getHeight(), image, true));
            refreshResourceCards();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Unable to load image:\n" + ex.getMessage(), "Load Image", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addConstructedResource() {
        Resource source = chooseOneResource("Select Source Resource", r -> !availableOperationsFor(r.object()).isEmpty());
        if (source == null) return;
        Resource generated = openConstructorChainDialog(source, true);
        if (generated == null) return;
        if (hasResourceName(generated.name())) {
            JOptionPane.showMessageDialog(this, "A resource named '" + generated.name() + "' already exists.", "Add Constructed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        addResource(generated);
        refreshResourceCards();
    }

    private void renameResource() {
        Resource resource = chooseOneResource("Rename Resource", r -> true);
        if (resource == null) return;
        String newName = JOptionPane.showInputDialog(this, "New resource name:", resource.name());
        if (newName == null) return;
        newName = newName.trim();
        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Resource name cannot be blank.", "Rename", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!newName.equals(resource.name()) && hasResourceName(newName)) {
            JOptionPane.showMessageDialog(this, "A resource named '" + newName + "' already exists.", "Rename", JOptionPane.ERROR_MESSAGE);
            return;
        }
        resource.setName(newName);
        refreshResourceCards();
    }

    private void removeResources() {
        List<Resource> selected = chooseManyResources("Remove Resources", r -> true);
        if (selected.isEmpty()) return;
        int answer = JOptionPane.showConfirmDialog(this, "Remove " + selected.size() + " resource(s) from this dump?", "Remove", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) return;
        for (Resource resource : selected) resourceModel.removeElement(resource);
        refreshResourceCards();
    }

    private void saveCurrentFile(boolean showResult) {
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this, "No current dump file is loaded.", "Debug Dump", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            DebugDump.writeDecoded(currentFile, resourceMap(), currentExceptionText());
            if (showResult) JOptionPane.showMessageDialog(this, "Saved:\n" + currentFile.getAbsolutePath(), "Debug Dump", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Unable to save debug dump:\n" + ex.getMessage(), "Debug Dump", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void expandDump() {
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this, "No current dump file is loaded.", "Expand Dump", JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<ExpandRule> selectedRules = chooseExpandRules();
        if (selectedRules == null) return;

        int added = expandResourcesInMemory(selectedRules);
        File expanded = expandedFile(currentFile);
        currentFile = expanded;
        try {
            DebugDump.writeDecoded(currentFile, resourceMap(), currentExceptionText());
            refreshResourceCards();
            JOptionPane.showMessageDialog(this, "Expanded dump saved with " + added + " new resource(s):\n" + currentFile.getAbsolutePath(), "Expand Dump", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Unable to save expanded debug dump:\n" + ex.getMessage(), "Expand Dump", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<ExpandRule> chooseExpandRules() {
        List<ExpandRule> rules = expandRules();
        JPanel checkPanel = new JPanel();
        checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.Y_AXIS));
        ArrayList<JCheckBox> boxes = new ArrayList<>();
        for (ExpandRule rule : rules) {
            JCheckBox box = new JCheckBox(rule.label(), Application.prefs.getBoolean(rule.prefKey(), rule.defaultChecked()));
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            boxes.add(box);
            checkPanel.add(box);
        }

        JScrollPane scroll = new JScrollPane(checkPanel);
        scroll.setPreferredSize(new Dimension(640, Math.min(420, 34 + rules.size() * 28)));
        int result = JOptionPane.showConfirmDialog(
                this,
                scroll,
                "Expand Dump Rules",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) return null;

        ArrayList<ExpandRule> selected = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            boolean checked = boxes.get(i).isSelected();
            Application.prefs.putBoolean(rules.get(i).prefKey(), checked);
            if (checked) selected.add(rules.get(i));
        }
        return selected;
    }

    private List<ExpandRule> expandRules() {
        ArrayList<ExpandRule> rules = new ArrayList<>();
        rules.add(new ExpandRule(
                "integer_list_map_to_pointcollection",
                "Map<Integer,List<Integer>> -> PointCollection",
                this::isIntegerListMap,
                POINT_COLLECTION_SUFFIX,
                DEFAULT_EXPAND_INTEGER_LIST_MAP_TO_POINT_COLLECTION,
                this::pointCollectionFromMap
        ));
        rules.add(new ExpandRule(
                "integer_list_map_to_shapecontour",
                "Map<Integer,List<Integer>> -> ShapeContour",
                this::isIntegerListMap,
                SHAPE_CONTOUR_SUFFIX,
                DEFAULT_EXPAND_INTEGER_LIST_MAP_TO_SHAPE_CONTOUR,
                o -> new ShapeContour(pointCollectionFromMap(o))
        ));
        rules.add(new ExpandRule(
                "integer_list_map_to_shapebounds",
                "Map<Integer,List<Integer>> -> ShapeBounds",
                this::isIntegerListMap,
                SHAPE_BOUNDS_SUFFIX,
                DEFAULT_EXPAND_INTEGER_LIST_MAP_TO_SHAPE_BOUNDS,
                this::shapeBoundsFromMap
        ));
        rules.add(new ExpandRule(
                "integer_bounds_map_to_pointcollection",
                "Map<Integer,IntegerBounds> -> PointCollection",
                this::isIntegerBoundsMap,
                POINT_COLLECTION_SUFFIX,
                DEFAULT_EXPAND_INTEGER_BOUNDS_MAP_TO_POINT_COLLECTION,
                this::pointCollectionFromMap
        ));
        rules.add(new ExpandRule(
                "integer_bounds_map_to_shapecontour",
                "Map<Integer,IntegerBounds> -> ShapeContour",
                this::isIntegerBoundsMap,
                SHAPE_CONTOUR_SUFFIX,
                DEFAULT_EXPAND_INTEGER_BOUNDS_MAP_TO_SHAPE_CONTOUR,
                o -> new ShapeContour(pointCollectionFromMap(o))
        ));
        rules.add(new ExpandRule(
                "integer_bounds_map_to_shapebounds",
                "Map<Integer,IntegerBounds> -> ShapeBounds",
                this::isIntegerBoundsMap,
                SHAPE_BOUNDS_SUFFIX,
                DEFAULT_EXPAND_INTEGER_BOUNDS_MAP_TO_SHAPE_BOUNDS,
                this::shapeBoundsFromMap
        ));
        rules.add(new ExpandRule(
                "pointcollection_to_shapecontour",
                "PointCollection -> ShapeContour",
                o -> o instanceof PointCollection,
                SHAPE_CONTOUR_SUFFIX,
                DEFAULT_EXPAND_POINT_COLLECTION_TO_SHAPE_CONTOUR,
                o -> new ShapeContour((PointCollection) o)
        ));
        rules.add(new ExpandRule(
                "pointcollection_to_shapebounds",
                "PointCollection -> ShapeBounds",
                o -> o instanceof PointCollection,
                SHAPE_BOUNDS_SUFFIX,
                DEFAULT_EXPAND_POINT_COLLECTION_TO_SHAPE_BOUNDS,
                o -> new ShapeBounds(new PointCollection((PointCollection) o))
        ));
        rules.add(new ExpandRule(
                "shapebounds_to_shapecontour",
                "ShapeBounds -> ShapeContour",
                o -> o instanceof ShapeBounds,
                SHAPE_CONTOUR_SUFFIX,
                DEFAULT_EXPAND_SHAPE_BOUNDS_TO_SHAPE_CONTOUR,
                o -> new ShapeContour((ShapeBounds) o)
        ));
        rules.add(new ExpandRule(
                "imagehandler_to_fastrgb",
                "ImageHandler -> FastRGB",
                this::isImageHandlerLike,
                FAST_RGB_SUFFIX,
                DEFAULT_EXPAND_IMAGE_HANDLER_TO_FAST_RGB,
                this::fastRGBFromImageHandlerLike
        ));
        return Collections.unmodifiableList(rules);
    }

    private int expandResourcesInMemory(List<ExpandRule> rules) {
        int added = 0;
        int originalSize = resourceModel.size();
        Set<String> names = currentNameSet();

        for (int i = 0; i < originalSize; i++) {
            Resource resource = resourceModel.getElementAt(i);
            Object object = resource.object();
            String base = rootName(resource.name());

            for (ExpandRule rule : rules) {
                if (!rule.supports(object)) continue;
                String targetName = base + rule.suffix();
                if (names.contains(targetName)) continue;

                try {
                    Object generated = rule.apply(object);
                    addResource(new Resource(targetName, typeName(generated), "expanded from " + resource.name() + " via " + rule.label(), generated, false));
                    names.add(targetName);
                    added++;
                } catch (Throwable ex) {
                    addResource(new Resource(uniqueName(targetName + "_error"), "ExpansionError", "failed expansion from " + resource.name() + " via " + rule.label(), String.valueOf(ex), false));
                    names.add(targetName);
                    added++;
                }
            }
        }
        return added;
    }

    private void openDebuggerViewDialog() {
        JDialog dialog = new JDialog(this, "Debugger View Arguments", true);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultListModel<Resource> availableModel = new DefaultListModel<>();
        for (int i = 0; i < resourceModel.size(); i++) availableModel.addElement(resourceModel.getElementAt(i));
        DefaultListModel<Resource> selectedModel = new DefaultListModel<>();

        JList<Resource> availableList = new JList<>(availableModel);
        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JList<Resource> selectedList = new JList<>(selectedModel);
        selectedList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JButton addButton = new JButton("Add ->");
        addButton.addActionListener(e -> {
            for (Resource resource : availableList.getSelectedValuesList()) selectedModel.addElement(resource);
        });

        JButton addConstructedButton = new JButton("Add Constructed ->");
        addConstructedButton.addActionListener(e -> {
            Resource source = availableList.getSelectedValue();
            if (source == null) return;
            if (availableOperationsFor(source.object()).isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "No constructor operations are available for this resource.", "Debugger View", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            Resource generated = openConstructorChainDialog(source, false);
            if (generated != null) selectedModel.addElement(generated);
        });

        JButton removeButton = new JButton("<- Remove");
        removeButton.addActionListener(e -> {
            List<Resource> selected = selectedList.getSelectedValuesList();
            for (Resource resource : selected) selectedModel.removeElement(resource);
        });

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        addButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        addConstructedButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        removeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttons.add(Box.createVerticalGlue());
        buttons.add(addButton);
        buttons.add(Box.createVerticalStrut(8));
        buttons.add(addConstructedButton);
        buttons.add(Box.createVerticalStrut(8));
        buttons.add(removeButton);
        buttons.add(Box.createVerticalGlue());

        JPanel lists = new JPanel(new GridLayout(1, 3, 8, 8));
        lists.add(wrapList("Available resources", availableList));
        lists.add(buttons);
        lists.add(wrapList("DebuggerEx.visN arguments", selectedList));
        lists.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton view = new JButton("View");
        view.addActionListener(e -> {
            try {
                Object[] args = new Object[selectedModel.size()];
                boolean updated = false;
                for (int i = 0; i < selectedModel.size(); i++) {
                    Resource resource = selectedModel.get(i);
                    Object before = resource.object();
                    args[i] = resolveDebuggerViewArgument(resource);
                    updated |= before != resource.object();
                }
                dialog.dispose();
                if (updated) refreshResourceCards();
                DebuggerEx.visN("Debugger View", args);
            } catch (Throwable ex) {
                JOptionPane.showMessageDialog(dialog, "Unable to open DebuggerEx.visN view:\n" + rootMessage(ex), "Debugger View", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(cancel);
        bottom.add(view);

        dialog.add(lists, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setSize(900, 540);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private Resource openConstructorChainDialog(Resource source, boolean requireName) {
        JDialog dialog = new JDialog(this, "Constructor Chain", true);
        dialog.setLayout(new BorderLayout(10, 10));

        final Object[] currentObject = { source.object() };
        final String[] currentName = { source.name() };
        DefaultListModel<String> operationModel = new DefaultListModel<>();
        JList<String> operationList = new JList<>(operationModel);
        JTextArea status = new JTextArea(8, 62);
        status.setEditable(false);
        status.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JTextField nameField = new JTextField(uniqueName(rootName(source.name()) + suffixForObject(source.object())));
        final Resource[] result = { null };
        final int[] appliedCount = { 0 };

        Runnable refreshOps = () -> {
            operationModel.clear();
            for (ConstructorOperation op : availableOperationsFor(currentObject[0])) operationModel.addElement(op.label());
            status.setText("source: " + source.name() + "\ncurrent name: " + currentName[0] + "\ncurrent type: " + typeName(currentObject[0]) + "\n\n" + limitText(describeObject(currentObject[0]), CARD_TEXT_MAX_CHARS));
            if (!requireName) nameField.setText(currentName[0]);
        };
        refreshOps.run();

        JButton apply = new JButton("Apply Selected Constructor");
        apply.addActionListener(e -> {
            int idx = operationList.getSelectedIndex();
            if (idx < 0) return;
            List<ConstructorOperation> ops = availableOperationsFor(currentObject[0]);
            if (idx >= ops.size()) return;
            ConstructorOperation op = ops.get(idx);
            try {
                currentObject[0] = op.apply(currentObject[0]);
                appliedCount[0]++;
                currentName[0] = currentName[0] + op.suffix();
                nameField.setText(uniqueName(currentName[0]));
                refreshOps.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Constructor failed:\n" + ex.getMessage(), "Constructor Chain", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());

        JButton use = new JButton(requireName ? "Add Resource" : "Use as Argument");
        use.addActionListener(e -> {
            if (appliedCount[0] == 0) {
                JOptionPane.showMessageDialog(dialog, "Apply at least one constructor first.", "Constructor Chain", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String name = requireName ? nameField.getText().trim() : currentName[0];
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Name cannot be blank.", "Constructor Chain", JOptionPane.ERROR_MESSAGE);
                return;
            }
            result[0] = new Resource(name, typeName(currentObject[0]), "constructor chain from " + source.name(), currentObject[0], false);
            dialog.dispose();
        });

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        center.add(wrapList("Available constructors", operationList));
        center.add(new JScrollPane(status));

        JPanel namePanel = new JPanel(new BorderLayout(8, 8));
        namePanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        namePanel.add(new JLabel("Result name:"), BorderLayout.WEST);
        namePanel.add(nameField, BorderLayout.CENTER);
        namePanel.setVisible(requireName);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(apply);
        bottom.add(cancel);
        bottom.add(use);

        JPanel south = new JPanel(new BorderLayout());
        south.add(namePanel, BorderLayout.NORTH);
        south.add(bottom, BorderLayout.SOUTH);

        dialog.add(center, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setSize(780, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return result[0];
    }

    private JComponent wrapList(String title, JList<?> list) {
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder(title));
        return scroll;
    }

    private Resource chooseOneResource(String title, Predicate<Resource> filter) {
        List<Resource> selected = chooseResources(title, filter, ListSelectionModel.SINGLE_SELECTION);
        return selected.isEmpty() ? null : selected.get(0);
    }

    private List<Resource> chooseManyResources(String title, Predicate<Resource> filter) {
        return chooseResources(title, filter, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    private List<Resource> chooseResources(String title, Predicate<Resource> filter, int selectionMode) {
        DefaultListModel<Resource> model = new DefaultListModel<>();
        for (int i = 0; i < resourceModel.size(); i++) {
            Resource resource = resourceModel.get(i);
            if (filter.test(resource)) model.addElement(resource);
        }
        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No matching resources are available.", title, JOptionPane.INFORMATION_MESSAGE);
            return List.of();
        }
        JList<Resource> list = new JList<>(model);
        list.setSelectionMode(selectionMode);
        int result = JOptionPane.showConfirmDialog(this, new JScrollPane(list), title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return List.of();
        return list.getSelectedValuesList();
    }

    private LinkedHashMap<String, Object> resourceMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < resourceModel.size(); i++) {
            Resource resource = resourceModel.get(i);
            map.put(resource.name(), resource.object());
        }
        return map;
    }

    private String currentExceptionText() {
        return exceptionArea == null ? exceptionStackTrace : exceptionArea.getText();
    }

    private File expandedFile(File file) {
        String name = file.getName();
        String ext = "." + DebugDump.FILE_EXTENSION;
        String base = name.endsWith(ext) ? name.substring(0, name.length() - ext.length()) : name;
        File parent = file.getParentFile();
        return new File(parent == null ? new File(".") : parent, base + "_expanded" + ext);
    }

    private Set<String> currentNameSet() {
        HashSet<String> names = new HashSet<>();
        for (int i = 0; i < resourceModel.size(); i++) names.add(resourceModel.get(i).name());
        return names;
    }

    private boolean hasResourceName(String name) {
        return getResourceByName(name) != null;
    }

    private Resource getResourceByName(String name) {
        for (int i = 0; i < resourceModel.size(); i++) {
            Resource resource = resourceModel.get(i);
            if (Objects.equals(resource.name(), name)) return resource;
        }
        return null;
    }

    private int indexOfResource(String name) {
        for (int i = 0; i < resourceModel.size(); i++) {
            if (Objects.equals(resourceModel.get(i).name(), name)) return i;
        }
        return -1;
    }

    private String uniqueName(String desired) {
        String base = desired == null || desired.isBlank() ? "resource" : desired.trim();
        if (!hasResourceName(base)) return base;
        int i = 2;
        while (hasResourceName(base + "_" + i)) i++;
        return base + "_" + i;
    }

    private String rootName(String name) {
        String root = name == null || name.isBlank() ? "resource" : name.trim();
        boolean changed;
        do {
            changed = false;
            for (String suffix : GENERATED_SUFFIXES) {
                if (root.endsWith(suffix) && root.length() > suffix.length()) {
                    root = root.substring(0, root.length() - suffix.length());
                    changed = true;
                }
            }
        } while (changed);
        return root;
    }

    private List<ConstructorOperation> availableOperationsFor(Object object) {
        ArrayList<ConstructorOperation> ops = new ArrayList<>();

        if (isIntegerListMap(object) || isIntegerBoundsMap(object)) {
            ops.add(new ConstructorOperation("new PointCollection(Map)", POINT_COLLECTION_SUFFIX, this::pointCollectionFromMap));
            ops.add(new ConstructorOperation("new ShapeBounds(Map)", SHAPE_BOUNDS_SUFFIX, this::shapeBoundsFromMap));
            ops.add(new ConstructorOperation("new ShapeContour(new PointCollection(Map))", SHAPE_CONTOUR_SUFFIX, o -> new ShapeContour(pointCollectionFromMap(o))));
        }
        if (object instanceof PointCollection) {
            ops.add(new ConstructorOperation("new PointCollection(PointCollection)", POINT_COLLECTION_SUFFIX, o -> new PointCollection((PointCollection) o)));
            ops.add(new ConstructorOperation("new ShapeBounds(PointCollection)", SHAPE_BOUNDS_SUFFIX, o -> new ShapeBounds(new PointCollection((PointCollection) o))));
            ops.add(new ConstructorOperation("new ShapeContour(PointCollection)", SHAPE_CONTOUR_SUFFIX, o -> new ShapeContour((PointCollection) o)));
        }
        if (object instanceof ShapeBounds) {
            ops.add(new ConstructorOperation("new ShapeBounds(ShapeBounds)", SHAPE_BOUNDS_SUFFIX, o -> {
                PointCollection points = ((ShapeBounds) o).get();
                return new ShapeBounds(points == null ? new PointCollection() : new PointCollection(points));
            }));
            ops.add(new ConstructorOperation("new ShapeContour(ShapeBounds)", SHAPE_CONTOUR_SUFFIX, o -> new ShapeContour((ShapeBounds) o)));
        }
        if (object instanceof ShapeContour) {
            ops.add(new ConstructorOperation("new ShapeContour(ShapeContour)", SHAPE_CONTOUR_SUFFIX, o -> new ShapeContour(new PointCollection(pointsOf((ShapeContour) o)))));
        }
        if (isImageHandlerLike(object)) {
            ops.add(new ConstructorOperation("new FastRGB(ImageHandler)", FAST_RGB_SUFFIX, this::fastRGBFromImageHandlerLike));
        }
        if (object instanceof FastRGB) {
            ops.add(new ConstructorOperation("new FastRGB(FastRGB)", FAST_RGB_SUFFIX, o -> new FastRGB((FastRGB) o)));
        }
        return ops;
    }

    private PointCollection pointCollectionFromMap(Object object) {
        if (isIntegerListMap(object)) return new PointCollection(copyIntegerListMap((Map<?, ?>) object));
        if (isIntegerBoundsMap(object)) return new PointCollection(integerBoundsMapToListMap((Map<?, ?>) object));
        throw new IllegalArgumentException("Object is not Map<Integer,List<Integer>> or Map<Integer,IntegerBounds>");
    }

    private ShapeBounds shapeBoundsFromMap(Object object) {
        if (isIntegerListMap(object)) return new ShapeBounds(copyIntegerListMap((Map<?, ?>) object));
        if (isIntegerBoundsMap(object)) {
            TreeMap<Integer, IntegerBounds> copy = copyIntegerBoundsMap((Map<?, ?>) object);
            return new ShapeBounds(() -> copy);
        }
        throw new IllegalArgumentException("Object is not Map<Integer,List<Integer>> or Map<Integer,IntegerBounds>");
    }

    private TreeMap<Integer, List<Integer>> copyIntegerListMap(Map<?, ?> source) {
        TreeMap<Integer, List<Integer>> copy = new TreeMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            ArrayList<Integer> ys = new ArrayList<>();
            for (Object value : (List<?>) entry.getValue()) ys.add((Integer) value);
            copy.put((Integer) entry.getKey(), ys);
        }
        return copy;
    }

    private TreeMap<Integer, IntegerBounds> copyIntegerBoundsMap(Map<?, ?> source) {
        TreeMap<Integer, IntegerBounds> copy = new TreeMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            IntegerBounds bounds = (IntegerBounds) entry.getValue();
            copy.put((Integer) entry.getKey(), new IntegerBounds(bounds.getLowerBound(), bounds.getUpperBound()));
        }
        return copy;
    }

    private TreeMap<Integer, List<Integer>> integerBoundsMapToListMap(Map<?, ?> source) {
        TreeMap<Integer, List<Integer>> copy = new TreeMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            IntegerBounds bounds = (IntegerBounds) entry.getValue();
            copy.put((Integer) entry.getKey(), bounds.getSequence());
        }
        return copy;
    }

    private FastRGB fastRGBFromImageHandlerLike(Object object) {
        return new FastRGB(imageHandlerFromObject(object));
    }

    private Object resolveDebuggerViewArgument(Resource resource) throws Exception {
        Object object = resource.object();
        TypeBehavior behavior = behaviorFor(object);
        Object resolved = behavior == null || behavior.visArgumentFactory() == null
                ? object
                : behavior.visArgumentFactory().apply(object);
        if (resolved instanceof ImageHandler handler && isImageHandlerLike(object)) {
            resource.setObject(handler);
            resource.setType(ImageHandler.class.getName());
            resource.setLoadedImage(true);
            resource.setPreviewStatus("");
        }
        return resolved;
    }

    private ImageHandler imageHandlerFromObject(Object object) {
        if (object instanceof ImageHandler handler) return handler;
        if (object instanceof DebugDump.MetadataObject metadata && "ImageHandler".equals(metadata.sourceType())) {
            return imageHandlerFromMetadata(metadata);
        }
        throw new IllegalArgumentException("Object is not an ImageHandler");
    }

    private ImageHandler imageHandlerFromMetadata(DebugDump.MetadataObject metadata) {
        String imageFile = metadata.fields().get("imageFile");
        if (imageFile == null || imageFile.isBlank() || "null".equals(imageFile)) {
            throw new IllegalArgumentException("ImageHandler metadata does not contain an imageFile path");
        }
        int rgbType = BufferedImage.TYPE_INT_RGB;
        String rgbTypeText = metadata.fields().get("rgbType");
        if (rgbTypeText != null && !rgbTypeText.isBlank() && !"null".equals(rgbTypeText)) {
            try {
                rgbType = Integer.parseInt(rgbTypeText.trim());
            } catch (NumberFormatException ignored) {
                rgbType = BufferedImage.TYPE_INT_RGB;
            }
        }
        return new ImageHandler(new File(imageFile), rgbType);
    }

    private boolean isImageHandlerLike(Object object) {
        return object instanceof ImageHandler || isImageHandlerMetadata(object);
    }

    private boolean isImageHandlerMetadata(Object object) {
        return object instanceof DebugDump.MetadataObject metadata && "ImageHandler".equals(metadata.sourceType());
    }

    private String describeImageHandlerLike(Object object) {
        if (object instanceof ImageHandler handler) {
            try {
                BufferedImage image = handler.getImage();
                BufferedImage gui = handler.getGuiImage();
                return "ImageHandler"
                        + "image=" + (image == null ? "not loaded" : image.getWidth() + "x" + image.getHeight())
                        + ", guiImage=" + (gui == null ? "not loaded" : gui.getWidth() + "x" + gui.getHeight());
            } catch (Throwable ignored) {
                return "ImageHandler";
            }
        }
        if (object instanceof DebugDump.MetadataObject metadata) return metadata.toString();
        return String.valueOf(object);
    }

    private boolean isIntegerListMap(Object object) {
        if (!(object instanceof Map<?, ?> map) || map.isEmpty()) return false;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof Integer)) return false;
            if (!(entry.getValue() instanceof List<?> list)) return false;
            for (Object value : list) if (!(value instanceof Integer)) return false;
        }
        return true;
    }

    private boolean isIntegerBoundsMap(Object object) {
        if (!(object instanceof Map<?, ?> map) || map.isEmpty()) return false;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof Integer) || !(entry.getValue() instanceof IntegerBounds)) return false;
        }
        return true;
    }

    private long countIntegerListMapValues(Map<?, ?> map) {
        long count = 0L;
        for (Object value : map.values()) count += ((List<?>) value).size();
        return count;
    }

    private List<java.awt.Point> pointsOf(ShapeContour contour) {
        ArrayList<java.awt.Point> points = new ArrayList<>(contour.size());
        for (java.awt.Point point : contour) points.add(new java.awt.Point(point));
        return points;
    }

    private String suffixForObject(Object object) {
        if (object instanceof PointCollection) return POINT_COLLECTION_SUFFIX;
        if (object instanceof ShapeContour) return SHAPE_CONTOUR_SUFFIX;
        if (object instanceof ShapeBounds) return SHAPE_BOUNDS_SUFFIX;
        if (object instanceof FastRGB || isImageHandlerLike(object)) return FAST_RGB_SUFFIX;
        if (isIntegerListMap(object) || isIntegerBoundsMap(object)) return POINT_COLLECTION_SUFFIX;
        return "_constructed";
    }

    private String describeObject(Object object) {
        if (object == null) return "null";
        if (object instanceof PointCollection pc) return "PointCollection size=" + pc.size();
        if (object instanceof ShapeContour contour) return "ShapeContour size=" + contour.size();
        if (object instanceof ShapeBounds bounds) return "ShapeBounds points=" + (bounds.get() == null ? 0 : bounds.get().size());
        if (object instanceof FastRGB rgb) return "FastRGB " + rgb.getWidth() + "x" + rgb.getHeight() + ", hasAlpha=" + rgb.hasAlphaChannel();
        if (isImageHandlerLike(object)) return describeImageHandlerLike(object);
        if (isIntegerListMap(object)) return "Map<Integer,List<Integer>> columns=" + ((Map<?, ?>) object).size() + "\n" + DebugDump.debugString(object);
        if (isIntegerBoundsMap(object)) return "Map<Integer,IntegerBounds> columns=" + ((Map<?, ?>) object).size() + "\n" + DebugDump.debugString(object);
        if (object instanceof DebugDump.MetadataObject metadata) return metadata.toString();
        if (object instanceof BufferedImage image) return "BufferedImage " + image.getWidth() + "x" + image.getHeight();
        if (object instanceof Collection<?> || object instanceof Map<?, ?>) return DebugDump.debugString(object);
        if (object.getClass().isArray()) return DebugDump.debugString(object);
        if (object instanceof Rectangle rect) return rect.toString();
        return String.valueOf(object);
    }

    private boolean isVisCandidate(Object object) {
        if (object == null) return false;
        if (object instanceof PointCollection || object instanceof ShapeContour || object instanceof ShapeBounds || object instanceof BufferedImage || object instanceof FastRGB || isImageHandlerLike(object)) return true;
        if (object instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> item instanceof PointCollection || item instanceof ShapeContour || item instanceof ShapeBounds);
        }
        if (object.getClass().isArray()) {
            int len = Array.getLength(object);
            for (int i = 0; i < len; i++) {
                Object item = Array.get(object, i);
                if (item instanceof PointCollection || item instanceof ShapeContour || item instanceof ShapeBounds) return true;
            }
        }
        return false;
    }

    private static String typeName(Object object) {
        if (object instanceof DebugDump.MetadataObject metadata) return metadata.sourceType();
        return object == null ? "null" : object.getClass().getName();
    }

    public static DebugDumpViewer viewer() {
        return debuggerTarget;
    }

    public static Object get(String id) {
        DebugDumpViewer viewer = requireDebuggerTarget();
        Resource resource = viewer.getResourceByName(id);
        return resource == null ? null : resource.object();
    }

    public static Map<String, Object> objects() {
        return new LinkedHashMap<>(requireDebuggerTarget().resourceMap());
    }

    public static void add(String id, Object object) {
        put(id, object);
    }

    public static void put(String id, Object object) {
        mutateDebuggerTarget(viewer -> viewer.putResourceFromDebugger(id, object));
    }

    public static void remove(String id) {
        mutateDebuggerTarget(viewer -> {
            int idx = viewer.indexOfResource(id);
            if (idx >= 0) viewer.resourceModel.remove(idx);
            viewer.refreshResourceCards();
        });
    }

    public static void rename(String oldId, String newId) {
        mutateDebuggerTarget(viewer -> {
            Resource resource = viewer.getResourceByName(oldId);
            if (resource == null) return;
            if (viewer.hasResourceName(newId) && !Objects.equals(oldId, newId)) {
                throw new IllegalArgumentException("Resource already exists: " + newId);
            }
            resource.setName(newId);
            viewer.refreshResourceCards();
        });
    }

    public static void clear() {
        mutateDebuggerTarget(viewer -> {
            viewer.resourceModel.clear();
            viewer.refreshResourceCards();
        });
    }

    public static void refresh() {
        mutateDebuggerTarget(DebugDumpViewer::refreshResourceCards);
    }

    public static void save() {
        mutateDebuggerTarget(viewer -> viewer.saveCurrentFile(false));
    }

    public static File currentFile() {
        return requireDebuggerTarget().currentFile;
    }

    private void putResourceFromDebugger(String id, Object object) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Resource id cannot be blank");
        Resource resource = new Resource(id.trim(), typeName(object), "added from debugger evaluation", object, false);
        int idx = indexOfResource(resource.name());
        if (idx >= 0) resourceModel.set(idx, resource);
        else resourceModel.addElement(resource);
        refreshResourceCards();
    }

    private static DebugDumpViewer requireDebuggerTarget() {
        DebugDumpViewer viewer = debuggerTarget;
        if (viewer == null) throw new IllegalStateException("No DebugDumpViewer is currently registered as debugger target");
        return viewer;
    }

    private static void mutateDebuggerTarget(Consumer<DebugDumpViewer> action) {
        DebugDumpViewer viewer = requireDebuggerTarget();
        if (SwingUtilities.isEventDispatchThread()) {
            action.accept(viewer);
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> action.accept(viewer));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Place a breakpoint on the dummy assignment below, then press the Trigger Debugger button. */
    public static void triggerDebugger() {
        int debuggerBreakpointTarget = 1;
        debuggerBreakpointTarget++;
        if (debuggerBreakpointTarget == Integer.MIN_VALUE) {
            System.out.println(debuggerBreakpointTarget);
        }
    }

    private static String limitText(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n... truncated " + (text.length() - maxChars) + " character(s) for viewer display";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable t = throwable;
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private record TypeBehavior(String label, Predicate<Object> predicate,
                                Function<Object, BufferedImage> previewFactory,
                                Function<Object, Object> visArgumentFactory) {
        boolean supports(Object input) {
            return input != null && predicate.test(input);
        }
    }

    private record ConstructorOperation(String label, String suffix, Function<Object, Object> factory) {
        Object apply(Object input) {
            return factory.apply(input);
        }
    }

    private record ExpandRule(String id, String label, Predicate<Object> sourcePredicate, String suffix,
                              boolean defaultChecked, Function<Object, Object> factory) {
        boolean supports(Object input) {
            return sourcePredicate.test(input);
        }

        Object apply(Object input) {
            return factory.apply(input);
        }

        String prefKey() {
            return EXPAND_PREF_PREFIX + id;
        }
    }

    private static final class Resource {
        private String name;
        private String type;
        private final String info;
        private Object object;
        private boolean loadedImage;
        private String previewStatus = "";

        private Resource(String name, String type, String info, Object object, boolean loadedImage) {
            this.name = name == null || name.isBlank() ? "resource" : name;
            this.type = type == null ? "unknown" : type;
            this.info = info == null ? "" : info;
            this.object = object;
            this.loadedImage = loadedImage;
        }

        String name() { return name; }
        void setName(String name) { this.name = name; }
        String type() { return type; }
        void setType(String type) { this.type = type == null ? "unknown" : type; }
        String info() { return info; }
        Object object() { return object; }
        void setObject(Object object) { this.object = object; }
        boolean loadedImage() { return loadedImage; }
        void setLoadedImage(boolean loadedImage) { this.loadedImage = loadedImage; }
        String previewStatus() { return previewStatus; }
        void setPreviewStatus(String previewStatus) { this.previewStatus = previewStatus == null ? "" : previewStatus; }

        @Override
        public String toString() {
            return name + "  [" + simpleType(type) + "]";
        }

        private static String simpleType(String type) {
            if (type == null) return "unknown";
            int idx = type.lastIndexOf('.');
            return idx < 0 ? type : type.substring(idx + 1);
        }
    }
}
