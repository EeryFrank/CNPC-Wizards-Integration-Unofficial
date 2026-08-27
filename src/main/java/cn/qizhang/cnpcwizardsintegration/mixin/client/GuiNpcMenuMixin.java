package cn.qizhang.cnpcwizardsintegration.mixin.client;

import cn.qizhang.cnpcwizardsintegration.client.SkillBlockEditorScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a non-invasive optional tab to the CustomNPCs NPC editor without linking its classes. */
@Pseudo
@Mixin(targets = "noppes.npcs.client.gui.util.GuiNpcMenu", remap = false)
public abstract class GuiNpcMenuMixin {
    private static final Identifier CNPC_WIZARDS_TAB_TEXTURE = Identifier.of(
            "customnpcs", "textures/gui/menutopbutton.png");
    private static final int CNPC_WIZARDS_TAB_WIDTH = 66;
    private int cnpcWizards$tabX;
    private int cnpcWizards$tabY;

    @Inject(method = "initGui", at = @At("TAIL"), remap = false)
    private void cnpcWizards$placeProgrammingTab(int guiLeft, int guiTop, int guiWidth, CallbackInfo callback) {
        cnpcWizards$tabX = guiLeft + guiWidth - 140;
        cnpcWizards$tabY = guiTop - 17;
    }

    @Inject(method = "drawElements", at = @At("TAIL"), remap = false)
    private void cnpcWizards$drawProgrammingTab(
            DrawContext context,
            TextRenderer textRenderer,
            int mouseX,
            int mouseY,
            MinecraftClient client,
            float delta,
            CallbackInfo callback) {
        boolean hovered = cnpcWizards$isInside(mouseX, mouseY);
        int textureV = hovered ? 40 : 20;
        int leftWidth = CNPC_WIZARDS_TAB_WIDTH / 2;
        int rightWidth = CNPC_WIZARDS_TAB_WIDTH - leftWidth;
        context.drawTexture(
                CNPC_WIZARDS_TAB_TEXTURE,
                cnpcWizards$tabX,
                cnpcWizards$tabY,
                0,
                textureV,
                leftWidth,
                20);
        context.drawTexture(
                CNPC_WIZARDS_TAB_TEXTURE,
                cnpcWizards$tabX + leftWidth,
                cnpcWizards$tabY,
                200 - rightWidth,
                textureV,
                rightWidth,
                20);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("menu.cnpc_wizards_integration.programming_skills"),
                cnpcWizards$tabX + CNPC_WIZARDS_TAB_WIDTH / 2,
                cnpcWizards$tabY + 6,
                hovered ? 0xFFFFA0 : 0xE0E0E0);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcWizards$openProgrammingTab(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> callback) {
        if (button != 0 || !cnpcWizards$isInside(mouseX, mouseY)) {
            return;
        }
        try {
            Object menu = this;
            Object parent = cnpcWizards$readField(menu, "parent");
            Object npc = cnpcWizards$readField(menu, "npc");
            if (!(parent instanceof Screen parentScreen) || !(npc instanceof LivingEntity livingNpc)) {
                return;
            }
            Method save = parent.getClass().getMethod("save");
            save.invoke(parent);
            MinecraftClient.getInstance().setScreen(new SkillBlockEditorScreen(
                    livingNpc.getId(),
                    livingNpc.getName().getString(),
                    parentScreen));
            callback.setReturnValue(true);
        }
        catch (ReflectiveOperationException error) {
            // Leave the original CustomNPCs screen usable if its private menu layout changes.
        }
    }

    private boolean cnpcWizards$isInside(double mouseX, double mouseY) {
        return mouseX >= cnpcWizards$tabX
                && mouseX < cnpcWizards$tabX + CNPC_WIZARDS_TAB_WIDTH
                && mouseY >= cnpcWizards$tabY
                && mouseY < cnpcWizards$tabY + 20;
    }

    private static Object cnpcWizards$readField(Object instance, String name) throws ReflectiveOperationException {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }
}
