import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kotlin.jvm.internal.DefaultConstructorMarker;
import org.tiqian.math.core.MathFontClass;
import org.tiqian.math.core.MathFontWeight;
import org.tiqian.math.core.MathMode;
import org.tiqian.math.core.MathStyle;
import org.tiqian.math.core.SourceRange;
import org.tiqian.math.font.opentype.LeteSansMath;
import org.tiqian.math.font.opentype.OpenTypeMathFont;
import org.tiqian.math.font.skia.GeometryGoldenTest;
import org.tiqian.math.font.skia.SkiaMathFontFace;
import org.tiqian.math.font.stix.StixTwoMath;

class MathPrecision {
    public static void main(String[] args) throws Exception {
        var output = Path.of(args[0]);
        var append = GeometryGoldenTest.class.getDeclaredMethod("appendFace", StringBuilder.class, String.class, OpenTypeMathFont.class);
        append.setAccessible(true);
        var snapshot = new StringBuilder("geometry-v3\n");
        var metrics = new StringBuilder();
        var names = List.of("Lete Sans Math", "STIX Two Math");
        var fonts = List.of(LeteSansMath.INSTANCE.load(), StixTwoMath.INSTANCE.load());
        var constructor = SkiaMathFontFace.class.getConstructor(OpenTypeMathFont.class, String.class,
                MathFontClass.class, MathFontWeight.class, MathFontWeight.class, DefaultConstructorMarker.class);
        for (int i = 0; i < fonts.size(); i++) {
            var math = fonts.get(i);
            append.invoke(new GeometryGoldenTest(), snapshot, names.get(i), math);
            try (var face = constructor.newInstance(math, "legacy-single-face", MathFontClass.Serif,
                    MathFontWeight.Regular, MathFontWeight.Regular, null)) {
                var sample = face.getClass().getDeclaredMethod("getDesignShapingSize");
                sample.setAccessible(true);
                metrics.append(names.get(i)).append(" upm=").append(math.getUnitsPerEm())
                        .append(" designSample=").append(sample.invoke(face)).append('\n');
                var style = MathStyle.Companion.initial(MathMode.Inline);
                for (int cp : new int[] {0x1d44e, 0x1d44f, 0x1d450, 0x1d451, 0x1d452, 43}) {
                    var text = new String(Character.toChars(cp));
                    var run = face.shape(text, 40f, style, new SourceRange(0, text.length()));
                    var glyph = run.getGlyphs().getFirst();
                    metrics.append(text).append(" width=").append(run.getWidth())
                            .append(" advance=").append(glyph.getAdvance()).append('\n');
                }
            }
        }
        Files.writeString(output.resolve("metrics.txt"), metrics.toString());
        Files.writeString(output.resolve("actual-geometry-v3.txt"), snapshot.toString());
    }
}
