package com.elijahsarte.celtools.gui;

import com.elijahsarte.celtools.gui.pages.*;
import com.elijahsarte.celtools.gui.windows.ApplicationSettings;
import com.elijahsarte.celtools.main.Main;
import com.elijahsarte.celtools.main.colormodel.HSVBounds;
import com.elijahsarte.celtools.main.framefactory.FrameParser;
import com.elijahsarte.celtools.main.selectionui.FreeformSelectionManager;
import com.elijahsarte.celtools.main.util.*;
import com.elijahsarte.celtools.main.util.iterators.CoordIterator;
import com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection;
import com.elijahsarte.celtools.mainex.Debugger;
import com.elijahsarte.celtools.mainex.TaskTracker;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.prefs.Preferences;
import java.util.stream.IntStream;

import static com.elijahsarte.celtools.main.util.ProgrammingEx.*;

public final class Application extends JFrame {

    private static final String MEMORY_GB_PREFERENCE = "memory_allocation_gb";
    private static final String MEMORY_GB_PROPERTY = "celtools.memory.gigabytes";
    private static final int DEFAULT_MEMORY_GB = 4;
    private static final int MINIMUM_MEMORY_GB = 2;
    private static final int STOP_ESCALATION_PASSES = 18;
    private static final long STOP_ESCALATION_PAUSE_MILLIS = 25L;
    private static final long STOP_CONTROLLER_RELEASE_MILLIS = 450L;

    private final List<ProgramPage> pages = List.of(new CPF(), new CPO(), new CIX(), new CLPS(), new CPC(), new CAI());

    private final Border border = BorderFactory.createEmptyBorder(20, 20, 20, 20);
    private final CardLayout cardLayout;
    private final JPanel cardsPanel, rightPanel;
    private final JTextArea consoleArea;
    private final JScrollPane consoleScroll;
    private final JComboBox<String> combo;

    private final JButton runBtn = new JButton("Run"), stopBtn = new JButton("Stop");
    private SwingWorker<Void, String> worker;
    private volatile RunControl activeRun;
    private volatile Thread execThread;
    private volatile ThreadGroup execThreadGroup;
    private volatile StoppableInputStream activeInput;

    private boolean consoleAutoScrollLeft = true;
    private boolean consoleAdjustingHorizontally = false;

    public static final Preferences prefs = Preferences.userNodeForPackage(Application.class);

    private static void warmUpLambdas() {
        noExcept(() -> {
            Class.forName("java.awt.Point");
            Class.forName("com.elijahsarte.celtools.main.framefactory.FrameParser");
            Class.forName("com.elijahsarte.celtools.main.util.function.blocks.forloop.ForLoopBase");
            Class.forName("com.elijahsarte.celtools.main.util.function.blocks.forloop.ForEach");
            Class.forName("com.elijahsarte.celtools.main.util.function.blocks.forloop.ForIncrement");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.TriConsumer");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.OctaConsumer");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.QuadConsumer");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.ThrowableSupplier");
            Class.forName("com.elijahsarte.celtools.main.util.function.fntypes.ThrowableRunnable");
            Class.forName("com.elijahsarte.celtools.main.util.iterators.CoordIterator");
            Class.forName("com.elijahsarte.celtools.main.util.iterators.ListMapIterator");
            Class.forName("com.elijahsarte.celtools.main.util.iterators.PixelIterator");
            Class.forName("com.elijahsarte.celtools.main.util.structures.collections.IntList");
            Class.forName("com.elijahsarte.celtools.main.util.structures.collections.point.PointCollection");
            Class.forName("com.elijahsarte.celtools.main.util.MathEx");
            Class.forName("com.elijahsarte.celtools.main.util.CollectionsEx");
            Class.forName("com.elijahsarte.celtools.main.util.ProgrammingEx");
            Class.forName("com.elijahsarte.celtools.main.util.OptionalEx");
        });
        FrameParser.filterHSV(new int[400], 20, 20, new HSVBounds(new double[] {0, 0, 0}, new double[] {1, 0, 0}));
        int[] mockPixels = new int[400];
        Predicate<double[]> mockQualifier = hsv -> hsv[0] > 180;
        for (int i = 0; i < 500; i++) {
            FrameParser.filterHSV(
                    mockPixels, 20, 20, mockQualifier,
                    (hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> {
                    },
                    (hPixels, col, row, index, rawRGB, rgb, colLoop, rowLoop) -> {
                    }
            );
        }
        new CoordIterator(1, 2, 1, 2).execute((x, y) -> {});
        varOper(6, (y) -> "8");
        ProgrammingEx.varMutate("a", b -> {});
        noExcept(() -> {});
        OptionalEx.ofCond(6, true).thenRun(() -> {}).orElse(() -> {});
        Main.groupPoints(new PointCollection(IntStream.rangeClosed(0, 10).mapToObj(i -> new Point(i, i + 1)).toList()), 4);
    }

    public Application() {
        super("CelTools");
        System.out.println("Copyright (c) 2025 Elijah Sarte. All rights reserved.");
        this.setTitle("CelTools by Elijah Sarte");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        combo = new JComboBox<>(pages.stream().map(ProgramPage::name).toArray(String[]::new));
        combo.setSelectedItem(prefs.get("last_page", pages.get(0).name()));
        combo.addActionListener(this::onSelectionChanged);

        JButton settingsButton = new JButton("\u2699");
        settingsButton.setFocusable(false);
        settingsButton.setToolTipText("Settings");
        settingsButton.setMargin(new Insets(4, 8, 4, 8));
        settingsButton.addActionListener(e -> ApplicationSettings.open());

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));

        JPanel leftTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10));
        JPanel centerTop = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));

        leftTop.add(settingsButton);
        centerTop.add(combo);

        topBar.add(leftTop, BorderLayout.WEST);
        topBar.add(centerTop, BorderLayout.CENTER);

        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBorder(border);
        pages.forEach(p -> cardsPanel.add(p, p.name()));
        this.onSelectionChanged(null);

        JPanel leftPanel = new JPanel(new BorderLayout());
        this.rightPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.add(topBar, BorderLayout.NORTH);
        leftPanel.add(cardsPanel, BorderLayout.CENTER);

        runBtn.addActionListener(e -> {
            Debugger.clear();
            rightPanel.add(stopBtn, BorderLayout.NORTH);
            rightPanel.remove(runBtn);
            runBtn.setEnabled(false);
            runBtn.setVisible(false);
            stopBtn.setVisible(true);
            stopBtn.setEnabled(true);
            noExcept(this::onRun);
        });

        stopBtn.addActionListener(e -> {
            stopCurrentRun();
        });

        stopBtn.setVisible(false);
        rightPanel.add(runBtn, BorderLayout.NORTH);

        consoleArea = new JTextArea(12, 30) {
            @Override
            public void scrollRectToVisible(Rectangle aRect) {
                if (consoleAutoScrollLeft) {
                    aRect = new Rectangle(0, aRect.y, aRect.width, aRect.height);
                }
                super.scrollRectToVisible(aRect);
            }
        };
        consoleArea.setEditable(false);

        this.consoleScroll = new JScrollPane(
                consoleArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        ((DefaultCaret) consoleArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        consoleScroll.getHorizontalScrollBar().addAdjustmentListener(e -> {
            if (consoleAdjustingHorizontally) return;
            consoleAutoScrollLeft = (e.getValue() == consoleScroll.getHorizontalScrollBar().getMinimum());
        });

        rightPanel.add(consoleScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setResizeWeight(0.6);
        split.setOneTouchExpandable(true);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(split, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }

    private ProgramPage getCurrPage() {
        return pages.get(combo.getSelectedIndex());
    }

    private void onSelectionChanged(ActionEvent e) {
        String sel = getCurrPage().name();
        cardLayout.show(cardsPanel, sel);
        prefs.put("last_page", sel);
    }

    private void onRun() {
        consoleArea.setText("");
        final ProgramPage page = getCurrPage();

        this.worker = new SwingWorker<>() {
            private PrintStream consoleOut = System.out;
            private PrintStream consoleErr = System.err;
            private InputStream consoleIn = System.in;
            private Thread.UncaughtExceptionHandler previousDefaultHandler;
            private RunControl run;
            private Throwable runFailure;

            @Override
            protected Void doInBackground() {
                this.consoleOut = System.out;
                this.consoleErr = System.err;
                this.consoleIn = System.in;
                this.previousDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();

                ThreadGroup group = new ThreadGroup("CelTools run - " + page.name());
                StoppableInputStream runInput = new StoppableInputStream();
                this.run = new RunControl(page.name(), group, Thread.getAllStackTraces().keySet());
                Application.this.activeRun = this.run;
                Application.this.execThreadGroup = group;
                Application.this.activeInput = runInput;

                PrintStream ps = new PrintStream(new OutputStream() {
                    @Override
                    public void write(int b) {
                        Application.this.appendConsoleText(String.valueOf((char) b), consoleOut);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        Application.this.appendConsoleText(new String(b, off, len), consoleOut);
                    }
                }, true);

                System.out.println(page.name() + "\n---------------------------------\n");
                System.setOut(ps);
                System.setErr(ps);
                System.setIn(runInput);

                Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                    if (throwable instanceof ThreadDeath) return;
                    if (throwable instanceof OutOfMemoryError) {
                        ps.print("\nran out of memory, terminating\n");
                        return;
                    }
                    if (this.run != null && this.run.stopRequested) return;
                    ps.print("\n[uncaught exception in thread \"" + thread.getName() + "\"]\n");
                    throwable.printStackTrace(ps);
                });

                FreeformSelectionManager.saving = true;
                ImageIO.setUseCache(false);

                Thread runner = new Thread(group, () -> {
                    Application.this.execThread = Thread.currentThread();
                    this.run.runner = Thread.currentThread();
                    try {
                        page.program().get().run();
                    } catch (ThreadDeath td) {
                        this.run.threadDeathDelivered = true;
                        throw td;
                    } catch (Throwable t) {
                        if (t instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        } else if (!isStopRelatedThrowable(t, this.run)) {
                            this.runFailure = t;
                        }
                    } finally {
                        this.run.runnerFinished = true;
                        if (Application.this.execThread == Thread.currentThread()) {
                            Application.this.execThread = null;
                        }
                    }
                }, "CelTools runner - " + page.name());
                runner.setDaemon(true);
                this.run.runner = runner;
                runner.start();

                while (runner.isAlive()) {
                    try {
                        runner.join(100L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        this.run.stopRequested = true;
                        stopRunAggressively(this.run, true);
                        break;
                    }

                    if (isCancelled() || this.run.stopRequested) {
                        this.run.stopRequested = true;
                        stopRunAggressively(this.run, true);
                        break;
                    }
                }

                if (this.run.stopRequested && runner.isAlive()) {
                    long releaseAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(STOP_CONTROLLER_RELEASE_MILLIS);
                    while (runner.isAlive() && System.nanoTime() < releaseAt) {
                        stopRunAggressively(this.run, true);
                        sleepQuietly(STOP_ESCALATION_PAUSE_MILLIS);
                    }
                }

                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                StreamEx.forEachMulti(chunks.stream(), consoleArea::append, consoleOut::append);
                consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (runFailure != null && !isStopRelatedThrowable(runFailure, run)) {
                        if (runFailure instanceof OutOfMemoryError) {
                            appendStatusLine("ran out of memory, terminating", consoleOut);
                        } else {
                            appendConsoleText("\n", consoleOut);
                            runFailure.printStackTrace(consoleOut);
                            runFailure.printStackTrace(System.err);
                            if (runFailure instanceof Exception && !(runFailure instanceof InterruptedException)) {
                                File debugDumpFile = Main.createDebugDump(Application.this, runFailure);
                                if (debugDumpFile != null) {
                                    appendStatusLine("debug dump exported to " + debugDumpFile.getAbsolutePath(), consoleOut);
                                }
                            }
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    appendStatusLine("cancelled", consoleOut);
                } catch (CancellationException ce) {
                    appendStatusLine("cancelled", consoleOut);
                } catch (Exception e) {
                    Throwable cause = ThreadEx.getRootCause(e);
                    if (!(cause instanceof ThreadDeath)) {
                        if (cause instanceof OutOfMemoryError) appendStatusLine("ran out of memory, terminating", consoleOut);
                        else e.printStackTrace(consoleOut);
                    }
                } finally {
                    commonDone();
                }
            }

            private void commonDone() {
                RunControl runToClean = this.run;
                if (runToClean != null && runToClean.stopRequested) {
                    stopRunAggressively(runToClean, true);
                }

                finishTaskTrackerBeforeDone();

                stopBtn.setVisible(false);
                stopBtn.setEnabled(false);
                runBtn.setVisible(true);
                runBtn.setEnabled(true);
                rightPanel.remove(stopBtn);
                rightPanel.add(runBtn, BorderLayout.NORTH);
                ConstructionEx.forceReleaseAllArrays();

                cleanupRunGlobals(runToClean, this.consoleOut, this.consoleErr, this.consoleIn, this.previousDefaultHandler);
                appendStatusLine("done", this.consoleOut);
            }
        };

        worker.execute();
    }

    private static boolean isStopRelatedThrowable(Throwable throwable, RunControl run) {
        if (throwable == null) return false;
        if (run != null && run.stopRequested) return true;
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof ThreadDeath
                    || t instanceof CancellationException
                    || t instanceof InterruptedException
                    || t instanceof InterruptedIOException
                    || t instanceof ClosedByInterruptException
                    || t instanceof AsynchronousCloseException) {
                return true;
            }
        }
        return false;
    }

    private void stopCurrentRun() {
        stopBtn.setEnabled(false);
        RunControl run = activeRun;
        if (run != null) {
            run.stopRequested = true;
            stopRunAggressively(run, true);
        }

        SwingWorker<Void, String> currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.cancel(true);
        }

        StoppableInputStream input = activeInput;
        if (input != null) {
            input.stopReading();
        }
    }

    private void stopRunAggressively(RunControl run, boolean useAsyncThreadDeath) {
        if (run == null) return;
        run.stopRequested = true;

        StoppableInputStream input = activeInput;
        if (input != null) {
            input.stopReading();
        }

        for (int pass = 0; pass < STOP_ESCALATION_PASSES; pass++) {
            for (Thread thread : getRunThreads(run)) {
                if (thread == null || thread == Thread.currentThread() || !thread.isAlive()) {
                    continue;
                }
                try {
                    thread.interrupt();
                } catch (Throwable ignored) {
                }
                if (useAsyncThreadDeath) {
                    forceThreadDeath(thread, run);
                }
            }

            if (useAsyncThreadDeath) {
                forceThreadGroupStop(run.group, run);
            }

            Thread runner = run.runner;
            if (runner == null || !runner.isAlive()) return;
            sleepQuietly(STOP_ESCALATION_PAUSE_MILLIS);
        }
    }

    private Set<Thread> getRunThreads(RunControl run) {
        LinkedHashSet<Thread> threads = new LinkedHashSet<>();
        if (run.runner != null) threads.add(run.runner);
        Thread directExec = execThread;
        if (directExec != null) threads.add(directExec);

        ThreadGroup group = run.group;
        if (group != null) {
            int estimate = Math.max(8, group.activeCount() * 2 + 8);
            Thread[] groupThreads = new Thread[estimate];
            int count = group.enumerate(groupThreads, true);
            for (int i = 0; i < count; i++) {
                if (groupThreads[i] != null) threads.add(groupThreads[i]);
            }
        }

        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread == null || run.baselineThreads.contains(thread)) continue;
            if (thread == Thread.currentThread()) continue;
            if (isGuiOrJvmInfrastructureThread(thread)) continue;
            threads.add(thread);
        }
        return threads;
    }

    private static boolean isGuiOrJvmInfrastructureThread(Thread thread) {
        String name = thread.getName();
        if (name == null) return false;
        return name.startsWith("AWT-")
                || name.startsWith("SwingWorker")
                || name.startsWith("TimerQueue")
                || name.startsWith("DestroyJavaVM")
                || name.startsWith("Reference Handler")
                || name.startsWith("Finalizer")
                || name.startsWith("Common-Cleaner")
                || name.startsWith("Signal Dispatcher")
                || name.startsWith("Notification Thread")
                || name.startsWith("Attach Listener")
                || name.startsWith("Java2D")
                || name.startsWith("Image Fetcher")
                || name.startsWith("AppKit")
                || name.startsWith("QuantumRenderer");
    }

    private static void forceThreadDeath(Thread thread, RunControl run) {
        if (thread == null || thread == Thread.currentThread() || !thread.isAlive()) return;
        if (invokeThreadStop(thread, run)) return;
        invokeLegacyStop0(thread, run);
    }

    private static boolean invokeThreadStop(Thread thread, RunControl run) {
        try {
            Method stop = Thread.class.getDeclaredMethod("stop");
            stop.setAccessible(true);
            stop.invoke(thread);
            return true;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof UnsupportedOperationException || cause instanceof NoSuchMethodError) {
                if (run != null) run.threadStopUnavailable = true;
            }
        } catch (NoSuchMethodException nsme) {
            if (run != null) run.threadStopUnavailable = true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean invokeLegacyStop0(Thread thread, RunControl run) {
        try {
            Method stop0 = Thread.class.getDeclaredMethod("stop0", Object.class);
            stop0.setAccessible(true);
            stop0.invoke(thread, new ThreadDeath());
            return true;
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof UnsupportedOperationException || cause instanceof NoSuchMethodError) {
                if (run != null) run.legacyStop0Unavailable = true;
            }
        } catch (NoSuchMethodException nsme) {
            if (run != null) run.legacyStop0Unavailable = true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void forceThreadGroupStop(ThreadGroup group, RunControl run) {
        if (group == null) return;
        try {
            Method stop = ThreadGroup.class.getDeclaredMethod("stop");
            stop.setAccessible(true);
            stop.invoke(group);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof UnsupportedOperationException || cause instanceof NoSuchMethodError) {
                if (run != null) run.threadGroupStopUnavailable = true;
            }
        } catch (NoSuchMethodException nsme) {
            if (run != null) run.threadGroupStopUnavailable = true;
        } catch (Throwable ignored) {
        }
    }

    private void cleanupRunGlobals(RunControl run, PrintStream consoleOut, PrintStream consoleErr, InputStream consoleIn,
                                   Thread.UncaughtExceptionHandler previousDefaultHandler) {
        if (run != null && !run.cleaned.compareAndSet(false, true)) return;

        if (activeInput != null) {
            activeInput.stopReading();
        }
        if (activeRun == run) activeRun = null;
        if (execThreadGroup == (run == null ? null : run.group)) execThreadGroup = null;
        activeInput = null;
        execThread = null;

        Thread.setDefaultUncaughtExceptionHandler(previousDefaultHandler);
        System.setOut(consoleOut);
        System.setErr(consoleErr);
        System.setIn(consoleIn);
    }

    private void finishTaskTrackerBeforeDone() {
        try {
            TaskTracker.endTrack();
        } catch (Throwable ignored) {
        }
        try {
            TaskTracker.reset();
        } catch (Throwable ignored) {
        }
    }

    private void appendConsoleText(String text, PrintStream mirror) {
        if (text == null || text.isEmpty()) return;
        if (mirror != null) mirror.print(text);
        runOnEdtAndWait(() -> {
            consoleArea.append(text);
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }

    private void appendStatusLine(String text, PrintStream mirror) {
        runOnEdtAndWait(() -> {
            String existing = consoleArea.getText();
            boolean needsLeadingNewline = !existing.isEmpty() && !existing.endsWith("\n") && !existing.endsWith("\r");
            if (needsLeadingNewline) {
                consoleArea.append("\n");
                if (mirror != null) mirror.print("\n");
            }
            consoleArea.append(text + "\n");
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
            if (mirror != null) mirror.println(text);
        });
    }

    private static void runOnEdtAndWait(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (Exception ignored) {
            SwingUtilities.invokeLater(action);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class RunControl {
        final String pageName;
        final ThreadGroup group;
        final Set<Thread> baselineThreads;
        final AtomicBoolean cleaned = new AtomicBoolean(false);
        volatile Thread runner;
        volatile boolean stopRequested;
        volatile boolean runnerFinished;
        volatile boolean threadDeathDelivered;
        volatile boolean threadStopUnavailable;
        volatile boolean legacyStop0Unavailable;
        volatile boolean threadGroupStopUnavailable;

        RunControl(String pageName, ThreadGroup group, Set<Thread> baselineThreads) {
            this.pageName = pageName;
            this.group = group;
            this.baselineThreads = Collections.unmodifiableSet(new LinkedHashSet<>(baselineThreads));
        }
    }

    private static final class StoppableInputStream extends InputStream {
        private boolean stopped;

        @Override
        public synchronized int read() throws IOException {
            while (!stopped) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Input interrupted", e);
                }
            }
            throw new IOException("Input stopped");
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) throws IOException {
            if (b == null) throw new NullPointerException("b");
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            if (len == 0) return 0;
            return read();
        }

        @Override
        public synchronized int available() throws IOException {
            if (stopped) throw new IOException("Input stopped");
            return 0;
        }

        @Override
        public synchronized void close() throws IOException {
            stopReading();
        }

        synchronized void stopReading() {
            stopped = true;
            notifyAll();
        }
    }


    public static void allocMemory(long requiredMaxBytes, String xmsFlag, String xmxFlag, String softXmxFlag, Class<?> mainClass) {
        // Condition: If current JVM heap max is already enough, short-circuit immediately
        if (Runtime.getRuntime().maxMemory() >= requiredMaxBytes) {
            return;
        }

        // Step 1: Query system RAM using varOper
        long freeSystemRam = varOper(ManagementFactory.getOperatingSystemMXBean(), os ->
                (os instanceof com.sun.management.OperatingSystemMXBean)
                        ? ((com.sun.management.OperatingSystemMXBean) os).getFreeMemorySize()
                        : Long.MAX_VALUE
        );

        // Step 2: Handle insufficient physical system RAM
        if (freeSystemRam < requiredMaxBytes) {
            varExec(String.format(
                            "This application requires at least %.2f GB of free system RAM to execute safely.\n" +
                                    "Your computer currently has %.2f GB of free RAM available.\n\n" +
                                    "Please close other intensive applications and try launching again.",
                            MathEx.divide(requiredMaxBytes, MathEx.cube(1024)),
                            MathEx.divide(freeSystemRam, MathEx.cube(1024))
                    ),
                    msg -> varOper(new JOptionPane(msg, JOptionPane.ERROR_MESSAGE), pane ->
                            varMutate(pane.createDialog("Insufficient System Memory"), d -> d.setAlwaysOnTop(true))
                    ).setVisible(true),
                    msg -> System.exit(0));
            return;
        }

        // Step 3: Handle sufficient RAM -> Assemble process command line and execute relaunch
        varExec(new ArrayList<String>(),
                cmd -> cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"),
                cmd -> cmd.add("-XX:+UseZGC"),
                cmd -> cmd.add(xmsFlag),
                cmd -> cmd.add(xmxFlag),
                cmd -> cmd.add(softXmxFlag),
                cmd -> ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                        .filter(arg -> !arg.startsWith("-Xmx") && !arg.startsWith("-Xms"))
                        .forEach(cmd::add),
                cmd -> { cmd.add("-cp"); cmd.add(System.getProperty("java.class.path")); },
                cmd -> cmd.add(mainClass.getName()),
                cmd -> {
                    try {
                        varMutate(new ProcessBuilder(cmd), ProcessBuilder::inheritIO).start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                cmd -> System.exit(0)
        );
    }

    public static int getConfiguredMemoryGigabytes() {
        int configuredMemoryGb = Math.max(
                MINIMUM_MEMORY_GB,
                prefs.getInt(MEMORY_GB_PREFERENCE, DEFAULT_MEMORY_GB)
        );
        return Math.min(configuredMemoryGb, getSafeMemoryLimitGigabytes());
    }

    public static void setConfiguredMemoryGigabytes(int memoryGb) {
        int safeMemoryGb = Math.min(
                Math.max(MINIMUM_MEMORY_GB, memoryGb),
                getSafeMemoryLimitGigabytes()
        );
        prefs.putInt(MEMORY_GB_PREFERENCE, safeMemoryGb);
    }

    public static int getSafeMemoryLimitGigabytes() {
        long totalSystemMemory = varOper(ManagementFactory.getOperatingSystemMXBean(), os ->
                (os instanceof com.sun.management.OperatingSystemMXBean)
                        ? ((com.sun.management.OperatingSystemMXBean) os).getTotalMemorySize()
                        : Runtime.getRuntime().maxMemory()
        );
        long safeBytes = totalSystemMemory * 3L / 4L;
        long gigabytes = safeBytes / (1024L * 1024L * 1024L);
        return (int) Math.max(MINIMUM_MEMORY_GB, Math.min(Integer.MAX_VALUE, gigabytes));
    }

    public static void restartWithMemoryAllocation(int memoryGb) {
        launchWithMemoryAllocation(Math.max(MINIMUM_MEMORY_GB, memoryGb), Application.class);
    }

    private static void ensureConfiguredMemoryAllocation(Class<?> mainClass) {
        int configuredMemoryGb = getConfiguredMemoryGigabytes();
        if (Integer.toString(configuredMemoryGb).equals(System.getProperty(MEMORY_GB_PROPERTY))) {
            return;
        }
        if (!hasEnoughFreeMemory(configuredMemoryGb) && continueWithoutConfiguredMemory(configuredMemoryGb)) {
            return;
        }
        launchWithMemoryAllocation(configuredMemoryGb, mainClass);
    }

    private static boolean hasEnoughFreeMemory(int configuredMemoryGb) {
        long requiredBytes = configuredMemoryGb * 1024L * 1024L * 1024L;
        long freeSystemMemory = varOper(ManagementFactory.getOperatingSystemMXBean(), os ->
                (os instanceof com.sun.management.OperatingSystemMXBean)
                        ? ((com.sun.management.OperatingSystemMXBean) os).getFreeMemorySize()
                        : Long.MAX_VALUE
        );
        return freeSystemMemory >= requiredBytes;
    }

    private static boolean continueWithoutConfiguredMemory(int configuredMemoryGb) {
        Object[] options = {"OK", "Continue"};
        JOptionPane pane = new JOptionPane(
                "CelTools is configured to use a maximum " + configuredMemoryGb + " GB of memory, but the computer "
                        + "does not currently have enough free memory to satisfy that request.\n\n"
                        + "Select OK to close CelTools, or Continue to launch without applying the configured memory setting.",
                JOptionPane.ERROR_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                options,
                options[0]
        );
        JDialog dialog = pane.createDialog(null, "Insufficient System Memory");
        dialog.setAlwaysOnTop(true);
        dialog.setModal(true);
        dialog.setVisible(true);
        dialog.dispose();

        if ("Continue".equals(pane.getValue())) {
            return true;
        }
        System.exit(0);
        return false;
    }

    private static void launchWithMemoryAllocation(int memoryGb, Class<?> mainClass) {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add("-XX:+UseZGC");
        command.add("-Xms" + MINIMUM_MEMORY_GB + "g");
        command.add("-Xmx" + memoryGb + "g");
        command.add("-XX:SoftMaxHeapSize=" + memoryGb + "g");
        command.add("-D" + MEMORY_GB_PROPERTY + "=" + memoryGb);
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(Application::isNotMemoryArgument)
                .forEach(command::add);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(mainClass.getName());

        try {
            new ProcessBuilder(command).inheritIO().start();
            System.exit(0);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    null,
                    "Unable to restart CelTools:\n" + ex.getMessage(),
                    "Restart Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static boolean isNotMemoryArgument(String argument) {
        return !argument.startsWith("-Xmx")
                && !argument.startsWith("-Xms")
                && !argument.startsWith("-XX:SoftMaxHeapSize=")
                && !argument.startsWith("-XX:MaxRAMPercentage=")
                && !argument.startsWith("-XX:InitialRAMPercentage=")
                && !argument.startsWith("-agentlib:jdwp")
                && !argument.startsWith("-Xrunjdwp")
                && !argument.equals("-XX:+UseZGC")
                && !argument.equals("-XX:-UseZGC")
                && !argument.startsWith("-D" + MEMORY_GB_PROPERTY + "=");
    }



    private static void staticBlk() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        ReflectionEx.getStaticField(Class.forName("java.lang.Integer$IntegerCache"), "archivedCache");
    }

    public static void main(String[] args) {
        ensureConfiguredMemoryAllocation(Application.class);
        noExcept(Application::staticBlk);
        Main.async(Application::warmUpLambdas);
        SwingUtilities.invokeLater(() -> new Application().setVisible(true));
    }
}
