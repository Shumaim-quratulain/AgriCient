package com.agricient.backend;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.awt.RenderingHints;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

@Component
public class ImagePreprocessor {

    private static final int SIZE = 224;

   public BufferedImage resize(byte[] imageBytes) throws IOException {
    BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
    int w = original.getWidth();
    int h = original.getHeight();
    double scale = 256.0 / Math.min(w, h);
    int newW = (int) Math.round(w * scale);
    int newH = (int) Math.round(h * scale);

    BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
    Graphics2D pen = scaled.createGraphics();
    pen.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    pen.drawImage(original, 0, 0, newW, newH, null);
    pen.dispose();

    int left = (newW - SIZE) / 2;
    int top = (newH - SIZE) / 2;
    return scaled.getSubimage(left, top, SIZE, SIZE);
}
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    public float[][][][] toArray(BufferedImage image) {
        float[][][][] data = new float[1][3][SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int rgb = image.getRGB(x, y);
                float r = ((rgb >> 16) & 0xFF) / 255f;
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;
                data[0][0][y][x] = (r - MEAN[0]) / STD[0];
                data[0][1][y][x] = (g - MEAN[1]) / STD[1];
                data[0][2][y][x] = (b - MEAN[2]) / STD[2];
            }
        }
        return data;
    }
}