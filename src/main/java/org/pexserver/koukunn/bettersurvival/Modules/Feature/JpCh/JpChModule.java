package org.pexserver.koukunn.bettersurvival.Modules.Feature.JpCh;

import com.google.gson.JsonObject;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * JPCh モジュール。
 *
 * <p>チャットに投稿されたローマ字メッセージを検出し、外部 GAS 翻訳 API 経由で
 * 日本語へ自動変換して置き換えます。英単語や既に日本語のメッセージは変換しません。</p>
 *
 * <p>検出ロジック (英語との誤爆を避けるため厳格化):</p>
 * <ul>
 *   <li>メッセージが ASCII 英字のみで構成されていること (非 ASCII があれば既に日本語と見なしスキップ)。</li>
 *   <li><b>全</b>トークンが厳格なヘボン式モーラ表で分解できること。1 語でも分解不能なら英語混在と判断し
 *       メッセージ全体をスキップする (例: {@code l/q/v/x}、{@code si/ti/tu/hu} 等の非ヘボン綴りや子音連結)。</li>
 *   <li>残った「ヘボン式として妥当」なトークン群をスコアリングし、しきい値を超えた場合のみ変換する。
 *       スコアは以下を加味する:
 *       <ul>
 *         <li>{@code shi/chi/tsu}・拗音・{@code desu/masu} 等のローマ字特有パターン (強い加点)。</li>
 *         <li>音節数 (3 以上で加点、単音節は減点)。</li>
 *         <li>ローマ字にも分解できてしまう短い英単語のブラックリスト (減点)。</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>この二段構え (厳格な音韻フィルタ → スコアリング) により、"hello world" や "make it" のような
 * 英語文はモーラ分解の段階で確実に除外され、"take/note" のような両義的な短英単語もスコア不足で除外される。</p>
 */
public class JpChModule implements Listener {

    /** ToggleModule に登録する機能キー。 */
    public static final String FEATURE_KEY = "jpch";

    /** translate.rs の DEFAULT_GAS_URL と同一。 */
    private static final String GAS_URL =
            "https://script.google.com/macros/s/AKfycbxPh_IjkSYpkfxHoGXVzK4oNQ2Vy0uRByGeNGA6ti3M7flAMCYkeJKuoBrALNCMImEi_g/exec";

    /** 変換対象の最小英字数 (これ未満の 1 単語チャットは英語誤爆を防ぐためスキップ)。 */
    private static final int MIN_LETTER_COUNT = 3;

    /** GAS API のタイムアウト。AsyncChatEvent 上で同期送信するため短めに設定。 */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(6);

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
     * 厳格なヘボン式モーラ表。{@code n} (撥音) と促音は {@link #matchMora} で個別に処理するため含めない。
     *
     * <p>あえて {@code si/ti/tu/hu/zi/di/du/wi/we/wu/ye} 等の非ヘボン綴りを除外することで、
     * 英単語 ("site", "time", "sink" 等) をモーラ分解の段階で弾く。</p>
     */
    private static final Set<String> VALID_MORA = new HashSet<>();
    static {
        String[] moras = {
                "a", "i", "u", "e", "o",
                "ka", "ki", "ku", "ke", "ko",
                "ga", "gi", "gu", "ge", "go",
                "sa", "shi", "su", "se", "so",
                "za", "ji", "zu", "ze", "zo",
                "ta", "chi", "tsu", "te", "to",
                "da", "de", "do",
                "na", "ni", "nu", "ne", "no",
                "ha", "hi", "fu", "he", "ho",
                "ba", "bi", "bu", "be", "bo",
                "pa", "pi", "pu", "pe", "po",
                "ma", "mi", "mu", "me", "mo",
                "ya", "yu", "yo",
                "ra", "ri", "ru", "re", "ro",
                "wa", "wo",
                // 拗音
                "kya", "kyu", "kyo", "gya", "gyu", "gyo",
                "sha", "shu", "sho", "ja", "ju", "jo", "jya", "jyu", "jyo",
                "cha", "chu", "cho",
                "nya", "nyu", "nyo", "hya", "hyu", "hyo",
                "bya", "byu", "byo", "pya", "pyu", "pyo",
                "mya", "myu", "myo", "rya", "ryu", "ryo"
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

        if (!isLikelyRomaji(trimmed)) {
            return;
        }

        String translated = translateViaGas(trimmed);
        if (translated == null) {
            return;
        }
        translated = translated.trim();
        if (translated.isEmpty() || translated.equalsIgnoreCase(trimmed)) {
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
     * 文字列がローマ字入力らしいかどうかを判定します。
     *
     * <p>アルゴリズムは 2 段構え:
     * <ol>
     *   <li><b>音韻フィルタ</b> — 全トークンを厳格なヘボン式モーラ表で分解する。1 語でも分解不能なら
     *       英語混在と判断し即座に {@code false} を返す。</li>
     *   <li><b>スコアリング</b> — 分解に成功したトークン群を重み付け加点し、
     *       {@link #ACCEPT_SCORE} 以上なら {@code true}。</li>
     * </ol>
     *
     * @param text 判定対象 (トリム済み推奨)
     * @return ローマ字入力と推定される場合 true
     */
    static boolean isLikelyRomaji(String text) {
        // 非 ASCII (=既に日本語や他言語) を含む場合はスキップ
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return false;
            }
        }

        String lower = text.toLowerCase();
        String[] tokens = lower.split("[^a-z]+");

        int letterCount = 0;
        int totalMora = 0;
        int tokenCount = 0;
        int score = 0;

        for (String tok : tokens) {
            if (tok.isEmpty()) {
                continue;
            }
            tokenCount++;
            letterCount += tok.length();

            int mora = decomposeStrict(tok);
            if (mora <= 0) {
                // 1 語でも厳格モーラ分解に失敗 = 英語 (混在) とみなし全体をスキップ
                return false;
            }
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
        }

        if (tokenCount == 0 || letterCount < MIN_LETTER_COUNT) {
            return false;
        }
        if (totalMora >= TOTAL_MORA_BONUS_THRESHOLD) {
            score += SCORE_TOTAL_MORA_BONUS;
        }

        return score >= ACCEPT_SCORE;
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
                "jya", "jyu", "jyo",
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
     * GAS 翻訳エンドポイントに同期リクエストを送り、翻訳結果を返します。
     * AsyncChatEvent は非同期スレッドで発火するため、ここでの同期送信は許容されます。
     */
    private String translateViaGas(String text) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("text", text);
            // ローマ字は日本語入力の代替表記として扱う
            payload.addProperty("from", "ja");
            payload.addProperty("to", "ja");

            HttpRequest request = HttpRequest.newBuilder(URI.create(GAS_URL))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                plugin.getLogger().warning("JPCh 翻訳失敗: HTTP " + resp.statusCode());
                return null;
            }
            String body = resp.body();
            if (body == null || body.isBlank()) {
                return null;
            }
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("translation") || json.get("translation").isJsonNull()) {
                return null;
            }
            return json.get("translation").getAsString();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "JPCh 翻訳失敗: " + e.getMessage());
            return null;
        }
    }
}
