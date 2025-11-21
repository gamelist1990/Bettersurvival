package org.pexserver.koukunn.bettersurvival.Core.Util;

import org.bukkit.entity.Player;
import java.util.List;
import java.util.function.Consumer;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.response.SimpleFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * Form utilities — Bedrock players via Geyser Cumulus SimpleForm, Java players fall back to inventory.
 */
public final class FormsUtil {

    private FormsUtil() {}

    public static boolean openSimpleForm(Player p, String title, List<String> buttons, Consumer<Integer> callback) {
        if (p == null) return false;

        // Bedrock check
        if (FloodgateUtil.isBedrock(p)) {
            // NOTE: Bedrock (Geyser) SimpleForm integration is planned.
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null) return false;

            try {
                SimpleForm.Builder b = SimpleForm.builder().title(title).content("");
                for (String btn : buttons) {
                    b.button(btn);
                }
                b.validResultHandler((form, resp) -> {
                    if (resp == null) {
                        if (callback != null) callback.accept(-1);
                        return;
                    }
                    int idx = ((SimpleFormResponse) resp).clickedButtonId();
                    if (callback != null) callback.accept(idx);
                });
                SimpleForm form = b.build();
                boolean ok = api.sendForm(p.getUniqueId(), form);
                return ok;
            } catch (NoClassDefFoundError | Exception e) {
                return false;
            }
        }

        // Fallback: Not Bedrock or forms not available. Return false to indicate not displayed as SimpleForm.
        return false;
    }
}
