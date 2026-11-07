package ru.tlauncher.safewalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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
    public static final String VERSION = "2.4";

    private static KeyBinding keyToggleMod;
    private static KeyBinding keyToggleCrit;

    private static boolean isModActive = false;
    private static boolean isCritActive = false;
    private static boolean waitingForFall = false; // Флаг: мы прыгнули и ждем фазу падения

    private static int attackTimer = 0;
    private static final int ATTACK_DELAY = 12; // 12 тиков = 100% заряда меча (1.6 speed)

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        keyToggleMod = new KeyBinding("key.autopvp.toggle", Keyboard.KEY_X, "key.categories.combat");
        keyToggleCrit = new KeyBinding("key.autopvp.crit", Keyboard.KEY_R, "key.categories.combat");
        
        ClientRegistry.registerKeyBinding(keyToggleMod);
        ClientRegistry.registerKeyBinding(keyToggleCrit);
        
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP Mod v2.4 (Survival Edition) загружен!");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // Включение/выключение мода на X
        if (keyToggleMod.isPressed()) {
            isModActive = !isModActive;
            String msg = "AutoPvP: " + (isModActive ? "§aВключен" : "§cВыключен");
            mc.player.sendMessage(new TextComponentString(msg));
            
            // При выключении мода сбрасываем криты и ожидания
            if (!isModActive) {
                isCritActive = false;
                waitingForFall = false;
                mc.player.sendMessage(new TextComponentString("§eКритические прыжки: §cАвтоматически выключены"));
            }
            attackTimer = 0; 
        }

        // Включение/выключение критов на R
        if (keyToggleCrit.isPressed()) {
            if (!isModActive) {
                mc.player.sendMessage(new TextComponentString("§cСначала включите AutoPvP (Клавиша X)"));
                return;
            }
            isCritActive = !isCritActive;
            waitingForFall = false; // Сбрасываем ожидание при переключении
            String msg = "§eКритические прыжки: " + (isCritActive ? "§aВключены" : "§cВыключены");
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Работаем только на клиенте и в конце тика
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;
        
        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isModActive) return;

        // --- Логика идеального критического удара ---
        if (waitingForFall) {
            // Крит засчитывается, когда мы уже начали падать (motionY < 0) и мы в воздухе
            if (player.motionY < 0 && !player.onGround) {
                Entity target = findTarget(player);
                if (target != null) {
                    performAttack(player, target);
                    waitingForFall = false;
                    attackTimer = 0; // Таймер сбрасывается после успешного удара
                } else {
                    waitingForFall = false; // Цель убежала
                }
            } else if (player.onGround) {
                waitingForFall = false; // Приземлились, удар не удался
            }
            return; // Пока ждем падения, другие действия не выполняем
        }

        // --- Логика накопления таймера атаки ---
        if (attackTimer < ATTACK_DELAY) {
            attackTimer++;
        }
        if (attackTimer < ATTACK_DELAY) return; // Бьем только на 12-м тике

        Entity target = findTarget(player);
        if (target == null) return;

        // --- Логика нанесения удара ---
        if (isCritActive && player.onGround && !player.isInWater() && !player.isOnLadder()) {
            // Если криты включены и мы на земле — прыгаем и ЖДЕМ падения
            player.jump();
            waitingForFall = true;
        } else {
            // Обычный удар (без критов)
            performAttack(player, target);
            attackTimer = 0;
        }
    }

    // 🔍 Поиск цели строго для выживания (дальность 3 блока)
    private Entity findTarget(EntityPlayer player) {
        double reach = 3.0; // Ванильная дальность атаки в выживании
        AxisAlignedBB area = player.getEntityBoundingBox().grow(reach + 1.0);
        List<EntityLivingBase> entities = player.world.getEntitiesWithinAABB(EntityLivingBase.class, area, e -> e != player && e.isEntityAlive());
        
        Vec3d lookVec = player.getLookVec();
        Entity bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (EntityLivingBase entity : entities) {
            // Расстояние до хитбокса (учитываем ширину модели моба/игрока)
            double distanceToHitbox = player.getDistance(entity) - (entity.width / 2.0);
            if (distanceToHitbox > reach) continue;

            // Проверка, что цель в поле зрения (угол обзора)
            Vec3d toEntity = new Vec3d(entity.posX - player.posX, entity.posY - player.posY, entity.posZ - player.posZ).normalize();
            if (lookVec.dotProduct(toEntity) < 0.7) continue;

            if (distanceToHitbox < bestDistance) {
                bestDistance = distanceToHitbox;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }

    // ⚔️ Нанесение удара
    private void performAttack(EntityPlayer player, Entity target) {
        // Взмах рукой для визуала
        player.swingArm(EnumHand.MAIN_HAND);
        
        // Стандартный клиентский метод атаки (идеально работает в выживании в пределах 3 блоков)
        Minecraft.getMinecraft().playerController.attackEntity(player, target);
    }
}
