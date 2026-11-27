import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 生成 16×16 的称号徽章图标。
 *
 * 为什么不用 ImageIO 画曲线再缩小：Minecraft 的 bitmap 字形是 1 像素对 1 像素渲染的，
 * 24×24 的图声明成 height 16 会被客户端双线性缩到 16，边缘全糊。所以这里直接按 16×16
 * 逐像素判定形状，画出来就是最终大小，没有缩放、没有抗锯齿，边缘干净。
 *
 * 用法： java tools/IconGen.java <输出目录>
 */
public final class IconGen {

    private static final int SIZE = 16;
    /** 统一的描边色：深到接近黑紫，跟所有图标都能搭 */
    private static final int OUTLINE = 0xFF201428;

    private interface Painter {
        /** 返回 0 表示这一格透明 */
        int colorAt(double x, double y);
    }

    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "src/main/resources/images");
        outDir.mkdirs();

        save(outDir, "icon_star", new Painter() {
            // 五角星：外径 7.6 / 内径 2.7，内径小一点下摆的缝才看得出来
            final double[] xs = starX(8, 7.0, 3.3);
            final double[] ys = starY(8.4, 7.0, 3.3);

            public int colorAt(double x, double y) {
                if (!inside(x, y, xs, ys)) {
                    return 0;
                }
                return lerp(0xFFD9A21B, 0xFFFFF0B8, clamp((y - 1.0) / 13.0));
            }
        }, false);

        save(outDir, "icon_flame", new Painter() {
            final double[] xs = {8, 11.2, 10.0, 12.4, 11.8, 8, 4.6, 3.9, 6.3, 5.2};
            final double[] ys = {0.2, 5.2, 7.4, 9.2, 12.8, 15.8, 13.4, 9.8, 7.6, 5.2};

            public int colorAt(double x, double y) {
                if (!inside(x, y, xs, ys)) {
                    return 0;
                }
                double d = Math.hypot(x - 8, (y - 12) * 0.85);
                if (d < 2.2) {
                    return 0xFFFFF3B0;
                }
                if (d < 4.0) {
                    return 0xFFFFC44D;
                }
                if (d < 6.0) {
                    return 0xFFFF8A3D;
                }
                return 0xFFFF5A2B;
            }
        });

        save(outDir, "icon_thunder", new Painter() {
            final double[] xs = {10.8, 2.6, 6.8, 5.4, 13.4, 8.6};
            final double[] ys = {0.2, 9.2, 9.2, 15.8, 6.4, 6.4};

            public int colorAt(double x, double y) {
                if (!inside(x, y, xs, ys)) {
                    return 0;
                }
                return lerp(0xFFFFE066, 0xFFFFF9C4, clamp(1.0 - y / 15.0));
            }
        });

        save(outDir, "icon_crown", new Painter() {
            final double[] xs = {2.2, 2.2, 5.4, 8.0, 10.6, 13.8, 13.8};
            final double[] ys = {12.4, 5.2, 8.4, 3.4, 8.4, 5.2, 12.4};

            public int colorAt(double x, double y) {
                boolean body = y >= 11.0 && y <= 14.2 && x >= 2.6 && x <= 13.4;
                boolean spikes = inside(x, y, xs, ys);
                if (!body && !spikes) {
                    return 0;
                }
                // 三个尖顶上的宝石
                if (near(x, y, 8.0, 4.6, 1.15) || near(x, y, 2.9, 7.2, 1.0) || near(x, y, 13.1, 7.2, 1.0)) {
                    return 0xFFFF5C8A;
                }
                if (y > 12.6 && y < 13.8) {
                    return 0xFFFFB300;
                }
                return lerp(0xFFFFD24D, 0xFFFFF6CC, clamp((12.0 - y) / 9.0));
            }
        });

        save(outDir, "icon_moon", new Painter() {
            public int colorAt(double x, double y) {
                boolean disc = near(x, y, 7.2, 8.0, 6.3);
                boolean bite = near(x, y, 11.4, 5.9, 5.5);
                if (!disc || bite) {
                    // 两颗小星：月牙旁边点两点，夜里才不像一块饼
                    if (near(x, y, 12.4, 12.2, 0.9) || near(x, y, 13.6, 4.2, 0.7)) {
                        return 0xFFE8EEFF;
                    }
                    return 0;
                }
                return lerp(0xFF9FB0FF, 0xFFE4EAFF, clamp((y - 2.0) / 12.0));
            }
        });

        save(outDir, "icon_orchid", new Painter() {
            public int colorAt(double x, double y) {
                for (int i = 0; i < 5; i++) {
                    double a = Math.toRadians(-90 + i * 72);
                    double cx = 8 + Math.cos(a) * 4.0;
                    double cy = 7.2 + Math.sin(a) * 4.0;
                    if (near(x, y, cx, cy, 2.45)) {
                        return lerp(0xFFA97CFF, 0xFFEBDDFF, clamp((cy - 1) / 12.0));
                    }
                }
                if (near(x, y, 8, 7.2, 1.5)) {
                    return 0xFFFFE066;
                }
                if ((x > 7 && x < 9 && y > 10) || (x > 9 && x < 12 && y > 11 && y < 13)) {
                    return 0xFF5E9E57;
                }
                return 0;
            }
        });

        save(outDir, "icon_flower", new Painter() {
            public int colorAt(double x, double y) {
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(-90 + i * 60);
                    double cx = 8 + Math.cos(a) * 3.9;
                    double cy = 7.4 + Math.sin(a) * 3.9;
                    if (near(x, y, cx, cy, 2.35)) {
                        return lerp(0xFFFF8FBF, 0xFFFFD6E6, clamp((cy - 1) / 8.0));
                    }
                }
                if (near(x, y, 8, 7.4, 2.0)) {
                    return 0xFFFFD24D;
                }
                return 0;
            }
        });

        save(outDir, "icon_aurora", new Painter() {
            // 极光：四角闪光，紫→青的渐变，小尺寸下比丝带好认
            public int colorAt(double x, double y) {
                double dx = Math.abs(x - 8);
                double dy = Math.abs(y - 8.2);
                double sparkle = Math.pow(dx, 0.62) + Math.pow(dy, 0.62);
                if (sparkle > Math.pow(6.9, 0.62)) {
                    return 0;
                }
                double t = clamp((y - 1.0) / 14.0);
                return lerp(0xFF9A6BFF, 0xFF5CE1E6, t);
            }
        });

        save(outDir, "icon_bounty", new Painter() {
            final double[] MARK_X = starX(8, 3.9, 1.7);
            final double[] MARK_Y = starY(8.4, 3.9, 1.7);

            public int colorAt(double x, double y) {
                double d = Math.hypot(x - 8, y - 8);
                if (d > 7.1) {
                    return 0;
                }
                if (d > 5.9) {
                    return 0xFFE0A52B;   // 币边
                }
                // 币面压一颗小五角星，比十字更像"赏金"
                if (inside(x, y, MARK_X, MARK_Y)) {
                    return 0xFF8A5A00;
                }
                return lerp(0xFFFFE07A, 0xFFFFC23D, clamp(d / 6.0));
            }
        });

        save(outDir, "icon_glyph", new Painter() {
            public int colorAt(double x, double y) {
                double d = Math.hypot(x - 8, y - 8.4);
                boolean palette = d < 6.8;
                boolean thumb = near(x, y, 10.6, 10.4, 2.0);
                if (palette && !thumb) {
                    if (near(x, y, 5.4, 6.0, 1.5)) return 0xFFFF6B8A;
                    if (near(x, y, 8.4, 4.4, 1.4)) return 0xFF6BC8FF;
                    if (near(x, y, 4.4, 9.6, 1.4)) return 0xFF8BE08A;
                    if (near(x, y, 8.2, 8.6, 1.3)) return 0xFFFFD166;
                    return lerp(0xFFF6EBDD, 0xFFDCC8B4, clamp(d / 7.0));
                }
                return 0;
            }
        });

        System.out.println("图标已生成到 " + outDir.getAbsolutePath());
    }

    // ---------------------------------------------------------------- 绘制工具

    private static void save(File dir, String name, Painter painter) throws Exception {
        save(dir, name, painter, true);
    }

    private static void save(File dir, String name, Painter painter, boolean outline) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] colors = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int argb = painter.colorAt(x + 0.5, y + 0.5);
                // 偶发算出来的半透明一律按实心处理，字体渲染不支持半透明边缘好看
                colors[y * SIZE + x] = argb == 0 ? 0 : (0xFF000000 | argb);
            }
        }
        // 描边：把紧贴实心像素的透明格涂成深色，图标才有「贴纸」感而不是一坨色块
        int[] withOutline = colors.clone();
        if (outline) for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (colors[y * SIZE + x] != 0) {
                    continue;
                }
                boolean edge = (x > 0 && colors[y * SIZE + x - 1] != 0)
                        || (x < SIZE - 1 && colors[y * SIZE + x + 1] != 0)
                        || (y > 0 && colors[(y - 1) * SIZE + x] != 0)
                        || (y < SIZE - 1 && colors[(y + 1) * SIZE + x] != 0);
                if (edge) {
                    withOutline[y * SIZE + x] = OUTLINE;
                }
            }
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setRGB(x, y, withOutline[y * SIZE + x]);
            }
        }
        ImageIO.write(image, "png", new File(dir, name + ".png"));
    }

    private static boolean near(double x, double y, double cx, double cy, double r) {
        return Math.hypot(x - cx, y - cy) <= r;
    }

    private static boolean inside(double x, double y, double[] xs, double[] ys) {
        boolean result = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            if ((ys[i] > y) != (ys[j] > y)
                    && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                result = !result;
            }
        }
        return result;
    }

    private static double[] starX(double cx, double outer, double inner) {
        double[] values = new double[10];
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? outer : inner;
            values[i] = cx + Math.cos(Math.toRadians(-90 + i * 36)) * r;
        }
        return values;
    }

    private static double[] starY(double cy, double outer, double inner) {
        double[] values = new double[10];
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? outer : inner;
            values[i] = cy + Math.sin(Math.toRadians(-90 + i * 36)) * r;
        }
        return values;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static int lerp(int from, int to, double t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = (int) Math.round(fr + (tr - fr) * t);
        int g = (int) Math.round(fg + (tg - fg) * t);
        int b = (int) Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }
}
