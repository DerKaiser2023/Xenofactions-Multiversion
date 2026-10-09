package com.hfr.data;

import com.hfr.clowder.FactionRole;
import com.hfr.clowder.FactionMemberRecord;
import com.hfr.config.FoundationConfig;
import com.hfr.main.Xenofactions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Native binding for the current Clowder schema. Inspection never rewrites legacy data. */
public final class FactionSavedData extends WorldSavedData {
    public static final String ID = "hfr_clowder";
    private CompoundNBT payload = new CompoundNBT();
    public FactionSavedData() { super(ID); }
    public static FactionSavedData get(MinecraftServer server) {
        return SavedDataFiles.get(server, ID, FactionSavedData::new);
    }
    @Override public void load(CompoundNBT nbt) {
        if (!nbt.contains("clowderCount", 3) || nbt.getInt("clowderCount") < 0 || nbt.getInt("territory_count") < 0)
            throw new IllegalArgumentException("Invalid Clowder data counts");
        payload = nbt.copy();
    }
    @Override public CompoundNBT save(CompoundNBT nbt) { nbt.merge(payload.copy()); return nbt; }
    public List<String> names() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < payload.getInt("clowderCount"); i++) result.add(payload.getString(i + "_name"));
        return Collections.unmodifiableList(result);
    }
    public String describe(String name) {
        for (int i = 0; i < payload.getInt("clowderCount"); i++) {
            if (!payload.getString(i + "_name").equalsIgnoreCase(name)) continue;
            String prefix = i + "_";
            return payload.getString(prefix + "name") + " | members: " + payload.getInt(prefix + "members")
                    + " | leader: " + payload.getString(prefix + "leader") + " | capital: "
                    + (payload.getBoolean(prefix + "capitalDesignated") ? payload.getString(prefix + "capitalCityId") : "not designated");
        }
        return "Unknown faction: " + name;
    }
    public String membership(ServerPlayerEntity player) {
        String mode = FoundationConfig.IDENTITY_MODE.get();
        boolean names = "NAME".equalsIgnoreCase(mode) || ("AUTO".equalsIgnoreCase(mode)
                && (!FMLEnvironment.production || !player.getServer().usesAuthentication()));
        String profileName = player.getGameProfile().getName();
        UUID uuid = player.getUUID();
        for (int i = 0; i < payload.getInt("clowderCount"); i++) {
            String prefix = i + "_";
            ListNBT members = payload.getList(prefix + "identityMembers", 10);
            for (int j = 0; j < members.size(); j++) {
                CompoundNBT record = members.getCompound(j);
                boolean matches = names ? record.getString("lastKnownName").equalsIgnoreCase(profileName)
                        : record.getString("uuid").equals(uuid.toString());
                if (matches) {
                    try {
                        FactionMemberRecord member = new FactionMemberRecord(UUID.fromString(record.getString("uuid")),
                                record.getString("lastKnownName"), record.getLong("joinedAt"), FactionRole.valueOf(record.getString("role")));
                        return payload.getString(prefix + "name") + " / " + member.role;
                    }
                    catch (IllegalArgumentException invalid) { Xenofactions.LOGGER.warn("Invalid member role in faction {}", payload.getString(prefix + "name")); }
                }
            }
            if (names) {
                for (int j = 0; j < payload.getInt(prefix + "members"); j++) {
                    if (profileName.equalsIgnoreCase(payload.getString(prefix + j))) return payload.getString(prefix + "name") + " / legacy name membership";
                }
            }
        }
        return "No resolved faction membership";
    }
    public List<String> cities() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < payload.getInt("territory_count"); i++) {
            String code = "meta_" + i;
            String id = payload.getString("cityId_" + code);
            if (id.isEmpty()) continue;
            String city = payload.getString("cityName_" + code);
            String line = city + " | " + id + " | " + payload.getString("ownership_" + code + "_owner")
                    + " | legacy dimension " + payload.getInt("dim_" + code);
            if (!result.contains(line)) result.add(line);
        }
        return Collections.unmodifiableList(result);
    }
}
