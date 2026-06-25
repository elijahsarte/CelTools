package com.elijahsarte.celtools.main.framefactory;

import com.elijahsarte.celtools.main.util.MathEx;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.io.*;

public class ImageHandler {

    private File imageFile;
    private BufferedImage image;
    private BufferedImage guiImage;
    private BufferedImage miniImage;

    private static final int defaultRGBType = BufferedImage.TYPE_INT_RGB;
    public static final double MINI_IMAGE_PERCENT = 10.0;
    private static final int MIN_MINI_IMAGE_WIDTH = 320, MIN_MINI_IMAGE_HEIGHT = 240;
    private final int rgbType;

    public ImageHandler(File imageFile, int imageType) {
        this.imageFile = imageFile;
        this.rgbType = imageType;
    }
    public ImageHandler(File imageFile) {
        this(imageFile, defaultRGBType);
    }
    public ImageHandler(BufferedImage image, int imageType) {
        this.image = convertImage(image, imageType);
        this.rgbType = imageType;
    }
    public ImageHandler(BufferedImage image) {
        this(image, defaultRGBType);
    }


    private BufferedImage convertImage(BufferedImage in, int imageType) {
        if (in.getType() == imageType) return in;
        BufferedImage newImage = new BufferedImage(in.getWidth(), in.getHeight(), imageType);

        Graphics2D g = newImage.createGraphics();
        g.drawImage(in, 0, 0, in.getWidth(), in.getHeight(), null);
        g.dispose();
        return newImage;
    }

    // https://stackoverflow.com/a/27458294
    public static BufferedImage fromFile(File file, int imageType) throws IOException {
        BufferedImage image = ImageIO.read(file);

        if (image == null) {
            throw new IllegalArgumentException("Unable to read image: " + file);
        }

        if (image.getType() == imageType) {
            return image;
        }

        BufferedImage converted = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                imageType);

        Graphics2D g = converted.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        return converted;
    }
    public static BufferedImage fromFile(File file) throws IOException {
        return fromFile(file, defaultRGBType);
    }

    public int guiWidth() {
        int width = this.image.getWidth(), height = this.image.getHeight();
        return width > height && width > 850 ? 850 : (height > 850 ? (int) (height * MathEx.divide(width, height)) : width);
    }
    public int guiHeight() {
        int width = this.image.getWidth(), height = this.image.getHeight();
        return width > height && width > 850 ? (int)(850 / ((double)width/height)) : Math.min(height, 850);
    }


    public void loadImage() throws IOException {
        if (this.image != null) return;
        try {
            this.image = fromFile(this.imageFile, this.rgbType);
        } catch (IOException ignored) {
            this.image = convertImage(ImageIO.read(this.imageFile), this.rgbType);
        }
    }

    public void loadGuiImage() {
        if (this.guiImage != null) return;

        Image scaledImage = this.image.getScaledInstance(guiWidth(), guiHeight(), Image.SCALE_FAST);
        BufferedImage scaledBI = new BufferedImage(scaledImage.getWidth(null), scaledImage.getHeight(null), this.rgbType);
        Graphics2D g = scaledBI.createGraphics();
        g.drawImage(scaledImage, 0, 0, null);
        g.dispose();
        this.guiImage = scaledBI;
    }

    public void loadMiniImage() {
        if (this.miniImage != null) return;
        if (this.image == null) {
            throw new IllegalStateException("Cannot load mini image before the source image");
        }

        int sourceWidth = this.image.getWidth();
        int sourceHeight = this.image.getHeight();
        int miniWidth = Math.max(MIN_MINI_IMAGE_WIDTH, (int) Math.round(sourceWidth * MINI_IMAGE_PERCENT / 100.0));
        int miniHeight = Math.max(MIN_MINI_IMAGE_HEIGHT, (int) Math.round(sourceHeight * MINI_IMAGE_PERCENT / 100.0));
        BufferedImage scaled = new BufferedImage(miniWidth, miniHeight, this.rgbType);

        for (int y = 0; y < miniHeight; y++) {
            int sourceY = scaledCoordinate(y, miniHeight, sourceHeight);
            for (int x = 0; x < miniWidth; x++) {
                int sourceX = scaledCoordinate(x, miniWidth, sourceWidth);
                scaled.setRGB(x, y, this.image.getRGB(sourceX, sourceY));
            }
        }
        this.miniImage = scaled;
    }

    private static int scaledCoordinate(int coordinate, int scaledSize, int sourceSize) {
        return Math.min(sourceSize - 1, (int) (((coordinate + 0.5) * sourceSize) / scaledSize));
    }

    public Point miniImagePointToImage(Point miniPoint) {
        if (this.miniImage == null) {
            throw new IllegalStateException("Mini image has not been loaded");
        }
        if (miniPoint.x < 0 || miniPoint.x >= this.miniImage.getWidth()
                || miniPoint.y < 0 || miniPoint.y >= this.miniImage.getHeight()) {
            throw new IllegalArgumentException("Mini-image point is outside the mini image: " + miniPoint);
        }
        return new Point(
                scaledCoordinate(miniPoint.x, this.miniImage.getWidth(), this.image.getWidth()),
                scaledCoordinate(miniPoint.y, this.miniImage.getHeight(), this.image.getHeight())
        );
    }

    public double getMiniImageScale() {
        if (this.miniImage == null) {
            throw new IllegalStateException("Mini image has not been loaded");
        }
        return Math.max(
                (double) this.miniImage.getWidth() / this.image.getWidth(),
                (double) this.miniImage.getHeight() / this.image.getHeight()
        );
    }


    public ImageHandler copy() throws IOException {
        ImageHandler copy = new ImageHandler(this.imageFile, this.rgbType);
        if (this.image != null) copy.loadImage();
        return copy;
    }



    public int getWidth() {
        return this.image.getWidth();
    }
    public int getHeight() {
        return this.image.getHeight();
    }
    public WritableRaster getAlphaRaster() {
        return this.image.getAlphaRaster();
    }
    public boolean hasAlpha() {
        return this.getAlphaRaster() != null;
    }

    public BufferedImage getImage() {
        return this.image;
    }
    public BufferedImage getGuiImage() {
        return this.guiImage;
    }
    public BufferedImage getMiniImage() {
        return this.miniImage;
    }
    // common method to get image as pixels to reduce confusion

    private int[] getImgPixels(BufferedImage image) {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }
    public int[] getImgPixels() {
        return getImgPixels(this.image);
    }


}

