import com.sade.launcher.LanguagePolicy;
public final class LanguagePolicyTest {
    public static void main(String[] args) {
        String[] supported = {"en","tr","ar","zh","zh-Hant","fil","id","jv","nb","pt","he"};
        String[][] cases = {{"tr","tr"},{"ar","ar"},{"zh","zh"},{"sv","en"},{"","en"},{"tl","fil"},{"in","id"},{"jw","jv"},
            {"zh-TW","zh-Hant"},{"zh-HK","zh-Hant"},{"zh-MO","zh-Hant"},{"zh-Hant","zh-Hant"},
            {"zh-Hans-TW","zh"},{"zh-CN","zh"},{"no-NO","nb"},{"nb-NO","nb"},{"pt-BR","pt"},
            {"tr-TR","tr"},{"iw-IL","he"},{"xx-ZZ","en"}};
        for (String[] c : cases) if (!LanguagePolicy.choose(c[0],supported).equals(c[1])) throw new AssertionError(c[0]);
        System.out.println("Language policy: " + cases.length + " checks passed");
    }
}
