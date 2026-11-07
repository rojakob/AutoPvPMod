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
    public static final String VERSION = "1.4"; // Версия с фиксом наведения

    private static KeyBinding keyToggleMod;
    private static KeyBinding keyToggleDelay;
    
    private static boolean isModActive = false;
    private static int attackTimer = 0;
    
    // Динамическая задержка (по умолчанию 13 тиков для 1.12)
    private static int ATTACK_DELAY = 13; 

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Клавиша X - вкл/выкл мод
        keyToggleMod = new KeyBinding("key.autopvp.toggle", Keyboard.KEY_X, "key.categories.combat");
        // Клавиша R - смена задержки (13 <-> 4)
        keyToggleDelay = new KeyBinding("key.autopvp.delay", Keyboard.KEY_R, "key.categories.combat");
        
        ClientRegistry.registerKeyBinding(keyToggleMod);
        ClientRegistry.registerKeyBinding(keyToggleDelay);
        
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP Mod v1.4 (Raycast Fix) загружен!");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // Обработка клавиши X (Вкл/Выкл мод)
        if (keyToggleMod.isPressed()) {
            isModActive = !isModActive;
            String msg = "AutoPvP: " + (isModActive ? "§aВключен" : "§cВыключен");
            mc.player.sendMessage(new TextComponentString(msg));
            
            attackTimer = 0; // Сбрасываем таймер
        }

        // Обработка клавиши R (Переключение задержки)
        if (keyToggleDelay.isPressed()) {
            ATTACK_DELAY = (ATTACK_DELAY == 13) ? 4 : 13;
            
            String msg = "§eAutoPvP: Задержка переключена на §b" + ATTACK_DELAY + " §eтиков";
            mc.player.sendMessage(new TextComponentString(msg));
            
            attackTimer = 0; // Сбрасываем таймер, чтобы новая задержка применилась сразу
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Работаем только на клиенте и в конце тика
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;
        
        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isModActive) return;

        // Накапливаем таймер атаки
        if (attackTimer < ATTACK_DELAY) {
            attackTimer++;
        }
        if (attackTimer < ATTACK_DELAY) return; // Ждем нужное количество тиков

        // Ищем цель
        Entity target = findTarget(player);
        if (target == null) return;

        // Наносим удар
        Minecraft.getMinecraft().playerController.attackEntity(player, target);
        attackTimer = 0; // Сбрасываем таймер после успешного удара
    }

    // 🔍 Поиск цели через пересечение луча взгляда с хитбоксом (работает под любым углом)
    private Entity findTarget(EntityPlayer player) {
        double reach = 3.0; 
        AxisAlignedBB area = player.getEntityBoundingBox().grow(reach + 1.0);
        List<EntityLivingBase> entities = player.world.getEntitiesWithinAABB(EntityLivingBase.class, area, e -> e != player && e.isEntityAlive());
        
        // Берем позицию глаз игрока
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLookVec();
        Vec3d reachVec = eyePos.add(lookVec.scale(reach)); // Конечная точка луча

        Entity bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (EntityLivingBase entity : entities) {
            // Быстрая проверка дистанции до хитбокса (отсекаем слишком далеких)
            double distanceToHitbox = player.getDistance(entity) - (entity.width / 2.0);
            if (distanceToHitbox > reach) continue;

            // Проверяем, пересекает ли луч взгляда хитбокс сущности (с запасом 0.1 блока)
            RayTraceResult intercept = entity.getEntityBoundingBox().grow(0.1).calculateIntercept(eyePos, reachVec);
            if (intercept == null) continue; // Если луч не попал в хитбокс — пропускаем

            if (distanceToHitbox < bestDistance) {
                bestDistance = distanceToHitbox;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }
}
