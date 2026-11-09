package ru.tlauncher.safewalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Comparator;
import java.util.List;

@Mod(modid = AutoPvPMod.MODID, name = AutoPvPMod.NAME, version = AutoPvPMod.VERSION)
public class AutoPvPMod {

    public static final String MODID = "autopvp";
    public static final String NAME = "Auto PvP";
    public static final String VERSION = "3.0.2";

    private static KeyBinding keyToggleAura;
    private static KeyBinding keyToggleCrits;

    private static boolean isAuraActive = false;
    private static boolean isCritsActive = false;

    private static final double ATTACK_RANGE = 3.8;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        keyToggleAura = new KeyBinding("key.autopvp.aura", Keyboard.KEY_X, "key.categories.combat");
        keyToggleCrits = new KeyBinding("key.autopvp.crits", Keyboard.KEY_R, "key.categories.combat");

        ClientRegistry.registerKeyBinding(keyToggleAura);
        ClientRegistry.registerKeyBinding(keyToggleCrits);

        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP Mod v3.0.2 (Aimbot Edition) загружен!");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (keyToggleAura.isPressed()) {
            isAuraActive = !isAuraActive;
            mc.player.sendMessage(new TextComponentString("KillAura: " + (isAuraActive ? "§aВключена" : "§cВыключена")));
            if (!isAuraActive) {
                isCritsActive = false;
                mc.player.sendMessage(new TextComponentString("§eCriticals: §cАвтоматически выключены"));
            }
        }

        if (keyToggleCrits.isPressed()) {
            if (!isAuraActive) {
                mc.player.sendMessage(new TextComponentString("§cСначала включите KillAura (Клавиша X)"));
                return;
            }
            isCritsActive = !isCritsActive;
            mc.player.sendMessage(new TextComponentString("Criticals: " + (isCritsActive ? "§aВключены" : "§cВыключены")));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;

        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isAuraActive) return;

        if (player.getCooledAttackStrength(0) < 1.0F) {
            return;
        }

        EntityLivingBase target = findClosestTarget(player);
        if (target == null) return;

        if (isCritsActive && player.onGround && !player.isInWater() && !player.isOnLadder()) {
            player.jump();
        }

        Minecraft.getMinecraft().playerController.attackEntity(player, target);
        player.swingArm(EnumHand.MAIN_HAND);
    }

    private EntityLivingBase findClosestTarget(EntityPlayer player) {
        List<Entity> entities = player.world.getEntitiesWithinAABBExcludingEntity(
                player,
                player.getEntityBoundingBox().grow(ATTACK_RANGE)
        );

        return entities.stream()
                .filter(e -> e instanceof EntityLivingBase)
                .map(e -> (EntityLivingBase) e)
                .filter(e -> e.isEntityAlive() && e.getDistance(player) <= ATTACK_RANGE)
                .min(Comparator.comparingDouble(e -> e.getDistanceSq(player)))
                .orElse(null);
    }

    @SubscribeEvent
    public void onAimbotTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;

        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player) return;

        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU)) {
            EntityLivingBase aimTarget = findAimTarget(player);
            if (aimTarget != null) {
                double dx = aimTarget.posX - player.posX;
                double dy = (aimTarget.posY + (aimTarget.height / 2.0)) - (player.posY + player.getEyeHeight());
                double dz = aimTarget.posZ - player.posZ;
                double dist = Math.sqrt(dx * dx + dz * dz);

                float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
                float targetPitch = (float) (-(Math.atan2(dy, dist) * 180.0D / Math.PI));

                player.rotationYaw = targetYaw;
                player.rotationPitch = targetPitch;
                player.prevRotationYaw = targetYaw;
                player.prevRotationPitch = targetPitch;
            }
        }
    }

    private EntityLivingBase findAimTarget(EntityPlayer player) {
        double reach = 6.0;
        List<Entity> entities = player.world.getEntitiesWithinAABBExcludingEntity(
                player,
                player.getEntityBoundingBox().grow(reach)
        );

        return entities.stream()
                .filter(e -> e instanceof EntityLivingBase)
                .map(e -> (EntityLivingBase) e)
                .filter(e -> e.isEntityAlive() && e.getDistance(player) <= reach)
                .min(Comparator.comparingDouble(e -> e.getDistanceSq(player)))
                .orElse(null);
    }
}
