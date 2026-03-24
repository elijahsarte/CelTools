package com.elijahsarte.celtools.main.framefactory;

import com.elijahsarte.celtools.main.util.MathEx;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class ImageHandler {

    private File imageFile;
    private BufferedImage image;
    private BufferedImage guiImage;

    private static final int defaultRGBType = BufferedImage.TYPE_INT_RGB;
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
        BufferedImage newImage = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = newImage.createGraphics();
        g.drawImage(in, 0, 0, in.getWidth(), in.getHeight(), null);
        g.dispose();
        return newImage;
    }

    // https://stackoverflow.com/a/27458294
    public static BufferedImage fromFile(File file, int imageType) throws IOException {
        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new IllegalArgumentException("No reader for: " + file);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);

                ImageReadParam param = reader.getDefaultReadParam();
                param.setDestinationType(ImageTypeSpecifier.createFromBufferedImageType(imageType));

                image = reader.read(0, param);
            }
            finally {
                reader.dispose();
                input.close();
            }
        }
        return image;
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
            this.image = fromFile(this.imageFile);
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
    // common method to get image as pixels to reduce confusion

    private int[] getImgPixels(BufferedImage image) {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }
    public int[] getImgPixels() {
        return getImgPixels(this.image);
    }


}

