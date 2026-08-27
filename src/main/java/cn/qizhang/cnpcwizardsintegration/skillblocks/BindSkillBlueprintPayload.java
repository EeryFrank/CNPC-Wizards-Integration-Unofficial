package cn.qizhang.cnpcwizardsintegration.skillblocks;

import cn.qizhang.cnpcwizardsintegration.CnPcWizardsIntegration;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client-to-server request containing one visual program and the looked-at entity id. */
public record BindSkillBlueprintPayload(int entityId, String blueprintJson) implements CustomPayload {
    public static final CustomPayload.Id<BindSkillBlueprintPayload> ID = new CustomPayload.Id<>(
            Identifier.of(CnPcWizardsIntegration.MOD_ID, "bind_skill_blueprint"));
    public static final PacketCodec<RegistryByteBuf, BindSkillBlueprintPayload> CODEC = PacketCodec.of(
            (payload, buffer) -> {
                buffer.writeVarInt(payload.entityId());
                buffer.writeString(payload.blueprintJson(), SkillBlueprintValidator.MAX_JSON_LENGTH);
            },
            buffer -> new BindSkillBlueprintPayload(
                    buffer.readVarInt(),
                    buffer.readString(SkillBlueprintValidator.MAX_JSON_LENGTH)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
