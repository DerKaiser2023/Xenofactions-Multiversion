package com.hfr.server;

import com.hfr.config.FoundationConfig;
import com.hfr.data.MapSavedData;
import com.hfr.inventory.FoundationContainer;
import com.hfr.main.Xenofactions;
import com.hfr.network.FoundationNetwork;
import com.hfr.tdm.BlockAreaEdges;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.network.NetworkHooks;

/** Server-owned MAP selection and editor authorization. Item possession grants no authority. */
public final class EditorSessions {
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final long LEASE_TICKS = 20 * 300;
    private static final String CONTEXT = "xfAdminEditorContext";
    private static final class Session {
        final String token = UUID.randomUUID().toString();
        final String map, dimension;
        CompoundNBT expected;
        BlockPos a, b;
        boolean selecting;
        long expires, lastAction = -1;
        Session(ServerPlayerEntity player, String map, CompoundNBT expected) {
            this.map = map; this.expected = expected;
            dimension = player.level.dimension().location().toString();
            expires = player.getServer().getTickCount() + LEASE_TICKS;
        }
    }
    private EditorSessions() { }
    public static void clear(ServerPlayerEntity player) { ACTIVE.remove(player.getUUID()); }
    public static void clearAll() { ACTIVE.clear(); }
    private static Session valid(ServerPlayerEntity player) {
        Session session = ACTIVE.get(player.getUUID());
        if (session == null) return null;
        if (!player.isAlive() || !player.hasPermissions(4) || player.getServer().getTickCount() > session.expires
                || !session.dimension.equals(player.level.dimension().location().toString())
                || !session.expected.equals(MapSavedData.get(player.getServer()).map(session.map))) {
            clear(player); return null;
        }
        return session;
    }
    public static void tick(ServerPlayerEntity player) {
        if (valid(player) == null && player.containerMenu instanceof FoundationContainer) player.closeContainer();
    }
    public static void open(ServerPlayerEntity player, String requestedMap) {
        if (!player.hasPermissions(4)) throw new IllegalArgumentException("TDM editor requires operator level 4.");
        if (!player.inventory.getCarried().isEmpty()) throw new IllegalArgumentException("Put the carried item away before opening the editor.");
        String name = MapSavedData.normalize(requestedMap);
        CompoundNBT persisted = player.getPersistentData().getCompound(PlayerEntityTag.PERSISTED);
        if (name.isEmpty()) name = persisted.getCompound(CONTEXT).getString("map");
        MapSavedData maps = MapSavedData.get(player.getServer());
        if (maps.map(name) == null) {
            if (!requestedMap.isEmpty()) throw new IllegalArgumentException("Unknown map: " + requestedMap);
            if (maps.names().isEmpty()) throw new IllegalArgumentException("Create a map first with /tdm map create <name>.");
            name = maps.names().get(0);
        }
        Session previous = valid(player);
        Session session = new Session(player, name, maps.map(name));
        if (previous != null && previous.map.equals(name)) { session.a = previous.a; session.b = previous.b; session.selecting = previous.selecting; }
        ACTIVE.put(player.getUUID(), session);
        CompoundNBT context = persisted.getCompound(CONTEXT); context.putString("map", name); persisted.put(CONTEXT, context);
        player.getPersistentData().put(PlayerEntityTag.PERSISTED, persisted);
        CompoundNBT snapshot = snapshot(session);
        NetworkHooks.openGui(player, new SimpleNamedContainerProvider((id, inventory, owner) -> new FoundationContainer(id, snapshot),
                new StringTextComponent("Xenofactions map bounds")), buffer -> buffer.writeNbt(snapshot));
    }
    public static void action(ServerPlayerEntity player, int containerId, String token, int action) {
        Session session = valid(player);
        if (session == null || !(player.containerMenu instanceof FoundationContainer) || player.containerMenu.containerId != containerId
                || !session.token.equals(token) || !((FoundationContainer)player.containerMenu).snapshot().getString("token").equals(token)) return;
        long now = player.getServer().getTickCount();
        if (session.lastAction == now) return;
        session.lastAction = now;
        session.expires = now + LEASE_TICKS;
        try {
            if (action == 1) {
                // Validate before entering selection: every stored dimension must retain an explicit legacy mapping.
                if (FoundationConfig.legacyId(player.level.dimension().location()) == null) throw new IllegalArgumentException("Configure a legacy dimension mapping before editing here.");
                session.selecting = true;
                boolean carried = player.inventory.getCarried().getItem() == Xenofactions.WAND.get();
                for (ItemStack item : player.inventory.items) if (item.getItem() == Xenofactions.WAND.get()) carried = true;
                if (!carried && !player.inventory.add(new ItemStack(Xenofactions.WAND.get()))) say(player, "Inventory full; make room and choose Use wand again.");
                player.inventoryMenu.broadcastChanges();
                player.closeContainer();
                say(player, "Left block click: A; right block click: B. Reopen /tdm editor gui to save or cancel. Air clicks do nothing.");
            } else if (action == 2) {
                if (!session.selecting || session.a == null || session.b == null) throw new IllegalArgumentException("Select both block corners first.");
                Integer dimension = FoundationConfig.legacyId(player.level.dimension().location());
                if (dimension == null) throw new IllegalArgumentException("Missing legacy dimension mapping.");
                CompoundNBT bounds = session.expected.getCompound("bounds").copy();
                bounds.putInt("dim", dimension); bounds.putString("dimensionKey", session.dimension);
                bounds.putBoolean("hasPos1", true); bounds.putBoolean("hasPos2", true);
                bounds.putInt("x1", session.a.getX()); bounds.putInt("y1", session.a.getY()); bounds.putInt("z1", session.a.getZ());
                bounds.putInt("x2", session.b.getX()); bounds.putInt("y2", session.b.getY()); bounds.putInt("z2", session.b.getZ());
                MapSavedData maps = MapSavedData.get(player.getServer()); maps.setBounds(session.map, session.expected, bounds);
                session.expected = maps.map(session.map); session.selecting = false; session.a = session.b = null;
                say(player, "Map bounds saved. Match border enforcement is not ported in this foundation build.");
            } else if (action == 3) { session.selecting = false; session.a = session.b = null; }
            else if (action != 0) return;
            if (player.containerMenu instanceof FoundationContainer) FoundationNetwork.snapshot(player, containerId, snapshot(session));
        } catch (IllegalArgumentException | IllegalStateException failure) { say(player, failure.getMessage()); }
    }
    public static boolean point(ServerPlayerEntity player, BlockPos position, boolean first) {
        Session session = valid(player);
        if (session == null || !session.selecting) return false;
        if (first) session.a = position.immutable(); else session.b = position.immutable();
        session.expires = player.getServer().getTickCount() + LEASE_TICKS;
        say(player, "Point " + (first ? "A" : "B") + ": " + position.getX() + ", " + position.getY() + ", " + position.getZ());
        return true;
    }
    private static CompoundNBT snapshot(Session session) {
        CompoundNBT result = new CompoundNBT();
        result.putString("token", session.token); result.putString("map", session.map); result.putString("dimension", session.dimension);
        result.putBoolean("selecting", session.selecting);
        result.putString("a", session.a == null ? "unset" : session.a.getX() + ", " + session.a.getY() + ", " + session.a.getZ());
        result.putString("b", session.b == null ? "unset" : session.b.getX() + ", " + session.b.getY() + ", " + session.b.getZ());
        CompoundNBT bounds = session.expected.getCompound("bounds");
        String description = "unset";
        if (bounds.getBoolean("hasPos1") && bounds.getBoolean("hasPos2")) {
            BlockAreaEdges edges = BlockAreaEdges.of(bounds.getInt("x1"), bounds.getInt("z1"), bounds.getInt("x2"), bounds.getInt("z2"));
            description = "X [" + edges.minX + ", " + edges.maxXExclusive + ") Z [" + edges.minZ + ", " + edges.maxZExclusive + ")";
        }
        result.putString("bounds", description); return result;
    }
    private static void say(ServerPlayerEntity player, String text) { player.sendMessage(new StringTextComponent(text), net.minecraft.util.Util.NIL_UUID); }
    public static final class PlayerEntityTag {
        public static final String PERSISTED = net.minecraft.entity.player.PlayerEntity.PERSISTED_NBT_TAG;
        private PlayerEntityTag() { }
    }
}
