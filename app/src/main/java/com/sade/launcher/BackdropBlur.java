package com.sade.launcher;

/** Sliding-window blur: fixed work per pixel, independent of the blur radius. */
public final class BackdropBlur {
    private BackdropBlur() {}
    public static int[] blur(int[] source, int width, int height, int radius) {
        if (width < 1 || height < 1 || source.length != width * height || radius < 1 || radius > 64)
            throw new IllegalArgumentException("Invalid blur dimensions or radius");
        int[] input = source.clone(), output = new int[source.length];
        for (int pass = 0; pass < 3; pass++) {
            for (int axis = 0; axis < 2; axis++) {
                int length = axis == 0 ? width : height;
                int lines = axis == 0 ? height : width;
                int stride = axis == 0 ? 1 : width;
                int count = radius * 2 + 1;
                for (int line = 0; line < lines; line++) {
                    int start = axis == 0 ? line * width : line;
                    int red = 0, green = 0, blue = 0;
                    for (int i = -radius; i <= radius; i++) {
                        int color = input[start + Math.max(0, Math.min(length - 1, i)) * stride];
                        red += (color >>> 16) & 255; green += (color >>> 8) & 255; blue += color & 255;
                    }
                    for (int i = 0; i < length; i++) {
                        output[start + i * stride] = 0xff000000 | ((red / count) << 16) | ((green / count) << 8) | (blue / count);
                        int remove = input[start + Math.max(0, i - radius) * stride];
                        int add = input[start + Math.min(length - 1, i + radius + 1) * stride];
                        red += ((add >>> 16) & 255) - ((remove >>> 16) & 255);
                        green += ((add >>> 8) & 255) - ((remove >>> 8) & 255);
                        blue += (add & 255) - (remove & 255);
                    }
                }
                int[] previous = input; input = output; output = previous;
            }
        }
        return input;
    }
}
