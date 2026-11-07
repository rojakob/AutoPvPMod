package ru.tlauncher.safewalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
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
    public static final String VERSION = "1.9";

    private static KeyBinding keyToggleMod;
    private static KeyBinding keyToggleDelay;
    
    private static boolean isModActive = false;
    private static int attackTimer = 0;
    private static int ATTACK_DELAY = 13; 

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        keyToggleMod = new KeyBinding("key.autopvp.toggle", Keyboard.KEY_X, "key.categories.combat");
        keyToggleDelay = new KeyBinding("key.autopvp.delay", Keyboard.KEY_R, "key.categories.combat");
        
        ClientRegistry.registerKeyBinding(keyToggleMod);
        ClientRegistry.registerKeyBinding(keyToggleDelay);
        
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP Mod v1.9 (Fully Optimized) загружен!");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (keyToggleMod.isPressed()) {
            isModActive = !isModActive;
            mc.player.sendMessage(new TextComponentString("AutoPvP: " + (isModActive ? "§aВключен" : "§cВыключен")));
            attackTimer = 0; 
        }

        if (keyToggleDelay.isPressed()) {
            ATTACK_DELAY = (ATTACK_DELAY == 13) ? 4 : 13;
            mc.player.sendMessage(new TextComponentString("§eAutoPvP: Задержка переключена на §b" + ATTACK_DELAY + " §eтиков"));
            attackTimer = 0; 
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;
        
        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isModActive) return;

        // Накапливаем таймер
        if (attackTimer < ATTACK_DELAY) {
            attackTimer++;
        }

        boolean isAimbotActive = Keyboard.isKeyDown(Keyboard.KEY_LMENU);
        boolean isAttackReady = (attackTimer >= ATTACK_DELAY);

        // ⚡ ОПТИМИЗАЦИЯ: Если нам не нужно искать цели, мы вообще не трогаем мир
        if (!isAimbotActive && !isAttackReady) return;

        // ⚡ ОПТИМИЗАЦИЯ: Сканируем мир ОДИН раз за тик, а не дважды
        double scanRadius = isAimbotActive ? 6.0 : 3.5; 
        AxisAlignedBB area = player.getEntityBoundingBox().grow(scanRadius);
        List<EntityLivingBase> entities = player.world.getEntitiesWithinAABB(EntityLivingBase.class, area, e -> e != player && e.isEntityAlive());

        // --- 🤖 ЛОГИКА AIMBOT ---
        if (isAimbotActive) {
            Entity aimTarget = findClosestTarget(player, entities, 6.0);
            if (aimTarget != null) {
                double dx = aimTarget.posX - player.posX;
                double dy = (aimTarget.posY + (aimTarget.height / 2.0)) - (player.posY + player.getEyeHeight());
                double dz = aimTarget.posZ - player.posZ;
                double dist = Math.sqrt(dx * dx + dz * dz); // Тут sqrt нужен для угла

                float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
                float targetPitch = (float) (-(Math.atan2(dy, dist) * 180.0D / Math.PI));

                player.rotationYaw = targetYaw;
                player.rotationPitch = targetPitch;
                player.prevRotationYaw = targetYaw;
                player.prevRotationPitch = targetPitch;

                Minecraft.getMinecraft().getConnection().sendPacket(
                    new net.minecraft.network.play.client.CPacketPlayer.Rotation(targetYaw, targetPitch, player.onGround)
                );
            }
        }

        // --- ⚔️ ЛОГИКА АТАКИ ---
        if (isAttackReady) {
            Entity target = findAttackTarget(player, entities, 3.5);
            if (target != null) {
                // Спуфим прицел, чтобы клиент отправил пакет атаки на сервер
                RayTraceResult originalMouseOver = Minecraft.getMinecraft().objectMouseOver;
                Minecraft.getMinecraft().objectMouseOver = new RayTraceResult(target);

                Minecraft.getMinecraft().playerController.attackEntity(player, target);

                Minecraft.getMinecraft().objectMouseOver = originalMouseOver;
                attackTimer = 0; 
            }
        }
    }

    // ⚡ ОПТИМИЗИРОВАНО: Используем getDistanceSq (без квадратного корня)
    private Entity findClosestTarget(EntityPlayer player, List<EntityLivingBase> entities, double reach) {
        Entity bestTarget = null;
        double bestDistanceSq = reach * reach;

        for (EntityLivingBase entity : entities) {
            double distSq = player.getDistanceSq(entity);
            if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }

    // ⚡ ОПТИМИЗИРОВАНО: Используем getDistanceSq для отсева перед тяжелым RayTrace
    private Entity findAttackTarget(EntityPlayer player, List<EntityLivingBase> entities, double reach) {
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLookVec();
        Vec3d reachVec = eyePos.add(lookVec.scale(reach)); 

        Entity bestTarget = null;
        double bestDistanceSq = reach * reach;

        for (EntityLivingBase entity : entities) {
            // Быстрая проверка (без sqrt). Отсекаем тех, кто слишком далеко
            double distSq = player.getDistanceSq(entity);
            if (distSq > bestDistanceSq) continue;

            // Тяжелая проверка (RayTrace), только если цель прошла быструю проверку
            RayTraceResult intercept = entity.getEntityBoundingBox().grow(0.1).calculateIntercept(eyePos, reachVec);
            if (intercept == null) continue; 

            bestDistanceSq = distSq;
            bestTarget = entity;
        }
        return bestTarget;
    }
}
