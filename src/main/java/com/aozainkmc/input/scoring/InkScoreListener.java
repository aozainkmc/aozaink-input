package com.aozainkmc.input.scoring;

import com.aozainkmc.core.api.InkRecognitionResult;
import com.aozainkmc.core.api.InkRecognizedEvent;
import com.aozainkmc.core.api.InkSource;
import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.dev.AozaiInputDevMode;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = AozaiInkInput.MOD_ID)
public final class InkScoreListener {

    private InkScoreListener() {}

    @SubscribeEvent
    public static void onGlyphRecognized(InkRecognizedEvent event) {
        InkSource source = event.source();
        if (source.extra().get("talisman_type") == null) {
            return;
        }
        ServerPlayer player = event.player();
        if (player == null || !AozaiInputDevMode.isEnabled(player)) {
            return;
        }

        InkRecognitionResult result = event.result();
        String glyph = result.topGlyph();
        if (glyph == null || glyph.isEmpty()) {
            return;
        }

        Optional<TalismanScorer.Score> scored = TalismanScorer.score(
            glyph,
            result.simplifiedStrokeCount(),
            result.simplifiedPointCount(),
            result.writingDurationMs(),
            result.confidence()
        );

        if (scored.isEmpty()) {
            player.sendSystemMessage(Component.literal("[评分] " + glyph + "  无基线（未校准）"));
            return;
        }

        player.sendSystemMessage(Component.literal(format(scored.get())));
    }

    private static String format(TalismanScorer.Score s) {
        return String.format(
            "[评分] %s · %s  总分 %.1f%%  | 信 %.1f%%³→%.1f%% · 笔 %d/%.0f→%.0f%% · 点 %d/%.0f→%.0f%% · 时 %.2fs/%.1fs→x%.2f",
            s.glyph(),
            TalismanGrade.of(s.composite()).display(),
            s.composite() * 100,
            s.confidence() * 100, s.confScore() * 100,
            s.strokeCount(), s.baselineStrokes(), s.strokeScore() * 100,
            s.pointCount(), s.baselinePoints(), s.pointScore() * 100,
            s.durationMs() / 1000.0, s.targetMs() / 1000.0, s.timeFactor()
        );
    }
}
