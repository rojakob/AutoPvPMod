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
    public static final String VERSION = "1.5"; // Версия с Aimbot

    private static KeyBinding keyToggleMod;
    private static KeyBinding keyToggleDelay;
    
    private static boolean isModActive = false;
    private static int attackTimer = 0;
    
    // Динамическая задержка
    private static int ATTACK_DELAY = 13; 

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        keyToggleMod = new KeyBinding("key.autopvp.toggle", Keyboard.KEY_X, "key.categories.combat");
        keyToggleDelay = new KeyBinding("key.autopvp.delay", Keyboard.KEY_R, "key.categories.combat");
        
        ClientRegistry.registerKeyBinding(keyToggleMod);
        ClientRegistry.registerKeyBinding(keyToggleDelay);
        
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP Mod v1.5 (Aimbot Edition) загружен!");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (keyToggleMod.isPressed()) {
            isModActive = !isModActive;
            String msg = "AutoPvP: " + (isModActive ? "§aВключен" : "§cВыключен");
            mc.player.sendMessage(new TextComponentString(msg));
            attackTimer = 0; 
        }

        if (keyToggleDelay.isPressed()) {
            ATTACK_DELAY = (ATTACK_DELAY == 13) ? 4 : 13;
            String msg = "§eAutoPvP: Задержка переключена на §b" + ATTACK_DELAY + " §eтиков";
            mc.player.sendMessage(new TextComponentString(msg));
            attackTimer = 0; 
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;
        
        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isModActive) return;

        // --- 🤖 ЛОГИКА AIMBOT (Удержание Left Alt) ---
        // Оптимизация: проверяем нажатие клавиши ДО того, как искать цели
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU)) {
            Entity aimTarget = findAimTarget(player);
            if (aimTarget != null) {
                // Математика наведения
                double dx = aimTarget.posX - player.posX;
                double dy = (aimTarget.posY + (aimTarget.height / 2.0)) - (player.posY + player.getEyeHeight());
                double dz = aimTarget.posZ - player.posZ;
                double dist = Math.sqrt(dx * dx + dz * dz);

                // Вычисляем углы
                float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
                float targetPitch = (float) (-(Math.atan2(dy, dist) * 180.0D / Math.PI));

                // Мгновенно поворачиваем камеру (с защитой от рывков при переходе через 180 градусов)
                player.rotationYaw = targetYaw;
                player.rotationPitch = targetPitch;
                
                // Синхронизируем прошлые углы, чтобы не было визуального "дрожания"
                player.prevRotationYaw = targetYaw;
                player.prevRotationPitch = targetPitch;
            }
        }

        // --- ⚔️ ЛОГИКА АТАКИ ---
        if (attackTimer < ATTACK_DELAY) {
            attackTimer++;
        }
        if (attackTimer < ATTACK_DELAY) return;

        // Ищем цель для удара (луч взгляда, который мы только что повернули)
        Entity target = findAttackTarget(player);
        if (target == null) return;

        Minecraft.getMinecraft().playerController.attackEntity(player, target);
        attackTimer = 0; 
    }

    // 🎯 Поиск цели для Aimbot (ищет ближайшего врага в радиусе 6 блоков, без проверки взгляда)
    private Entity findAimTarget(EntityPlayer player) {
        double reach = 6.0; // Радиус поиска для наведения
        AxisAlignedBB area = player.getEntityBoundingBox().grow(reach);
        List<EntityLivingBase> entities = player.world.getEntitiesWithinAABB(EntityLivingBase.class, area, e -> e != player && e.isEntityAlive());
        
        Entity bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (EntityLivingBase entity : entities) {
            // Проверяем дистанцию от глаз игрока до центра врага
            double dist = player.getDistance(entity);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }

    // 🔍 Поиск цели для Атаки (только та, на которую направлен прицел)
    private Entity findAttackTarget(EntityPlayer player) {
        double reach = 3.0; 
        AxisAlignedBB area = player.getEntityBoundingBox().grow(reach + 1.0);
        List<EntityLivingBase> entities = player.world.getEntitiesWithinAABB(EntityLivingBase.class, area, e -> e != player && e.isEntityAlive());
        
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLookVec();
        Vec3d reachVec = eyePos.add(lookVec.scale(reach)); 

        Entity bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (EntityLivingBase entity : entities) {
            double distanceToHitbox = player.getDistance(entity) - (entity.width / 2.0);
            if (distanceToHitbox > reach) continue;

            RayTraceResult intercept = entity.getEntityBoundingBox().grow(0.1).calculateIntercept(eyePos, reachVec);
            if (intercept == null) continue; 

            if (distanceToHitbox < bestDistance) {
                bestDistance = distanceToHitbox;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }
}
