package ru.tlauncher.safewalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;

@Mod(modid = AutoPvPMod.MODID, name = AutoPvPMod.NAME, version = AutoPvPMod.VERSION)
public class AutoPvPMod {

    public static final String MODID = "autopvp";
    public static final String NAME = "Auto PvP";
    public static final String VERSION = "2.1";

    private static KeyBinding keyToggleMod;
    private static KeyBinding keyToggleCrit;

    private static boolean isModActive = false;
    private static boolean isCritActive = false;
    private static boolean waitingForFall = false;

    private static int attackTimer = 0;
    private static final int ATTACK_DELAY = 12;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        keyToggleMod = new KeyBinding("key.autopvp.toggle", Keyboard.KEY_X, "key.categories.combat");
        keyToggleCrit = new KeyBinding("key.autopvp.crit", Keyboard.KEY_R, "key.categories.combat");
        
        ClientRegistry.registerKeyBinding(keyToggleMod);
        ClientRegistry.registerKeyBinding(keyToggleCrit);
        
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP Mod v2.1 (CPacketUseEntity Fix) загружен!");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (keyToggleMod.isPressed()) {
            isModActive = !isModActive;
            String msg = "AutoPvP: " + (isModActive ? "§aВключен" : "§cВыключен");
            mc.player.sendMessage(new TextComponentString(msg));
            if (!isModActive) {
                isCritActive = false;
                waitingForFall = false;
                mc.player.sendMessage(new TextComponentString("§eКритические прыжки: §cАвтоматически выключены"));
            }
            attackTimer = 0; 
        }

        if (keyToggleCrit.isPressed()) {
            if (!isModActive) {
                mc.player.sendMessage(new TextComponentString("§cСначала включите AutoPvP (Клавиша X)"));
                return;
            }
            isCritActive = !isCritActive;
            waitingForFall = false;
            String msg = "§eКритические прыжки: " + (isCritActive ? "§aВключены" : "§cВыключены");
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;
        
        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isModActive) return;

        if (waitingForFall) {
            if (player.motionY < 0 && !player.onGround) {
                Entity target = findTarget(player);
                if (target != null) {
                    performAttack(player, target);
                    waitingForFall = false;
                    attackTimer = 0;
                } else {
                    waitingForFall = false;
                }
            } else if (player.onGround) {
                waitingForFall = false;
            }
            return;
        }

        if (attackTimer < ATTACK_DELAY) {
            attackTimer++;
        }
        if (attackTimer < ATTACK_DELAY) return;

        Entity target = findTarget(player);
        if (target == null) return;

        if (isCritActive && player.onGround && !player.isInWater() && !player.isOnLadder()) {
            player.jump();
            waitingForFall = true;
        } else {
            performAttack(player, target);
            attackTimer = 0;
        }
    }

    private Entity findTarget(EntityPlayer player) {
        double reach = 6.0;
        AxisAlignedBB area = player.getEntityBoundingBox().grow(reach + 1.0);
        List<EntityLivingBase> entities = player.world.getEntitiesWithinAABB(EntityLivingBase.class, area, e -> e != player && e.isEntityAlive());
        
        Vec3d lookVec = player.getLookVec();
        Entity bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (EntityLivingBase entity : entities) {
            double distanceToHitbox = player.getDistance(entity) - (entity.width / 2.0);
            if (distanceToHitbox > reach) continue;

            Vec3d toEntity = new Vec3d(entity.posX - player.posX, entity.posY - player.posY, entity.posZ - player.posZ).normalize();
            if (lookVec.dotProduct(toEntity) < 0.7) continue;

            if (distanceToHitbox < bestDistance) {
                bestDistance = distanceToHitbox;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }

    private void performAttack(EntityPlayer player, Entity target) {
        player.swingArm(EnumHand.MAIN_HAND);
        Minecraft.getMinecraft().getConnection().sendPacket(
            new CPacketUseEntity(target, CPacketUseEntity.Action.ATTACK)
        );
    }
}
