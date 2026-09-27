package com.siparo.upload;

import com.siparo.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Yüklenen görseli mobil ağ için küçültür: uzun kenar en fazla {@link #MAX_EDGE} piksel; JPEG yeniden sıkıştırılır, PNG
 * (şeffaf logo) PNG kalır. Daha küçük sonuç çıkmazsa özgün dosya saklanır. EXIF yönü 1 dışındaki JPEG'e dokunulmaz
 * (yeniden kodlama yön bilgisini düşürür, telefon fotoğrafı yan döner). WEBP (ImageIO okuyamaz) olduğu gibi saklanır.
 */
@Component
public class ImageOptimizer {

    static final int MAX_EDGE = 1600;
    private static final int MAX_SOURCE_EDGE = 10_000;
    private static final float JPEG_QUALITY = 0.82f;

    public byte[] optimize(byte[] original, String mimeType) {
        boolean jpeg = "image/jpeg".equals(mimeType);
        if (!jpeg && !"image/png".equals(mimeType)) return original;
        if (jpeg && exifOrientation(original) > 1) return original;
        try {
            int[] size = dimensions(original);
            if (size == null) return original;
            if (size[0] > MAX_SOURCE_EDGE || size[1] > MAX_SOURCE_EDGE) {
                throw new BusinessException("FILE_TOO_LARGE", "Image dimensions are too large");
            }
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) return original;
            BufferedImage scaled = scale(source, jpeg);
            byte[] encoded = jpeg ? writeJpeg(scaled) : writePng(scaled);
            return encoded.length < original.length ? encoded : original;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException business) throw business;
            return original;
        }
    }

    private static int[] dimensions(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage scale(BufferedImage source, boolean opaque) {
        int width = source.getWidth();
        int height = source.getHeight();
        double ratio = Math.min(1.0, (double) MAX_EDGE / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (opaque) {
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] writeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    /** JPEG EXIF (APP1) yön etiketi (0x0112); bulunamazsa 1. */
    static int exifOrientation(byte[] jpeg) {
        int offset = 2;
        while (offset + 4 < jpeg.length && (jpeg[offset] & 0xFF) == 0xFF) {
            int marker = jpeg[offset + 1] & 0xFF;
            int length = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
            if (marker == 0xE1 && offset + 10 < jpeg.length && jpeg[offset + 4] == 'E' && jpeg[offset + 5] == 'x'
                    && jpeg[offset + 6] == 'i' && jpeg[offset + 7] == 'f') {
                return readOrientation(jpeg, offset + 10, Math.min(jpeg.length, offset + 2 + length));
            }
            if (marker == 0xDA) break; // görüntü verisi başladı
            offset += 2 + length;
        }
        return 1;
    }

    private static int readOrientation(byte[] data, int tiff, int end) {
        if (tiff + 8 > end) return 1;
        boolean little = data[tiff] == 'I';
        int ifd = tiff + readInt(data, tiff + 4, little);
        if (ifd + 2 > end) return 1;
        int entries = readShort(data, ifd, little);
        for (int index = 0; index < entries; index++) {
            int entry = ifd + 2 + index * 12;
            if (entry + 12 > end) return 1;
            if (readShort(data, entry, little) == 0x0112) return readShort(data, entry + 8, little);
        }
        return 1;
    }

    private static int readShort(byte[] data, int at, boolean little) {
        int a = data[at] & 0xFF;
        int b = data[at + 1] & 0xFF;
        return little ? (b << 8) | a : (a << 8) | b;
    }

    private static int readInt(byte[] data, int at, boolean little) {
        int a = readShort(data, at, little);
        int b = readShort(data, at + 2, little);
        return little ? (b << 16) | a : (a << 16) | b;
    }
}
