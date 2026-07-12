package com.aozainkmc.input.api;

import com.aozainkmc.input.binding.QuickGlyphBinding;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Gameplay-neutral notification emitted after a specified talisman is consumed. */
public final class QuickBindingChangedEvent extends Event {
    private final ServerPlayer player;
    private final QuickGlyphBinding.Binding binding;
    public QuickBindingChangedEvent(ServerPlayer player, QuickGlyphBinding.Binding binding) {
        this.player = player; this.binding = binding;
    }
    public ServerPlayer player() { return player; }
    public QuickGlyphBinding.Binding binding() { return binding; }
}
