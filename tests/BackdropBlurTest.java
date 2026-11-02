import com.sade.launcher.BackdropBlur;
import java.util.Arrays;
import java.util.Random;

public class BackdropBlurTest {
    private static int[] reference(int[] input, int w, int h, int radius) {
        int[] src = input.clone();
        for (int pass = 0; pass < 3; pass++) for (int axis = 0; axis < 2; axis++) {
            int[] dst = new int[src.length];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int d = -radius; d <= radius; d++) {
                    int sx = axis == 0 ? Math.max(0, Math.min(w - 1, x + d)) : x;
                    int sy = axis == 1 ? Math.max(0, Math.min(h - 1, y + d)) : y;
                    int c = src[sy * w + sx];
                    r += (c >>> 16) & 255; g += (c >>> 8) & 255; b += c & 255;
                }
                int n = radius * 2 + 1;
                dst[y * w + x] = 0xff000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
            src = dst;
        }
        return src;
    }
    public static void main(String[] args) {
        Random random = new Random(18);
        int checks = 0;
        for (int[] size : new int[][]{{1,1},{1,9},{11,1},{17,13}}) for (int radius : new int[]{1,4,12}) {
            int[] pixels = new int[size[0] * size[1]];
            for (int i = 0; i < pixels.length; i++) pixels[i] = 0xff000000 | random.nextInt(1 << 24);
            int[] original = pixels.clone();
            int[] result = BackdropBlur.blur(pixels, size[0], size[1], radius);
            if (!Arrays.equals(result, reference(pixels, size[0], size[1], radius)) || !Arrays.equals(original, pixels))
                throw new AssertionError("Blur mismatch or mutated source");
            checks++;
        }
        int[] flat = new int[100]; Arrays.fill(flat, 0xff123456);
        if (!Arrays.equals(flat, BackdropBlur.blur(flat, 10, 10, 6))) throw new AssertionError("Uneven flat surface");
        System.out.println("Backdrop blur: " + (checks + 1) + " checks passed.");
    }
}
