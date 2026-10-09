package com.hfr.client;

import com.hfr.inventory.FoundationContainer;
import com.hfr.network.FoundationNetwork;
import net.minecraft.client.Minecraft;

public final class ClientPackets {
    public static void receive(FoundationNetwork.Snapshot packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.containerMenu instanceof FoundationContainer
                && client.player.containerMenu.containerId == packet.container)
            ((FoundationContainer)client.player.containerMenu).receive(packet.data);
    }
    private ClientPackets() { }
}
