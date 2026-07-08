package org.pexserver.koukunn.bettersurvival.Modules.Feature.JpCh;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JPCh モジュール。
 *
 * <p>チャットに投稿されたローマ字メッセージを検出し、Google Input Tools (IME) 音訳 API 経由で
 * かな/漢字へ自動変換して置き換えます。英単語や既に日本語のメッセージは変換しません。</p>
 *
 * <p>ローマ字と英語が混在するメッセージ ("watashi wa happy desu" 等) では、<b>ローマ字部分だけ</b>を
 * 翻訳し英単語はそのまま残す (→ "私は happy です")。</p>
 *
 * <p>検出ロジック (英語との誤爆を避けるため厳格化):</p>
 * <ul>
 *   <li>メッセージが ASCII 英字のみで構成されていること (非 ASCII があれば既に日本語と見なしスキップ)。</li>
 *   <li>連続するトークンを厳格なヘボン式モーラ表で分解し、分解できるトークンの連なりを 1 つの
 *       「ローマ字ラン」とみなす。分解不能なトークン ({@code l/q/v/x} や {@code si/ti/tu/hu} 等の
 *       非ヘボン綴り、子音連結を含む語 = 英単語) はランの区切りとなり、そのまま原文で残す。</li>
 *   <li>各ランをスコアリングし、しきい値を超えたランのみ翻訳して置き換える。
 *       スコアは以下を加味する:
 *       <ul>
 *         <li>{@code shi/chi/tsu}・拗音・{@code desu/masu} 等のローマ字特有パターン (強い加点)。</li>
 *         <li>音節数 (3 以上で加点、単音節は減点)。</li>
 *         <li>ローマ字にも分解できてしまう短い英単語のブラックリスト (減点)。</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>この二段構え (厳格な音韻フィルタ → スコアリング) により、"make it" のような英語文はどのランも
 * スコア不足/分解不能で翻訳されず、"take/note" のような両義的な短英単語もスコア不足で除外される。</p>
 */
public class JpChModule implements Listener {

    /** ToggleModule に登録する機能キー。 */
    public static final String FEATURE_KEY = "jpch";

    /**
     * ローマ字→かな/漢字の音訳に用いる Google Input Tools (IME) エンドポイント。
     *
     * <p>ローマ字の日本語化は「翻訳」ではなく「音訳 (transliteration)」であり、Google 翻訳では実現できない
     * (同一言語間 ja→ja は非対応)。IME 用の本エンドポイント ({@code itc=ja-t-i0-und}) が正しい変換元となる。
     * API キー不要・GET で JSON を返す。</p>
     */
    private static final String TRANSLITERATE_URL =
            "https://inputtools.google.com/request?itc=ja-t-i0-und&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8&app=bettersurvival&text=";

    /** 変換対象の最小英字数 (これ未満の 1 単語チャットは英語誤爆を防ぐためスキップ)。 */
    private static final int MIN_LETTER_COUNT = 3;

    /** GAS API のタイムアウト。AsyncChatEvent 上で同期送信するため短めに設定。 */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(6);

    /**
     * 語トークンの抽出パターン。{@code n'} 表記のアポストロフィと、長音を表す {@code '-'}
     * ({@code haro- → ハロー}) をトークンに含める。
     */
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z'-]+");

    /**
     * ヘボン式モーラ表として妥当に分解できてしまう典型的な英単語のブラックリスト。
     *
     * <p>厳格なモーラフィルタ ({@link #decomposeStrict}) を通過してしまう「両義的な」英単語のみを列挙する。
     * {@code l/q/v/x} を含む語や子音連結を持つ語 ({@code hello, nice, cool} 等) はフィルタ段階で
     * 除外されるため、ここに載せる必要はない。</p>
     */
    private static final Set<String> ENGLISH_BLACKLIST = new HashSet<>();
    static {
        String[] words = {
                // 2 モーラで綴りがローマ字と一致する短英単語
                "me", "he", "no", "so", "to", "do", "go", "we",
                "take", "make", "name", "note", "same", "here", "more", "mine",
                "nine", "mode", "made", "rate", "hate", "mate", "kite", "mane",
                "tone", "bone", "bane", "wane", "dare", "bare", "hare", "care",
                "maze", "haze", "some", "come", "home", "hope", "date", "tape",
                "sake", "pane", "cane", "hire", "mare", "tore", "sore", "pore",
                // 3 モーラ以上でもローマ字化しうる一般英単語
                "banana", "panorama", "karate", "tomato", "sonata", "samba"
        };
        for (String w : words) {
            ENGLISH_BLACKLIST.add(w);
        }
    }

    /**
     * ワープロローマ字 (IME で実際に打鍵される綴り) のモーラ表。
     * {@code n} (撥音)・促音・長音 {@code '-'} は {@link #matchMora} で個別に処理するため含めない。
     *
     * <p>ヘボン式 ({@code shi/chi/tsu/fu}) に加え、訓令式/日本式 ({@code si/ti/tu/hu/zi/di/du})、
     * 拗音の {@code sy/ty/zy/dy} 綴り ({@code tya→ちゃ} 等)、外来音 ({@code fa/va/che/she/je/tsa} 等) を
     * 網羅する。これらは Google IME が受理する綴りであり、除外すると "tyanto" や "hosii" が変換されず残る。</p>
     *
     * <p>一方で {@code l/q/x} や子音連結 ("site" の {@code te} は可だが "hello" の {@code ll}、"time" の
     * 末尾 {@code me} 単体は不可) は依然として弾かれ、2 モーラ程度の短い英単語はスコアリング段階で除外される。</p>
     */
    private static final Set<String> VALID_MORA = new HashSet<>();
    static {
        String[] moras = {
                "a", "i", "u", "e", "o",
                "ka", "ki", "ku", "ke", "ko",
                "ga", "gi", "gu", "ge", "go",
                "sa", "shi", "si", "su", "se", "so",
                "za", "ji", "zi", "zu", "ze", "zo",
                "ta", "chi", "ti", "tsu", "tu", "te", "to",
                "da", "di", "du", "de", "do",
                "na", "ni", "nu", "ne", "no",
                "ha", "hi", "fu", "hu", "he", "ho",
                "ba", "bi", "bu", "be", "bo",
                "pa", "pi", "pu", "pe", "po",
                "ma", "mi", "mu", "me", "mo",
                "ya", "yu", "yo", "ye",
                "ra", "ri", "ru", "re", "ro",
                "wa", "wi", "we", "wo",
                // 拗音 (ヘボン式 + 訓令/日本式の sy/ty/zy/dy 綴り)
                "kya", "kyu", "kyo", "gya", "gyu", "gyo",
                "sha", "shu", "sho", "sya", "syu", "syo", "she",
                "cha", "chu", "cho", "tya", "tyu", "tyo", "che",
                "ja", "ju", "jo", "je", "jya", "jyu", "jyo", "zya", "zyu", "zyo",
                "dya", "dyu", "dyo",
                "nya", "nyu", "nyo", "hya", "hyu", "hyo",
                "bya", "byu", "byo", "pya", "pyu", "pyo",
                "mya", "myu", "myo", "rya", "ryu", "ryo",
                // 外来音
                "fa", "fi", "fe", "fo",
                "va", "vi", "vu", "ve", "vo",
                "tsa", "tsi", "tse", "tso"
        };
        for (String m : moras) {
            VALID_MORA.add(m);
        }
    }

    /** ローマ字特有パターンを 1 つ検出したときのスコア加算 (強いローマ字シグナル)。 */
    private static final int SCORE_DISTINCTIVE = 3;
    /** 3 モーラ以上の非自明トークン 1 つあたりの加算。 */
    private static final int SCORE_LONG_TOKEN = 2;
    /** 単音節トークン 1 つあたりの減算 (英語の短語や助詞的断片を抑制)。 */
    private static final int SCORE_SHORT_TOKEN = -1;
    /** ブラックリスト該当トークン 1 つあたりの減算。 */
    private static final int SCORE_BLACKLIST = -3;
    /** メッセージ全体の合計モーラ数が十分多いときのボーナス。 */
    private static final int SCORE_TOTAL_MORA_BONUS = 1;
    /** 合計モーラ数がこの値以上なら {@link #SCORE_TOTAL_MORA_BONUS} を付与。 */
    private static final int TOTAL_MORA_BONUS_THRESHOLD = 4;
    /** 変換対象と判定する最小スコア。 */
    private static final int ACCEPT_SCORE = 2;

    private final Loader plugin;
    private final ToggleModule toggleModule;
    private final HttpClient httpClient;

    /**
     * JPCh モジュールを生成します。
     *
     * @param plugin       プラグイン本体
     * @param toggleModule 有効/無効管理を担う ToggleModule
     */
    public JpChModule(Loader plugin, ToggleModule toggleModule) {
        this.plugin = plugin;
        this.toggleModule = toggleModule;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // 一部の Google エンドポイントは 302 リダイレクトで本体を返すため追従を有効化
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * AsyncChatEvent を捕捉し、必要ならメッセージをローマ字→日本語に置き換えます。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!toggleModule.getGlobal(FEATURE_KEY)) {
            return;
        }

        String original = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (original == null) {
            return;
        }
        String trimmed = original.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        // ローマ字ランのみを翻訳し、英単語はそのまま残した文を組み立てる
        String translated = translateMessage(trimmed);
        if (translated == null || translated.equals(trimmed)) {
            return;
        }

        // 翻訳結果 (元テキスト) の形で置き換える
        Component newMessage = Component.text(translated)
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(trimmed, NamedTextColor.DARK_GRAY))
                .append(Component.text(")", NamedTextColor.GRAY));
        event.message(newMessage);
    }

    /**
     * メッセージ内のローマ字ランだけを翻訳し、英単語や記号を原文のまま残した文字列を組み立てます。
     *
     * <p>例: {@code "watashi wa happy desu"} → {@code "私は happy です"}。
     * ローマ字ランが 1 つも無い、または翻訳で何も変化しなかった場合は {@code null} を返します。</p>
     *
     * @param text 判定・翻訳対象 (トリム済み推奨)
     * @return 置き換え後の文字列、変換不要なら null
     */
    private String translateMessage(String text) {
        List<int[]> runs = findRomajiRuns(text);
        if (runs == null || runs.isEmpty()) {
            return null;
        }

        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        boolean changed = false;
        for (int[] run : runs) {
            int start = run[0];
            int end = run[1];
            String runText = text.substring(start, end);
            String tr = transliterateRomaji(runText);
            if (tr == null) {
                continue; // 音訳失敗時はこのランを原文のまま残す
            }
            tr = tr.trim();
            if (tr.isEmpty() || tr.equalsIgnoreCase(runText)) {
                continue;
            }
            out.append(text, last, start); // ラン間の英単語・区切りを原文コピー
            out.append(tr);
            last = end;
            changed = true;
        }
        if (!changed) {
            return null;
        }
        out.append(text, last, text.length());
        return out.toString();
    }

    /**
     * メッセージ中の「翻訳すべきローマ字ラン」を、原文文字列上のスパン {@code [start, end)} として抽出します。
     *
     * <p>アルゴリズム:
     * <ol>
     *   <li>非 ASCII (=既に日本語や他言語) を含む場合は {@code null} を返す。</li>
     *   <li>語トークンを順に走査し、厳格なヘボン式モーラ表で分解できるトークンの連なりを 1 ランとする。
     *       分解不能なトークン (英単語) はランの区切りとなる。</li>
     *   <li>各ランを重み付けスコアリングし、{@link #ACCEPT_SCORE} 以上かつ最小英字数を満たすランのみ返す。</li>
     * </ol>
     *
     * @param text 判定対象 (トリム済み推奨)
     * @return 翻訳対象ランのスパン一覧 (空可)、既に日本語等の場合は null
     */
    static List<int[]> findRomajiRuns(String text) {
        // 非 ASCII (=既に日本語や他言語) を含む場合はスキップ
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return null;
            }
        }

        // 語トークンの位置と小文字表記を収集
        List<int[]> spans = new ArrayList<>();
        List<String> lowers = new ArrayList<>();
        Matcher m = WORD_PATTERN.matcher(text);
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
            lowers.add(text.substring(m.start(), m.end()).toLowerCase());
        }

        List<int[]> runs = new ArrayList<>();
        int n = spans.size();
        int i = 0;
        while (i < n) {
            if (decomposeStrict(lowers.get(i)) <= 0) {
                i++; // 分解不能トークン (英単語) はランの区切り
                continue;
            }
            // [i, j) を連続する分解可能トークンのランとして確定させつつスコアリング
            int letterCount = 0;
            int totalMora = 0;
            int score = 0;
            int j = i;
            while (j < n) {
                String tok = lowers.get(j);
                int mora = decomposeStrict(tok);
                if (mora <= 0) {
                    break;
                }
                letterCount += countAsciiLetters(tok); // '-' や "'" は英字数に数えない
                totalMora += mora;
                if (containsDistinctiveRomaji(tok)) {
                    score += SCORE_DISTINCTIVE;
                } else if (ENGLISH_BLACKLIST.contains(tok)) {
                    score += SCORE_BLACKLIST;
                } else if (mora >= 3) {
                    score += SCORE_LONG_TOKEN;
                } else if (mora == 1) {
                    score += SCORE_SHORT_TOKEN;
                }
                // mora == 2 の非自明トークンは中立 (加減点なし)
                j++;
            }
            if (totalMora >= TOTAL_MORA_BONUS_THRESHOLD) {
                score += SCORE_TOTAL_MORA_BONUS;
            }
            if (letterCount >= MIN_LETTER_COUNT && score >= ACCEPT_SCORE) {
                // ラン先頭トークンの開始 〜 末尾トークンの終端 (途中の空白を含む)
                runs.add(new int[]{spans.get(i)[0], spans.get(j - 1)[1]});
            }
            i = j;
        }
        return runs;
    }

    /**
     * 英語に頻出しないローマ字特有パターンを含むか判定します。
     */
    private static boolean containsDistinctiveRomaji(String s) {
        String[] markers = {
                "shi", "chi", "tsu", "sha", "shu", "sho", "cha", "chu", "cho",
                "kya", "kyu", "kyo", "gya", "gyu", "gyo",
                "nya", "nyu", "nyo", "hya", "hyu", "hyo",
                "mya", "myu", "myo", "rya", "ryu", "ryo",
                "bya", "byu", "byo", "pya", "pyu", "pyo",
                // 訓令/日本式の拗音綴り (英語には現れないため強いローマ字シグナル)
                "sya", "syu", "syo", "tya", "tyu", "tyo",
                "jya", "jyu", "jyo", "zya", "zyu", "zyo", "dya", "dyu", "dyo",
                "desu", "masu", "watashi", "arigato", "konnichi", "konban",
                "ohayo", "sayonara", "sumimasen", "gomen", "onegai",
                "sensei", "yoroshiku", "hajime", "otsukare", "sugoi",
                "nihon", "nippon", "gozai", "kudasai"
        };
        for (String m : markers) {
            if (s.contains(m)) {
                return true;
            }
        }
        return false;
    }

    /** トークン中の ASCII 英字数を返します ({@code '-'} や {@code '\''} は数えない)。 */
    private static int countAsciiLetters(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') {
                count++;
            }
        }
        return count;
    }

    /**
     * 単語を厳格なヘボン式モーラ列として分解し、そのモーラ数を返します。
     *
     * <p>各位置で {@link #matchMora} を最長一致で適用する。1 文字でも消費できない箇所があれば
     * ヘボン式として不正 (= 英語等) と判断し {@code -1} を返す。</p>
     *
     * @param w 小文字化済み単語
     * @return 分解成功時のモーラ数、失敗時は -1
     */
    static int decomposeStrict(String w) {
        int i = 0;
        int n = w.length();
        int mora = 0;
        while (i < n) {
            int consumed = matchMora(w, i);
            if (consumed <= 0) {
                return -1;
            }
            i += consumed;
            mora++;
        }
        return mora;
    }

    /**
     * 位置 {@code i} から始まる 1 モーラ分の文字数を返します。マッチしなければ 0。
     *
     * <p>撥音 (単独 {@code n})・促音 (小さい っ) を先に個別処理し、それ以外は
     * {@link #VALID_MORA} への最長一致 (3→2→1 文字) で判定する。</p>
     */
    private static int matchMora(String s, int i) {
        int n = s.length();
        char c = s.charAt(i);

        // 長音 ('-'): 直前モーラを伸ばす記号として 1 文字消費 (haro- → ハロー)
        if (c == '-') {
            return 1;
        }

        // 促音 (小さい っ): 同一子音の連続、または matcha の t+ch
        if (i + 1 < n && isSokuonConsonant(c)) {
            char next = s.charAt(i + 1);
            if (c == next) {
                return 1; // 促音として 1 文字だけ消費し、後続モーラは次反復で処理
            }
            if (c == 't' && next == 'c' && i + 2 < n && s.charAt(i + 2) == 'h') {
                return 1; // 't' + "ch..." の促音 (matcha 等)
            }
        }

        // 撥音 ん: 後続が母音/y/アポストロフィ以外なら単独の ん
        if (c == 'n') {
            if (i + 1 >= n) {
                return 1; // 語末の ん
            }
            char next = s.charAt(i + 1);
            if (next == '\'') {
                return 1; // n' 表記の区切り
            }
            if (!isVowel(next) && next != 'y') {
                return 1; // ん + 子音
            }
            // na / nya 等は下の集合マッチに委ねる
        }

        // モーラ表への最長一致 (shi/chi/tsu/拗音 = 3, CV = 2, 母音 = 1)
        int max = Math.min(3, n - i);
        for (int len = max; len >= 1; len--) {
            if (VALID_MORA.contains(s.substring(i, i + len))) {
                return len;
            }
        }
        return 0;
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'i' || c == 'u' || c == 'e' || c == 'o';
    }

    /** 促音 (小さい っ) を取りうる子音: か/さ/た/ぱ 行の頭子音。 */
    private static boolean isSokuonConsonant(char c) {
        return c == 'k' || c == 's' || c == 't' || c == 'p';
    }

    /**
     * Google Input Tools (IME) にローマ字を送り、かな/漢字へ音訳した第 1 候補を返します。
     *
     * <p>AsyncChatEvent は非同期スレッドで発火するため、ここでの同期送信は許容されます。
     * レスポンス形式:
     * {@code ["SUCCESS",[["<入力>",["<候補1>", ...],[],{...}]]]}。失敗時は
     * {@code ["FAILED_...", ...]} 等が返るため {@code "SUCCESS"} を確認する。</p>
     *
     * @param text 音訳対象のローマ字 (空白を含みうる)
     * @return 変換後文字列、失敗時は null
     */
    private String transliterateRomaji(String text) {
        try {
            String url = TRANSLITERATE_URL + URLEncoder.encode(text, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                plugin.getLogger().warning("JPCh 音訳失敗: HTTP " + resp.statusCode());
                return null;
            }
            String body = resp.body();
            if (body == null || body.isBlank()) {
                return null;
            }

            // ["SUCCESS",[["watashi",["私"],[],{...}]]] を分解して第 1 候補を取り出す
            JsonArray root = JsonParser.parseString(body).getAsJsonArray();
            if (root.size() < 2 || !"SUCCESS".equals(root.get(0).getAsString())) {
                return null;
            }
            JsonArray results = root.get(1).getAsJsonArray();
            if (results.isEmpty()) {
                return null;
            }
            JsonArray first = results.get(0).getAsJsonArray();
            if (first.size() < 2) {
                return null;
            }
            JsonArray candidates = first.get(1).getAsJsonArray();
            if (candidates.isEmpty()) {
                return null;
            }
            return candidates.get(0).getAsString();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "JPCh 音訳失敗: " + e.getMessage());
            return null;
        }
    }
}
